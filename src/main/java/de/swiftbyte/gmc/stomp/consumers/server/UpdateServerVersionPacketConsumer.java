package de.swiftbyte.gmc.stomp.consumers.server;

import de.swiftbyte.gmc.packet.server.ServerUpdatePacket;
import de.swiftbyte.gmc.server.GameServer;
import de.swiftbyte.gmc.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.stomp.StompPacketInfo;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@StompPacketInfo(path = "/user/queue/server/update", packetClass = ServerUpdatePacket.class)
public class UpdateServerVersionPacketConsumer implements StompPacketConsumer<ServerUpdatePacket> {

    @Override
    public void onReceive(ServerUpdatePacket packet) {
        log.info("Updating server with id " + packet.getServerId() + ".");
        GameServer server = GameServer.getServerById(packet.getServerId());

        if(server != null) {
            server.installServer();
        } else {
            log.error("Server with id " + packet.getServerId() + " not found!");
        }
    }
}
