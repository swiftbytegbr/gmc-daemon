package de.swiftbyte.gmc.daemon.utils;

import de.swiftbyte.gmc.common.model.SettingProfile;
import de.swiftbyte.gmc.common.parser.IniConverter;
import de.swiftbyte.gmc.daemon.Application;
import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.daemon.cache.CacheModel;
import de.swiftbyte.gmc.daemon.cache.GameServerCacheModel;
import de.swiftbyte.gmc.daemon.server.AsaServer;
import de.swiftbyte.gmc.daemon.server.AseServer;
import de.swiftbyte.gmc.daemon.server.GameServer;
import lombok.CustomLog;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@CustomLog
public final class ServerUtils {

    /**
     * Builds a combined server argument string while ensuring required arguments appear first.
     *
     * @param argsType1        first argument list, separated by '?'
     * @param additionalArgsType1 optional extra arguments for the first section
     * @param argsType2        second argument list, prefixed with '-'
     * @param additionalArgsType2 optional extra arguments for the second section
     * @param requiredArgs1    required entries for the first section
     * @param requiredArgs2    required entries for the second section
     * @return concatenated argument string ready for execution
     */
    public static @NonNull String generateServerArgs(@NonNull List<@NonNull String> argsType1,
                                                     @Nullable String additionalArgsType1,
                                                     @NonNull List<@NonNull String> argsType2,
                                                     @Nullable String additionalArgsType2,
                                                     @NonNull List<@NonNull String> requiredArgs1,
                                                     @NonNull List<@NonNull String> requiredArgs2) {

        StringBuilder preArgs = new StringBuilder();

        requiredArgs1.forEach(arg -> preArgs.append(arg).append("?"));

        preArgs.delete(preArgs.length() - 1, preArgs.length());

        argsType1.stream()
                .filter(arg -> requiredArgs1.stream().noneMatch(requiredArg -> (arg.split("=")[0].equalsIgnoreCase(requiredArg.split("=")[0]))))
                .forEach(arg -> {
                    if (Utils.isNullOrEmpty(arg)) {
                        return;
                    }
                    if (!arg.contains("?")) {
                        preArgs.append("?");
                    }
                    preArgs.append(arg);
                });
        if (!Utils.isNullOrEmpty(additionalArgsType1) && !additionalArgsType1.startsWith("?")) {
            preArgs.append("?");
        }
        preArgs.append(additionalArgsType1);

        requiredArgs2.forEach(arg -> preArgs.append(" -").append(arg));

        argsType2.stream()
                .filter(arg -> requiredArgs2.stream().noneMatch(requiredArg -> (arg.split("=")[0].equalsIgnoreCase(requiredArg.split("=")[0]))))
                .forEach(arg -> {
                    if (Utils.isNullOrEmpty(arg)) {
                        return;
                    }
                    if (!arg.replace(" ", "").startsWith("-")) {
                        preArgs.append(" -");
                    } else {
                        preArgs.append(" ");
                    }
                    preArgs.append(arg);
                });
        if (!Utils.isNullOrEmpty(additionalArgsType2)) {
            preArgs.append(" ");
        }
        preArgs.append(additionalArgsType2);

        return preArgs.toString();
    }

    /**
     * Converts a map of arguments into a list of CLI-ready strings.
     *
     * @param args                 argument key/value pairs
     * @param writeOutBooleanFlags whether boolean {@code true} should emit {@code key=value} instead of just {@code key}
     * @return list of formatted arguments
     */
    public static @NonNull List<@NonNull String> generateArgListFromMap(@NonNull Map<@NonNull String, @Nullable Object> args, boolean writeOutBooleanFlags) {
        ArrayList<String> argList = new ArrayList<>();
        args.forEach((key, value) -> {
            if (value == null) {
                return;
            }

            if (!writeOutBooleanFlags && value instanceof Boolean) {
                if ((Boolean) value) {
                    argList.add(key);
                }
            } else {
                argList.add(key + "=" + value);
            }
        });
        return argList;
    }

    /**
     * Converts a map of arguments into CLI-ready strings, always writing boolean flags.
     *
     * @param args argument key/value pairs
     * @return list of formatted arguments
     */
    public static @NonNull List<@NonNull String> generateArgListFromMap(@NonNull Map<@NonNull String, Object> args) {
        return generateArgListFromMap(args, true);
    }

