package de.swiftbyte.gmc.daemon.stomp.consumers.server;

import de.swiftbyte.gmc.common.packet.from.backend.server.ServerUpdatePacket;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import lombok.CustomLog;
import org.jspecify.annotations.NonNull;

@CustomLog
@StompPacketInfo(path = "/user/queue/server/update", packetClass = ServerUpdatePacket.class)
public class UpdateServerVersionPacketConsumer implements StompPacketConsumer<ServerUpdatePacket> {

    @Override
    public void onReceive(@NonNull ServerUpdatePacket packet) {
        log.info("Updating server with id {}.", packet.getServerId());
        GameServer server = GameServer.getServerById(packet.getServerId());

        if (server != null) {

            server.stop(false).queue(success -> {
                if (success) {
                    if (server.install().complete()) {
                        log.info("Updated server with id {} successfully.", packet.getServerId());

                        if (packet.isStartAfterUpdate()) {
                            log.info("Starting server with id {} after update.", packet.getServerId());
                            server.start().queue(startSuccess -> {
                                if (startSuccess) {
                                    log.info("Started server with id {} successfully after update.", packet.getServerId());
                                } else {
                                    log.error("Starting server with id {} after update failed.", packet.getServerId());
                                }
                            });
                        }
                    } else {
                        log.error("Updating server with id {} failed.", packet.getServerId());
                    }
                } else {
                    log.error("Failed to update server with id {} because it could not be stopped!", packet.getServerId());
                }
            });
        } else {
            log.error("Server with id {} not found!", packet.getServerId());
        }
    }
}
