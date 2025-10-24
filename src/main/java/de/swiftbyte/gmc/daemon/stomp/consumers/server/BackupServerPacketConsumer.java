package de.swiftbyte.gmc.daemon.stomp.consumers.server;

import de.swiftbyte.gmc.common.packet.from.backend.server.ServerBackupPacket;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.service.BackupService;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@StompPacketInfo(path = "/user/queue/server/backup", packetClass = ServerBackupPacket.class)
public class BackupServerPacketConsumer implements StompPacketConsumer<ServerBackupPacket> {

    @Override
    public void onReceive(ServerBackupPacket packet) {
        log.info("Creating backup for server with id {}.", packet.getServerId());
        GameServer server = GameServer.getServerById(packet.getServerId());

        if (server != null) {
            BackupService.backupServer(server, false, packet.getName());
        } else {
            log.error("Server with id {} not found!", packet.getServerId());
        }
    }
}
