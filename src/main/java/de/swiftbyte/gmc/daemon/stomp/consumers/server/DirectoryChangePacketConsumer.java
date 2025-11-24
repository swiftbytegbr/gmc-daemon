package de.swiftbyte.gmc.daemon.stomp.consumers.server;

import de.swiftbyte.gmc.common.model.NodeTask;
import de.swiftbyte.gmc.common.packet.from.backend.server.ServerChangeDirectoryPacket;
import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.service.TaskService;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import de.swiftbyte.gmc.daemon.tasks.consumers.ServerDirectoryChangeTaskConsumer;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;

@Slf4j
@StompPacketInfo(path = "/user/queue/server/change-directory", packetClass = ServerChangeDirectoryPacket.class)
public class DirectoryChangePacketConsumer implements StompPacketConsumer<ServerChangeDirectoryPacket> {


    @Override
    public void onReceive(ServerChangeDirectoryPacket packet) {
        log.info("Received server directory change for {} -> {}", packet.getServerId(), packet.getServerDirectory());
        GameServer server = GameServer.getServerById(packet.getServerId());
        if (server == null) {
            log.error("Server with id {} not found!", packet.getServerId());
            return;
        }

        try {
            java.nio.file.Path newParent = java.nio.file.Path.of(packet.getServerDirectory()).toAbsolutePath().normalize();
            java.nio.file.Path currentParent = server.getInstallDir().getParent().toAbsolutePath().normalize();

            if (currentParent.equals(newParent)) {
                log.info("Server '{}' already located under '{}'. Skipping move.", server.getFriendlyName(), newParent);
                return;
            }

            log.info("Scheduling SERVER_DIRECTORY_CHANGE task: {} -> {}", currentParent, newParent);
            HashMap<String, Object> context = new HashMap<>();
            context.put("sourceParent", currentParent.toString());
            context.put("destinationParent", newParent.toString());

            boolean created = TaskService.createTask(
                    NodeTask.Type.SERVER_DIRECTORY_CHANGE,
                    new ServerDirectoryChangeTaskConsumer.ServerDirectoryChangeTaskPayload(server.getServerId(), currentParent, newParent),
                    Node.INSTANCE.getNodeId(),
                    context,
                    packet.getServerId()
            );
            if (!created) {
                log.warn("Could not create SERVER_DIRECTORY_CHANGE task for server {}", packet.getServerId());
            }
        } catch (Exception e) {
            log.error("Failed to schedule server directory change for {}", packet.getServerId(), e);
        }
    }
}
