package de.swiftbyte.gmc.server;

import de.swiftbyte.gmc.Node;
import de.swiftbyte.gmc.packet.entity.GameServerState;
import de.swiftbyte.gmc.packet.entity.ServerSettings;
import de.swiftbyte.gmc.packet.server.ServerDeletePacket;
import de.swiftbyte.gmc.stomp.StompHandler;
import de.swiftbyte.gmc.utils.*;
import de.swiftbyte.gmc.utils.AsyncAction;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import xyz.astroark.Rcon;
import xyz.astroark.exception.AuthenticationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

@Slf4j
public class AsaServer extends GameServer {

    private static final String STEAM_CMD_ID = "2430930";

    @Setter
    private int rconPort;
    @Setter
    private String rconPassword;

    public AsaServer(String id, String friendlyName, ServerSettings settings) {

        super(id, friendlyName, settings);

        rconPassword = settings.getRconPassword();
        rconPort = settings.getRconPort();

        PID = CommonUtils.getProcessPID(String.valueOf(installDir));
        if (PID == null && settings.isStartOnBoot()) start().queue();
        else if (PID != null) super.setState(GameServerState.ONLINE);
    }

    public AsaServer(String id, String friendlyName, Path installDir, ServerSettings settings) {

        super(id, friendlyName, settings);
        this.installDir = installDir;

        rconPassword = settings.getRconPassword();
        rconPort = settings.getRconPort();

        PID = CommonUtils.getProcessPID(String.valueOf(installDir));
        if (PID == null && settings.isStartOnBoot()) start().queue();
        else if (PID != null) super.setState(GameServerState.ONLINE);
    }

    @Override
    public AsyncAction<Boolean> install() {
        return () -> {
            super.setState(GameServerState.CREATING);
            String installCommand = "cmd /c start \"steamcmd\" \"" + CommonUtils.convertPathSeparator(NodeUtils.getSteamCmdPath().toAbsolutePath()) + "\""
                    + " +force_install_dir \"" + CommonUtils.convertPathSeparator(installDir.toAbsolutePath()) + "\""
                    + " +login anonymous +app_update " + STEAM_CMD_ID + " validate +quit";
            log.debug("Starting server installation with command " + installCommand);
            try {
                Process process = Runtime.getRuntime().exec(installCommand);

                Scanner scanner = new Scanner(process.getInputStream());

                while (scanner.hasNextLine()) {
                    scanner.nextLine();
                }

                if (process.exitValue() == 7 || process.exitValue() == 0) {
                    log.debug("Server was installed successfully!");
                    super.setState(GameServerState.OFFLINE);
                } else {
                    log.error("Server installation returned error code " + process.exitValue() + ".");
                    return false;
                }

            } catch (IOException e) {
                log.error("An unknown exception occurred while installing the server '" + friendlyName + "'.", e);
                return false;
            }
            return true;
        };
    }

    @Override
    public AsyncAction<Boolean> delete() {
        return () -> {
            try {
                if (state != GameServerState.OFFLINE && state != GameServerState.CREATING) {
                    stop().complete();
                }
                Files.delete(installDir);
                BackupService.deleteAllBackupsByServer(this);
                GameServer.removeServerById(serverId);
                updateScheduler.cancel(false);
                NodeUtils.cacheInformation(Node.INSTANCE);

                ServerDeletePacket packet = new ServerDeletePacket();
                packet.setServerId(serverId);
                StompHandler.send("/app/server/delete", packet);

            } catch (IOException e) {
                log.error("An unknown exception occurred while deleting the server '" + friendlyName + "'.", e);
                return false;
            }
            return true;
        };
    }

    @Override
    public AsyncAction<Boolean> start() {

        return () -> {
            ServerUtils.killServerProcess(PID);

            super.setState(GameServerState.INITIALIZING);

            if (!Files.exists(installDir)) {
                super.setState(GameServerState.OFFLINE);
                install().queue();
                return false;
            }

            new Thread(() -> {
                ServerUtils.writeAsaStartupBatch(this);
                try {
                    log.debug("cmd /c start \"" + CommonUtils.convertPathSeparator(installDir + "/start.bat\""));
                    serverProcess = Runtime.getRuntime().exec("cmd /c start /min \"" + "\" \"" + CommonUtils.convertPathSeparator(installDir + "/start.bat\""));
                    Scanner scanner = new Scanner(serverProcess.getInputStream());
                    while (scanner.hasNextLine()) {
                    }

                    if (settings.isRestartOnCrash() && state != GameServerState.OFFLINE) {
                        super.setState(GameServerState.RESTARTING);
                    } else {
                        super.setState(GameServerState.OFFLINE);
                    }

                } catch (IOException e) {
                    log.error("An unknown exception occurred while starting the server '" + friendlyName + "'.", e);
                }
            }).start();

            return true;
        };
    }

    @Override
    public AsyncAction<Boolean> stop() {
        return () -> {
            super.setState(GameServerState.STOPPING);

            if (sendRconCommand("saveworld") == null) {
                log.debug("No connection to server '" + friendlyName + "'. Killing process...");
                ServerUtils.killServerProcess(PID);
            } else {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ignored) {
                    ServerUtils.killServerProcess(PID);
                    super.setState(GameServerState.OFFLINE);
                }
                sendRconCommand("doexit");
            }

            while (state != GameServerState.OFFLINE) {}

            super.setState(GameServerState.OFFLINE);
            return true;
        };
    }

    @Override
    public void update() {

        if (PID == null) PID = CommonUtils.getProcessPID(String.valueOf(installDir));

        switch (state) {
            case INITIALIZING -> {
                if (sendRconCommand("ping") == null) {
                    log.debug("Server '" + friendlyName + "' is still initializing...");
                } else {
                    log.debug("Server '" + friendlyName + "' is ready!");
                    super.setState(GameServerState.ONLINE);
                }
            }
            case ONLINE -> {
                if (sendRconCommand("ping") == null) {

                    log.warn("Server crash detected! Restarting server...");

                    ServerUtils.killServerProcess(PID);

                    if (settings.isRestartOnCrash()) {
                        log.debug("Restarting server '" + friendlyName + "'...");
                        start().queue();
                    } else {
                        super.setState(GameServerState.OFFLINE);
                    }
                }
            }
            case RESTARTING -> {
                log.debug("Server '" + friendlyName + "' is restarting...");
                stop().complete();
                start().complete();
            }
        }
    }

    @Override
    public String sendRconCommand(String command) {
        try {
            if (rconPort == 0 || CommonUtils.isNullOrEmpty(rconPassword)) return null;
            Rcon rcon = new Rcon("127.0.0.1", rconPort, rconPassword.getBytes());
            return rcon.command(command);
        } catch (IOException e) {
            log.debug("Server '" + friendlyName + "' is offline.");
            return null;
        } catch (AuthenticationException e) {
            log.error("Rcon authentication failed for server '" + friendlyName + "'.");
            return null;
        }
    }
}
