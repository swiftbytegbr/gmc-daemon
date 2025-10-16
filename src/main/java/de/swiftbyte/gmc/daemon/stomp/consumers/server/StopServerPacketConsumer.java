package de.swiftbyte.gmc.daemon.stomp.consumers.server;

import de.swiftbyte.gmc.common.packet.server.ServerStopPacket;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@StompPacketInfo(path = "/user/queue/server/stop", packetClass = ServerStopPacket.class)
public class StopServerPacketConsumer implements StompPacketConsumer<ServerStopPacket> {

    @Override
    public void onReceive(ServerStopPacket packet) {
        log.info("Stopping server with id {}.", packet.getServerId());
        GameServer server = GameServer.getServerById(packet.getServerId());

        if (server != null) {
            server.stop(false, packet.isForceStop()).complete();
            log.info("Stopped server with id {} successfully.", packet.getServerId());
        } else {
            log.error("Server with id {} not found!", packet.getServerId());
        }
    }
}
