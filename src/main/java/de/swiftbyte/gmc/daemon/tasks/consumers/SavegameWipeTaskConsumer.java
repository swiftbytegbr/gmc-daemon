package de.swiftbyte.gmc.daemon.tasks.consumers;

import de.swiftbyte.gmc.common.entity.GameServerState;
import de.swiftbyte.gmc.common.model.NodeTask;
import de.swiftbyte.gmc.daemon.server.AsaServer;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.tasks.NodeTaskConsumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.nio.file.Path;

@Slf4j
public class SavegameWipeTaskConsumer implements NodeTaskConsumer {

    @Override
    public void run(NodeTask task, Object payload) {
        // Expect exactly one target server id; iterate to be consistent with other consumers
        for (String serverId : task.getTargetIds()) {
            GameServer server = GameServer.getServerById(serverId);
            if (server == null) {
                throw new IllegalArgumentException("Server not found: " + serverId);
            }

            // Validate state; backend ensures OFFLINE, but double‑check here for safety
            if (server.getState() != GameServerState.OFFLINE) {
                throw new IllegalStateException("Server must be OFFLINE to wipe savegames");
            }

            // Compute the savegames directory used by backups (including player files)
            Path base = server.getInstallDir();
            File saveDir = new File(base.toFile(), "ShooterGame/Saved/SavedArks" + (server instanceof AsaServer ? "/" + server.getSettings().getMap() : ""));

            log.info("Wiping savegames for server '{}' at '{}'...", server.getFriendlyName(), saveDir.getAbsolutePath());

            // Temporarily set to CREATING to block auto-backups or other operations
            GameServerState previousState = server.getState();
            try {
                server.setState(GameServerState.CREATING);

                if (!saveDir.exists()) {
                    log.info("Savegames directory '{}' does not exist; nothing to wipe.", saveDir.getAbsolutePath());
                    continue;
                }

                // Clean directory contents but keep folder structure to avoid downstream issues
                // If it's not a directory, attempt to delete it and recreate as directory
                if (saveDir.isDirectory()) {
                    FileUtils.cleanDirectory(saveDir);
                } else {
                    FileUtils.forceDelete(saveDir);
                    // Recreate as empty directory
                    if (!saveDir.mkdirs()) {
                        throw new RuntimeException("Failed to recreate savegames directory after wipe: " + saveDir.getAbsolutePath());
                    }
                }

                log.info("Savegames wiped successfully for server '{}' ({}).", server.getFriendlyName(), saveDir.getAbsolutePath());
            } catch (Exception e) {
                log.error("Failed to wipe savegames for server '{}' at '{}'.", server.getFriendlyName(), saveDir.getAbsolutePath(), e);
                throw new RuntimeException("Savegame wipe failed: " + e.getMessage(), e);
            } finally {
                try {
                    server.setState(previousState);
                } catch (Exception ignored) {
                }
            }
        }
    }
}

