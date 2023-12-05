package de.swiftbyte.gmc.stomp.consumers.server;

import de.swiftbyte.gmc.packet.server.ServerRestartPacket;
import de.swiftbyte.gmc.server.GameServer;
import de.swiftbyte.gmc.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.stomp.StompPacketInfo;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@StompPacketInfo(path = "/user/queue/server/restart", packetClass = ServerRestartPacket.class)
public class RestartServerPacketConsumer implements StompPacketConsumer<ServerRestartPacket> {

    @Override
    public void onReceive(ServerRestartPacket packet) {
        log.info("Restarting server with id " + packet.getServerId() + ".");
        GameServer server = GameServer.getServerById(packet.getServerId());

        if (server != null) {
            server.restart().queue();
        } else {
            log.error("Server with id " + packet.getServerId() + " not found!");
        }
    }
}
