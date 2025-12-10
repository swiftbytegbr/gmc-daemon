package de.swiftbyte.gmc.daemon.utils;

import de.swiftbyte.gmc.common.entity.GameType;
import de.swiftbyte.gmc.daemon.Application;
import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.daemon.cache.CacheModel;
import de.swiftbyte.gmc.daemon.cache.GameServerCacheModel;
import de.swiftbyte.gmc.daemon.server.AseServer;
import de.swiftbyte.gmc.daemon.server.GameServer;
import lombok.CustomLog;
import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.shell.component.context.ComponentContext;
import org.springframework.shell.component.flow.ComponentFlow;
import org.zeroturnaround.zip.ZipUtil;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

@CustomLog
public final class NodeUtils {

    public static final @NonNull String TMP_PATH = "tmp/",
            DAEMON_LATEST_DOWNLOAD_URL = "https://github.com/swiftbytegbr/gmc-daemon/releases/latest/download/gmc-daemon-setup.exe",
            STEAM_CMD_DIR = "steamcmd/",
            STEAM_CMD_PATH = STEAM_CMD_DIR + "steamcmd.exe",
            STEAM_CMD_DOWNLOAD_URL = "https://steamcdn-a.akamaihd.net/client/installer/steamcmd.zip",
            CACHE_FILE_PATH = "./cache.json";

    private NodeUtils() {
    }

    /**
     * Resolves the absolute path to the SteamCMD executable.
     *
     * @return normalized path to SteamCMD
     */
    public static @NonNull Path getSteamCmdPath() {
        return PathUtils.getAbsolutPath(STEAM_CMD_PATH).normalize();
    }

    /**
     * Validates and normalizes an invite token.
     *
     * @param token raw invite token (may include separators)
     * @return six-digit token as integer or {@code null} if invalid
     */
    public static @Nullable Integer getValidatedToken(@NonNull String token) {

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

    /**
     * Prompts the user for an invite token using the configured component flow.
     *
     * @return flow context containing the entered token
     * @throws IllegalStateException if the component flow builder is not initialised
     */
    public static @NonNull ComponentContext<?> promptForInviteToken() {

        if (Application.getComponentFlowBuilder() == null) {
            throw new IllegalStateException("Component flow has not been initialised yet");
        }

        ComponentFlow flow = Application.getComponentFlowBuilder().clone().reset()
                .withStringInput("inviteToken")
                .name("Please enter the Invite Token. You can find the Invite Token in the create node window in the web panel:")
                .and().build();
        return flow.run().getContext();
    }

    /**
     * Ensures SteamCMD is installed, triggering installation if missing.
     */
    public static void checkInstallation() {
        if (Files.exists(getSteamCmdPath())) {
            log.debug("SteamCMD installation found.");
        } else {
            log.info("SteamCMD installation not found. Try to install...");
            installSteamCmd();
        }
    }

    /**
     * Downloads and extracts SteamCMD into the configured directory.
     */
    private static void installSteamCmd() {
        log.debug("Downloading SteamCMD from " + STEAM_CMD_DOWNLOAD_URL + "...");

        File tmp = new File(TMP_PATH);
        try {
            FileUtils.copyURLToFile(
                    new URI(STEAM_CMD_DOWNLOAD_URL).toURL(),
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
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Downloads the latest daemon installer into the temporary directory.
     */
    public static void downloadLatestDaemonInstaller() {
        File tmp = new File(TMP_PATH);

        try {
            FileUtils.copyURLToFile(
                    new URI(DAEMON_LATEST_DOWNLOAD_URL).toURL(),
                    new File(TMP_PATH + "latest-installer.exe"));

            log.debug("Update successfully downloaded!");
        } catch (IOException e) {
            log.error("An error occurred while downloading the update. Please check your internet connection!", e);
            try {
                FileUtils.deleteDirectory(tmp);
            } catch (IOException ex) {
                log.warn("An error occurred while deleting the temporary directory.", ex);
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Caches node and server information to disk for reuse on restart.
     *
     * @param node node data to serialize
     */
    public static void cacheInformation(@NonNull Node node) {

        if (node.getConnectionState() == ConnectionState.DELETING) {
            return;
        }

        HashMap<String, GameServerCacheModel> gameServers = new HashMap<>();

        for (GameServer gameServer : GameServer.getAllServers()) {

            GameType gameType = GameType.ARK_ASCENDED;
            if (gameServer instanceof AseServer) {
                gameType = GameType.ARK_EVOLVED;
            }

            GameServerCacheModel gameServerCacheModel = GameServerCacheModel.builder()
                    .friendlyName(gameServer.getFriendlyName())
                    .gameType(gameType)
                    .settings(gameServer.getSettings())
                    .build();
            gameServerCacheModel.setInstallDir(gameServer.getInstallDir().toString());
            gameServers.put(gameServer.getServerId(), gameServerCacheModel);
        }

        CacheModel cacheModel = CacheModel.builder()
                .nodeName(node.getNodeName())
                .teamName(node.getTeamName())
                .defaultServerDirectory(node.getDefaultServerDirectory().toString())
                .backupPath(node.getBackupPath().toString())
                .isAutoUpdateEnabled(node.isAutoUpdateEnabled())
                .gameServerCacheModelHashMap(gameServers)
                .manageFirewallAutomatically(node.isManageFirewallAutomatically())
                .build();

        ObjectWriter writer = JsonMapper.builder().build().writerWithDefaultPrettyPrinter();
        try {
            File file = new File(CACHE_FILE_PATH);
            if (!file.exists() && !file.createNewFile()) {
                log.warn("There was an error creating the node cache file!");
            }
            writer.writeValue(file, cacheModel);
        } catch (IOException e) {
            log.error("An unknown error occurred while caching information.", e);
        }
    }
}
