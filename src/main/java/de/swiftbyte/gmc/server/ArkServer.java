package de.swiftbyte.gmc.server;

import de.swiftbyte.gmc.Node;
import de.swiftbyte.gmc.common.entity.GameServerState;
import de.swiftbyte.gmc.common.model.SettingProfile;
import de.swiftbyte.gmc.common.packet.server.ServerDeletePacket;
import de.swiftbyte.gmc.service.BackupService;
import de.swiftbyte.gmc.service.FirewallService;
import de.swiftbyte.gmc.stomp.StompHandler;
import de.swiftbyte.gmc.utils.CommonUtils;
import de.swiftbyte.gmc.utils.NodeUtils;
import de.swiftbyte.gmc.utils.ServerUtils;
import de.swiftbyte.gmc.utils.action.AsyncAction;
import de.swiftbyte.gmc.utils.settings.MapSettingsAdapter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import xyz.astroark.Rcon;
import xyz.astroark.exception.AuthenticationException;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Scanner;

@Slf4j
public abstract class ArkServer extends GameServer {

    @Setter
    protected int rconPort;

    @Setter
    protected String rconPassword;

    public ArkServer(String id, String friendlyName, SettingProfile settings) {
        super(id, friendlyName, settings);
    }

    public AsyncAction<Boolean> install() {
        return () -> {
            setState(GameServerState.CREATING);
            String installCommand = "cmd /c start \"steamcmd\" \"" + CommonUtils.convertPathSeparator(NodeUtils.getSteamCmdPath().toAbsolutePath()) + "\""
                    + " +force_install_dir \"" + CommonUtils.convertPathSeparator(installDir.toAbsolutePath()) + "\""
                    + " +login anonymous +app_update " + getGameId() + " validate +quit";
            log.debug("Starting server installation with command {}", installCommand);
            try {
                Process process = Runtime.getRuntime().exec(installCommand);

                Scanner scanner = new Scanner(process.getInputStream());

                while (scanner.hasNextLine()) {
                    scanner.nextLine();
                }

                if (process.exitValue() == 7 || process.exitValue() == 0) {
                    log.debug("Server was installed successfully!");

                    allowFirewallPorts();

                    setState(GameServerState.OFFLINE);
                } else {
                    log.error("Server installation returned error code {}.", process.exitValue());
                    return false;
                }

            } catch (IOException e) {
                log.error("An unknown exception occurred while installing the server '{}'.", friendlyName, e);
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
                super.setState(GameServerState.DELETING);
                Thread.sleep(5000);
                FileUtils.deleteDirectory(installDir.toFile());
                FirewallService.removePort(friendlyName);
                BackupService.deleteAllBackupsByServer(this);
                GameServer.removeServerById(serverId);
                updateScheduler.cancel(false);
                NodeUtils.cacheInformation(Node.INSTANCE);

                ServerDeletePacket packet = new ServerDeletePacket();
                packet.setServerId(serverId);
                StompHandler.send("/app/server/delete", packet);

            } catch (IOException e) {
                log.error("An unknown exception occurred while deleting the server '{}'.", friendlyName, e);
                return false;
            } catch (InterruptedException e) {
                log.error("An unknown exception occurred while deleting the server '{}'.", friendlyName, e);
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
                writeStartupBatch();
                ServerUtils.writeIniFiles(this, installDir);
                try {
                    log.debug("cmd /c start \"{}", CommonUtils.convertPathSeparator(installDir + "/start.bat\""));
                    serverProcess = Runtime.getRuntime().exec("cmd /c start /min \"" + "\" \"" + CommonUtils.convertPathSeparator(installDir + "/start.bat\""));
                    Scanner scanner = new Scanner(serverProcess.getInputStream());
                    while (scanner.hasNextLine()) {
                    }

                    MapSettingsAdapter gmcSettings = new MapSettingsAdapter(settings.getGmcSettings());

                    if (gmcSettings.getBoolean("StartOnBoot", false) && (state != GameServerState.OFFLINE && state != GameServerState.STOPPING)) {
                        super.setState(GameServerState.RESTARTING);
                    } else {
                        super.setState(GameServerState.OFFLINE);
                    }

                } catch (IOException e) {
                    log.error("An unknown exception occurred while starting the server '{}'.", friendlyName, e);
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
                    log.debug("Sending stop message to server '{}'...", friendlyName);
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
                log.warn("Failed to send stop message to server '{}'.", friendlyName);
            }
            if (sendRconCommand("saveworld") == null) {
                log.debug("No connection to server '{}'. Killing process...", friendlyName);
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
                        log.error("An unknown exception occurred while stopping the server '{}'.", friendlyName, e);
                    }
                }
                log.debug("Server '{}' is offline.", friendlyName);
                return true;
            }
        };
    }

    public AsyncAction<Boolean> restart() {
        if (!CommonUtils.isNullOrEmpty(Node.INSTANCE.getServerStopMessage()))
            sendRconCommand("serverchat " + Node.INSTANCE.getServerRestartMessage());
        return () -> (stop(true).complete() && start().complete());
    }

    private int restartCounter = 0;

    @Override
    public void update() {
        if (PID == null && installDir != null)
            PID = CommonUtils.getProcessPID(installDir + CommonUtils.convertPathSeparator("/ShooterGame/Binaries/Win64/"));

        switch (state) {
            case INITIALIZING -> {
                if (sendRconCommand("ping") == null) {
                    log.debug("Server '{}' is still initializing...", friendlyName);
                } else {
                    log.debug("Server '{}' is ready!", friendlyName);
                    super.setState(GameServerState.ONLINE);
                }
            }
            case ONLINE -> {
                restartCounter = 0;
                String listPlayersResponse = sendRconCommand("listplayers");

                if (listPlayersResponse == null) {

                    log.warn("Server crash detected! Restarting server...");

                    ServerUtils.killServerProcess(PID);

                    MapSettingsAdapter gmcSettings = new MapSettingsAdapter(settings.getGmcSettings());

                    if (gmcSettings.getBoolean("RestartOnCrash", false)) {
                        log.debug("Restarting server '{}'...", friendlyName);
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
                    log.error("Server '{}' crashed 3 times in a row. Restarting is aborted!", friendlyName);
                    super.setState(GameServerState.OFFLINE);
                    return;
                }
                restartCounter++;
                log.debug("Server '{}' is restarting...", friendlyName);
                new Thread(() -> {
                    ServerUtils.killServerProcess(PID);
                    start().complete();
                }).start();
            }
            case STOPPING -> {
                if (CommonUtils.getProcessPID(installDir + CommonUtils.convertPathSeparator("/ShooterGame/Binaries/Win64/")) == null)
                    super.setState(GameServerState.OFFLINE);
            }
            case OFFLINE -> restartCounter = 0;
        }

        if (state != GameServerState.ONLINE) {
            currentOnlinePlayers = 0;
        }
    }

    @Override
    public String sendRconCommand(String command) {
        try {
            if (rconPort == 0 || CommonUtils.isNullOrEmpty(rconPassword)) return null;
            Rcon rcon = new Rcon("127.0.0.1", rconPort, rconPassword.getBytes());
            return rcon.command(command);
        } catch (IOException e) {
            log.debug("Information: Port: {}, Password: {}", rconPort, rconPassword);
            log.debug("Can not send rcon command because server '{}' is offline.", friendlyName);
            return null;
        } catch (AuthenticationException e) {
            log.error("Rcon authentication failed for server '{}'.", friendlyName);
            return null;
        }
    }

    public abstract void writeStartupBatch();
}
