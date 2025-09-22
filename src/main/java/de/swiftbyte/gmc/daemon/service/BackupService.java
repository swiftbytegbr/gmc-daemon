package de.swiftbyte.gmc.daemon.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.swiftbyte.gmc.daemon.Application;
import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.common.entity.Backup;
import de.swiftbyte.gmc.common.packet.server.ServerBackupResponsePacket;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.stomp.StompHandler;
import de.swiftbyte.gmc.daemon.utils.CommonUtils;
import de.swiftbyte.gmc.daemon.utils.NodeUtils;
import de.swiftbyte.gmc.daemon.utils.settings.MapSettingsAdapter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class BackupService {

    private static HashMap<String, Backup> backups = new HashMap<>();
    private static HashMap<String, ScheduledFuture<?>> backupSchedulers;

    public static void initialiseBackupService() {

        log.debug("Initialising backup service...");

        backupSchedulers = new HashMap<>();

        File cacheFile = new File("./backups.json");

        if (!cacheFile.exists()) {
            log.debug("No backups found. Skipping...");
            return;
        }

        try {
            TypeReference<HashMap<String, Backup>> typeRef = new TypeReference<>() {
            };
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModules(new JavaTimeModule());
            backups = mapper.readValue(new File("./backups.json"), typeRef);

            log.debug("Got saved backups.");

        } catch (IOException e) {
            log.error("An unknown error occurred while loading backups.", e);
        }
    }

    public static void updateAutoBackupSettings(String serverId) {

        ScheduledFuture<?> backupScheduler = backupSchedulers.get(serverId);

        if (backupScheduler != null) {
            backupScheduler.cancel(false);
        }

        GameServer server = GameServer.getServerById(serverId);

        if(server.getSettings().getGmcSettings() == null) {
            log.debug("No GMC settings found for server '{}'. Skipping auto backup setup...", server.getFriendlyName());
            return;
        }

        MapSettingsAdapter settings = new MapSettingsAdapter(server.getSettings().getGmcSettings());

        int autoBackupInterval = settings.getInt("AutoBackupInterval", 30);

        if (settings.getBoolean("AutoBackupEnabled", false) && autoBackupInterval > 0) {
            log.debug("Starting backup scheduler...");

            long delay = autoBackupInterval - (System.currentTimeMillis() / 60000) % autoBackupInterval;

            log.debug("Starting auto backup in {} minutes.", delay);

            backupSchedulers.put(serverId, Application.getExecutor().scheduleAtFixedRate(() -> {
                log.debug("Starting auto backup...");
                BackupService.backupServer(serverId, true);
            }, delay, autoBackupInterval, TimeUnit.MINUTES));

        }
    }

    private static void saveBackupCache() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
        try {
            File file = new File("./backups.json");
            if (!file.exists()) file.createNewFile();
            writer.writeValue(file, backups);
        } catch (IOException e) {
            log.error("An unknown error occurred while saving backups.", e);
        }
    }

    public static void backupServer(String serverId, boolean autoBackup) {
        backupServer(GameServer.getServerById(serverId), autoBackup, null);
    }

    public static void backupServer(GameServer server, boolean autoBackup) {
        backupServer(server, autoBackup, null);
    }

    public static void backupServer(GameServer server, boolean autoBackup, String name) {

        if (server == null) {
            log.error("Could not backup server because server id was not found!");
            return;
        }

        MapSettingsAdapter settings = new MapSettingsAdapter(server.getSettings().getGmcSettings());

        log.debug("Backing up server '{}'...", server.getFriendlyName());

        if (settings.hasAndNotEmpty("AutoBackupMessage"))
            server.sendRconCommand("serverchat " + settings.get("AutoBackupMessage", "Server backup in progress..."));

        Backup backup = new Backup();

        backup.setBackupId("gmc-back-" + UUID.randomUUID());
        backup.setCreatedAt(Instant.now());
        backup.setExpiresAt(backup.getCreatedAt().plus(settings.getInt("AutoBackupRetention") * 24 * 60, ChronoUnit.MINUTES));
        backup.setServerId(server.getServerId());
        if (CommonUtils.isNullOrEmpty(name))
            backup.setName(DateTimeFormatter.ofPattern("yyyy.MM.dd_HH-mm-ss").withZone(ZoneId.systemDefault()).format(LocalDateTime.now()) + "_" + server.getSettings().getMap());
        else backup.setName(name);
        backup.setAutoBackup(autoBackup);

        File tempBackupLocation = new File(NodeUtils.TMP_PATH + server.getServerId() + "/" + backup.getBackupId());
        File backupLocation = new File(Node.INSTANCE.getServerPath() + "/backups/" + server.getFriendlyName().toLowerCase().replace(" ", "-") + "/" + backup.getName() + ".zip");
        File saveLocation = new File(server.getInstallDir() + "/ShooterGame/Saved/SavedArks/" + server.getSettings().getMap());

        log.debug("Creating backup directories...");

        if (!tempBackupLocation.exists()) {
            tempBackupLocation.mkdirs();
        }

        if (!backupLocation.getParentFile().exists()) {
            backupLocation.getParentFile().mkdirs();
        }

        if (!saveLocation.exists()) {
            log.error("Could not backup server because save location does not exist!");
            return;
        }

        log.debug("Copying save files to temporary backup location...");

        try {
            FileUtils.copyDirectory(saveLocation, tempBackupLocation);

            //Remove ark backup files
            FileFilter mapSaveFilter = WildcardFileFilter.builder().setWildcards("*.ark").get();
            File[] mapSaveFiles = tempBackupLocation.listFiles(mapSaveFilter);
            Arrays.stream(mapSaveFiles).filter(file -> !file.getName().equalsIgnoreCase(server.getSettings().getMap() + ".ark")).forEach(File::delete);

            log.debug("Compressing backup...");
            ZipUtil.pack(tempBackupLocation, backupLocation);

            log.debug("Gathering backup information...");
            backup.setSize(backupLocation.length());
            backups.put(backup.getBackupId(), backup);

            ServerBackupResponsePacket responsePacket = new ServerBackupResponsePacket();
            responsePacket.setBackup(backup);
            responsePacket.setServerId(server.getServerId());
            StompHandler.send("/app/server/backup", responsePacket);

            log.debug("Cleaning up temporary backup location...");
            FileUtils.deleteDirectory(tempBackupLocation);

            saveBackupCache();
        } catch (IOException e) {
            log.error("An unknown error occurred while backing up server '{}'.", server.getFriendlyName(), e);
        }
    }

    public static boolean deleteBackup(String backupId) {
        Backup backup = backups.get(backupId);

        if (backup == null) {
            log.error("Could not delete backup because backup id was not found!");
            return true;
        }

        GameServer server = GameServer.getServerById(backup.getServerId());
        if (server == null) {
            log.error("Could not delete backup on file system because server id was not found!");
            backups.remove(backupId);
            saveBackupCache();
            return true;
        }

        log.debug("Deleting backup '{}'...", backup.getName());
        File backupLocation = new File(Node.INSTANCE.getServerPath() + "/backups/" + GameServer.getServerById(backup.getServerId()).getFriendlyName().toLowerCase().replace(" ", "-") + "/" + backup.getName() + ".zip");
        if (!backupLocation.exists()) {
            log.error("Could not delete backup because backup location does not exist!");
            backups.remove(backupId);
            return true;
        }

        try {
            FileUtils.forceDelete(backupLocation);
            backups.remove(backupId);
            saveBackupCache();
            return true;
        } catch (IOException e) {
            log.error("An unknown error occurred while deleting backup '{}'.", backup.getName(), e);
            return false;
        }
    }


    public static void deleteAllExpiredBackups() {
        List<Backup> expiredBackups = backups.values().stream().filter(backup -> backup.getExpiresAt().isBefore(Instant.now())).toList();

        expiredBackups.forEach(backup -> {
            log.debug("Deleting expired backup '{}'...", backup.getName());
            deleteBackup(backup.getBackupId());
        });
    }

    public static void rollbackBackup(String backupId, boolean playerData) {

        log.debug("Rolling back backup '{}'...", backupId);

        Backup backup = backups.get(backupId);
        if (backup == null) {
            log.error("Could not find backup because backup id was not found!");
            return;
        }

        GameServer server = GameServer.getServerById(backup.getServerId());
        if (server == null) {
            log.error("Could not delete backup because server id was not found!");
            return;
        }

        server.stop(false).complete();

        File backupLocation = new File(Node.INSTANCE.getServerPath() + "/backups/" + server.getFriendlyName().toLowerCase().replace(" ", "-") + "/" + backup.getName() + ".zip");
        File saveLocation = new File(server.getInstallDir() + "/ShooterGame/Saved/SavedArks/" + server.getSettings().getMap());

        if (!backupLocation.exists()) {
            log.error("Could not rollback backup because backup location does not exist!");
            return;
        }

        if (!saveLocation.exists()) {
            log.error("Could not rollback backup because server save location does not exist!");
            return;
        }

        if (playerData) {
            File[] playerDataFiles = saveLocation.listFiles();

            for (File playerDataFile : playerDataFiles) playerDataFile.delete();

            ZipUtil.unpack(backupLocation, saveLocation);
        } else {
            ZipUtil.unpackEntry(backupLocation, server.getSettings().getMap() + ".ark", new File(saveLocation + "/" + server.getSettings().getMap() + ".ark"));
        }

    }

    public static void backupAllServers(boolean autoBackup) {

        GameServer.getAllServers().forEach((server) -> backupServer(server, autoBackup));

    }

    public static List<Backup> getAllBackups() {
        return backups.values().stream().toList();
    }

    public static Backup getBackupById(String backupId) {
        return backups.get(backupId);
    }

    public static List<Backup> getBackupsByServer(String serverId) {
        return getBackupsByServer(GameServer.getServerById(serverId));
    }

    public static List<Backup> getBackupsByServer(GameServer server) {
        return backups.values().stream().filter(backup -> backup.getServerId().equals(server.getServerId())).toList();
    }

    public static void deleteAllBackupsByServer(GameServer server) {
        getBackupsByServer(server).forEach(backup -> deleteBackup(backup.getBackupId()));
    }
}
