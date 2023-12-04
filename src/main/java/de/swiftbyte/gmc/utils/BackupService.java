package de.swiftbyte.gmc.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.swiftbyte.gmc.Node;
import de.swiftbyte.gmc.cache.CacheModel;
import de.swiftbyte.gmc.packet.entity.Backup;
import de.swiftbyte.gmc.server.GameServer;
import lombok.Getter;
import lombok.Setter;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Slf4j
public class BackupService {

    private static HashMap<String, Backup> backups = new HashMap<>();


    public static void initialiseBackupService() {

        log.debug("Initialising backup service...");

        File cacheFile = new File("./backups.json");

        if (!cacheFile.exists()) {
            log.debug("No backups found. Skipping...");
            return;
        }

        try {
            TypeReference<HashMap<String, Backup>> typeRef = new TypeReference<>() {};
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModules(new JavaTimeModule());
            backups = mapper.readValue(new File("./backups.json"), typeRef);

            log.debug("Got saved backups.");

        } catch (IOException e) {
            log.error("An unknown error occurred while loading backups.", e);
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
        backupServer(GameServer.getServerById(serverId), autoBackup);
    }

    public static void backupServer(GameServer server, boolean autoBackup) {

        if(server == null) {
            log.error("Could not backup server because server id was not found!");
            return;
        }

        log.debug("Backing up server '" + server.getFriendlyName() + "'...");

        Backup backup = new Backup();
        
        backup.setBackupId("gmc-back-" + UUID.randomUUID());
        backup.setCreatedAt(Instant.now());
        backup.setServerId(server.getServerId());
        backup.setName(DateTimeFormatter.ofPattern("yyyy.MM.dd_HH-mm-ss").withZone(ZoneId.systemDefault()).format(LocalDateTime.now()) + "_" + server.getSettings().getMap());
        backup.setAutoBackup(autoBackup);

        File tempBackupLocation = new File(NodeUtils.TMP_PATH + server.getServerId() + "/" + backup.getBackupId());
        File backupLocation = new File(Node.INSTANCE.getServerPath() + "/backups/" + server.getFriendlyName().toLowerCase().replace(" ", "-") + "/" + backup.getName() + ".zip");
        File saveLocation = new File(server.getInstallDir() + "/ShooterGame/Saved/SavedArks/" + server.getSettings().getMap());

        log.debug("Creating backup directories...");

        if(!tempBackupLocation.exists()) {
            tempBackupLocation.mkdirs();
        }

        if(!backupLocation.getParentFile().exists()) {
            backupLocation.getParentFile().mkdirs();
        }

        if(!saveLocation.exists()) {
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

            log.debug("Cleaning up temporary backup location...");
            FileUtils.deleteDirectory(tempBackupLocation);

            saveBackupCache();
        } catch (IOException e) {
            log.error("An unknown error occurred while backing up server '" + server.getFriendlyName() + "'.", e);
        }
    }

    public static void backupAllServers(boolean autoBackup) {

        GameServer.getAllServers().forEach((server) -> backupServer(server, autoBackup));

    }

    public static void rollbackBackup(String backupId) {

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
}
