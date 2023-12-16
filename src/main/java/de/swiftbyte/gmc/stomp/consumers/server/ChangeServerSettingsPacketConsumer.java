package de.swiftbyte.gmc.stomp.consumers.server;

import de.swiftbyte.gmc.packet.server.ServerSettingsPacket;
import de.swiftbyte.gmc.packet.server.ServerSettingsResponsePacket;
import de.swiftbyte.gmc.server.AsaServer;
import de.swiftbyte.gmc.server.GameServer;
import de.swiftbyte.gmc.stomp.StompHandler;
import de.swiftbyte.gmc.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.stomp.StompPacketInfo;
import de.swiftbyte.gmc.utils.ServerUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@StompPacketInfo(path = "/user/queue/server/settings", packetClass = ServerSettingsPacket.class)
public class ChangeServerSettingsPacketConsumer implements StompPacketConsumer<ServerSettingsPacket> {

    @Override
    public void onReceive(ServerSettingsPacket packet) {
        log.info("Changing settings of server with id " + packet.getServerId() + ".");
        GameServer server = GameServer.getServerById(packet.getServerId());

        if (server != null) {

            server.setSettings(packet.getSettings());

            ServerSettingsResponsePacket responsePacket = new ServerSettingsResponsePacket();
            responsePacket.setServerId(server.getServerId());
            responsePacket.setSettings(server.getSettings());

            StompHandler.send("/app/server/settings", responsePacket);

            ServerUtils.writeAsaStartupBatch((AsaServer) server);

        } else {
            log.error("Server with id " + packet.getServerId() + " not found!");
        }
    }
}
