package de.swiftbyte.gmc.daemon.tasks.consumers;

import de.swiftbyte.gmc.common.entity.NodeSettings;
import de.swiftbyte.gmc.common.model.NodeTask;
import de.swiftbyte.gmc.common.packet.from.bidirectional.node.NodeSettingsPacket;
import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.daemon.service.BackupService;
import de.swiftbyte.gmc.daemon.stomp.StompHandler;
import de.swiftbyte.gmc.daemon.utils.NodeUtils;
import de.swiftbyte.gmc.daemon.tasks.NodeTaskConsumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BackupTaskConsumer implements NodeTaskConsumer {

    @Override
    public void run(NodeTask task, Object payload) {
        if (payload instanceof MoveBackupsTaskPayload(String oldBackupPath, String newBackupPath)) {
            // Suspend backups and schedulers, move backups, then resume
            try {
                BackupService.suspendBackups();
                BackupService.moveBackupsDirectory(oldBackupPath, newBackupPath);
                try { Node.INSTANCE.setBackupPath(newBackupPath); NodeUtils.cacheInformation(Node.INSTANCE);} catch (Exception ignored) {}
            } catch (Exception e) {
                // Best-effort: revert local server path so daemon stays consistent
                // No backend rollback API available here
                try { Node.INSTANCE.setBackupPath(oldBackupPath); } catch (Exception ignored) {}
                try {
                    NodeSettings rollback = new NodeSettings();
                    rollback.setName(Node.INSTANCE.getNodeName());
                    rollback.setServerPath(Node.INSTANCE.getServerPath());
                    rollback.setEnableAutoUpdate(Node.INSTANCE.isAutoUpdateEnabled());
                    rollback.setManageFirewallAutomatically(Node.INSTANCE.isManageFirewallAutomatically());

                    NodeSettingsPacket packet = new NodeSettingsPacket();
                    packet.setNodeSettings(rollback);
                    StompHandler.send("/app/node/settings", packet);
                } catch (Exception ex) {
                    log.warn("Failed to send rollback NodeSettingsPacket to backend.", ex);
                }
                throw new RuntimeException("Failed to move backups directory", e);
            } finally {
                BackupService.resumeBackups();
            }
            return;
        }

        if (!(payload instanceof BackupTaskPayload(boolean isAutoUpdate))) {
            throw new IllegalArgumentException("Expected BackupTaskPayload or MoveBackupsTaskPayload");
        }

        task.getTargetIds().forEach(id -> BackupService.backupServer(id, isAutoUpdate));
    }

    public record BackupTaskPayload(boolean isAutoUpdate) {}
    public record MoveBackupsTaskPayload(String oldBackupPath, String newBackupPath) {}
}
