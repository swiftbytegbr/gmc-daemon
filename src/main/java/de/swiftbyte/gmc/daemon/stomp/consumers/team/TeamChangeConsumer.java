package de.swiftbyte.gmc.daemon.stomp.consumers.team;

import de.swiftbyte.gmc.common.packet.team.TeamChangePacket;
import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.service.BackupService;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@StompPacketInfo(path = "/user/queue/team/change", packetClass = TeamChangePacket.class)
public class TeamChangeConsumer implements StompPacketConsumer<TeamChangePacket> {

    @Override
    public void onReceive(TeamChangePacket packet) {
        log.debug("Received team change packet: {}", packet);
        Node.INSTANCE.setNodeName(packet.getTeamName());
    }
}
