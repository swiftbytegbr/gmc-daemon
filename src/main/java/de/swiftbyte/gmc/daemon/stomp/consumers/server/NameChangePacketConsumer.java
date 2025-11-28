package de.swiftbyte.gmc.daemon.stomp.consumers.server;

import de.swiftbyte.gmc.common.packet.from.backend.server.ServerNameChangePacket;
import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.service.FirewallService;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@StompPacketInfo(path = "/user/queue/server/name-change", packetClass = ServerNameChangePacket.class)
public class NameChangePacketConsumer implements StompPacketConsumer<ServerNameChangePacket> {

    @Override
    public void onReceive(ServerNameChangePacket packet) {
        try {
            GameServer server = GameServer.getServerById(packet.getServerId());
            if (server == null) {
                log.warn("Received name change for unknown server '{}'.", packet.getServerId());
                return;
            }

            String newName = packet.getFriendlyName();
            if (newName == null || newName.isBlank()) {
                log.warn("Received empty friendlyName for server '{}'. Ignoring.", packet.getServerId());
                return;
            }

            if (!newName.equals(server.getFriendlyName())) {
                log.info("Applying server name change: '{}' -> '{}' (id={}).", server.getFriendlyName(), newName, packet.getServerId());
                server.changeFriendlyName(newName);
            }
        } catch (Exception e) {
            log.error("Failed to process server name change for '{}'.", packet.getServerId(), e);
        }
    }
}