    /**
     * Reconstructs server instances from cached data if available.
     */
    public static void loadCachedServerInformation() {

        log.debug("Loading cached server information...");

        File cacheFile = Path.of(NodeUtils.CACHE_FILE_PATH).toFile();

        if (!cacheFile.exists()) {
            log.debug("No cache file found. Skipping...");
            return;
        }

        CacheModel cacheModel = Utils.getObjectReader(CacheModel.class).readValue(cacheFile);
        HashMap<String, GameServerCacheModel> gameServerCacheModelHashMap = cacheModel.getGameServerCacheModelHashMap();

        if (gameServerCacheModelHashMap == null) {
            log.warn("No cached game servers found!");
            return;
        }

        gameServerCacheModelHashMap.forEach((s, gscm) -> {
            if (gscm.getGameType() == null || gscm.getFriendlyName() == null || gscm.getInstallDir() == null || gscm.getSettings() == null) {
                return;
            }
            switch (gscm.getGameType()) {
                case ARK_ASCENDED ->
                        new AsaServer(s, gscm.getFriendlyName(), Path.of(gscm.getInstallDir()), gscm.getSettings(), false);
                case ARK_EVOLVED ->
                        new AseServer(s, gscm.getFriendlyName(), Path.of(gscm.getInstallDir()), gscm.getSettings(), false);
            }
        });

    }

    /**
     * Retrieves cached settings for a specific game server.
     *
     * @param id server identifier
     * @return cached {@link SettingProfile} or {@code null} if unavailable
     */
    public static @Nullable SettingProfile getCachedGameServerSettings(@NonNull String id) {

        File cacheFile = new File(NodeUtils.CACHE_FILE_PATH);

        if (!cacheFile.exists()) {
            log.debug("No cache file found. Skipping...");
            return null;
        }

        CacheModel cacheModel = Utils.getObjectReader(CacheModel.class).readValue(cacheFile);
        HashMap<String, GameServerCacheModel> gameServerCacheModelHashMap = cacheModel.getGameServerCacheModelHashMap();

        GameServerCacheModel cached = gameServerCacheModelHashMap != null ? gameServerCacheModelHashMap.get(id) : null;
        if (cached != null) {
            return cached.getSettings();
        }
        return null;
    }

    /**
     * Writes ARK configuration files derived from the server's settings.
     *
     * @param server     server instance providing settings
     * @param installDir installation directory where files should be written
     */
    public static void writeIniFiles(@NonNull GameServer server, @NonNull Path installDir) {

        IniConverter iniConverter = new IniConverter();

        log.debug("Writing ini files for server {}...", server.getFriendlyName());

        final String NEEDED_SECTION_KEY = "/Script/ShooterGame.ShooterGameUserSettings";
        LinkedHashMap<String, LinkedHashMap<String, Object>> gus = server.getSettings().getGameUserSettings();

        if (gus.containsKey(NEEDED_SECTION_KEY)) {
            gus.get(NEEDED_SECTION_KEY).put("Version", 5);
        } else {
            LinkedHashMap<String, Object> neededSection = new LinkedHashMap<>();
            neededSection.put("Version", 5);

            gus.put(NEEDED_SECTION_KEY, neededSection);
        }

        String gameUserSettings = iniConverter.convertFromMap(server.getSettings().getGameUserSettings());
        String gameSettings = iniConverter.convertFromMap(server.getSettings().getGameSettings());
        try {

            Path gusPath = installDir.resolve("ShooterGame/Saved/Config/WindowsServer/GameUserSettings.ini");
            if (!Files.exists(gusPath.getParent())) {
                Files.createDirectories(gusPath.getParent());
            }
            if (!Utils.isNullOrEmpty(gameUserSettings)) {
                Files.write(gusPath, gameUserSettings.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }

            Path gameSettingsPath = installDir.resolve("ShooterGame/Saved/Config/WindowsServer/Game.ini");
            if (!Files.exists(gameSettingsPath.getParent())) {
                Files.createDirectories(gameSettingsPath.getParent());
            }
            if (!Utils.isNullOrEmpty(gameSettings)) {
                Files.write(gameSettingsPath, gameSettings.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (IOException e) {
            log.error("An unknown error occurred while writing the ini files.", e);
        }

    }

    /**
     * Fetches a setting profile from the backend service.
     *
     * @param settingProfileId identifier of the setting profile
     * @return parsed {@link SettingProfile} or {@code null} when not found or on error
     */
    public static @Nullable SettingProfile getSettingProfile(@NonNull String settingProfileId) {

        Node node = Node.INSTANCE;

        if (node == null) {
            throw new IllegalStateException("Node is not initialized yet.");
        }

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(Application.getBackendUrl() + "/setting/profile/" + settingProfileId)
                .addHeader("Node-Id", node.getNodeId())
                .addHeader("Node-Secret", node.getSecret())
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {

            if (!response.isSuccessful()) {
                log.debug("Received error code {} with message '{}' while getting setting profile with id '{}'", response.code(), response.body().string(), settingProfileId);
                return null;
            }

            return Utils.getObjectReader(SettingProfile.class).readValue(response.body().string());
        } catch (IOException e) {
            log.error("An unknown error occurred.", e);
            return null;
        }
    }

}
