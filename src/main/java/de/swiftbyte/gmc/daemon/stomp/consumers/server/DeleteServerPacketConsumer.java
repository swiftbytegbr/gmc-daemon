package de.swiftbyte.gmc.daemon.stomp.consumers.server;

import de.swiftbyte.gmc.common.packet.from.backend.server.ServerDeletePacket;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@StompPacketInfo(path = "/user/queue/server/delete", packetClass = ServerDeletePacket.class)
public class DeleteServerPacketConsumer implements StompPacketConsumer<ServerDeletePacket> {

    @Override
    public void onReceive(ServerDeletePacket packet) {
        log.info("Deleting server with id {}.", packet.getServerId());
        GameServer server = GameServer.getServerById(packet.getServerId());

        if (server != null) {
            server.delete().complete();
            log.info("Deleted server with id {} successfully.", packet.getServerId());
        } else {
            log.error("Server with id {} not found!", packet.getServerId());
        }
    }
}
