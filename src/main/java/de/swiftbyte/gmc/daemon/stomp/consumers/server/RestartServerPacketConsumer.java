package de.swiftbyte.gmc.daemon.stomp.consumers.server;

import de.swiftbyte.gmc.common.packet.from.backend.server.ServerRestartPacket;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@StompPacketInfo(path = "/user/queue/server/restart", packetClass = ServerRestartPacket.class)
public class RestartServerPacketConsumer implements StompPacketConsumer<ServerRestartPacket> {

    @Override
    public void onReceive(ServerRestartPacket packet) {
        log.info("Restarting server with id {}.", packet.getServerId());
        GameServer server = GameServer.getServerById(packet.getServerId());

        if (server != null) {
            server.restart().queue(success -> {
                if(success) log.info("Restarted server with id {} successfully.", packet.getServerId());
                else log.error("Restarting server with id {} failed.", packet.getServerId());
            });
        } else {
            log.error("Server with id {} not found!", packet.getServerId());
        }
    }
}
