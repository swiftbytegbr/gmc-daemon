package de.swiftbyte.gmc.daemon.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.swiftbyte.gmc.common.entity.Backup;
import de.swiftbyte.gmc.common.packet.from.daemon.server.ServerBackupResponsePacket;
import de.swiftbyte.gmc.daemon.Application;
import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.daemon.server.AsaServer;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.stomp.StompHandler;
import de.swiftbyte.gmc.daemon.service.TaskService;
import de.swiftbyte.gmc.daemon.utils.CommonUtils;
import de.swiftbyte.gmc.daemon.utils.NodeUtils;
import de.swiftbyte.gmc.daemon.utils.settings.MapSettingsAdapter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Path;
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
    private static volatile boolean backupsSuspended = false;

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

        if (server.getSettings().getGmcSettings() == null) {
            log.debug("No GMC settings found for server '{}'. Skipping auto backup setup...", server.getFriendlyName());
            return;
        }

        MapSettingsAdapter settings = new MapSettingsAdapter(server.getSettings().getGmcSettings());

        int autoBackupInterval = settings.getInt("AutoBackupInterval", 30);

        if (!backupsSuspended && settings.getBoolean("AutoBackupEnabled", false) && autoBackupInterval > 0) {
            log.debug("Starting backup scheduler...");

            long delay = autoBackupInterval - (System.currentTimeMillis() / 60000) % autoBackupInterval;

            log.debug("Starting auto backup in {} minutes.", delay);

            backupSchedulers.put(serverId, Application.getExecutor().scheduleAtFixedRate(() -> {
                try {
                    if (backupsSuspended) {
                        log.debug("Auto backup skipped (backups suspended).");
                        return;
                    }
                    log.debug("Starting auto backup...");
                    // Schedule backup as a non-cancellable task via TaskService
                    TaskService.createTask(
                            de.swiftbyte.gmc.common.model.NodeTask.Type.BACKUP,
                            new de.swiftbyte.gmc.daemon.tasks.consumers.BackupTaskConsumer.BackupTaskPayload(true, null),
                            Node.INSTANCE.getNodeId(),
                            serverId
                    );
                } catch (Exception e) {
                    log.error("Unhandled exception during auto backup for server '{}'.", server.getFriendlyName(), e);
                }
            }, delay, autoBackupInterval, TimeUnit.MINUTES));

        }
    }

    private static void saveBackupCache() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
        try {
            File file = new File("./backups.json");
            if (!file.exists()) {
                file.createNewFile();
            }
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
            throw new IllegalArgumentException("Cannot create backup: server not found");
        }

        if (backupsSuspended) {
            log.warn("Backups are currently suspended. Skipping backup for server '{}'.", server.getFriendlyName());
            return;
        }

        MapSettingsAdapter settings = new MapSettingsAdapter(server.getSettings().getGmcSettings());

        log.debug("Backing up server '{}'...", server.getFriendlyName());

        if (settings.hasAndNotEmpty("AutoBackupMessage")) {
            server.sendRconCommand("serverchat " + settings.get("AutoBackupMessage", "Server backup in progress..."));
        }

        server.sendRconCommand("saveworld");

        Backup backup = new Backup();

        backup.setBackupId("gmc-back-" + UUID.randomUUID());
        backup.setCreatedAt(Instant.now());
        backup.setExpiresAt(backup.getCreatedAt().plus(settings.getInt("AutoBackupRetention") * 24 * 60, ChronoUnit.MINUTES));
        backup.setServerId(server.getServerId());
        if (CommonUtils.isNullOrEmpty(name)) {
            backup.setName(DateTimeFormatter.ofPattern("yyyy.MM.dd_HH-mm-ss").withZone(ZoneId.systemDefault()).format(LocalDateTime.now()) + "_" + server.getSettings().getMap());
        } else {
            backup.setName(name);
        }
        backup.setAutoBackup(autoBackup);

        File tempBackupLocation = Path.of(NodeUtils.TMP_PATH, server.getServerId(), backup.getBackupId()).toFile();
        File backupLocation = Path.of(Node.INSTANCE.getBackupPath(), server.getServerId(), backup.getName() + ".zip").toFile();

        //TODO find a better way to handle different save locations for different game servers then hardcoding it here
        File saveLocation = new File(server.getInstallDir() + "/ShooterGame/Saved/SavedArks" + (server instanceof AsaServer ? "/" + server.getSettings().getMap() : ""));

        log.debug("Creating backup directories...");

        if (!tempBackupLocation.exists()) {
            tempBackupLocation.mkdirs();
        }

        if (!backupLocation.getParentFile().exists()) {
            backupLocation.getParentFile().mkdirs();
        }

        if (!saveLocation.exists()) {
            throw new IllegalStateException("Save location does not exist: " + saveLocation.getAbsolutePath());
        }

        log.debug("Copying save files to temporary backup location...");

        try {
            IOFileFilter filter = FileFilterUtils.notFileFilter(FileFilterUtils.suffixFileFilter(".tmp"));
            FileUtils.copyDirectory(saveLocation, tempBackupLocation, filter);

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
            throw new RuntimeException("Backup failed for server '" + server.getFriendlyName() + "': " + e.getMessage(), e);
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
        File backupLocation = Path.of(Node.INSTANCE.getBackupPath(), server.getServerId(), backup.getName() + ".zip").toFile();
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
        if (backupsSuspended) {
            log.debug("Skipping expired backup cleanup; backups suspended.");
            return;
        }
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
            throw new IllegalArgumentException("Backup not found: " + backupId);
        }

        GameServer server = GameServer.getServerById(backup.getServerId());
        if (server == null) {
            throw new IllegalStateException("Server not found for backup: " + backup.getServerId());
        }

        server.stop(false).complete();

        File backupLocation = Path.of(Node.INSTANCE.getBackupPath(), server.getServerId(), backup.getName() + ".zip").toFile();

        //TODO find a better way to handle different save locations for different game servers then hardcoding it here
        File saveLocation = new File(server.getInstallDir() + "/ShooterGame/Saved/SavedArks" + (server instanceof AsaServer ? "/" + server.getSettings().getMap() : ""));

        if (!backupLocation.exists()) {
            throw new IllegalStateException("Backup file does not exist: " + backupLocation.getAbsolutePath());
        }

        if (!saveLocation.exists()) {
            throw new IllegalStateException("Server save location does not exist: " + saveLocation.getAbsolutePath());
        }

        try {
            if (playerData) {
                File[] playerDataFiles = saveLocation.listFiles();
                if (playerDataFiles != null) {
                    for (File playerDataFile : playerDataFiles) {
                        if (!playerDataFile.delete()) {
                            // Non-fatal: try to continue and let Zip overwrite
                        }
                    }
                }
                ZipUtil.unpack(backupLocation, saveLocation);
            } else {
                ZipUtil.unpackEntry(backupLocation, server.getSettings().getMap() + ".ark", new File(saveLocation + "/" + server.getSettings().getMap() + ".ark"));
            }
        } catch (Exception e) {
            throw new RuntimeException("Rollback failed for backup '" + backupId + "': " + e.getMessage(), e);
        }

    }

    public static void backupAllServers(boolean autoBackup) {

        if (backupsSuspended) {
            log.debug("Skipping backupAllServers; backups suspended.");
            return;
        }
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

    public static void suspendBackups() {
        log.info("Suspending backups and auto-backup schedulers...");
        backupsSuspended = true;
        if (backupSchedulers != null) {
            backupSchedulers.values().forEach(s -> {
                try { s.cancel(false); } catch (Exception ignored) {}
            });
            backupSchedulers.clear();
        }
    }

    public static void resumeBackups() {
        log.info("Resuming backups and restoring auto-backup schedulers...");
        backupsSuspended = false;
        // Recreate auto-backup schedulers for all servers based on their settings
        GameServer.getAllServers().forEach(server -> updateAutoBackupSettings(server.getServerId()));
    }

    public static void moveBackupsDirectory(Path oldBackupsDirPath, Path newBackupsDirPath) throws IOException {
        // Treat inputs as backup base directories
        File oldBackupsDir = oldBackupsDirPath.toFile();
        File newBackupsDir = newBackupsDirPath.toFile();

        log.info("Moving backups from '{}' to '{}'...", oldBackupsDir.getAbsolutePath(), newBackupsDir.getAbsolutePath());

        if (!oldBackupsDir.exists()) {
            log.info("Old backups directory does not exist. Nothing to move.");
            return;
        }

        if (!newBackupsDir.exists()) {
            if (!newBackupsDir.mkdirs()) {
                throw new IOException("Failed to create new backups directory: " + newBackupsDir.getAbsolutePath());
            }
        }

        File[] entries = oldBackupsDir.listFiles();
        if (entries == null) return;

        for (File entry : entries) {
            File dest = new File(newBackupsDir, entry.getName());
            if (entry.isDirectory()) {
                if (dest.exists()) {
                    // merge
                    FileUtils.copyDirectory(entry, dest);
                    FileUtils.deleteDirectory(entry);
                } else {
                    FileUtils.moveDirectory(entry, dest);
                }
            } else {
                if (dest.exists()) {
                    FileUtils.forceDelete(dest);
                }
                FileUtils.moveFile(entry, dest);
            }
        }

        // Try to remove old empty backups dir
        try {
            FileUtils.deleteDirectory(oldBackupsDir);
        } catch (Exception ignored) {
        }

        log.info("Backups move completed.");
    }
}
