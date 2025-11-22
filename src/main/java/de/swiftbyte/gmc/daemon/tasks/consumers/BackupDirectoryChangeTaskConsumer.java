package de.swiftbyte.gmc.daemon.tasks.consumers;

import de.swiftbyte.gmc.common.entity.NodeSettings;
import de.swiftbyte.gmc.common.model.NodeTask;
import de.swiftbyte.gmc.common.packet.from.bidirectional.node.NodeSettingsPacket;
import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.daemon.service.BackupService;
import de.swiftbyte.gmc.daemon.stomp.StompHandler;
import de.swiftbyte.gmc.daemon.tasks.NodeTaskConsumer;
import de.swiftbyte.gmc.daemon.utils.NodeUtils;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;

@Slf4j
public class BackupDirectoryChangeTaskConsumer implements NodeTaskConsumer {

    @Override
    public void run(NodeTask task, Object payload) {
        if (!(payload instanceof BackupDirectoryChangeTaskPayload(Path oldBackupPath, Path newBackupPath))) {
            throw new IllegalArgumentException("Expected BackupDirectoryChangeTaskPayload");
        }

        try {
            log.info("Starting BACKUP_DIRECTORY_CHANGE task: {} -> {}", oldBackupPath, newBackupPath);
            BackupService.suspendBackups();
            BackupService.moveBackupsDirectory(oldBackupPath, newBackupPath);
            // Apply new backup path after successful move and persist
            Node.INSTANCE.setBackupPath(newBackupPath);
            NodeUtils.cacheInformation(Node.INSTANCE);
            log.info("BACKUP_DIRECTORY_CHANGE task finished successfully.");
        } catch (Exception e) {
            try { Node.INSTANCE.setBackupPath(oldBackupPath); } catch (Exception ignored) {}
            // Inform backend to rollback settings
            try {
                NodeSettings rollback = new NodeSettings();
                rollback.setName(Node.INSTANCE.getNodeName());
                rollback.setServerPath(Node.INSTANCE.getServerPath());
                rollback.setEnableAutoUpdate(Node.INSTANCE.isAutoUpdateEnabled());
                rollback.setManageFirewallAutomatically(Node.INSTANCE.isManageFirewallAutomatically());
                // Ensure defaultServerDirectory is preserved during rollback
                try {
                    if (Node.INSTANCE.getDefaultServerDirectory() != null) {
                        rollback.setDefaultServerDirectory(Node.INSTANCE.getDefaultServerDirectory().toString());
                    }
                } catch (Exception ignored) { }
                // Revert backups dir to the previous value
                rollback.setServerBackupsDirectory(oldBackupPath.toString());

                NodeSettingsPacket packet = new NodeSettingsPacket();
                packet.setNodeSettings(rollback);
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

    public record BackupDirectoryChangeTaskPayload(Path oldBackupPath, Path newBackupPath) {}
}
