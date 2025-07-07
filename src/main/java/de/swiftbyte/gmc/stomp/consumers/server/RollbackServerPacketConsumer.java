package de.swiftbyte.gmc.stomp.consumers.server;

import de.swiftbyte.gmc.common.packet.server.ServerRollbackPacket;
import de.swiftbyte.gmc.server.GameServer;
import de.swiftbyte.gmc.service.BackupService;
import de.swiftbyte.gmc.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.stomp.StompPacketInfo;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@StompPacketInfo(path = "/user/queue/server/rollback", packetClass = ServerRollbackPacket.class)
public class RollbackServerPacketConsumer implements StompPacketConsumer<ServerRollbackPacket> {

    @Override
    public void onReceive(ServerRollbackPacket packet) {
        log.info("Start rollback of server {}.", packet.getServerId());
        GameServer server = GameServer.getServerById(packet.getServerId());

        if (server != null) {
            BackupService.rollbackBackup(packet.getBackupId(), packet.isRollbackPlayers());
        } else {
            log.error("Server with id {} not found!", packet.getServerId());
        }
    }
}
