package de.swiftbyte.gmc.daemon.stomp.consumers.server;

import de.swiftbyte.gmc.common.packet.from.backend.server.ServerUpdatePacket;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@StompPacketInfo(path = "/user/queue/server/update", packetClass = ServerUpdatePacket.class)
public class UpdateServerVersionPacketConsumer implements StompPacketConsumer<ServerUpdatePacket> {

    @Override
    public void onReceive(ServerUpdatePacket packet) {
        log.info("Updating server with id {}.", packet.getServerId());
        GameServer server = GameServer.getServerById(packet.getServerId());

        if (server != null) {

            server.stop(false).queue(success -> {
                if(success) {
                    if(server.install().complete()) log.info("Updated server with id {} successfully.", packet.getServerId());
                    else log.error("Updating server with id {} failed.", packet.getServerId());
                } else {
                    log.error("Failed to update server with id {} because it could not be stopped!", packet.getServerId());
                }
            });
        } else {
            log.error("Server with id {} not found!", packet.getServerId());
        }
    }
}
