package de.swiftbyte.gmc.stomp.consumers;

import de.swiftbyte.gmc.Node;
import de.swiftbyte.gmc.packet.node.NodeLoginAckPacket;
import de.swiftbyte.gmc.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.stomp.StompPacketInfo;
import de.swiftbyte.gmc.utils.ConnectionState;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@StompPacketInfo(path = "/user/queue/node/login", packetClass = NodeLoginAckPacket.class)
public class LoginAckPacketConsumer implements StompPacketConsumer<NodeLoginAckPacket> {

    @Override
    public void onReceive(NodeLoginAckPacket packet) {

        Node.INSTANCE.setNodeName(packet.getNodeSettings().getName());
        Node.INSTANCE.setTeamName(packet.getTeamName());

        log.info("Backend running in profile '" + packet.getBackendProfile() + "' with version '" + packet.getBackendVersion() + "'.");
        log.info("I am '" + packet.getNodeSettings().getName() + "' in team " + packet.getTeamName() + "!");

        Node.INSTANCE.setConnectionState(ConnectionState.CONNECTED);
    }
}
