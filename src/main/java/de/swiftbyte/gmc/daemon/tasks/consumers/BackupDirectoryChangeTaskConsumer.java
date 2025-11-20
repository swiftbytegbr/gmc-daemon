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

@Slf4j
public class BackupDirectoryChangeTaskConsumer implements NodeTaskConsumer {

    @Override
    public void run(NodeTask task, Object payload) {
        if (!(payload instanceof BackupDirectoryChangeTaskPayload(String oldServerPath, String newServerPath))) {
            throw new IllegalArgumentException("Expected BackupDirectoryChangeTaskPayload");
        }

        try {
            log.info("Starting BACKUP_DIRECTORY_CHANGE task: {} -> {}", oldServerPath, newServerPath);
            BackupService.suspendBackups();
            BackupService.moveBackupsDirectory(oldServerPath, newServerPath);
            // Apply new server path after successful move and persist
            Node.INSTANCE.setServerPath(newServerPath);
            NodeUtils.cacheInformation(Node.INSTANCE);
            log.info("BACKUP_DIRECTORY_CHANGE task finished successfully.");
        } catch (Exception e) {
            try { Node.INSTANCE.setServerPath(oldServerPath); } catch (Exception ignored) {}
            // Inform backend to rollback settings
            try {
                NodeSettings rollback = new NodeSettings();
                rollback.setName(Node.INSTANCE.getNodeName());
                rollback.setServerPath(oldServerPath);
                rollback.setEnableAutoUpdate(Node.INSTANCE.isAutoUpdateEnabled());
                rollback.setManageFirewallAutomatically(Node.INSTANCE.isManageFirewallAutomatically());

                NodeSettingsPacket packet = new NodeSettingsPacket();
                packet.setNodeSettings(rollback);
                StompHandler.send("/app/node/settings", packet);
                log.info("Rollback NodeSettingsPacket sent to backend (path reverted to '{}').", oldServerPath);
            } catch (Exception ex) {
                log.warn("Failed to send rollback NodeSettingsPacket to backend.", ex);
            }
            throw new RuntimeException("Failed to move backups directory", e);
        } finally {
            BackupService.resumeBackups();
        }
    }

    public record BackupDirectoryChangeTaskPayload(String oldServerPath, String newServerPath) {}
}
