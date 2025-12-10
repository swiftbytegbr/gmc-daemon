package de.swiftbyte.gmc.daemon.stomp.consumers.node;

import de.swiftbyte.gmc.common.packet.from.backend.node.NodeUpdatePacket;
import de.swiftbyte.gmc.daemon.Application;
import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import lombok.CustomLog;
import org.jspecify.annotations.NonNull;

@CustomLog
@StompPacketInfo(path = {"/user/queue/node/update", "/topic/node/update"}, packetClass = NodeUpdatePacket.class)
public class NodeUpdatePacketConsumer implements StompPacketConsumer<NodeUpdatePacket> {

    @Override
    public void onReceive(@NonNull NodeUpdatePacket packet) {

        Node node = getNode();

        if (packet.isAutoUpdate()) {

            if (Application.getVersion().equals(packet.getVersion())) {
                log.debug("Daemon got notified of new update but is already on the newest version...");
                return;
            }

            log.info("New daemon version available: {}", packet.getVersion());
            if (node.isAutoUpdateEnabled() && !Application.getVersion().contains("DEV")) {
                log.info("Auto update enabled. Updating...");
                node.updateDaemon();
            } else {
                log.warn("Auto update disabled. Skipping update. Please update manually.");
            }
        } else {
            log.info("Updating daemon to version {}...", packet.getVersion());
            node.updateDaemon();
        }
    }
}
