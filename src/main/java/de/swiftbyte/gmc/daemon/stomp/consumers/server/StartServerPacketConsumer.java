package de.swiftbyte.gmc.daemon.stomp.consumers.server;

import de.swiftbyte.gmc.common.packet.server.ServerStartPacket;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@StompPacketInfo(path = "/user/queue/server/start", packetClass = ServerStartPacket.class)
public class StartServerPacketConsumer implements StompPacketConsumer<ServerStartPacket> {

    @Override
    public void onReceive(ServerStartPacket packet) {
        log.info("Starting server with id {}.", packet.getServerId());
        GameServer server = GameServer.getServerById(packet.getServerId());

        if (server != null) {
            server.start().complete();
            log.info("Started server with id {} successfully.", packet.getServerId());
        } else {
            log.error("Server with id {} not found!", packet.getServerId());
        }
    }
}
