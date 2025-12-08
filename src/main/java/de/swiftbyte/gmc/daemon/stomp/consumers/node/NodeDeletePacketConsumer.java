package de.swiftbyte.gmc.daemon.stomp.consumers.node;

import de.swiftbyte.gmc.common.packet.from.backend.node.NodeDeletePacket;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import lombok.CustomLog;
import org.jspecify.annotations.NonNull;

@CustomLog
@StompPacketInfo(path = "/user/queue/node/delete", packetClass = NodeDeletePacket.class)
public class NodeDeletePacketConsumer implements StompPacketConsumer<NodeDeletePacket> {

    @Override
    public void onReceive(@NonNull NodeDeletePacket packet) {

        log.debug("Received node deletion packet.");
        getNode().delete();

    }
}
