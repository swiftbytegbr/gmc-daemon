package de.swiftbyte.gmc.stomp.consumers.server;

import de.swiftbyte.gmc.common.packet.server.ServerRconPacket;
import de.swiftbyte.gmc.common.packet.server.ServerRconResponsePacket;
import de.swiftbyte.gmc.server.GameServer;
import de.swiftbyte.gmc.stomp.StompHandler;
import de.swiftbyte.gmc.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.stomp.StompPacketInfo;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

@Slf4j
@StompPacketInfo(path = "/user/queue/server/rcon", packetClass = ServerRconPacket.class)
public class SendRCONPacketConsumer implements StompPacketConsumer<ServerRconPacket> {

    @Override
    public void onReceive(ServerRconPacket packet) {
        log.info("Sending RCON command to server with id {}.", packet.getServerId());
        GameServer server = GameServer.getServerById(packet.getServerId());

        if (server != null) {
            String response = server.sendRconCommand(packet.getCommand().getCommand());
            packet.getCommand().setResponse(response);
            packet.getCommand().setTimestamp(Instant.now());

            log.debug("Sending RCON command was successful: {}.", packet);
            StompHandler.send("/app/server/rcon", packet);

        } else {
            log.error("Server with id {} not found!", packet.getServerId());
        }
    }
}
