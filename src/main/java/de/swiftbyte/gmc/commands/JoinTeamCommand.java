package de.swiftbyte.gmc.commands;

import de.swiftbyte.gmc.Node;
import de.swiftbyte.gmc.utils.ConnectionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;

@Command
@Slf4j
public class JoinTeamCommand {

    @Command(command = "join", description = "Invite this daemon to a team. Only available when not already joined.", group = "Daemon Management", hidden = true)
    public String joinCommand(@Option(description = "The Invite Token, which is displayed in the Web Panel.", required = true) String token) {

        if (Node.INSTANCE.getConnectionState() != ConnectionState.NOT_JOINED) {
            log.error("The daemon has already joined a team. To reinvite the daemon to another team, you must completely reinstall the program. All data will be lost in the process.");
            return "";
        }

        Node.INSTANCE.joinTeam(token);
        return "";
    }

}
