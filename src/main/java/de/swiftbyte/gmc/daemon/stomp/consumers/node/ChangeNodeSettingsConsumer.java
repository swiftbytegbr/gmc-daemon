package de.swiftbyte.gmc.daemon.stomp.consumers.node;

import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.common.packet.node.NodeSettingsPacket;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@StompPacketInfo(path = "/user/queue/node/settings", packetClass = NodeSettingsPacket.class)
public class ChangeNodeSettingsConsumer implements StompPacketConsumer<NodeSettingsPacket> {

    @Override
    public void onReceive(NodeSettingsPacket packet) {
        log.debug("Received node settings packet.");
        Node.INSTANCE.updateSettings(packet.getNodeSettings());
    }
}
