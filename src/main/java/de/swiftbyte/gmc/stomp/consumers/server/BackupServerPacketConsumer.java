package de.swiftbyte.gmc.stomp.consumers.server;

import de.swiftbyte.gmc.packet.server.ServerBackupPacket;
import de.swiftbyte.gmc.server.GameServer;
import de.swiftbyte.gmc.service.BackupService;
import de.swiftbyte.gmc.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.stomp.StompPacketInfo;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@StompPacketInfo(path = "/user/queue/server/backup", packetClass = ServerBackupPacket.class)
public class BackupServerPacketConsumer implements StompPacketConsumer<ServerBackupPacket> {

    @Override
    public void onReceive(ServerBackupPacket packet) {
        log.debug("Creating backup for server with id " + packet.getServerId() + ".");
        GameServer server = GameServer.getServerById(packet.getServerId());

        if (server != null) {
            BackupService.backupServer(server, false, packet.getName());
        } else {
            log.error("Server with id " + packet.getServerId() + " not found!");
        }
    }
}
