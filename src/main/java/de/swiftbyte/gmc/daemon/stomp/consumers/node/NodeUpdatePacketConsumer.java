package de.swiftbyte.gmc.daemon.stomp.consumers.node;

import de.swiftbyte.gmc.common.packet.from.backend.node.NodeUpdatePacket;
import de.swiftbyte.gmc.daemon.Application;
import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@StompPacketInfo(path = {"/user/queue/node/update", "/topic/node/update"}, packetClass = NodeUpdatePacket.class)
public class NodeUpdatePacketConsumer implements StompPacketConsumer<NodeUpdatePacket> {

    @Override
    public void onReceive(NodeUpdatePacket packet) {

        if (packet.isAutoUpdate()) {

            log.info("New daemon version available: {}", packet.getVersion());
            if (Node.INSTANCE.isAutoUpdateEnabled() && !Application.getVersion().contains("DEV")) {
                log.info("Auto update enabled. Updating...");
                Node.INSTANCE.updateDaemon();
            } else {
                log.warn("Auto update disabled. Skipping update. Please update manually.");
            }
        } else {
            log.info("Updating daemon to version {}...", packet.getVersion());
            Node.INSTANCE.updateDaemon();
        }
    }
}
