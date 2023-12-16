package de.swiftbyte.gmc.stomp.consumers.server;

import de.swiftbyte.gmc.packet.server.ServerDeletePacket;
import de.swiftbyte.gmc.server.GameServer;
import de.swiftbyte.gmc.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.stomp.StompPacketInfo;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@StompPacketInfo(path = "/user/queue/server/delete", packetClass = ServerDeletePacket.class)
public class DeleteServerPacketConsumer implements StompPacketConsumer<ServerDeletePacket> {

    @Override
    public void onReceive(ServerDeletePacket packet) {
        log.info("Deleting server with id " + packet.getServerId() + ".");
        GameServer server = GameServer.getServerById(packet.getServerId());

        if (server != null) {
            server.delete().complete();
            log.info("Deleted server with id " + packet.getServerId() + " successfully.");
        } else {
            log.error("Server with id " + packet.getServerId() + " not found!");
        }
    }
}
