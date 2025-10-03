package de.swiftbyte.gmc.daemon.stomp.consumers.node;

import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.common.packet.node.NodeDeletePacket;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import de.swiftbyte.gmc.daemon.utils.ConfigUtils;
import de.swiftbyte.gmc.daemon.utils.ConnectionState;
import de.swiftbyte.gmc.daemon.utils.NodeUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

@Slf4j
@StompPacketInfo(path = "/user/queue/node/delete", packetClass = NodeDeletePacket.class)
public class NodeDeletePacketConsumer implements StompPacketConsumer<NodeDeletePacket> {

    @Override
    public void onReceive(NodeDeletePacket packet) {

        log.debug("Received node deletion packet.");
        Node.INSTANCE.delete();

    }
}
