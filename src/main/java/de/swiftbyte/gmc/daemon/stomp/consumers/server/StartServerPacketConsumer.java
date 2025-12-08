package de.swiftbyte.gmc.daemon.stomp.consumers.server;

import de.swiftbyte.gmc.common.packet.from.backend.server.ServerStartPacket;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import lombok.CustomLog;
import org.jspecify.annotations.NonNull;

@CustomLog
@StompPacketInfo(path = "/user/queue/server/start", packetClass = ServerStartPacket.class)
public class StartServerPacketConsumer implements StompPacketConsumer<ServerStartPacket> {

    @Override
    public void onReceive(@NonNull ServerStartPacket packet) {
        log.info("Starting server with id {}.", packet.getServerId());
        GameServer server = GameServer.getServerById(packet.getServerId());

        if (server != null) {
            server.start().queue((success) -> {
                if (success) {
                    log.info("Started server with id {} successfully.", packet.getServerId());
                } else {
                    log.error("Starting server with id {} failed.", packet.getServerId());
                }
            });
        } else {
            log.error("Server with id {} not found!", packet.getServerId());
        }
    }
}
