package de.swiftbyte.gmc.daemon.migration;

import de.swiftbyte.gmc.daemon.cache.CacheModel;
import de.swiftbyte.gmc.daemon.cache.GameServerCacheModel;
import de.swiftbyte.gmc.daemon.utils.CommonUtils;
import lombok.CustomLog;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;

/**
 * MIGRATION LEVEL 1
 * This script will migrate the old server directory name to our new system based on the server id.
 * In addition, after that, the cache file will be cleared.
 */
@CustomLog
public class MigrateServerInstallDir implements MigrationScript {

    @Override
    public void run() {
        try {
            log.info("Staring migration script 1...");

            File cacheFile = new File("./cache.json");

            if (!cacheFile.exists()) {
                log.debug("No cache file found. Skipping...");
                return;
            }

            CacheModel cacheModel = CommonUtils.getObjectReader().readValue(cacheFile, CacheModel.class);
            HashMap<String, GameServerCacheModel> gameServerCacheModelHashMap = cacheModel.getGameServerCacheModelHashMap();

            log.info("Found {} servers. Starting folder name migration...", gameServerCacheModelHashMap.size());

            gameServerCacheModelHashMap.forEach((id, gameServerCacheModel) -> {
                Path installDir = Path.of(gameServerCacheModel.getInstallDir());
                Path serverDir = installDir.getParent();
                File gameServerFolder = installDir.toFile();
                if (!gameServerFolder.renameTo(serverDir.resolve(id).toFile())) {
                    log.error("Failed to rename game server folder {} to {}", gameServerFolder, serverDir);
                } else {
                    log.info("[{}] - SUCCESS", id);
                }
            });

            log.info("Folder name migration was successful");

            log.info("Starting deletion of cache files...");
            if (!cacheFile.delete()) {
                log.error("Failed to delete cache file {}", cacheFile);
            } else {
                log.info("[cache.json] - SUCCESS");
            }

            log.info("Folder name migration was successful. Exiting migration script...");

        } catch (IOException e) {
            log.error("An unknown error occurred while getting cached information.", e);
        } catch (Exception e) {
            log.error("An unknown error occurred while migrating server install dir.", e);
        }

    }
}
