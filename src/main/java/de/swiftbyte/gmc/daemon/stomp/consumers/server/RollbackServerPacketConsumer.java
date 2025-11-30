package de.swiftbyte.gmc.daemon.stomp.consumers.server;

import de.swiftbyte.gmc.common.model.NodeTask;
import de.swiftbyte.gmc.common.packet.from.backend.server.ServerRollbackPacket;
import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.service.TaskService;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import de.swiftbyte.gmc.daemon.tasks.consumers.RollbackTaskConsumer;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;

@Slf4j
@StompPacketInfo(path = "/user/queue/server/rollback", packetClass = ServerRollbackPacket.class)
public class RollbackServerPacketConsumer implements StompPacketConsumer<ServerRollbackPacket> {

    @Override
    public void onReceive(ServerRollbackPacket packet) {
        log.info("Start rollback of server {}.", packet.getServerId());
        GameServer server = GameServer.getServerById(packet.getServerId());

        if (server != null) {
            // Create a ROLLBACK task using RollbackTaskConsumer payload

            HashMap<String, Object> context = new HashMap<>();
            context.put("backupId", packet.getBackupId());

            TaskService.createTask(
                    NodeTask.Type.ROLLBACK,
                    new RollbackTaskConsumer.RollbackTaskPayload(packet.getBackupId(), packet.isRollbackPlayers()),
                    Node.INSTANCE.getNodeId(),
                    context,
                    packet.getServerId()
            );
        } else {
            log.error("Server with id {} not found!", packet.getServerId());
        }
    }
}
