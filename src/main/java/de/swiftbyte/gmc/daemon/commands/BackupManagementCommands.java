package de.swiftbyte.gmc.daemon.commands;

import de.swiftbyte.gmc.common.entity.Backup;
import de.swiftbyte.gmc.common.model.NodeTask;
import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.service.BackupService;
import de.swiftbyte.gmc.daemon.service.TaskService;
import de.swiftbyte.gmc.daemon.tasks.consumers.BackupTaskConsumer;
import de.swiftbyte.gmc.daemon.tasks.consumers.RollbackTaskConsumer;
import lombok.CustomLog;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;

import java.util.List;

@Command
@CustomLog
public class BackupManagementCommands {

    @Command(command = "backup create", description = "Create a backup.", group = "Backup Management")
    public void createBackupCommand(@NonNull @Option(description = "The server", required = true) String serverId, @Option(description = "The backup name") String backupName) {

        Node node = Node.INSTANCE;
        if (node == null) {
            throw new IllegalStateException("Node is not initialized yet.");
        }

        GameServer server = GameServer.getServerById(serverId);

        if (server == null) {
            log.error("Server with id {} not found!", serverId);
            return;
        }

        boolean created = TaskService.createTask(
                NodeTask.Type.BACKUP,
                new BackupTaskConsumer.BackupTaskPayload(false, backupName),
                node.getNodeId(),
                serverId
        );

        if (created) {
            log.info("Backup task scheduled.");
        } else {
            log.error("Backup task could not be scheduled.");
        }
    }

    @Command(command = "backup rollback", description = "Rollback a backup.", group = "Backup Management")
    public void rollbackBackupCommand(@NonNull @Option(description = "The backup", required = true) String backupId, @Option(description = "Should the player data also be restored?") boolean playerData) {

        Node node = Node.INSTANCE;
        if (node == null) {
            throw new IllegalStateException("Node is not initialized yet.");
        }

        Backup backup = BackupService.getBackupById(backupId);

        if (backup == null) {
            log.error("Backup with id {} not found!", backupId);
            return;
        }

        boolean created = TaskService.createTask(
                NodeTask.Type.ROLLBACK,
                new RollbackTaskConsumer.RollbackTaskPayload(backup.getBackupId(), playerData),
                node.getNodeId()
        );

        if (created) {
            log.info("Rollback task scheduled.");
        } else {
            log.error("Rollback task could not be scheduled.");
        }
    }

    @Command(command = "backup list", description = "List all backups.", group = "Backup Management")
    public void listBackupCommand(@Nullable @Option(description = "Filter for a server id") String serverId) {

        List<Backup> backupList;

        if (serverId == null) {
            backupList = BackupService.getAllBackups();
        } else {
            backupList = BackupService.getBackupsByServer(serverId);
        }

        StringBuilder list = new StringBuilder();

        for (Backup backup : backupList) {
            list.append(backup.getName()).append("(").append(backup.getBackupId()).append(") - Server: ").append(backup.getServerId()).append(" / Size:  ").append(backup.getSize() / 1024).append("KB\n");
        }

        log.info(list.toString());
    }

}
