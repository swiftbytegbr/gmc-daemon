package de.swiftbyte.gmc.daemon.server;

import de.swiftbyte.gmc.common.entity.GameServerState;
import de.swiftbyte.gmc.common.model.SettingProfile;
import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.daemon.service.FirewallService;
import de.swiftbyte.gmc.daemon.utils.CommonUtils;
import de.swiftbyte.gmc.daemon.utils.ServerUtils;
import de.swiftbyte.gmc.daemon.utils.settings.INISettingsAdapter;
import de.swiftbyte.gmc.daemon.utils.settings.MapSettingsAdapter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
public class AseServer extends ArkServer {

    private static final String STEAM_CMD_ID = "376030";

    @Override
    public String getGameId() {
        return STEAM_CMD_ID;
    }

    public AseServer(String id, String friendlyName, @NotNull Path installDir, SettingProfile settings, boolean overrideAutoStart) {

        super(id, installDir, friendlyName, settings);

        INISettingsAdapter iniSettingsAdapter = new INISettingsAdapter(settings.getGameUserSettings());
        MapSettingsAdapter gmcSettings = new MapSettingsAdapter(settings.getGmcSettings());

        rconPassword = iniSettingsAdapter.get("ServerSettings", "ServerAdminPassword", "gmc-rp-" + UUID.randomUUID());
        rconPort = iniSettingsAdapter.getInt("ServerSettings", "RCONPort", 27020);

        if (!overrideAutoStart) {
            PID = CommonUtils.getProcessPID(this.installDir + CommonUtils.convertPathSeparator("/ShooterGame/Binaries/Win64/"));
            if (PID == null && gmcSettings.getBoolean("StartOnBoot", false)) {
                start().queue();
            } else if (PID != null) {
                log.debug("Server '{}' with PID {} is already running. Setting state to ONLINE.", PID, friendlyName);
                super.setState(GameServerState.ONLINE);
            }
        }
    }

    @Override
    public void setSettings(SettingProfile settings) {
        SettingProfile oldSettings = getSettings();
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
    public List<Integer> getNeededPorts() {

        INISettingsAdapter spu = new INISettingsAdapter(settings.getGameUserSettings());

        int gamePort = settings.getQuestionMarkParams().containsKey("Port") ? (int) settings.getQuestionMarkParams().get("Port") : 7777;
        int rconPort = spu.getInt("ServerSettings", "RCONPort", 27020);
        int queryPort = settings.getQuestionMarkParams().containsKey("QueryPort") ? (int) settings.getQuestionMarkParams().get("QueryPort") : 27025;

        return List.of(gamePort, gamePort + 1, rconPort, queryPort);
    }

    @Override
    public void allowFirewallPorts() {
        if (Node.INSTANCE.isManageFirewallAutomatically()) {
            log.debug("Adding firewall rules for server '{}'...", friendlyName);
            Path executablePath = Path.of(installDir + "/ShooterGame/Binaries/Win64/ShooterGameServer.exe");
            FirewallService.allowPort(friendlyName, executablePath, getNeededPorts());
        }
    }

    @SuppressWarnings("DuplicatedCode")
    public void writeStartupBatch() {

        SettingProfile settings = getSettings();

        if (CommonUtils.isNullOrEmpty(settings.getMap())) {
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
                settings.getQuestionMarkParams() == null || settings.getQuestionMarkParams().isEmpty() ? new ArrayList<>() : ServerUtils.generateArgListFromMap(settings.getQuestionMarkParams()),
                gmcSettings.get("AdditionalQuestionMarkParameters", ""),
                settings.getHyphenParams() == null || settings.getHyphenParams().isEmpty() ? new ArrayList<>() : ServerUtils.generateArgListFromMap(settings.getHyphenParams(), false),
                gmcSettings.get("AdditionalHyphenParameters", ""),
                requiredLaunchParameters1,
                requiredLaunchParameters2
        );

        String changeDirectoryCommand = "cd /d \"" + CommonUtils.convertPathSeparator(getInstallDir()) + "\\ShooterGame\\Binaries\\Win64\"";

        String serverExeName = "ShooterGameServer.exe";

        if (Files.exists(Path.of(getInstallDir() + "/ShooterGame/Binaries/Win64/AseApiLoader.exe"))) {
            serverExeName = "AseApiLoader.exe";
        }

        String startCommand = "cmd /c start \"" + getFriendlyName() + "\""
                + " /min"
                + (gmcSettings.has("WindowsProcessPriority") ? " /" + gmcSettings.get("WindowsProcessPriority") : "")
                + (gmcSettings.has("WindowsProcessAffinity") ? " /affinity " + gmcSettings.get("WindowsProcessAffinity") : "")
                + " \"" + CommonUtils.convertPathSeparator(getInstallDir() + "/ShooterGame/Binaries/Win64/" + serverExeName) + "\""
                + " " + realStartPostArguments;
        log.debug("Writing startup batch for server {} with command '{}'", getFriendlyName(), startCommand);

        try {
            FileWriter fileWriter = new FileWriter(getInstallDir() + "/start.bat");
            PrintWriter printWriter = new PrintWriter(fileWriter);

            printWriter.println(changeDirectoryCommand);
            printWriter.println(startCommand);
            printWriter.println("exit");
            printWriter.close();

        } catch (IOException e) {
            log.error("An unknown exception occurred while writing the startup batch for server '{}'.", getFriendlyName(), e);
        }
    }

    private static List<String> getRequiredLaunchArgs1(String map) {
        return new ArrayList<>(List.of(
                map,
                "RCONEnabled=True"
        ));
    }

    private static List<String> getRequiredLaunchArgs2() {
        return new ArrayList<>(List.of(
                "oldconsole"
        ));
    }
}
