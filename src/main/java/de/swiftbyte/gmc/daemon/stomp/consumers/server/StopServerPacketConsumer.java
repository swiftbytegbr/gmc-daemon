package de.swiftbyte.gmc.daemon.stomp.consumers.server;

import de.swiftbyte.gmc.common.model.NodeTask;
import de.swiftbyte.gmc.common.packet.from.backend.server.ServerStopPacket;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.service.TaskService;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import de.swiftbyte.gmc.daemon.tasks.consumers.TimedShutdownTaskConsumer.TimedShutdownPayload;
import lombok.CustomLog;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;

@CustomLog
@StompPacketInfo(path = "/user/queue/server/stop", packetClass = ServerStopPacket.class)
public class StopServerPacketConsumer implements StompPacketConsumer<ServerStopPacket> {

    @Override
    public void onReceive(@NonNull ServerStopPacket packet) {
        log.info("Stopping server with id {}.", packet.getServerId());
        if (packet.getDelayMinutes() != null && packet.getDelayMinutes() > 0) {
            log.debug("Received timed stop request: serverId={}, delayMinutes={}, forceStop={}, hasMessage={}",
                    packet.getServerId(), packet.getDelayMinutes(), packet.isForceStop(), packet.getDelayedStopMessage() != null);
        } else {
            log.debug("Received immediate stop request: serverId={}, forceStop={}, hasMessage={}",
                    packet.getServerId(), packet.isForceStop(), packet.getDelayedStopMessage() != null);
        }
        Integer delay = packet.getDelayMinutes();
        if (delay != null && delay > 0) {
            // Create a timed shutdown task
            HashMap<String, Object> context = new HashMap<>();
            context.put("delay", delay);

            boolean created = TaskService.createTask(
                    NodeTask.Type.TIMED_SHUTDOWN,
                    new TimedShutdownPayload(packet.getServerId(), delay, packet.isForceStop(), packet.getDelayedStopMessage()),
                    getNode().getNodeId(),
                    context,
                    packet.getServerId()
            );
            if (!created) {
                log.warn("Could not create timed shutdown task for server {}", packet.getServerId());
            } else {
                log.debug("Timed shutdown task created for server {} with delay {} min", packet.getServerId(), delay);
            }
            return;
        }

        GameServer server = GameServer.getServerById(packet.getServerId());
        if (server != null) {
            server.stop(false, packet.isForceStop()).queue(success -> {
                if (success) {
                    log.info("Stopped server with id {} successfully.", packet.getServerId());
                } else {
                    log.error("Stopping server with id {} failed.", packet.getServerId());
                }
            });
        } else {
            log.error("Server with id {} not found!", packet.getServerId());
        }
    }
}
