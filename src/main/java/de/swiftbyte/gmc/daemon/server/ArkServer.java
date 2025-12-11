package de.swiftbyte.gmc.daemon.server;

import de.swiftbyte.gmc.common.entity.GameServerState;
import de.swiftbyte.gmc.common.model.SettingProfile;
import de.swiftbyte.gmc.common.packet.from.backend.server.ServerDeletePacket;
import de.swiftbyte.gmc.daemon.service.AutoRestartService;
import de.swiftbyte.gmc.daemon.service.BackupService;
import de.swiftbyte.gmc.daemon.service.FirewallService;
import de.swiftbyte.gmc.daemon.stomp.StompHandler;
import de.swiftbyte.gmc.daemon.utils.NodeUtils;
import de.swiftbyte.gmc.daemon.utils.PathUtils;
import de.swiftbyte.gmc.daemon.utils.ServerUtils;
import de.swiftbyte.gmc.daemon.utils.SystemUtils;
import de.swiftbyte.gmc.daemon.utils.Utils;
import de.swiftbyte.gmc.daemon.utils.action.AsyncAction;
import de.swiftbyte.gmc.daemon.utils.settings.MapSettingsAdapter;
import lombok.CustomLog;
import lombok.Setter;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import xyz.astroark.Rcon;
import xyz.astroark.exception.AuthenticationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

@CustomLog
public abstract class ArkServer extends GameServer {

    @Setter
    protected int rconPort;

    @Setter
    protected String rconPassword;

    public ArkServer(String id, @NotNull Path installDir, String friendlyName, SettingProfile settings) {
        super(id, installDir, friendlyName, settings);
        // After base init (which may load cached settings), apply backend settings to trigger deltas
        setSettings(settings);
    }

    @Override
    public void setSettings(@NonNull SettingProfile settings) {
        super.setSettings(settings);
        // Only write startup batch if install directory exists.
        // During initial creation, installDir may not exist yet and writing would fail.
        try {
            if (Files.exists(installDir)) {
                writeStartupBatch();
            } else {
                log.debug("Skipping startup batch write for '{}' because install directory does not exist yet.", friendlyName);
            }
        } catch (Exception e) {
            log.error("Failed while attempting to write startup batch for '{}' during settings update.", friendlyName, e);
        }
    }

    public @NonNull AsyncAction<@NonNull Boolean> install() {
        return () -> {
            if (state == GameServerState.CREATING) {
                log.warn("Server '{}' is busy (CREATING). Install/update aborted.", friendlyName);
                return false;
            }
            setState(GameServerState.CREATING);

            MapSettingsAdapter gmcSettings = new MapSettingsAdapter(settings.getGmcSettings());
            boolean isPreaquaticaBeta = gmcSettings.getBoolean("EnablePreaquaticaBeta", false);

            // Ensure install directory exists and is creatable before invoking SteamCMD.
            // If this fails, signal failure so the caller (creation flow) can delete the server.
            try {
                Files.createDirectories(installDir);
            } catch (Exception e) {
                log.error("Failed to create server install directory '{}' for '{}'.", installDir, friendlyName, e);
                setState(GameServerState.OFFLINE);
                return false;
            }

            String steamCmdPath = PathUtils.convertPathSeparator(NodeUtils.getSteamCmdPath());
            String installDirectory = PathUtils.convertPathSeparator(installDir);

            List<String> installCommand = getInstallCommand(steamCmdPath, installDirectory, isPreaquaticaBeta);

            log.debug("Starting server installation with command {}", String.join(" ", installCommand));
            try {
                Process process = new ProcessBuilder(installCommand).start();

                Scanner scanner = new Scanner(process.getInputStream());

                while (scanner.hasNextLine()) {
                    scanner.nextLine();
                }

                if (process.exitValue() == 7 || process.exitValue() == 0) {
                    log.debug("Server was installed successfully!");

                    allowFirewallPorts();

                    //Create server install dir alias when not already existing
                    Path aliasPath = this.installDir.getParent().resolve(friendlyName + " - Link");
                    if (!Files.exists(aliasPath)) {
                        try {
                            Files.createSymbolicLink(aliasPath, this.installDir);
                        } catch (IOException e) {
                            log.warn("Failed to create symbolic link for '{}'.", friendlyName, e);
                        }
                    }

                    setState(GameServerState.OFFLINE);
                } else {
                    log.error("Server installation returned error code {}.", process.exitValue());
                    return false;
                }

            } catch (IOException e) {
                log.error("An unknown exception occurred while installing the server '{}'.", friendlyName, e);
                setState(GameServerState.OFFLINE);
                return false;
            }
            return true;
        };
    }

