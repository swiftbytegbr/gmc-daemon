package de.swiftbyte.gmc.stomp.consumers.server;

import de.swiftbyte.gmc.packet.server.ServerCreatePacket;
import de.swiftbyte.gmc.server.AsaServer;
import de.swiftbyte.gmc.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.stomp.StompPacketInfo;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@StompPacketInfo(path = "/user/queue/server/create", packetClass = ServerCreatePacket.class)
public class CreateServerPacketConsumer implements StompPacketConsumer<ServerCreatePacket> {

    @Override
    public void onReceive(ServerCreatePacket packet) {
        log.info("Created server with id " + packet.getServerId() + " and name " + packet.getServerName() + ".");
        if (packet.getGame().equalsIgnoreCase("ASCENDED")) {
            AsaServer server = new AsaServer(packet.getServerId(), packet.getServerName(), packet.getDefaultSettings());

            server.install().queue();
        } else {
            log.error("Game " + packet.getGame() + " is not supported!");
        }
    }
}
