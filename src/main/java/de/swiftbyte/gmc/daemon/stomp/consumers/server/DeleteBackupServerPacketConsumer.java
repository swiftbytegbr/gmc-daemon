package de.swiftbyte.gmc.daemon.stomp.consumers.server;

import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.common.packet.server.ServerDeleteBackupPacket;
import de.swiftbyte.gmc.common.packet.server.ServerDeleteBackupResponsePacket;
import de.swiftbyte.gmc.daemon.service.BackupService;
import de.swiftbyte.gmc.daemon.stomp.StompHandler;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import de.swiftbyte.gmc.daemon.utils.ConnectionState;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@StompPacketInfo(path = "/user/queue/server/delete-backup", packetClass = ServerDeleteBackupPacket.class)
public class DeleteBackupServerPacketConsumer implements StompPacketConsumer<ServerDeleteBackupPacket> {

    @Override
    public void onReceive(ServerDeleteBackupPacket packet) {
        log.info("Delete backup with id {}.", packet.getBackupId());

        if(Node.INSTANCE.getConnectionState() != ConnectionState.CONNECTED) {
            log.error("Could not delete backup because node is not connected to the panel!");
            return;
        }

        if(BackupService.deleteBackup(packet.getBackupId())) {
            ServerDeleteBackupResponsePacket responsePacket = new ServerDeleteBackupResponsePacket();
            responsePacket.setServerId(packet.getServerId());
            responsePacket.setBackupId(packet.getBackupId());
            StompHandler.send("/app/server/delete-backup", responsePacket);
        }
    }
}
