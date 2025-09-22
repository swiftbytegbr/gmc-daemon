package de.swiftbyte.gmc.daemon.commands;

import de.swiftbyte.gmc.common.model.SettingProfile;
import de.swiftbyte.gmc.daemon.server.AsaServer;
import de.swiftbyte.gmc.daemon.server.GameServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;

@SuppressWarnings("ALL")
@Command
@Slf4j
public class ServerManagementCommands {

    @Command(command = "server create", description = "Create a new Server.", group = "Daemon Management", hidden = true)
    public String createServerCommand(@Option(description = "The type of game which server is to be created", required = true) String game, @Option(description = "The server id", required = true) String id, @Option(description = "The FriendlyName of the server", required = true) String name, @Option(description = "Should the server be installed?") boolean install) {


        if (game.equalsIgnoreCase("asa")) {

            AsaServer server = new AsaServer(id, name, new SettingProfile(), true);

            if (install) server.install().queue();

            return "The specified server was installed!";
        }

        return "The specified game type does not exist!";
    }

    @Command(command = "server list", description = "List all servers.", group = "Daemon Management")
    public String listServerCommand() {

        StringBuilder list = new StringBuilder();

        for (GameServer server : GameServer.getAllServers()) {
            list.append(server.getFriendlyName()).append("(").append(server.getServerId()).append(") - ").append(server.getInstallDir()).append(": ").append(server.getState()).append("\n");
        }

        return list.toString();
    }

    @Command(command = "server start", description = "Start a server.", group = "Daemon Management")
    public String startServerCommand(@Option(description = "The server id", required = true) String id) {

        GameServer server = GameServer.getServerById(id);

        server.start().queue();

        return "Server is starting...";
    }

    @Command(command = "server stop", description = "Stop a server.", group = "Daemon Management")
    public String stopServerCommand(@Option(description = "The server id", required = true) String id) {

        GameServer server = GameServer.getServerById(id);

        server.stop(false).queue();

        return "Server is stopping...";
    }

    @Command(command = "server restart", description = "Restart a server.", group = "Daemon Management")
    public String restartServerCommand(@Option(description = "The server id", required = true) String id) {

        GameServer server = GameServer.getServerById(id);

        server.restart().queue();

        return "Server is restarting...";
    }
}
