package de.swiftbyte.gmc.daemon.tasks.consumers;

import de.swiftbyte.gmc.common.entity.GameServerState;
import de.swiftbyte.gmc.common.model.NodeTask;
import de.swiftbyte.gmc.common.packet.from.daemon.server.ServerChangeDirectoryFailedPacket;
import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.stomp.StompHandler;
import de.swiftbyte.gmc.daemon.tasks.NodeTaskConsumer;
import de.swiftbyte.gmc.daemon.service.TaskService;
import de.swiftbyte.gmc.daemon.utils.NodeUtils;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;

@Slf4j
public class ServerDirectoryChangeTaskConsumer implements NodeTaskConsumer {

    @Override
    public void run(NodeTask task, Object payload) {
        if (!(payload instanceof ServerDirectoryChangeTaskPayload p)) {
            throw new IllegalArgumentException("Expected ServerDirectoryChangeTaskPayload");
        }

        GameServer server = GameServer.getServerById(p.serverId());
        if (server == null) {
            throw new IllegalArgumentException("Server not found: " + p.serverId());
        }

        log.info("Starting SERVER_DIRECTORY_CHANGE task for '{}' ({} -> {}).", server.getFriendlyName(), p.oldParentDir(), p.newParentDir());

        // Precompute paths and destination existence for guarded cleanup
        Path currentInstallDirInitial = server.getInstallDir();
        Path destInstallDirInitial = p.newParentDir().resolve(currentInstallDirInitial.getFileName());
        boolean destExistedBefore = destInstallDirInitial.toFile().exists();

        // Block operations by setting state to CREATING for the duration of the move
        // Precondition: server must be OFFLINE
        try {
            if (server.getState() != GameServerState.OFFLINE) {
                String msg = "Server must be offline to move directory";
                sendFailedPacket(p, msg);
                throw new IllegalStateException(msg);
            }
            server.setState(GameServerState.CREATING);
            // Initialize progress to 0%
            TaskService.updateTaskProgress(task, 0);

            Path currentInstallDir = currentInstallDirInitial;
            Path destInstallDir = destInstallDirInitial;

            // Copy all files first while updating progress; delete source only after successful copy
            // Use 0-85% for copy, 85-100% for deletion
            long totalBytes = calculateTotalBytes(currentInstallDir.toFile());
            copyDirectoryTreeWithProgress(currentInstallDir.toFile(), destInstallDir.toFile(), task, totalBytes, 0, 85);

            // Delete source directory after successful copy with progress for deletion (85-100%)
            deleteDirectoryTreeWithProgress(currentInstallDir.toFile(), task, totalBytes, 85, 15);

            // Update server's install dir only after source was deleted
            Path newAbs = destInstallDir.toAbsolutePath().normalize();
            server.setInstallDir(newAbs);
            NodeUtils.cacheInformation(Node.INSTANCE);

            log.info("SERVER_DIRECTORY_CHANGE task finished successfully for '{}'. New path: {}", server.getFriendlyName(), newAbs);
        } catch (Exception e) {
            try {
                sendFailedPacket(p, e.getMessage());
            } catch (Exception ignored) {}
            // Best-effort cleanup of destination if we created it and the task failed
            if (!destExistedBefore) {
                try {
                    File destDirFile = destInstallDirInitial.toFile();
                    if (destDirFile.exists()) {
                        FileUtils.deleteDirectory(destDirFile);
                        log.info("Cleaned up destination folder '{}' after failed move.", destDirFile.getAbsolutePath());
                    }
                } catch (Exception cleanupEx) {
                    log.warn("Failed to cleanup destination folder '{}' after failed move.", destInstallDirInitial, cleanupEx);
                }
            }
            throw new RuntimeException("Failed to move server directory: " + e.getMessage(), e);
        } finally {
            // Ensure state is returned to OFFLINE after operation
            try { server.setState(GameServerState.OFFLINE); } catch (Exception ignored) {}
        }
    }

