package de.swiftbyte.gmc.daemon.commands;

import de.swiftbyte.gmc.daemon.server.GameServer;
import lombok.CustomLog;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;

@SuppressWarnings("ALL")
@Command
@CustomLog
public class ServerManagementCommands {

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
