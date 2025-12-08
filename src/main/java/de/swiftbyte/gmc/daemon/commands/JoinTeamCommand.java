package de.swiftbyte.gmc.daemon.commands;

import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.daemon.utils.ConnectionState;
import lombok.CustomLog;
import org.jspecify.annotations.NonNull;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;

@Command
@CustomLog
public class JoinTeamCommand {

    @Command(command = "join", description = "Invite this daemon to a team. Only available when not already joined.", group = "Daemon Management", hidden = true)
    public void joinCommand(@NonNull @Option(description = "The Invite Token, which is displayed in the Web Panel.", required = true) String token) {

        Node node = Node.INSTANCE;
        if (node == null) {
            throw new IllegalStateException("Node is not initialized yet.");
        }

        if (node.getConnectionState() != ConnectionState.NOT_JOINED) {
            log.error("The daemon has already joined a team. To reinvite the daemon to another team, you must completely reinstall the program. All data will be lost in the process.");
            return;
        }

        node.joinTeam(token);
    }

}