    private @NonNull List<@NonNull String> getInstallCommand(@NonNull String steamCmdPath, @NonNull String installDirectory, boolean isPreaquaticaBeta) {
        List<String> installCommand = new ArrayList<>();
        installCommand.add("cmd");
        installCommand.add("/c");
        installCommand.add("start");
        installCommand.add("");
        installCommand.add(steamCmdPath);
        installCommand.add("+force_install_dir");
        installCommand.add(installDirectory);
        installCommand.add("+login");
        installCommand.add("anonymous");
        installCommand.add("+app_update");
        installCommand.add(getGameId());
        if (isPreaquaticaBeta) {
            installCommand.add("-beta");
            installCommand.add("preaquatica");
        }
        installCommand.add("validate");
        installCommand.add("+quit");
        return installCommand;
    }

    @Override
    public @NonNull AsyncAction<@NonNull Boolean> delete() {
        return () -> {

            if (state == GameServerState.CREATING) {
                log.warn("Server '{}' is busy (CREATING). Delete aborted.", friendlyName);
                return false;
            }
            try {
                if (state != GameServerState.OFFLINE) {
                    stop(false).complete();
                }
                super.setState(GameServerState.DELETING);

                AutoRestartService.cancelAutoRestart(serverId);
                try {
                    //Delete alias
                    Path aliasPath = this.installDir.getParent().resolve(friendlyName + " - Link");
                    Files.delete(aliasPath);
                } catch (IOException e) {
                    log.warn("Failed to delete symbolic link for '{}'.", friendlyName, e);
                }

                FileUtils.deleteDirectory(installDir.toFile());
                FirewallService.removePort(friendlyName);
                BackupService.deleteAllBackupsByServer(this);
                GameServer.removeServerById(serverId);
                updateScheduler.cancel(false);
                NodeUtils.cacheInformation(node);

                ServerDeletePacket packet = new ServerDeletePacket();
                packet.setServerId(serverId);
                StompHandler.send("/app/server/delete", packet);

            } catch (IOException e) {
                log.error("An unknown exception occurred while deleting the server '{}'.", friendlyName, e);
                return false;
            }
            return true;
        };
    }

    @Override
    public @NonNull AsyncAction<@NonNull Boolean> abandon() {
        return () -> {

            AutoRestartService.cancelAutoRestart(serverId);
            GameServer.removeServerById(serverId);
            updateScheduler.cancel(false);
            NodeUtils.cacheInformation(node);
            return true;
        };
    }

    @Override
    public @NonNull AsyncAction<@NonNull Boolean> start() {

        return () -> {
            if (state == GameServerState.CREATING) {
                log.warn("Server '{}' is busy (CREATING). Start aborted.", friendlyName);
                return false;
            }

            //Kill if a server process is already present
            if (PID != null) {
                SystemUtils.killSystemProcess(PID);
            }

            super.setState(GameServerState.INITIALIZING);

            if (!Files.exists(installDir)) {
                super.setState(GameServerState.OFFLINE);
                install().queue();
                return false;
            }
            //TODO rework start logic
            new Thread(() -> {
                writeStartupBatch();
                ServerUtils.writeIniFiles(this, installDir);
                try {
                    String startupScript = PathUtils.convertPathSeparator(installDir.resolve("start.bat").toString());
                    List<String> startCommand = List.of("cmd", "/c", "start", "/min", "", startupScript);
                    log.debug("Starting server with command {}", String.join(" ", startCommand));
                    serverProcess = new ProcessBuilder(startCommand).start();
                    PID = String.valueOf(serverProcess.pid());
                    Scanner scanner = new Scanner(serverProcess.getInputStream());
                    while (scanner.hasNextLine()) {
                        scanner.nextLine();
                    }

                    //TODO maybe delete this part
                    /*MapSettingsAdapter gmcSettings = new MapSettingsAdapter(settings.getGmcSettings());

                    if (gmcSettings.getBoolean("RestartOnCrash", false) && (state != GameServerState.OFFLINE && state != GameServerState.STOPPING)) {
                        super.setState(GameServerState.RESTARTING);
                    } else {
                        super.setState(GameServerState.OFFLINE);
                    }*/

                } catch (IOException e) {
                    log.error("An unknown exception occurred while starting the server '{}'.", friendlyName, e);
                }
            }).start();

            return true;
        };
    }

