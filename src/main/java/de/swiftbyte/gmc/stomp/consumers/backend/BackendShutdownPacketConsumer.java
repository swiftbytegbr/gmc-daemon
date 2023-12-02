package de.swiftbyte.gmc.stomp.consumers.backend;

import de.swiftbyte.gmc.Node;
import de.swiftbyte.gmc.packet.node.BackendShutdownPacket;
import de.swiftbyte.gmc.packet.server.ServerStartPacket;
import de.swiftbyte.gmc.stomp.StompHandler;
import de.swiftbyte.gmc.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.stomp.StompPacketInfo;
import de.swiftbyte.gmc.utils.ConnectionState;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@StompPacketInfo(path = "/user/queue/backend/shutdown", packetClass = BackendShutdownPacket.class)
public class BackendShutdownPacketConsumer implements StompPacketConsumer<BackendShutdownPacket> {

    @Override
    public void onReceive(BackendShutdownPacket packet) {
        log.info("Backend is shutting down... Reason: '" + packet.getReason() + "'.");
        Node.INSTANCE.setConnectionState(ConnectionState.NOT_CONNECTED);
        StompHandler.disconnect();
    }

}
