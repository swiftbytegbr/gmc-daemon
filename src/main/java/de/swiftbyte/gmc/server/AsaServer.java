package de.swiftbyte.gmc.server;

import de.swiftbyte.gmc.Node;
import de.swiftbyte.gmc.packet.entity.GameServerState;
import de.swiftbyte.gmc.packet.entity.ServerSettings;
import de.swiftbyte.gmc.packet.server.ServerDeletePacket;
import de.swiftbyte.gmc.service.BackupService;
import de.swiftbyte.gmc.service.FirewallService;
import de.swiftbyte.gmc.stomp.StompHandler;
import de.swiftbyte.gmc.utils.CommonUtils;
import de.swiftbyte.gmc.utils.NodeUtils;
import de.swiftbyte.gmc.utils.ServerUtils;
import de.swiftbyte.gmc.utils.action.AsyncAction;
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

    public AsaServer(String id, String friendlyName, ServerSettings settings, boolean overrideAutoStart) {

        super(id, friendlyName, settings);

        rconPassword = settings.getRconPassword();
        rconPort = settings.getRconPort();

        if (!overrideAutoStart) {
            PID = CommonUtils.getProcessPID(String.valueOf(installDir));
            if (PID == null && settings.isStartOnBoot()) start().queue();
            else if (PID != null) super.setState(GameServerState.ONLINE);
        }
    }

    public AsaServer(String id, String friendlyName, Path installDir, ServerSettings settings, boolean overrideAutoStart) {

        super(id, friendlyName, settings);
        this.installDir = installDir;

        rconPassword = settings.getRconPassword();
        rconPort = settings.getRconPort();

        if (!overrideAutoStart) {
            PID = CommonUtils.getProcessPID(String.valueOf(installDir));
            if (PID == null && settings.isStartOnBoot()) start().queue();
            else if (PID != null) super.setState(GameServerState.ONLINE);
        }
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

                    if (Node.INSTANCE.isManageFirewallAutomatically()) {
                        log.debug("Adding firewall rules for server '" + friendlyName + "'...");

                        allowFirewallPorts();
                    }

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
                    stop(false).complete();
                }
                Files.delete(installDir);
                FirewallService.removePort(friendlyName);
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
    public AsyncAction<Boolean> abandon() {
        return () -> {
            GameServer.removeServerById(serverId);
            updateScheduler.cancel(false);
            NodeUtils.cacheInformation(Node.INSTANCE);
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

                    if (settings.isRestartOnCrash() && (state != GameServerState.OFFLINE && state != GameServerState.STOPPING)) {
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
    public AsyncAction<Boolean> stop(boolean isRestart) {
        return () -> {
            if (state == GameServerState.OFFLINE) return true;
            super.setState(GameServerState.STOPPING);

            if (!isRestart) {
                if (!CommonUtils.isNullOrEmpty(Node.INSTANCE.getServerStopMessage())) {
                    sendRconCommand("serverchat " + Node.INSTANCE.getServerStopMessage());
                } else {
                    sendRconCommand("serverchat server ist stopping...");
                    log.debug("Sending stop message to server '" + friendlyName + "'...");
                }
            }

            try {
                Thread.sleep(7000);
                sendRconCommand("serverchat 3");
                Thread.sleep(1000);
                sendRconCommand("serverchat 2");
                Thread.sleep(1000);
                sendRconCommand("serverchat 1");
                Thread.sleep(1000);
                sendRconCommand("serverchat STOP");
            } catch (InterruptedException e) {
                log.warn("Failed to send stop message to server '" + friendlyName + "'.");
            }
            if (sendRconCommand("saveworld") == null) {
                log.debug("No connection to server '" + friendlyName + "'. Killing process...");
                ServerUtils.killServerProcess(PID);
            } else {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ignored) {
                    ServerUtils.killServerProcess(PID);
                }
                sendRconCommand("doexit");
            }

            synchronized (this) {
                while (state != GameServerState.OFFLINE) {
                    try {
                        this.wait(1000);
                    } catch (InterruptedException e) {
                        log.error("An unknown exception occurred while stopping the server '" + friendlyName + "'.", e);
                    }
                }
                log.debug("Server '" + friendlyName + "' is offline.");
                return true;
            }
        };
    }

    private int restartCounter = 0;

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
                restartCounter = 0;
                String listPlayersResponse = sendRconCommand("listplayers");

                if (listPlayersResponse == null) {

                    log.warn("Server crash detected! Restarting server...");

                    ServerUtils.killServerProcess(PID);

                    if (settings.isRestartOnCrash()) {
                        log.debug("Restarting server '" + friendlyName + "'...");
                        super.setState(GameServerState.RESTARTING);
                    } else {
                        super.setState(GameServerState.OFFLINE);
                    }
                } else {
                    String[] listPlayersResponseArray = listPlayersResponse.split("\n");
                    currentOnlinePlayers = listPlayersResponseArray.length - 2;
                }
            }
            case RESTARTING -> {
                if (restartCounter >= 3) {
                    log.error("Server '" + friendlyName + "' crashed 3 times in a row. Restarting is aborted!");
                    super.setState(GameServerState.OFFLINE);
                    return;
                }
                restartCounter++;
                log.debug("Server '" + friendlyName + "' is restarting...");
                new Thread(() -> {
                    ServerUtils.killServerProcess(PID);
                    start().complete();
                }).start();
            }
            case STOPPING -> {
                if (CommonUtils.getProcessPID(String.valueOf(installDir)) == null)
                    super.setState(GameServerState.OFFLINE);
            }
            case OFFLINE -> {
                restartCounter = 0;
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
