package de.swiftbyte.gmc.daemon.stomp.consumers.node;

import de.swiftbyte.gmc.common.packet.from.bidirectional.node.NodeSettingsPacket;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import lombok.CustomLog;
import org.jspecify.annotations.NonNull;

@CustomLog
@StompPacketInfo(path = "/user/queue/node/settings", packetClass = NodeSettingsPacket.class)
public class ChangeNodeSettingsConsumer implements StompPacketConsumer<NodeSettingsPacket> {

    @Override
    public void onReceive(@NonNull NodeSettingsPacket packet) {
        log.debug("Received node settings packet.");
        getNode().updateSettings(packet.getNodeSettings());
    }
}
