package de.swiftbyte.gmc.stomp.consumers.server;

import de.swiftbyte.gmc.common.packet.server.ServerUpdatePacket;
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

        if (server != null) {
            if (server.stop(false).complete()) {
                server.install().complete();
                log.info("Updated server with id " + packet.getServerId() + " successfully.");
            } else {
                log.error("Failed to update server with id " + packet.getServerId() + " because it could not be stopped!");
            }
        } else {
            log.error("Server with id " + packet.getServerId() + " not found!");
        }
    }
}
