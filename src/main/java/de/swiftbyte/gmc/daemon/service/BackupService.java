package de.swiftbyte.gmc.daemon.service;

import de.swiftbyte.gmc.common.entity.Backup;
import de.swiftbyte.gmc.common.entity.GameServerState;
import de.swiftbyte.gmc.common.packet.from.daemon.server.ServerBackupResponsePacket;
import de.swiftbyte.gmc.daemon.Application;
import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.daemon.server.AsaServer;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.stomp.StompHandler;
import de.swiftbyte.gmc.daemon.utils.NodeUtils;
import de.swiftbyte.gmc.daemon.utils.PathUtils;
import de.swiftbyte.gmc.daemon.utils.Utils;
import de.swiftbyte.gmc.daemon.utils.settings.MapSettingsAdapter;
import lombok.CustomLog;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zeroturnaround.zip.ZipUtil;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@CustomLog
public class BackupService {

    private static final @NonNull String BACKUP_CONFIG_PATH = "./backups.json";

    private static @NonNull HashMap<@NonNull String, @NonNull Backup> backups = new HashMap<>();
    private static final @NonNull HashMap<@NonNull String, @NonNull ScheduledFuture<?>> backupSchedulers = new HashMap<>();
    private static volatile boolean backupsSuspended = false;

    public static void initialiseBackupService() {

        log.debug("Initialising backup service...");

        backupSchedulers.clear();

        File cacheFile = new File("./backups.json");

        if (!cacheFile.exists()) {
            log.debug("No backups found. Skipping...");
            return;
        }

        TypeReference<HashMap<String, Backup>> typeRef = new TypeReference<>() {
        };
        JsonMapper mapper = JsonMapper.builder().build();
        backups = mapper.readValue(new File(BACKUP_CONFIG_PATH), typeRef);

        log.debug("Loaded saved backups.");

    }

