package de.swiftbyte.gmc.daemon.tasks.consumers;

import de.swiftbyte.gmc.common.entity.NodeSettings;
import de.swiftbyte.gmc.common.model.NodeTask;
import de.swiftbyte.gmc.common.packet.from.bidirectional.node.NodeSettingsPacket;
import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.daemon.service.BackupService;
import de.swiftbyte.gmc.daemon.stomp.StompHandler;
import de.swiftbyte.gmc.daemon.tasks.NodeTaskConsumer;
import de.swiftbyte.gmc.daemon.utils.NodeUtils;
import lombok.CustomLog;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

@CustomLog
public class BackupDirectoryChangeTaskConsumer implements NodeTaskConsumer {

    @Override
    public void run(@NonNull NodeTask task, @Nullable Object payload) {
        if (!(payload instanceof BackupDirectoryChangeTaskPayload(Path oldBackupPath, Path newBackupPath))) {
            throw new IllegalArgumentException("Expected BackupDirectoryChangeTaskPayload");
        }

        if (Node.INSTANCE == null) {
            throw new IllegalArgumentException("Node is not initialized yet");
        }

        try {
            log.info("Starting BACKUP_DIRECTORY_CHANGE task: {} -> {}", oldBackupPath, newBackupPath);
            BackupService.suspendBackups();
            BackupService.moveBackupsDirectory(oldBackupPath, newBackupPath);
            NodeUtils.cacheInformation(Node.INSTANCE);
            log.info("BACKUP_DIRECTORY_CHANGE task finished successfully.");
        } catch (Exception e) {
            try {
                Node.INSTANCE.setBackupPath(oldBackupPath);
            } catch (Exception ignored) {
            }
            // Inform backend to rollback settings
            try {
                NodeSettingsPacket packet = getRollbackPacket(Node.INSTANCE, oldBackupPath);
                StompHandler.send("/app/node/settings", packet);
                log.info("Rollback NodeSettingsPacket sent to backend (backup path reverted to '{}').", oldBackupPath);
            } catch (Exception ex) {
                log.warn("Failed to send rollback NodeSettingsPacket to backend.", ex);
            }
            throw new RuntimeException("Failed to move backups directory", e);
        } finally {
            BackupService.resumeBackups();
        }
    }

    private static @NonNull NodeSettingsPacket getRollbackPacket(@NonNull Node node, @NonNull Path oldBackupPath) {
        NodeSettings rollback = new NodeSettings();
        rollback.setName(node.getNodeName());
        rollback.setEnableAutoUpdate(node.isAutoUpdateEnabled());
        rollback.setManageFirewallAutomatically(node.isManageFirewallAutomatically());
        // Ensure defaultServerDirectory is preserved during rollback

        rollback.setDefaultServerDirectory(node.getDefaultServerDirectory().toString());

        // Revert backups dir to the previous value
        rollback.setServerBackupsDirectory(oldBackupPath.toString());

        NodeSettingsPacket packet = new NodeSettingsPacket();
        packet.setNodeSettings(rollback);
        return packet;
    }

    public record BackupDirectoryChangeTaskPayload(Path oldBackupPath, Path newBackupPath) {
    }
}
