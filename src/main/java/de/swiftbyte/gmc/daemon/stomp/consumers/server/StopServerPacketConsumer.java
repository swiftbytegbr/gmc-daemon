package de.swiftbyte.gmc.daemon.stomp.consumers.server;

import de.swiftbyte.gmc.common.packet.from.backend.server.ServerStopPacket;
import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.service.TaskService;
import de.swiftbyte.gmc.daemon.tasks.consumers.TimedShutdownTaskConsumer.TimedShutdownPayload;
import de.swiftbyte.gmc.common.model.NodeTask;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@StompPacketInfo(path = "/user/queue/server/stop", packetClass = ServerStopPacket.class)
public class StopServerPacketConsumer implements StompPacketConsumer<ServerStopPacket> {

    @Override
    public void onReceive(ServerStopPacket packet) {
        log.info("Stopping server with id {}.", packet.getServerId());
        Integer delay = packet.getDelayMinutes();
        if (delay != null && delay > 0) {
            // Create a timed shutdown task
            boolean created = TaskService.createTask(
                    NodeTask.Type.TIMED_SHUTDOWN,
                    new TimedShutdownPayload(packet.getServerId(), delay, packet.isForceStop(), packet.getDelayedStopMessage()),
                    Node.INSTANCE.getNodeId(),
                    packet.getServerId()
            );
            if (!created) {
                log.warn("Could not create timed shutdown task for server {}", packet.getServerId());
            }
            return;
        }

        GameServer server = GameServer.getServerById(packet.getServerId());
        if (server != null) {
            server.stop(false, packet.isForceStop()).queue(success -> {
                if(success) log.info("Stopped server with id {} successfully.", packet.getServerId());
                else log.error("Stopping server with id {} failed.", packet.getServerId());
            });
        } else {
            log.error("Server with id {} not found!", packet.getServerId());
        }
    }
}