    public static void updateAutoBackupSettings(@NonNull String serverId) {

        Node node = Node.INSTANCE;

        if (node == null) {
            throw new IllegalStateException("Node is not initialized yet");
        }

        ScheduledFuture<?> backupScheduler = backupSchedulers.get(serverId);

        if (backupScheduler != null) {
            backupScheduler.cancel(false);
        }

        GameServer server = GameServer.getServerById(serverId);

        if (server == null) {
            log.debug("No server found for id '{}'. Skipping auto backup setup...", serverId);
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
                    if (server.getState() == GameServerState.CREATING) {
                        log.debug("Auto backup skipped for '{}' (server is in CREATING state).", server.getFriendlyName());
                        return;
                    }
                    log.debug("Starting auto backup...");
                    // Schedule backup as a non-cancellable task via TaskService

                    HashMap<String, Object> context = new HashMap<>();
                    context.put("backupName", "Auto Backup");

                    TaskService.createTask(
                            de.swiftbyte.gmc.common.model.NodeTask.Type.BACKUP,
                            new de.swiftbyte.gmc.daemon.tasks.consumers.BackupTaskConsumer.BackupTaskPayload(true, null),
                            node.getNodeId(),
                            context,
                            serverId
                    );
                } catch (Exception e) {
                    log.error("Unhandled exception during auto backup for server '{}'.", server.getFriendlyName(), e);
                }
            }, delay, autoBackupInterval, TimeUnit.MINUTES));

        }
    }

    private static void saveBackupCache() {

        JsonMapper mapper = JsonMapper.builder().build();
        ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();

        try {
            File file = new File(BACKUP_CONFIG_PATH);
            if (!file.exists() && !file.createNewFile()) {
                log.warn("Backup config could not be created!");
            }
            writer.writeValue(file, backups);
        } catch (IOException e) {
            log.error("An unknown error occurred while saving backups.", e);
        }
    }

    @SuppressWarnings("unused")
    public static void backupServer(@NonNull String serverId, boolean autoBackup) {
        GameServer server = GameServer.getServerById(serverId);
        if(server == null) return;
        backupServer(server, autoBackup, null);
    }

    public static void backupServer(@NonNull GameServer server, boolean autoBackup) {
        backupServer(server, autoBackup, null);
    }

    public static void backupServer(@NonNull GameServer server, boolean autoBackup, @Nullable String name) {

        Node node = Node.INSTANCE;
        if (node == null) {
            throw new IllegalStateException("Node is not initialized yet");
        }

        if (server.getState() == GameServerState.CREATING) {
            log.warn("Backups are currently blocked. Server '{}' is busy (CREATING).", server.getFriendlyName());
            return;
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
        backup.setExpiresAt(backup.getCreatedAt().plus(settings.getInt("AutoBackupRetention", 1) * 24 * 60, ChronoUnit.MINUTES));
        backup.setServerId(server.getServerId());
        if (Utils.isNullOrEmpty(name)) {
            backup.setName(DateTimeFormatter.ofPattern("yyyy.MM.dd_HH-mm-ss").withZone(ZoneId.systemDefault()).format(LocalDateTime.now()) + "_" + server.getSettings().getMap());
        } else {
            backup.setName(name);
        }
        backup.setAutoBackup(autoBackup);

        File tempBackupLocation = Path.of(NodeUtils.TMP_PATH, server.getServerId(), backup.getBackupId()).toFile();
        File backupLocation = node.getBackupPath().resolve(server.getServerId(), backup.getName() + ".zip").toFile();

        //TODO find a better way to handle different save locations for different game servers then hardcoding it here
        File saveLocation = server.getInstallDir().resolve("/ShooterGame/Saved/SavedArks", server instanceof AsaServer ? "/" + server.getSettings().getMap() : "").toFile();

        log.debug("Creating backup directories...");

        if (!tempBackupLocation.exists() && !tempBackupLocation.mkdirs()) {
            throw new IllegalStateException("Unable to create temp backup directory!");
        }

        if (!backupLocation.getParentFile().exists() && backupLocation.getParentFile().mkdirs()) {
            throw new IllegalStateException("Could not create backup directory!");
        }

        if (!saveLocation.exists()) {
            throw new IllegalStateException("Save location does not exist!");
        }

        log.debug("Copying save files to temporary backup location...");

        try {
            IOFileFilter filter = FileFilterUtils.notFileFilter(FileFilterUtils.suffixFileFilter(".tmp"));
            FileUtils.copyDirectory(saveLocation, tempBackupLocation, filter);

            //Remove ark backup files
            FileFilter mapSaveFilter = WildcardFileFilter.builder().setWildcards("*.ark").get();
            File[] mapSaveFiles = tempBackupLocation.listFiles(mapSaveFilter);

            if (mapSaveFiles == null) {
                throw new RuntimeException("Could not list backup files in " + saveLocation.getAbsolutePath());
            }

            //noinspection ResultOfMethodCallIgnored
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
            log.error("Backup failed for server '{}': {}", server.getFriendlyName(), e.getMessage(), e);
        }
    }

    public static boolean deleteBackup(@NonNull String backupId) {

        Node node = Node.INSTANCE;
        if (node == null) {
            throw new IllegalStateException("Node is not initialized yet");
        }

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
        File backupLocation = node.getBackupPath().resolve(server.getServerId(), backup.getName() + ".zip").toFile();
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

        Node node = Node.INSTANCE;
        if (node == null) {
            throw new IllegalStateException("Node is not initialized yet");
        }

        Backup backup = backups.get(backupId);
        if (backup == null) {
            throw new IllegalArgumentException("Backup not found: " + backupId);
        }

        GameServer server = GameServer.getServerById(backup.getServerId());
        if (server == null) {
            throw new IllegalStateException("Server not found for backup: " + backup.getServerId());
        }

        server.stop(false).complete();

        File backupLocation = node.getBackupPath().resolve(server.getServerId(), backup.getName() + ".zip").toFile();

        //TODO find a better way to handle different save locations for different game servers then hardcoding it here
        File saveLocation = server.getInstallDir().resolve("/ShooterGame/Saved/SavedArks", server instanceof AsaServer ? "/" + server.getSettings().getMap() : "").toFile();

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
                            log.warn("Could not delete player data file!");
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

    @SuppressWarnings("unused")
    public static void backupAllServers(boolean autoBackup) {

        if (backupsSuspended) {
            log.debug("Skipping backupAllServers; backups suspended.");
            return;
        }
        GameServer.getAllServers().forEach((server) -> backupServer(server, autoBackup));

    }

    public static @NonNull List<@NonNull Backup> getAllBackups() {
        return backups.values().stream().toList();
    }

    public static Backup getBackupById(@NonNull String backupId) {
        return backups.get(backupId);
    }

    public static @NonNull List<@NonNull Backup> getBackupsByServer(@NonNull String serverId) {

        GameServer server = GameServer.getServerById(serverId);
        if(server == null) return new ArrayList<>();

        return getBackupsByServer(server);
    }

    public static @NonNull List<@NonNull Backup> getBackupsByServer(@NonNull GameServer server) {
        return backups.values().stream().filter(backup -> backup.getServerId().equals(server.getServerId())).toList();
    }

    public static void deleteAllBackupsByServer(@NonNull GameServer server) {
        getBackupsByServer(server).forEach(backup -> deleteBackup(backup.getBackupId()));
    }

    public static void suspendBackups() {
        log.info("Suspending backups and auto-backup schedulers...");
        backupsSuspended = true;
        backupSchedulers.values().forEach(s -> {
            try {
                s.cancel(false);
            } catch (Exception ignored) {
            }
        });
        backupSchedulers.clear();
    }

    public static void resumeBackups() {
        log.info("Resuming backups and restoring auto-backup schedulers...");
        backupsSuspended = false;
        // Recreate auto-backup schedulers for all servers based on their settings
        GameServer.getAllServers().forEach(server -> updateAutoBackupSettings(server.getServerId()));
    }

    public static void moveBackupsDirectory(@NonNull Path oldBackupsDirPath, @NonNull Path newBackupsDirPath) throws IOException {
        // Treat inputs as backup base directories
        File oldBackupsDir = oldBackupsDirPath.toFile();
        File newBackupsDir = newBackupsDirPath.toFile();

        log.info("Moving backups from '{}' to '{}'...", oldBackupsDir.getAbsolutePath(), newBackupsDir.getAbsolutePath());

        PathUtils.moveDirectoryContents(oldBackupsDirPath, newBackupsDirPath);

        log.info("Backups move completed.");
    }
}
