package de.swiftbyte.gmc.daemon.stomp.consumers.backend;

import de.swiftbyte.gmc.common.packet.from.backend.node.BackendShutdownPacket;
import de.swiftbyte.gmc.daemon.stomp.StompHandler;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import de.swiftbyte.gmc.daemon.utils.ConnectionState;
import lombok.CustomLog;
import org.jspecify.annotations.NonNull;

@CustomLog
@StompPacketInfo(path = "/user/queue/backend/shutdown", packetClass = BackendShutdownPacket.class)
public class BackendShutdownPacketConsumer implements StompPacketConsumer<BackendShutdownPacket> {

    @Override
    public void onReceive(@NonNull BackendShutdownPacket packet) {
        log.info("Backend is shutting down... Reason: '{}'.", packet.getReason());
        getNode().setConnectionState(ConnectionState.NOT_CONNECTED);
        StompHandler.disconnect();
    }

}
