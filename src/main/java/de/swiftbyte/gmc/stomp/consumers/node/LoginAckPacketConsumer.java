package de.swiftbyte.gmc.stomp.consumers.node;

import de.swiftbyte.gmc.Node;
import de.swiftbyte.gmc.common.entity.GameServerDto;
import de.swiftbyte.gmc.common.entity.GameType;
import de.swiftbyte.gmc.common.model.SettingProfile;
import de.swiftbyte.gmc.common.packet.node.NodeLoginAckPacket;
import de.swiftbyte.gmc.server.AsaServer;
import de.swiftbyte.gmc.server.AseServer;
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

        log.info("Backend running in profile '{}' with version '{}'.", packet.getBackendProfile(), packet.getBackendVersion());
        log.info("I am '{}' in team {}!", packet.getNodeSettings().getName(), packet.getTeamName());

        log.info("Loading '{}' game servers...", packet.getGameServers().size());
        GameServer.abandonAll();
        packet.getGameServers().forEach(gameServer -> {
            log.debug("Loading game server '{}' as type {}...", gameServer.getDisplayName(), gameServer.getType());

            String serverInstallDir = ServerUtils.getCachedServerInstallDir(gameServer.getId());

            SettingProfile settings = ServerUtils.getSettingProfile(gameServer.getSettingProfileId());
            if(settings == null) {
                log.error("Setting profile '{}' not found for game server '{}'. Using default setting profile.", gameServer.getSettingProfileId(), gameServer.getDisplayName());
                settings = new SettingProfile();
                //TODO handle
                return;
            }

            createGameServer(gameServer, settings, serverInstallDir);
        });

        Node.INSTANCE.updateSettings(packet.getNodeSettings());

        Node.INSTANCE.setConnectionState(ConnectionState.CONNECTED);
    }

    private void createGameServer(GameServerDto gameServer, SettingProfile settings, String serverInstallDir) {
        Path installDir = serverInstallDir != null ? Path.of(serverInstallDir) : null;

        if(gameServer.getType() == null) {
            log.error("Game server type is null for game server '{}'. Using ARK_ASCENDED to continue!", gameServer.getDisplayName());
            gameServer.setType(GameType.ARK_ASCENDED);
        }

        switch (gameServer.getType()) {
            case ARK_ASCENDED -> new AsaServer(gameServer.getId(), gameServer.getDisplayName(), installDir, settings, false);
            case ARK_EVOLVED -> new AseServer(gameServer.getId(), gameServer.getDisplayName(), installDir, settings, false);
            default -> log.error("Unknown game server type '{}'.", gameServer.getType());
        }
    }
}
