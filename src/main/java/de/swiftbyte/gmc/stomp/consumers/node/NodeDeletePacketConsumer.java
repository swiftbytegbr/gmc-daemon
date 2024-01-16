package de.swiftbyte.gmc.stomp.consumers.node;

import de.swiftbyte.gmc.Node;
import de.swiftbyte.gmc.common.packet.node.NodeDeletePacket;
import de.swiftbyte.gmc.server.GameServer;
import de.swiftbyte.gmc.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.stomp.StompPacketInfo;
import de.swiftbyte.gmc.utils.ConfigUtils;
import de.swiftbyte.gmc.utils.ConnectionState;
import de.swiftbyte.gmc.utils.NodeUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

@Slf4j
@StompPacketInfo(path = "/user/queue/node/delete", packetClass = NodeDeletePacket.class)
public class NodeDeletePacketConsumer implements StompPacketConsumer<NodeDeletePacket> {

    @Override
    public void onReceive(NodeDeletePacket packet) {

        Node.INSTANCE.setConnectionState(ConnectionState.DELETING);

        log.info("Received node deletion packet.");
        log.debug("Stopping all servers...");
        for (GameServer gameServer : GameServer.getAllServers()) {
            gameServer.stop(false).complete();
        }

        log.debug("Cleaning up...");

        try {
            FileUtils.deleteDirectory(new File(NodeUtils.TMP_PATH));
            FileUtils.deleteDirectory(new File(NodeUtils.STEAM_CMD_DIR));
            FileUtils.deleteDirectory(new File("logs"));

            try {
                FileUtils.delete(new File("cache.json"));
            } catch (Exception e) {
                log.debug("Could not delete cache.json directory.", e);
            }

            try {
                FileUtils.delete(new File("backups.json"));
            } catch (Exception e) {
                log.debug("Could not delete backups.json directory.", e);
            }
            ConfigUtils.remove("node.id");
            ConfigUtils.remove("node.secret");
        } catch (IOException e) {
            log.warn("An error occurred while cleaning up.", e);
        }

        log.info("Node deletion complete. Please note that the daemon must be uninstalled manually. Exiting...");

        System.exit(0);

    }

}
