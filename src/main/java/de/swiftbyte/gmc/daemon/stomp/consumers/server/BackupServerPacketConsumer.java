package de.swiftbyte.gmc.daemon.stomp.consumers.server;

import de.swiftbyte.gmc.common.packet.from.backend.server.ServerBackupPacket;
import de.swiftbyte.gmc.common.model.NodeTask;
import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.service.TaskService;
import de.swiftbyte.gmc.daemon.tasks.consumers.BackupTaskConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;

@Slf4j
@StompPacketInfo(path = "/user/queue/server/backup", packetClass = ServerBackupPacket.class)
public class BackupServerPacketConsumer implements StompPacketConsumer<ServerBackupPacket> {

    @Override
    public void onReceive(ServerBackupPacket packet) {
        log.info("Creating backup for server with id {}.", packet.getServerId());
        GameServer server = GameServer.getServerById(packet.getServerId());

        if (server != null) {
            // Create a non-cancellable BACKUP task for this server

            HashMap<String, Object> context = new HashMap<>();
            context.put("backupName", packet.getName());

            TaskService.createTask(
                    NodeTask.Type.BACKUP,
                    new BackupTaskConsumer.BackupTaskPayload(false, packet.getName()),
                    Node.INSTANCE.getNodeId(),
                    context,
                    packet.getServerId()
            );
        } else {
            log.error("Server with id {} not found!", packet.getServerId());
        }
    }
}
