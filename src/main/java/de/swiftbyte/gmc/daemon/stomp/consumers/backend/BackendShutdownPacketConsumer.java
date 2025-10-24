package de.swiftbyte.gmc.daemon.stomp.consumers.backend;

import de.swiftbyte.gmc.common.packet.from.backend.node.BackendShutdownPacket;
import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.daemon.stomp.StompHandler;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import de.swiftbyte.gmc.daemon.utils.ConnectionState;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@StompPacketInfo(path = "/user/queue/backend/shutdown", packetClass = BackendShutdownPacket.class)
public class BackendShutdownPacketConsumer implements StompPacketConsumer<BackendShutdownPacket> {

    @Override
    public void onReceive(BackendShutdownPacket packet) {
        log.info("Backend is shutting down... Reason: '{}'.", packet.getReason());
        Node.INSTANCE.setConnectionState(ConnectionState.NOT_CONNECTED);
        StompHandler.disconnect();
    }

}
