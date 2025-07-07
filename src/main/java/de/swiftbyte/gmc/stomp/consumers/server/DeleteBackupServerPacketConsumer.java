package de.swiftbyte.gmc.stomp.consumers.server;

import de.swiftbyte.gmc.common.packet.server.ServerDeleteBackupPacket;
import de.swiftbyte.gmc.service.BackupService;
import de.swiftbyte.gmc.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.stomp.StompPacketInfo;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@StompPacketInfo(path = "/user/queue/server/delete-backup", packetClass = ServerDeleteBackupPacket.class)
public class DeleteBackupServerPacketConsumer implements StompPacketConsumer<ServerDeleteBackupPacket> {

    @Override
    public void onReceive(ServerDeleteBackupPacket packet) {
        log.info("Delete backup with id {}.", packet.getBackupId());

        BackupService.deleteBackup(packet.getBackupId());
    }
}
