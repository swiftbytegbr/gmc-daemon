package de.swiftbyte.gmc.daemon.stomp.consumers.node;

import de.swiftbyte.gmc.common.packet.from.backend.node.NodeLoginAckPacket;
import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.common.entity.GameServerDto;
import de.swiftbyte.gmc.common.entity.GameType;
import de.swiftbyte.gmc.common.model.SettingProfile;
import de.swiftbyte.gmc.daemon.server.AsaServer;
import de.swiftbyte.gmc.daemon.server.AseServer;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import de.swiftbyte.gmc.daemon.utils.ConnectionState;
import de.swiftbyte.gmc.daemon.utils.ServerUtils;
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
            if (settings == null) {
                log.error("Setting profile '{}' not found for game server '{}'. Using default setting profile.", gameServer.getSettingProfileId(), gameServer.getDisplayName());
                settings = new SettingProfile();
                //TODO handle
                return;
            }

            createGameServer(gameServer, settings, serverInstallDir);
        });

        Node.INSTANCE.updateSettings(packet.getNodeSettings());

        Node.INSTANCE.setConnectionState(ConnectionState.CONNECTED);

        if(Node.INSTANCE.isFirstStart()) {
            log.info("""
                    
                    Congratulations — you have connected the daemon to your team!
                    
                    The daemon will continue to run on your server. Every time you perform an action in the web app, commands are sent to the daemon. It then executes these commands. If GMC ever needs to perform maintenance, you can manage the server using commands via the console.
                    
                    You are now finished here and can switch back to app.gamemanager.cloud.""");
            Node.INSTANCE.setFirstStart(false);
        }

    }

    private void createGameServer(GameServerDto gameServer, SettingProfile settings, String serverInstallDir) {
        Path installDir = serverInstallDir != null ? Path.of(serverInstallDir) : null;

        if (gameServer.getType() == null) {
            log.error("Game server type is null for game server '{}'. Using ARK_ASCENDED to continue!", gameServer.getDisplayName());
            gameServer.setType(GameType.ARK_ASCENDED);
        }

        switch (gameServer.getType()) {
            case ARK_ASCENDED ->
                    new AsaServer(gameServer.getId(), gameServer.getDisplayName(), installDir, settings, false);
            case ARK_EVOLVED ->
                    new AseServer(gameServer.getId(), gameServer.getDisplayName(), installDir, settings, false);
            default -> log.error("Unknown game server type '{}'.", gameServer.getType());
        }
    }
}
