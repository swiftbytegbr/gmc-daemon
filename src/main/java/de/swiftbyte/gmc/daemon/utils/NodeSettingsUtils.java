package de.swiftbyte.gmc.daemon.utils;

import de.swiftbyte.gmc.common.entity.NodeSettings;
import de.swiftbyte.gmc.common.packet.from.bidirectional.node.NodeSettingsPacket;
import de.swiftbyte.gmc.daemon.stomp.StompHandler;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;

@Slf4j
public final class NodeSettingsUtils {

    private NodeSettingsUtils() {
    }

    // Validates incoming defaultServerDirectory; if null/invalid, backfill to currentDefaultDir (or absolute ./servers) and sync once.
    public static String validateOrBackfillDefaultServerDirectory(NodeSettings nodeSettings, Path currentDefaultDir) {
        String incoming = nodeSettings.getDefaultServerDirectory();

        boolean incomingValid = PathValidationUtils.isWritableDirectory(incoming);
        if (!incomingValid) {
            String fallback = (currentDefaultDir != null && PathValidationUtils.isWritableDirectory(currentDefaultDir.toString()))
                    ? currentDefaultDir.toString()
                    : PathValidationUtils.canonicalizeOrAbsolute("./servers");

            if (!fallback.equals(incoming)) {
                nodeSettings.setDefaultServerDirectory(fallback);
                NodeSettingsPacket packet = new NodeSettingsPacket();
                packet.setNodeSettings(nodeSettings);
                StompHandler.send("/app/node/settings", packet);
                log.info("Backfilled defaultServerDirectory to '{}' (invalid or empty).", fallback);
            }
            return fallback;
        }

        return PathValidationUtils.canonicalizeOrAbsolute(incoming);
    }

    // Validates incoming serverBackupsDirectory; if null/invalid, backfill to currentBackupPath and sync once.
    public static String validateOrBackfillServerBackupsDirectory(NodeSettings nodeSettings, Path currentBackupPath) {
        String incoming = nodeSettings.getServerBackupsDirectory();
        if (CommonUtils.isNullOrEmpty(incoming) || !PathValidationUtils.isWritableDirectory(incoming)) {
            String fallback = currentBackupPath.normalize().toString();
            if (!fallback.equals(incoming)) {
                nodeSettings.setServerBackupsDirectory(fallback);
                NodeSettingsPacket packet = new NodeSettingsPacket();
                packet.setNodeSettings(nodeSettings);
                StompHandler.send("/app/node/settings", packet);
                log.info("Backfilled serverBackupsDirectory to '{}' (invalid or empty).", fallback);
            }
            return fallback;
        }
        return Path.of(incoming).normalize().toString();
    }
}
