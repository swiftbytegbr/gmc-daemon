package de.swiftbyte.gmc.daemon.server;

import de.swiftbyte.gmc.common.entity.GameServerState;
import de.swiftbyte.gmc.common.model.SettingProfile;
import de.swiftbyte.gmc.daemon.service.FirewallService;
import de.swiftbyte.gmc.daemon.utils.PathUtils;
import de.swiftbyte.gmc.daemon.utils.ServerUtils;
import de.swiftbyte.gmc.daemon.utils.Utils;
import de.swiftbyte.gmc.daemon.utils.settings.INISettingsAdapter;
import de.swiftbyte.gmc.daemon.utils.settings.MapSettingsAdapter;
import lombok.CustomLog;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@CustomLog
public class AseServer extends ArkServer {

    private static final @NonNull String STEAM_CMD_ID = "376030";
    private final @NonNull Path EXECUTABLE_PATH = installDir.resolve("/ShooterGame/Binaries/Win64/ShooterGameServer.exe");

    @Override
    public @NonNull String getGameId() {
        return STEAM_CMD_ID;
    }

    public AseServer(@NonNull String id, @NonNull String friendlyName, @NotNull Path installDir, @NonNull SettingProfile settings, boolean overrideAutoStart) {

        super(id, installDir, friendlyName, settings);

        INISettingsAdapter iniSettingsAdapter = new INISettingsAdapter(settings.getGameUserSettings());
        MapSettingsAdapter gmcSettings = new MapSettingsAdapter(settings.getGmcSettings());

        rconPassword = iniSettingsAdapter.get("ServerSettings", "ServerAdminPassword", "gmc-rp-" + UUID.randomUUID());
        rconPort = iniSettingsAdapter.getInt("ServerSettings", "RCONPort", 27020);

        if (!overrideAutoStart) {
            gatherPID();
            if (PID == null && gmcSettings.getBoolean("StartOnBoot", false)) {
                start().queue();
            } else if (PID != null) {
                log.debug("Server '{}' with PID {} is already running. Setting state to ONLINE.", PID, friendlyName);
                super.setState(GameServerState.ONLINE);
            }
        }
    }

    @Override
    public void setSettings(@NonNull SettingProfile settings) {
        SettingProfile oldSettings = this.settings;
        super.setSettings(settings);

        MapSettingsAdapter gmcSettings = new MapSettingsAdapter(settings.getGmcSettings());
        MapSettingsAdapter oldGmcSettings = new MapSettingsAdapter(oldSettings.getGmcSettings());

        if (gmcSettings.getBoolean("EnablePreaquaticaBeta", false) != oldGmcSettings.getBoolean("EnablePreaquaticaBeta", false)) {
            log.info("Detected change in Preaquatica Beta setting. Updating server to apply changes.");
            stop(false).queue((success) -> {
                if (success) {
                    if (install().complete()) {
                        log.info("Updated server with id {} successfully.", serverId);
                    } else {
                        log.error("Failed to update server with id {} during installation!", serverId);
                    }

                } else {
                    log.error("Failed to update server with id {} because it could not be stopped!", serverId);
                }

            });
        }

    }

    @Override
    public @NonNull List<@NonNull Integer> getNeededPorts() {

        INISettingsAdapter spu = new INISettingsAdapter(settings.getGameUserSettings());
        MapSettingsAdapter questionMarkParams = new MapSettingsAdapter(settings.getQuestionMarkParams());

        int gamePort = questionMarkParams.getInt("Port", 7777);
        int rconPort = spu.getInt("ServerSettings", "RCONPort", 27020);
        int queryPort = questionMarkParams.getInt("QueryPort", 27025);

        return List.of(gamePort, gamePort + 1, rconPort, queryPort);
    }

    @Override
    public void allowFirewallPorts() {
        if (node.isManageFirewallAutomatically()) {
            log.debug("Adding firewall rules for server '{}'...", friendlyName);
            FirewallService.allowPort(friendlyName, EXECUTABLE_PATH, getNeededPorts());
        }
    }

    @SuppressWarnings("DuplicatedCode")
    public void writeStartupBatch() {

        if (Utils.isNullOrEmpty(settings.getMap())) {
            log.error("Map is not set for server '{}'. Falling back to default map.", getFriendlyName());
            settings.setMap("TheIsland");
        }

        INISettingsAdapter spu = new INISettingsAdapter(settings.getGameUserSettings());
        MapSettingsAdapter gmcSettings = new MapSettingsAdapter(settings.getGmcSettings());

        setRconPort(spu.getInt("ServerSettings", "RCONPort", 27020));
        setRconPassword(spu.get("ServerSettings", "ServerAdminPassword", "gmc-rp-" + UUID.randomUUID()));

        List<String> requiredLaunchParameters1 = getRequiredLaunchArgs1(settings.getMap());
        List<String> requiredLaunchParameters2 = getRequiredLaunchArgs2();

        String realStartPostArguments = ServerUtils.generateServerArgs(
                settings.getQuestionMarkParams().isEmpty() ? new ArrayList<>() : ServerUtils.generateArgListFromMap(settings.getQuestionMarkParams()),
                gmcSettings.get("AdditionalQuestionMarkParameters", ""),
                settings.getHyphenParams().isEmpty() ? new ArrayList<>() : ServerUtils.generateArgListFromMap(settings.getHyphenParams(), false),
                gmcSettings.get("AdditionalHyphenParameters", ""),
                requiredLaunchParameters1,
                requiredLaunchParameters2
        );

        String changeDirectoryCommand = "cd /d \"" + PathUtils.convertPathSeparator(installDir.resolve("/ShooterGame/Binaries/Win64"));

        String serverExeName = "ShooterGameServer.exe";

        if (Files.exists(installDir.resolve("/ShooterGame/Binaries/Win64/AseApiLoader.exe"))) {
            serverExeName = "AseApiLoader.exe";
        }

        String startCommand = "cmd /c start \"" + friendlyName + "\""
                + " /min"
                + (gmcSettings.has("WindowsProcessPriority") ? " /" + gmcSettings.get("WindowsProcessPriority") : "")
                + (gmcSettings.has("WindowsProcessAffinity") ? " /affinity " + gmcSettings.get("WindowsProcessAffinity") : "")
                + " \"" + PathUtils.convertPathSeparator(installDir.resolve("/ShooterGame/Binaries/Win64/", serverExeName)) + "\""
                + " " + realStartPostArguments;
        log.debug("Writing startup batch for server {} with command '{}'", friendlyName, startCommand);

        try {
            FileWriter fileWriter = new FileWriter(installDir.resolve("/start.bat").toFile());
            PrintWriter printWriter = new PrintWriter(fileWriter);

            printWriter.println(changeDirectoryCommand);
            printWriter.println(startCommand);
            printWriter.println("exit");
            printWriter.close();

        } catch (IOException e) {
            log.error("An unknown exception occurred while writing the startup batch for server '{}'.", getFriendlyName(), e);
        }
    }

    private static @NonNull List<@NonNull String> getRequiredLaunchArgs1(@NonNull String map) {
        return new ArrayList<>(List.of(
                map,
                "RCONEnabled=True"
        ));
    }

    private static @NonNull List<@NonNull String> getRequiredLaunchArgs2() {
        return new ArrayList<>(List.of(
                "oldconsole"
        ));
    }
}
