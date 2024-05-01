package de.swiftbyte.gmc.stomp.consumers.node;

import de.swiftbyte.gmc.Node;
import de.swiftbyte.gmc.common.packet.node.NodeUpdatePacket;
import de.swiftbyte.gmc.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.stomp.StompPacketInfo;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@StompPacketInfo(path = {"/user/queue/node/update", "/topic/node/update"}, packetClass = NodeUpdatePacket.class)
public class NodeUpdatePacketConsumer implements StompPacketConsumer<NodeUpdatePacket> {

    @Override
    public void onReceive(NodeUpdatePacket packet) {

        if (packet.isAutoUpdate()) {

            log.info("New daemon version available: {}", packet.getVersion());
            if (Node.INSTANCE.isAutoUpdateEnabled()) {
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
