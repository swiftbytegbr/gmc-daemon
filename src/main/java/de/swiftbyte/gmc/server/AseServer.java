package de.swiftbyte.gmc.server;

import de.swiftbyte.gmc.Node;
import de.swiftbyte.gmc.common.entity.GameServerState;
import de.swiftbyte.gmc.common.model.SettingProfile;
import de.swiftbyte.gmc.service.FirewallService;
import de.swiftbyte.gmc.utils.CommonUtils;
import de.swiftbyte.gmc.utils.ServerUtils;
import de.swiftbyte.gmc.utils.SettingProfileUtils;
import lombok.extern.slf4j.Slf4j;

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

    public AseServer(String id, String friendlyName, SettingProfile settings, boolean overrideAutoStart) {

        super(id, friendlyName, settings);

        SettingProfileUtils settingProfileUtils = new SettingProfileUtils(settings.getGameUserSettings());

        rconPassword = settingProfileUtils.getSetting("ServerSettings", "ServerAdminPassword", "gmc-rp-" + UUID.randomUUID());
        rconPort = settingProfileUtils.getSettingAsInt("ServerSettings", "RconPort", 27020);

        if (!overrideAutoStart) {
            PID = CommonUtils.getProcessPID(installDir + CommonUtils.convertPathSeparator("/ShooterGame/Binaries/Win64/ArkAscendedServer.exe"));
            if (PID == null && settings.getGmcSettings().isStartOnBoot()) start().queue();
            else if (PID != null) {
                log.debug("Server '{}' with PID {} is already running. Setting state to ONLINE.", PID, friendlyName);
                super.setState(GameServerState.ONLINE);
            }
        }
    }

    public AseServer(String id, String friendlyName, Path installDir, SettingProfile settings, boolean overrideAutoStart) {

        super(id, friendlyName, settings);
        if(installDir != null) this.installDir = installDir;

        SettingProfileUtils settingProfileUtils = new SettingProfileUtils(settings.getGameUserSettings());

        rconPassword = settingProfileUtils.getSetting("ServerSettings", "ServerAdminPassword", "gmc-rp-" + UUID.randomUUID());
        rconPort = settingProfileUtils.getSettingAsInt("ServerSettings", "RconPort", 27020);

        if (!overrideAutoStart) {
            PID = CommonUtils.getProcessPID(this.installDir + CommonUtils.convertPathSeparator("/ShooterGame/Binaries/Win64/"));
            if (PID == null && settings.getGmcSettings().isStartOnBoot()) start().queue();
            else if (PID != null) {
                log.debug("Server '{}' with PID {} is already running. Setting state to ONLINE.", PID, friendlyName);
                super.setState(GameServerState.ONLINE);
            }
        }
    }

    @Override
    public List<Integer> getNeededPorts() {

        SettingProfileUtils spu = new SettingProfileUtils(settings.getGameUserSettings());

        int gamePort = spu.getSettingAsInt("SessionSettings", "Port", 7777);
        int rconPort = spu.getSettingAsInt("ServerSettings", "RCONPort", 27020);
        int queryPort = spu.getSettingAsInt("SessionSettings", "QueryPort", 27015);

        return List.of(gamePort, gamePort+1, rconPort, queryPort);
    }

    @Override
    public void allowFirewallPorts() {
        if (Node.INSTANCE.isManageFirewallAutomatically()) {
            log.debug("Adding firewall rules for server '{}'...", friendlyName);
            Path executablePath = Path.of(installDir + "/ShooterGame/Binaries/Win64/");
            FirewallService.allowPort(friendlyName, executablePath, getNeededPorts());
        }
    }

    @SuppressWarnings("DuplicatedCode")
    public void writeStartupBatch() {

        SettingProfile settings = getSettings();

        if (CommonUtils.isNullOrEmpty(settings.getGmcSettings().getMap())) {
            log.error("Map is not set for server '{}'. Falling back to default map.", getFriendlyName());
            settings.getGmcSettings().setMap("TheIsland");
        }

        SettingProfileUtils spu = new SettingProfileUtils(settings.getGameUserSettings());

        setRconPort(spu.getSettingAsInt("ServerSettings", "RCONPort", 27020));
        setRconPassword(spu.getSetting("ServerSettings", "ServerAdminPassword", "gmc-rp-" + UUID.randomUUID()));

        List<String> requiredLaunchParameters1 = getRequiredLaunchArgs1(settings.getGmcSettings().getMap());
        List<String> requiredLaunchParameters2 = getRequiredLaunchArgs2();

        String realStartPostArguments = ServerUtils.generateServerArgs(
                settings.getQuestionMarkParams() == null || settings.getQuestionMarkParams().isEmpty() ? new ArrayList<>() :  ServerUtils.generateArgListFromMap(settings.getQuestionMarkParams()),
                settings.getHyphenParams() == null || settings.getQuestionMarkParams().isEmpty() ? new ArrayList<>() :  ServerUtils.generateArgListFromMap(settings.getHyphenParams()),
                requiredLaunchParameters1,
                requiredLaunchParameters2
        );

        String changeDirectoryCommand = "cd /d \"" + CommonUtils.convertPathSeparator(getInstallDir()) + "\\ShooterGame\\Binaries\\Win64\"";

        String serverExeName = "ShooterGameServer.exe";

        if (Files.exists(Path.of(getInstallDir() + "/ShooterGame/Binaries/Win64/AseApiLoader.exe")))
            serverExeName = "AseApiLoader.exe";

        String startCommand = "start \"" + getFriendlyName() + "\""
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
