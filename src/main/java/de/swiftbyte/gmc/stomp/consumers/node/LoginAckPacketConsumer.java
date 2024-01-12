package de.swiftbyte.gmc.stomp.consumers.node;

import de.swiftbyte.gmc.Node;
import de.swiftbyte.gmc.common.packet.node.NodeLoginAckPacket;
import de.swiftbyte.gmc.server.AsaServer;
import de.swiftbyte.gmc.server.GameServer;
import de.swiftbyte.gmc.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.stomp.StompPacketInfo;
import de.swiftbyte.gmc.utils.ConnectionState;
import de.swiftbyte.gmc.utils.ServerUtils;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;

@Slf4j
@StompPacketInfo(path = "/user/queue/node/login", packetClass = NodeLoginAckPacket.class)
public class LoginAckPacketConsumer implements StompPacketConsumer<NodeLoginAckPacket> {

    @Override
    public void onReceive(NodeLoginAckPacket packet) {

        Node.INSTANCE.setTeamName(packet.getTeamName());

        log.info("Backend running in profile '" + packet.getBackendProfile() + "' with version '" + packet.getBackendVersion() + "'.");
        log.info("I am '" + packet.getNodeSettings().getName() + "' in team " + packet.getTeamName() + "!");

        log.info("Loading '" + packet.getGameServers().size() + "' game servers...");
        GameServer.abandonAll();
        packet.getGameServers().forEach(gameServer -> {
            log.debug("Loading game server '" + gameServer.getSettings().getName() + "'...");

            String serverInstallDir = ServerUtils.getCachedServerInstallDir(gameServer.getId());

            if (serverInstallDir == null) {
                new AsaServer(gameServer.getId(), gameServer.getDisplayName(), gameServer.getSettings(), false);
            } else {
                new AsaServer(gameServer.getId(), gameServer.getDisplayName(), Path.of(serverInstallDir), gameServer.getSettings(), false);
            }
        });

        Node.INSTANCE.updateSettings(packet.getNodeSettings());

        Node.INSTANCE.setConnectionState(ConnectionState.CONNECTED);
    }
}
