package de.swiftbyte.gmc.stomp.consumers.server;

import de.swiftbyte.gmc.packet.server.ServerStartPacket;
import de.swiftbyte.gmc.server.GameServer;
import de.swiftbyte.gmc.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.stomp.StompPacketInfo;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@StompPacketInfo(path = "/user/queue/server/start", packetClass = ServerStartPacket.class)
public class StartServerPacketConsumer implements StompPacketConsumer<ServerStartPacket> {

    @Override
    public void onReceive(ServerStartPacket packet) {
        log.info("Starting server with id " + packet.getServerId() + ".");
        GameServer server = GameServer.getServerById(packet.getServerId());

        if (server != null) {
            server.start().queue();
        } else {
            log.error("Server with id " + packet.getServerId() + " not found!");
        }
    }
}
