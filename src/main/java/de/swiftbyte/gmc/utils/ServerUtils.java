package de.swiftbyte.gmc.utils;

import de.swiftbyte.gmc.Application;
import de.swiftbyte.gmc.Node;
import de.swiftbyte.gmc.cache.CacheModel;
import de.swiftbyte.gmc.cache.GameServerCacheModel;
import de.swiftbyte.gmc.common.model.SettingProfile;
import de.swiftbyte.gmc.common.parser.IniConverter;
import de.swiftbyte.gmc.server.AsaServer;
import de.swiftbyte.gmc.server.AseServer;
import de.swiftbyte.gmc.server.GameServer;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

@Slf4j
public class ServerUtils {

    public static String generateServerArgs(List<String> argsType1, List<String> argsType2, List<String> requiredArgs1, List<String> requiredArgs2) {

        StringBuilder preArgs = new StringBuilder();

        requiredArgs1.forEach(arg -> preArgs.append(arg).append("?"));

        preArgs.delete(preArgs.length() - 1, preArgs.length());

        argsType1.stream()
                .filter(arg -> requiredArgs1.stream().noneMatch(requiredArg -> (arg.split("=")[0].equalsIgnoreCase(requiredArg.split("=")[0]))))
                .forEach(arg -> {
                    if (CommonUtils.isNullOrEmpty(arg)) return;
                    if (!arg.contains("?")) preArgs.append("?");
                    preArgs.append(arg);
                });

        requiredArgs2.forEach(arg -> preArgs.append(" -").append(arg));

        argsType2.stream()
                .filter(arg -> requiredArgs2.stream().noneMatch(requiredArg -> (arg.split("=")[0].equalsIgnoreCase(requiredArg.split("=")[0]))))
                .forEach(arg -> {
                    if (CommonUtils.isNullOrEmpty(arg)) return;
                    if (!arg.replace(" ", "").startsWith("-")) preArgs.append(" -");
                    else preArgs.append(" ");
                    preArgs.append(arg);
                });

        return preArgs.toString();
    }

    public static List<String> generateArgListFromMap(Map<String, Object> args) {
        ArrayList<String> argList = new ArrayList<>();
        args.forEach((key, value) -> {
            if (value == null) argList.add(key);
            else argList.add(key + "=" + value);
        });
        return argList;
    }

    public static void killServerProcess(String PID) {

        if (PID == null) return;

        Optional<ProcessHandle> handle = ProcessHandle.of(Long.parseLong(PID));
        if (handle.isPresent()) {
            log.debug("Server process running... Killing process...");
            handle.get().destroy();
        }
    }

    public static void getCachedServerInformation() {

        log.debug("Getting cached server information...");

        File cacheFile = new File("./cache.json");

        if (!cacheFile.exists()) {
            log.debug("No cache file found. Skipping...");
            return;
        }

        try {
            CacheModel cacheModel = CommonUtils.getObjectReader().readValue(cacheFile, CacheModel.class);
            HashMap<String, GameServerCacheModel> gameServerCacheModelHashMap = cacheModel.getGameServerCacheModelHashMap();

            gameServerCacheModelHashMap.forEach((s, gameServerCacheModel) -> {
                switch (gameServerCacheModel.getGameType()) {
                    case ARK_ASCENDED ->
                            new AsaServer(s, gameServerCacheModel.getFriendlyName(), Path.of(gameServerCacheModel.getInstallDir()), gameServerCacheModel.getSettings(), false);
                    case ARK_EVOLVED ->
                            new AseServer(s, gameServerCacheModel.getFriendlyName(), Path.of(gameServerCacheModel.getInstallDir()), gameServerCacheModel.getSettings(), false);
                }
            });

        } catch (IOException e) {
            log.error("An unknown error occurred while getting cached information.", e);
        }
    }

    public static String getCachedServerInstallDir(String id) {

        File cacheFile = new File("./cache.json");

        if (!cacheFile.exists()) {
            log.debug("No cache file found. Skipping...");
            return null;
        }

        try {
            CacheModel cacheModel = CommonUtils.getObjectReader().readValue(cacheFile, CacheModel.class);
            HashMap<String, GameServerCacheModel> gameServerCacheModelHashMap = cacheModel.getGameServerCacheModelHashMap();

            for (Map.Entry<String, GameServerCacheModel> entry : gameServerCacheModelHashMap.entrySet()) {
                if (entry.getKey().equals(id)) return entry.getValue().getInstallDir();
            }

        } catch (IOException e) {
            log.error("An unknown error occurred while getting cached information.", e);
        }
        return null;
    }

    public static void writeIniFiles(GameServer server, Path installDir) {
        IniConverter iniConverter = new IniConverter();

        log.debug("Writing ini files for server {}...", server.getFriendlyName());

        LinkedHashMap<String, Object> neededCategory = new LinkedHashMap<>();
        neededCategory.put("Version", 5);
        server.getSettings().getGameUserSettings().put("/Script/ShooterGame.ShooterGameUserSettings", neededCategory);

        String gameUserSettings = iniConverter.convertFromMap(server.getSettings().getGameUserSettings());
        String gameSettings = iniConverter.convertFromMap(server.getSettings().getGameSettings());
        try {

            Path gusPath = installDir.resolve("ShooterGame/Saved/Config/WindowsServer/GameUserSettings.ini");
            if (!Files.exists(gusPath.getParent())) {
                Files.createDirectories(gusPath.getParent());
            }
            if (!CommonUtils.isNullOrEmpty(gameUserSettings)) {
                Files.write(gusPath, gameUserSettings.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }

            Path gameSettingsPath = installDir.resolve("ShooterGame/Saved/Config/WindowsServer/Game.ini");
            if (!Files.exists(gameSettingsPath.getParent())) {
                Files.createDirectories(gameSettingsPath.getParent());
            }
            if (!CommonUtils.isNullOrEmpty(gameSettings)) {
                Files.write(gameSettingsPath, gameSettings.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (IOException e) {
            log.error("An unknown error occurred while writing the ini files.", e);
        }

    }

    public static SettingProfile getSettingProfile(String settingProfileId) {

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(Application.getBackendUrl() + "/setting/profile/" + settingProfileId)
                .addHeader("Node-Id", Node.INSTANCE.getNodeId())
                .addHeader("Node-Secret", Node.INSTANCE.getSecret())
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {

            if (!response.isSuccessful()) {
                log.debug("Received error code {} with message '{}' while getting setting profile with id '{}'", response.code(), response.body().string(), settingProfileId);
                return null;
            }

            return CommonUtils.getObjectReader().readValue(response.body().string(), SettingProfile.class);
        } catch (IOException e) {
            log.error("An unknown error occurred.", e);
            return null;
        }
    }

}
