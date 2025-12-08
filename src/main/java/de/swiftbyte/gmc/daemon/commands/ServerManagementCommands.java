package de.swiftbyte.gmc.daemon.commands;

import de.swiftbyte.gmc.daemon.server.GameServer;
import lombok.CustomLog;
import org.jspecify.annotations.NonNull;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;

@Command
@CustomLog
public class ServerManagementCommands {

    @Command(command = "server list", description = "List all servers.", group = "Daemon Management")
    public void listServerCommand() {

        StringBuilder list = new StringBuilder();

        for (GameServer server : GameServer.getAllServers()) {
            list.append(server.getFriendlyName()).append("(").append(server.getServerId()).append(") - ").append(server.getInstallDir()).append(": ").append(server.getState()).append("\n");
        }

        log.info(list.toString());
    }

    @Command(command = "server start", description = "Start a server.", group = "Daemon Management")
    public void startServerCommand(@NonNull @Option(description = "The server id", required = true) String id) {

        GameServer server = GameServer.getServerById(id);

        if (server == null) {
            log.error("No server with id {} found!", id);
            return;
        }

        server.start().queue();

        log.info("Server is starting...");
    }

    @Command(command = "server stop", description = "Stop a server.", group = "Daemon Management")
    public void stopServerCommand(@NonNull @Option(description = "The server id", required = true) String id) {

        GameServer server = GameServer.getServerById(id);

        if (server == null) {
            log.error("No server with id {} found!", id);
            return;
        }

        server.stop(false).queue();

        log.info("Server is stopping...");
    }

    @Command(command = "server restart", description = "Restart a server.", group = "Daemon Management")
    public void restartServerCommand(@NonNull @Option(description = "The server id", required = true) String id) {

        GameServer server = GameServer.getServerById(id);

        if (server == null) {
            log.error("No server with id {} found!", id);
            return;
        }

        server.restart().queue();

        log.info("Server is restarting...");
    }
}