    @Override
    public @NonNull AsyncAction<@NonNull Boolean> stop(boolean isRestart, boolean isKill) {
        return () -> {
            if (state == GameServerState.CREATING) {
                log.warn("Server '{}' is busy (CREATING). Stop aborted.", friendlyName);
                return false;
            }
            if (isKill) {
                log.debug("Killing server '{}'", friendlyName);
                super.setState(GameServerState.STOPPING);
                if (PID == null) {
                    log.error("Killing server '{}' failed. Reason: PID not found.", friendlyName);
                    return false;
                }
                SystemUtils.killSystemProcess(PID);
                return true;
            }

            if (state == GameServerState.OFFLINE) {
                return true;
            }

            super.setState(GameServerState.STOPPING);

            if (!isRestart) {
                MapSettingsAdapter gmcSettings = new MapSettingsAdapter(settings.getGmcSettings());
                String stopMessage = gmcSettings.get("StopMessage");
                if (!Utils.isNullOrEmpty(stopMessage)) {
                    sendRconCommand("serverchat " + stopMessage);
                } else {
                    sendRconCommand("serverchat server is stopping...");
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
                Thread.currentThread().interrupt();
            }
            if (sendRconCommand("saveworld") == null) {
                log.debug("No connection to server '{}'. Killing process...", friendlyName);

                if (PID != null) {
                    SystemUtils.killSystemProcess(PID);
                } else {
                    log.debug("No server pid found. It is assumed that the server has already been terminated.");
                }

            } else {
                sendRconCommand("doexit");
                currentOnlinePlayers = 0;
            }

            synchronized (this) {
                while (state != GameServerState.OFFLINE) {
                    try {
                        this.wait(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                log.debug("Server '{}' is offline.", friendlyName);
                return true;
            }
        };
    }

    public @NonNull AsyncAction<@NonNull Boolean> restart() {
        return () -> {
            if (state == GameServerState.CREATING) {
                log.warn("Server '{}' is busy (CREATING). Restart aborted.", friendlyName);
                return false;
            }
            MapSettingsAdapter gmcSettings = new MapSettingsAdapter(settings.getGmcSettings());
            String restartMessage = gmcSettings.get("RestartMessage");
            if (!Utils.isNullOrEmpty(restartMessage)) {
                sendRconCommand("serverchat " + restartMessage);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            return (stop(true).complete() && start().complete());
        };
    }

    private int restartCounter = 0;

    @Override
    public void update() {
        gatherPID();

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

                    if (PID != null) {
                        SystemUtils.killSystemProcess(PID);
                    } else {
                        log.debug("No server pid found. It is assumed that the server has already been terminated.");
                    }

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
                start().queue();
            }
            case STOPPING -> {
                if (PID == null) {
                    super.setState(GameServerState.OFFLINE);
                }
            }
            case OFFLINE -> restartCounter = 0;
        }

        if (state != GameServerState.ONLINE) {
            currentOnlinePlayers = 0;
        }
    }

    @Override
    public @Nullable String sendRconCommand(@NonNull String command) {
        try {
            if (state == GameServerState.CREATING) {
                log.warn("Server '{}' is busy (CREATING). RCON '{}' ignored.", friendlyName, command);
                return null;
            }
            if (rconPort == 0 || Utils.isNullOrEmpty(rconPassword)) {
                return null;
            }
            Rcon rcon = new Rcon("127.0.0.1", rconPort, rconPassword.getBytes());
            String response = rcon.command(command);

            rcon.disconnect();

            return response;
        } catch (IOException e) {
            log.debug("Information: Port: {}, Password: {}", rconPort, rconPassword);
            log.debug("Can not send rcon command because server '{}' is offline. THIS IS PROBABLY NOT AN ERROR:", friendlyName, e);
            return null;
        } catch (AuthenticationException e) {
            log.debug("Information: Port: {}, Password: {}", rconPort, rconPassword);
            log.error("Rcon authentication failed for server '{}'.", friendlyName);
            return null;
        }
    }

    @Override
    protected void gatherPID() {
        PID = SystemUtils.getProcessPID(PathUtils.convertPathSeparator(installDir.resolve("ShooterGame/Binaries/Win64/")));
    }

    public abstract void writeStartupBatch();
}
