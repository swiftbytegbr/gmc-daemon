package de.swiftbyte.gmc.daemon.utils;

import de.swiftbyte.gmc.common.entity.NodeSettings;
import de.swiftbyte.gmc.common.packet.from.bidirectional.node.NodeSettingsPacket;
import de.swiftbyte.gmc.daemon.stomp.StompHandler;
import lombok.CustomLog;
import org.jspecify.annotations.NonNull;

import java.nio.file.Path;

@CustomLog
public final class NodeSettingsUtils {

    private NodeSettingsUtils() {
    }

    /**
     * Validates the default server directory from the backend, backfilling and syncing if invalid.
     *
     * @param nodeSettings      node settings received from the backend
     * @param currentDefaultDir current default directory on disk
     * @return writable, absolute path to use as default server directory
     */
    public static @NonNull Path validateOrBackfillDefaultServerDirectory(@NonNull NodeSettings nodeSettings, @NonNull Path currentDefaultDir) {

        Path incoming = PathUtils.getAbsolutPath(nodeSettings.getDefaultServerDirectory());

        if (Utils.isNullOrEmpty(incoming) || !PathUtils.isWritableDirectory(incoming)) {

            Path fallback = PathUtils.isWritableDirectory(currentDefaultDir) ? currentDefaultDir : PathUtils.getAbsolutPath("./servers");

            if (!fallback.equals(incoming)) {
                nodeSettings.setDefaultServerDirectory(fallback.toString());
                NodeSettingsPacket packet = new NodeSettingsPacket();
                packet.setNodeSettings(nodeSettings);
                StompHandler.send("/app/node/settings", packet);
                log.info("Backfilled defaultServerDirectory to '{}' (invalid or empty).", fallback);
            }
            return fallback;
        }

        return incoming;
    }

    /**
     * Validates the server backups directory from the backend, backfilling and syncing if invalid.
     *
     * @param nodeSettings     node settings received from the backend
     * @param currentBackupPath current local backups directory
     * @return writable, absolute path to use for server backups
     */
    public static @NonNull Path validateOrBackfillServerBackupsDirectory(@NonNull NodeSettings nodeSettings, @NonNull Path currentBackupPath) {

        Path incoming = PathUtils.getAbsolutPath(nodeSettings.getServerBackupsDirectory());

        if (Utils.isNullOrEmpty(incoming) || !PathUtils.isWritableDirectory(incoming)) {

            Path fallback = PathUtils.isWritableDirectory(currentBackupPath) ? currentBackupPath : PathUtils.getAbsolutPath("./backups");

            if (!fallback.equals(incoming)) {
                nodeSettings.setServerBackupsDirectory(fallback.toString());
                NodeSettingsPacket packet = new NodeSettingsPacket();
                packet.setNodeSettings(nodeSettings);
                StompHandler.send("/app/node/settings", packet);
                log.warn("Corrected serverBackupsDirectory in backend to '{}' (invalid or empty).", fallback);
            }
            return fallback;
        }

        return incoming;
    }
}
