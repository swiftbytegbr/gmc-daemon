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
public class AsaServer extends ArkServer {

    private static final String STEAM_CMD_ID = "2430930";

    private @NonNull Path getExecutablePath() {
        return installDir.resolve("ShooterGame/Binaries/Win64/ArkAscendedServer.exe");
    }

    @Override
    public @NonNull String getGameId() {
        return STEAM_CMD_ID;
    }

    public AsaServer(String id, String friendlyName, @NotNull Path installDir, SettingProfile settings, boolean overrideAutoStart) {

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
    public @NonNull List<@NonNull Integer> getNeededPorts() {

        INISettingsAdapter spu = new INISettingsAdapter(settings.getGameUserSettings());
        MapSettingsAdapter hyphenParams = new MapSettingsAdapter(settings.getHyphenParams());

        int gamePort = hyphenParams.getInt("port", 7777);
        int rconPort = spu.getInt("ServerSettings", "RCONPort", 27020);

        return List.of(gamePort, gamePort + 1, rconPort);
    }

    @Override
    public void allowFirewallPorts() {
        if (node.isManageFirewallAutomatically()) {
            log.debug("Adding firewall rules for server '{}'...", friendlyName);
            FirewallService.allowPort(friendlyName, getExecutablePath(), getNeededPorts());
        }
    }

    @SuppressWarnings("DuplicatedCode")
    @Override
    public void writeStartupBatch() {

        if (Utils.isNullOrEmpty(settings.getMap())) {
            log.error("Map is not set for server '{}'. Falling back to default map.", getFriendlyName());
            settings.setMap("TheIsland_WP");
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

        String changeDirectoryCommand = "cd /d \"" + PathUtils.convertPathSeparator(installDir.resolve("ShooterGame/Binaries/Win64"));

        String serverExeName = "ArkAscendedServer.exe";

        if (Files.exists(installDir.resolve("ShooterGame/Binaries/Win64/AsaApiLoader.exe"))) {
            serverExeName = "AsaApiLoader.exe";
        }

        String startCommand = "cmd.exe /c start \"" + friendlyName + "\""
                + " /min"
                + (gmcSettings.has("WindowsProcessPriority") ? " /" + gmcSettings.get("WindowsProcessPriority") : "")
                + (gmcSettings.has("WindowsProcessAffinity") ? " /affinity " + gmcSettings.get("WindowsProcessAffinity") : "")
                + " \"" + PathUtils.convertPathSeparator(installDir.resolve("ShooterGame/Binaries/Win64/", serverExeName)) + "\""
                + " " + realStartPostArguments;
        log.debug("Writing startup batch for server {} with command '{}'", friendlyName, startCommand);

        try {
            FileWriter fileWriter = new FileWriter(installDir.resolve("start.bat").toFile());
            PrintWriter printWriter = new PrintWriter(fileWriter);

            printWriter.println(changeDirectoryCommand);
            printWriter.println(startCommand);
            printWriter.println("exit");
            printWriter.close();

        } catch (IOException e) {
            log.error("An unknown exception occurred while writing the startup batch for server '{}'.", friendlyName, e);
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
