package de.swiftbyte.gmc.daemon.utils;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.swiftbyte.gmc.daemon.Application;
import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.daemon.cache.CacheModel;
import de.swiftbyte.gmc.daemon.cache.GameServerCacheModel;
import de.swiftbyte.gmc.common.entity.GameType;
import de.swiftbyte.gmc.daemon.server.AseServer;
import de.swiftbyte.gmc.daemon.server.GameServer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.shell.component.context.ComponentContext;
import org.springframework.shell.component.flow.ComponentFlow;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

@Slf4j
public class NodeUtils {

    public static final String TMP_PATH = "tmp/",
            DAEMON_LATEST_DOWNLOAD_URL = "https://github.com/swiftbytegbr/gmc-daemon/releases/latest/download/gmc-daemon-setup.exe",
            STEAM_CMD_DIR = "steamcmd/",
            STEAM_CMD_PATH = STEAM_CMD_DIR + "steamcmd.exe",
            STEAM_CMD_DOWNLOAD_URL = "https://steamcdn-a.akamaihd.net/client/installer/steamcmd.zip";

    public static Path getSteamCmdPath() {
        return Paths.get(STEAM_CMD_PATH);
    }

    public static Integer getValidatedToken(String token) {

        log.debug("Validating token '{}'...", token);

        String normalizedToken = token.replace("-", "");

        log.debug("Token was normalized to '{}'. Checking length...", normalizedToken);

        if (normalizedToken.length() != 6) {
            log.debug("Token was not expected size.");
            return null;
        }

        log.debug("Token was expected size. Checking if token is a valid integer...");

        try {

            return Integer.parseInt(normalizedToken);

        } catch (NumberFormatException ignore) {

            log.debug("Convert token to integer failed.");
            return null;

        }
    }

    public static ComponentContext<?> promptForInviteToken() {
        ComponentFlow flow = Application.getComponentFlowBuilder().clone().reset()
                .withStringInput("inviteToken")
                .name("Please enter the Invite Token. You can find the Invite Token in the create node window in the web panel:")
                .and().build();
        return flow.run().getContext();
    }

    public static void checkInstallation() {
        if (Files.exists(Paths.get(STEAM_CMD_PATH))) {
            log.debug("SteamCMD installation found.");
        } else {
            log.info("SteamCMD installation not found. Try to install...");
            installSteamCmd();
        }
    }

    private static void installSteamCmd() {
        log.debug("Downloading SteamCMD from " + STEAM_CMD_DOWNLOAD_URL + "...");

        File tmp = new File(TMP_PATH);
        try {
            FileUtils.copyURLToFile(
                    new URL(STEAM_CMD_DOWNLOAD_URL),
                    new File(TMP_PATH + "steamcmd.zip"));

            ZipUtil.unpack(new File(TMP_PATH + "steamcmd.zip"), new File(STEAM_CMD_DIR));
            FileUtils.deleteDirectory(tmp);
            log.info("SteamCMD successfully installed!");
        } catch (IOException e) {
            log.error("An error occurred while downloading SteamCMD. Please check your internet connection!", e);
            try {
                FileUtils.deleteDirectory(tmp);
            } catch (IOException ex) {
                log.warn("An error occurred while deleting the temporary directory.", ex);
            }
            System.exit(1);
        }
    }

    public static void downloadLatestDaemonInstaller() {
        File tmp = new File(TMP_PATH);

        try {
            FileUtils.copyURLToFile(
                    new URL(DAEMON_LATEST_DOWNLOAD_URL),
                    new File(TMP_PATH + "latest-installer.exe"));

            log.debug("Update successfully downloaded!");
        } catch (IOException e) {
            log.error("An error occurred while downloading the update. Please check your internet connection!", e);
            try {
                FileUtils.deleteDirectory(tmp);
            } catch (IOException ex) {
                log.warn("An error occurred while deleting the temporary directory.", ex);
            }
        }
    }

    public static void cacheInformation(Node node) {

        if (node.getConnectionState() == ConnectionState.DELETING) return;

        HashMap<String, GameServerCacheModel> gameServers = new HashMap<>();

        for (GameServer gameServer : GameServer.getAllServers()) {

            GameType gameType = GameType.ARK_ASCENDED;
            if (gameServer instanceof AseServer) gameType = GameType.ARK_EVOLVED;

            GameServerCacheModel gameServerCacheModel = GameServerCacheModel.builder()
                    .friendlyName(gameServer.getFriendlyName())
                    .gameType(gameType)
                    .settings(gameServer.getSettings())
                    .build();
            if (gameServer.getInstallDir() == null) {
                log.error("Install directory is null for game server '{}'. Skipping...", gameServer.getFriendlyName());
                continue;
            }
            gameServerCacheModel.setInstallDir(gameServer.getInstallDir().toString());
            gameServers.put(gameServer.getServerId(), gameServerCacheModel);
        }

        CacheModel cacheModel = CacheModel.builder()
                .nodeName(node.getNodeName())
                .teamName(node.getTeamName())
                .serverPath(node.getServerPath())
                .isAutoUpdateEnabled(node.isAutoUpdateEnabled())
                .gameServerCacheModelHashMap(gameServers)
                .manageFirewallAutomatically(node.isManageFirewallAutomatically())
                .serverRestartMessage(node.getServerRestartMessage())
                .serverStopMessage(node.getServerStopMessage())
                .build();

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
        try {
            File file = new File("./cache.json");
            if (!file.exists()) file.createNewFile();
            writer.writeValue(file, cacheModel);
        } catch (IOException e) {
            log.error("An unknown error occurred while caching information.", e);
        }

    }
}