    private void copyDirectoryTreeWithProgress(File srcDir, File dstDir, NodeTask task, long totalBytes, int basePercent, int weightPercent) throws IOException {
        if (!srcDir.exists() || !srcDir.isDirectory()) {
            throw new IOException("Source directory not found: " + srcDir.getAbsolutePath());
        }
        if (!dstDir.exists() && !dstDir.mkdirs()) {
            throw new IOException("Failed to create destination directory: " + dstDir.getAbsolutePath());
        }

        if (totalBytes <= 0) {
            // Nothing to copy
            setProgress(task, basePercent + weightPercent);
            return;
        }

        Progress progress = new Progress(totalBytes, basePercent, weightPercent); // scaled to [base..base+weight]
        copyRecursive(srcDir, dstDir, task, progress);

        // Ensure final weighted percent for copy reaches base+weight
        setProgress(task, basePercent + weightPercent);
    }

    private long calculateTotalBytes(File dir) {
        long sum = 0;
        File[] children = dir.listFiles();
        if (children == null) return 0;
        for (File child : children) {
            if (child.isDirectory()) sum += calculateTotalBytes(child);
            else sum += child.length();
        }
        return sum;
    }

    private void copyRecursive(File src, File dst, NodeTask task, Progress progress) throws IOException {
        File[] children = src.listFiles();
        if (children == null) return;
        for (File child : children) {
            File destChild = new File(dst, child.getName());
            if (child.isDirectory()) {
                if (!destChild.exists() && !destChild.mkdirs()) {
                    throw new IOException("Failed to create directory: " + destChild.getAbsolutePath());
                }
                copyRecursive(child, destChild, task, progress);
            } else {
                // Overwrite if exists
                FileUtils.copyFile(child, destChild);
                progress.addBytes(child.length());
                setProgress(task, progress.getPercent());
            }
        }
    }

    private void deleteDirectoryTreeWithProgress(File srcDir, NodeTask task, long totalBytes, int basePercent, int weightPercent) throws IOException {
        // Delete files and directories while reporting progress based on total bytes
        Progress delProgress = new Progress(Math.max(1, totalBytes), basePercent, weightPercent);
        deleteRecursive(srcDir, task, delProgress);
        // Ensure final reaches 100%
        setProgress(task, basePercent + weightPercent);
    }

    private void deleteRecursive(File file, NodeTask task, Progress progress) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child, task, progress);
                }
            }
        }
        long len = file.isFile() ? file.length() : 0;
        if (file.isDirectory()) {
            if (!file.delete()) {
                throw new IOException("Failed to delete directory: " + file.getAbsolutePath());
            }
        } else {
            FileUtils.forceDelete(file);
        }
        if (len > 0) {
            progress.addBytes(len);
            setProgress(task, progress.getPercent());
        }
    }

    private void setProgress(NodeTask task, int percent) {
        try {
            TaskService.updateTaskProgress(task, Math.max(0, Math.min(100, percent)));
        } catch (Exception ignored) {}
    }

    private static class Progress {
        final long total;
        volatile long processed;
        final int base;
        final int weight;
        Progress(long total, int base, int weight) {
            this.total = total;
            this.processed = 0;
            this.base = base;
            this.weight = weight;
        }
        void addBytes(long n) { this.processed += n; }
        int getPercent() {
            if (total <= 0) return base + weight;
            int scaled = (int)Math.round(Math.min(1.0, (processed * 1.0) / total) * weight);
            return Math.min(100, base + scaled);
        }
    }

    private void sendFailedPacket(ServerDirectoryChangeTaskPayload p, String error) {
        ServerChangeDirectoryFailedPacket failedPacket = new ServerChangeDirectoryFailedPacket();
        failedPacket.setServerId(p.serverId());
        failedPacket.setPreviousDirectory(p.oldParentDir().toString());
        failedPacket.setErrorMessage(error);
        StompHandler.send("/app/server/change-directory-failed", failedPacket);
        log.info("ServerChangeDirectoryFailedPacket sent for server {} (previousDirectory={}, error={}).", p.serverId(), p.oldParentDir(), error);
    }

    public record ServerDirectoryChangeTaskPayload(String serverId, Path oldParentDir, Path newParentDir) {}
}
