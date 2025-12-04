package de.swiftbyte.gmc.daemon.stomp.consumers.team;

import de.swiftbyte.gmc.common.packet.from.backend.team.TeamChangePacket;
import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import lombok.CustomLog;

@CustomLog
@StompPacketInfo(path = "/user/queue/team/change", packetClass = TeamChangePacket.class)
public class TeamChangeConsumer implements StompPacketConsumer<TeamChangePacket> {

    @Override
    public void onReceive(TeamChangePacket packet) {
        log.debug("Received team change packet: {}", packet);
        Node.INSTANCE.setTeamName(packet.getTeamName());
    }
}
