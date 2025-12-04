package de.swiftbyte.gmc.daemon.stomp.consumers.server;

import de.swiftbyte.gmc.common.model.NodeTask;
import de.swiftbyte.gmc.common.packet.from.backend.server.ServerRestartPacket;
import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.service.TaskService;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import de.swiftbyte.gmc.daemon.tasks.consumers.TimedRestartTaskConsumer.TimedRestartPayload;
import lombok.CustomLog;

import java.util.HashMap;

@CustomLog
@StompPacketInfo(path = "/user/queue/server/restart", packetClass = ServerRestartPacket.class)
public class RestartServerPacketConsumer implements StompPacketConsumer<ServerRestartPacket> {

    @Override
    public void onReceive(ServerRestartPacket packet) {
        log.info("Restarting server with id {}.", packet.getServerId());
        if (packet.getDelayMinutes() != null && packet.getDelayMinutes() > 0) {
            log.debug("Received timed restart request: serverId={}, delayMinutes={}, hasMessage={}",
                    packet.getServerId(), packet.getDelayMinutes(), packet.getDelayedRestartMessage() != null);
        } else {
            log.debug("Received immediate restart request: serverId={}, hasMessage={}",
                    packet.getServerId(), packet.getDelayedRestartMessage() != null);
        }
        Integer delay = packet.getDelayMinutes();
        if (delay != null && delay > 0) {

            HashMap<String, Object> context = new HashMap<>();
            context.put("delay", delay);

            boolean created = TaskService.createTask(
                    NodeTask.Type.TIMED_RESTART,
                    new TimedRestartPayload(packet.getServerId(), delay, packet.getDelayedRestartMessage()),
                    Node.INSTANCE.getNodeId(),
                    context,
                    packet.getServerId()
            );
            if (!created) {
                log.warn("Could not create timed restart task for server {}", packet.getServerId());
            } else {
                log.debug("Timed restart task created for server {} with delay {} min", packet.getServerId(), delay);
            }
            return;
        }

        GameServer server = GameServer.getServerById(packet.getServerId());
        if (server != null) {
            server.restart().queue(success -> {
                if (success) {
                    log.info("Restarted server with id {} successfully.", packet.getServerId());
                } else {
                    log.error("Restarting server with id {} failed.", packet.getServerId());
                }
            });
        } else {
            log.error("Server with id {} not found!", packet.getServerId());
        }
    }
}
