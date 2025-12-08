package de.swiftbyte.gmc.daemon.stomp.consumers.team;

import de.swiftbyte.gmc.common.packet.from.backend.team.TeamChangePacket;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import lombok.CustomLog;
import org.jspecify.annotations.NonNull;

@CustomLog
@StompPacketInfo(path = "/user/queue/team/change", packetClass = TeamChangePacket.class)
public class TeamChangeConsumer implements StompPacketConsumer<TeamChangePacket> {

    @Override
    public void onReceive(@NonNull TeamChangePacket packet) {
        log.debug("Received team change packet: {}", packet);

        getNode().setTeamName(packet.getTeamName());
    }
}
