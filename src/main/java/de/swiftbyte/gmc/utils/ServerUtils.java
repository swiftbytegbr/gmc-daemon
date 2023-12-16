package de.swiftbyte.gmc.utils;

import de.swiftbyte.gmc.cache.CacheModel;
import de.swiftbyte.gmc.cache.GameServerCacheModel;
import de.swiftbyte.gmc.packet.entity.ServerSettings;
import de.swiftbyte.gmc.server.AsaServer;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Slf4j
public class ServerUtils {

    public static String generateAsaServerArgs(List<String> argsType1, List<String> argsType2, List<String> requiredArgs1, String rconPassword, List<String> requiredArgs2) {

        StringBuilder preArgs = new StringBuilder();

        requiredArgs1.forEach(arg -> preArgs.append(arg).append("?"));

        preArgs.delete(preArgs.length() - 1, preArgs.length());

        argsType1.stream()
                .filter(arg -> requiredArgs1.stream().noneMatch(requiredArg -> (arg.contains(requiredArg.split("=")[0]))))
                .forEach(arg -> {
                    if (CommonUtils.isNullOrEmpty(arg)) return;
                    if (!arg.contains("?")) preArgs.append("?");
                    preArgs.append(arg);
                });

        preArgs.append("?ServerAdminPassword=\"").append(rconPassword).append("\"");

        requiredArgs2.forEach(arg -> preArgs.append(" -").append(arg));

        argsType2.stream()
                .filter(arg -> requiredArgs1.stream().noneMatch(requiredArg -> (arg.contains(requiredArg.split("=")[0]))))
                .forEach(arg -> {
                    if (CommonUtils.isNullOrEmpty(arg)) return;
                    if (!arg.contains("-")) preArgs.append(" -");
                    else preArgs.append(" ");
                    preArgs.append(arg);
                });

        return preArgs.toString();
    }

    public static void writeAsaStartupBatch(AsaServer server) {

        ServerSettings settings = server.getSettings();

        if (CommonUtils.isNullOrEmpty(settings.getMap())) {
            log.error("Map is not set for server '" + server.getFriendlyName() + "'. Falling back to default map.");
            settings.setMap("TheIsland_WP");
        }

        if (CommonUtils.isNullOrEmpty(settings.getRconPassword())) {
            settings.setRconPassword("gmc-rp-" + UUID.randomUUID());
        }

        server.setRconPort(settings.getRconPort());
        server.setRconPassword(settings.getRconPassword());

        List<String> requiredLaunchParameters1 = getRequiredLaunchArgs1(server, settings);
        List<String> requiredLaunchParameters2 = getRequiredLaunchArgs2(settings);

        String realStartPostArguments = generateAsaServerArgs(
                settings.getLaunchParameters2() == null ? new ArrayList<>() : settings.getLaunchParameters2(),
                settings.getLaunchParameters3() == null ? new ArrayList<>() : settings.getLaunchParameters3(),
                requiredLaunchParameters1,
                settings.getRconPassword(),
                requiredLaunchParameters2
        );

        String realStartPreArguments = String.join(" ", settings.getLaunchParameters1() == null ? new ArrayList<>() : settings.getLaunchParameters1());

        String changeDirectoryCommand = "cd /d \"" + CommonUtils.convertPathSeparator(server.getInstallDir()) + "\\ShooterGame\\Binaries\\Win64\"";

        String serverExeName = "ArkAscendedServer.exe";

        if (Files.exists(Path.of(server.getInstallDir() + "/ShooterGame/Binaries/Win64/AsaApiLoader.exe")))
            serverExeName = "AsaApiLoader.exe";

        String startCommand = "start \"" + server.getFriendlyName() + "\""
                + (realStartPreArguments.isEmpty() ? "" : " " + realStartPreArguments)
                + " \"" + CommonUtils.convertPathSeparator(server.getInstallDir() + "/ShooterGame/Binaries/Win64/" + serverExeName) + "\""
                + " " + realStartPostArguments;
        log.debug("Writing startup batch for server " + server.getFriendlyName() + " with command '" + startCommand + "'");

        try {
            FileWriter fileWriter = new FileWriter(server.getInstallDir() + "/start.bat");
            PrintWriter printWriter = new PrintWriter(fileWriter);

            printWriter.println(changeDirectoryCommand);
            printWriter.println(startCommand);
            printWriter.println("exit");
            printWriter.close();

        } catch (IOException e) {
            log.error("An unknown exception occurred while writing the startup batch for server '" + server.getFriendlyName() + "'.", e);
        }
    }

    private static List<String> getRequiredLaunchArgs1(AsaServer server, ServerSettings settings) {
        List<String> requiredLaunchParameters1 = new ArrayList<>(List.of(
                settings.getMap(),
                "listen",
                "Port=" + settings.getGamePort(),
                "QueryPort=" + settings.getQueryPort(),
                "RCONEnabled=True",
                "RCONPort=" + settings.getRconPort(),
                "ClampItemStats=" + settings.isClampItemStats()
        ));

        if (!CommonUtils.isNullOrEmpty(settings.getName()))
            requiredLaunchParameters1.add("SessionName=" + settings.getName().replace(" ", "-"));
        if (!CommonUtils.isNullOrEmpty(settings.getServerIp()))
            requiredLaunchParameters1.add("MultiHome=" + settings.getServerIp());
        if (!CommonUtils.isNullOrEmpty(settings.getServerPassword()))
            requiredLaunchParameters1.add("ServerPassword=\"" + settings.getServerPassword() + "\"");
        if (!CommonUtils.isNullOrEmpty(settings.getSpecPassword()))
            requiredLaunchParameters1.add("SpectatorPassword=\"" + settings.getSpecPassword() + "\"");
        return requiredLaunchParameters1;
    }

    private static List<String> getRequiredLaunchArgs2(ServerSettings settings) {
        List<String> requiredLaunchParameters1 = new ArrayList<>(List.of(
                "game",
                "server",
                "log",
                "oldconsole"
        ));

        if (settings.getMaxPlayers() != 0)
            requiredLaunchParameters1.add("WinLiveMaxPlayers=" + settings.getMaxPlayers());
        if (!CommonUtils.isNullOrEmpty(settings.getModIds()))
            requiredLaunchParameters1.add("mods=" + settings.getModIds());
        if (!settings.isEnableBattlEye()) requiredLaunchParameters1.add("NoBattlEye");
        if (!CommonUtils.isNullOrEmpty(settings.getCulture()))
            requiredLaunchParameters1.add("culture=" + settings.getCulture());
        return requiredLaunchParameters1;
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

            gameServerCacheModelHashMap.forEach((s, gameServerCacheModel) -> new AsaServer(s, gameServerCacheModel.getFriendlyName(), Path.of(gameServerCacheModel.getInstallDir()), gameServerCacheModel.getSettings(), false));

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

}
