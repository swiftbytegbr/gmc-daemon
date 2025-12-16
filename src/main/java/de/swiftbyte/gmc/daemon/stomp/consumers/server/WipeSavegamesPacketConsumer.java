package de.swiftbyte.gmc.daemon.stomp.consumers.server;

import de.swiftbyte.gmc.common.model.NodeTask;
import de.swiftbyte.gmc.common.packet.from.backend.server.ServerWipeSavegamesPacket;
import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.service.TaskService;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;

@Slf4j
@StompPacketInfo(path = "/user/queue/server/wipe-savegames", packetClass = ServerWipeSavegamesPacket.class)
public class WipeSavegamesPacketConsumer implements StompPacketConsumer<ServerWipeSavegamesPacket> {

    @Override
    public void onReceive(ServerWipeSavegamesPacket packet) {
        log.info("Starting savegame wipe for server {}.", packet.getServerId());

        GameServer server = GameServer.getServerById(packet.getServerId());
        if (server == null) {
            log.error("Server with id {} not found!", packet.getServerId());
            return;
        }


        TaskService.createTask(
                NodeTask.Type.SAVEGAME_WIPE,
                null,
                Node.INSTANCE.getNodeId(),
                packet.getServerId()
        );
    }
}

