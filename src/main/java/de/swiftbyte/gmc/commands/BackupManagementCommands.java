package de.swiftbyte.gmc.commands;

import de.swiftbyte.gmc.packet.entity.Backup;
import de.swiftbyte.gmc.server.GameServer;
import de.swiftbyte.gmc.utils.BackupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;

import java.util.List;

@Command
@Slf4j
public class BackupManagementCommands {

    @Command(command = "backup create", description = "Create a backup.", group = "Backup Management")
    public String createBackupCommand(@Option(description = "The server", required = true) String serverId, @Option(description = "The backup name", required = false) String backupName) {

        GameServer server = GameServer.getServerById(serverId);

        if (server != null) {

            BackupService.backupServer(server, false, backupName);

        } else {
            return "Server with id " + serverId + " not found!";
        }

        return "Backup created!";
    }

    @Command(command = "backup list", description = "List all backups.", group = "Backup Management")
    public String listBackupCommand(@Option(description = "Filter for a server id", required = false) String serverId) {

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

        return list.toString();
    }

}
