package de.swiftbyte.gmc.daemon.stomp.consumers.node;

import de.swiftbyte.gmc.common.entity.GameServerDto;
import de.swiftbyte.gmc.common.model.SettingProfile;
import de.swiftbyte.gmc.common.packet.from.backend.node.NodeLoginAckPacket;
import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.daemon.server.AsaServer;
import de.swiftbyte.gmc.daemon.server.AseServer;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.service.TaskService;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import de.swiftbyte.gmc.daemon.utils.ConnectionState;
import de.swiftbyte.gmc.daemon.utils.ServerUtils;
import lombok.CustomLog;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.nio.file.Path;

@CustomLog
@StompPacketInfo(path = "/user/queue/node/login", packetClass = NodeLoginAckPacket.class)
public class LoginAckPacketConsumer implements StompPacketConsumer<NodeLoginAckPacket> {

    @Override
    public void onReceive(@NonNull NodeLoginAckPacket packet) {

        Node node = getNode();

        try {

            node.setTeamName(packet.getTeamName());

            log.info("Backend running in profile '{}' with version '{}'.", packet.getBackendProfile(), packet.getBackendVersion());
            log.info("I am '{}' in team {}!", packet.getNodeSettings().getName(), packet.getTeamName());

            log.info("Loading '{}' game servers...", packet.getGameServers().size());

            // First, reconcile display name changes for already known servers to keep symlinks tidy
            try {
                packet.getGameServers().forEach(gameServer -> {
                    GameServer existing = GameServer.getServerById(gameServer.getId());
                    if (existing != null) {
                        String newName = gameServer.getDisplayName();
                        if (!newName.equals(existing.getFriendlyName())) {
                            log.info("Detected name change on login: '{}' -> '{}' (id={}).", existing.getFriendlyName(), newName, gameServer.getId());
                            existing.changeFriendlyName(newName);
                        }
                    }
                });
            } catch (Exception e) {
                log.warn("Failed while applying server name changes during login ack.", e);
            }

            // Recreate in-memory instances to reflect backend state
            GameServer.abandonAll();
            packet.getGameServers().forEach(gameServer -> {
                try {
                    log.debug("Loading game server '{}' as type {}...", gameServer.getDisplayName(), gameServer.getGameType());

                    Path serverInstallDir = Path.of(gameServer.getServerDirectory(), gameServer.getId());

                    SettingProfile settings = ServerUtils.getSettingProfile(gameServer.getSettingProfileId());
                    if (settings == null) {
                        log.error("Setting profile '{}' not found for game server '{}'. Canceling server initialization.", gameServer.getSettingProfileId(), gameServer.getDisplayName());
                        return;
                    }

                    createGameServer(gameServer, settings, serverInstallDir);
                } catch (Exception e) {
                    log.error("An unhandled exception occurred while initializing game server '{}'.", gameServer.getDisplayName(), e);
                }
            });

            node.updateSettings(packet.getNodeSettings());

            node.setConnectionState(ConnectionState.CONNECTED);

            // If we reconnected while tasks were running, re-announce them to the backend
            try {
                TaskService.announceActiveTasks();
            } catch (Exception e) {
                log.warn("Failed to re-announce active tasks after login ack.", e);
            }

            if (node.isFirstStart()) {
                log.info("""
                        
                        Congratulations — you have connected the daemon to your team!
                        
                        The daemon will continue to run on your server. Every time you perform an action in the web app, commands are sent to the daemon. It then executes these commands. If GMC ever needs to perform maintenance, you can manage the server using commands via the console.
                        
                        You are now finished here and can switch back to app.gamemanager.cloud.""");
                node.setFirstStart(false);
            }

        } catch (Exception e) {
            log.error("An unknown error occurred while trying to start the daemon.", e);
            node.setConnectionState(ConnectionState.RECONNECTING);
        }
    }

    private void createGameServer(GameServerDto gameServer, SettingProfile settings, @NotNull Path serverInstallDir) {

        switch (gameServer.getGameType()) {
            case ARK_ASCENDED ->
                    new AsaServer(gameServer.getId(), gameServer.getDisplayName(), serverInstallDir, settings, false);
            case ARK_EVOLVED ->
                    new AseServer(gameServer.getId(), gameServer.getDisplayName(), serverInstallDir, settings, false);
            default -> log.error("Unknown game server type '{}'.", gameServer.getGameType());
        }
    }
}
