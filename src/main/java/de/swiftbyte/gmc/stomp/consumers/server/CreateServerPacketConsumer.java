package de.swiftbyte.gmc.stomp.consumers.server;

import de.swiftbyte.gmc.common.entity.GameType;
import de.swiftbyte.gmc.common.model.SettingProfile;
import de.swiftbyte.gmc.common.packet.server.ServerCreatePacket;
import de.swiftbyte.gmc.server.ArkServer;
import de.swiftbyte.gmc.server.AsaServer;
import de.swiftbyte.gmc.server.AseServer;
import de.swiftbyte.gmc.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.stomp.StompPacketInfo;
import de.swiftbyte.gmc.utils.ServerUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@StompPacketInfo(path = "/user/queue/server/create", packetClass = ServerCreatePacket.class)
public class CreateServerPacketConsumer implements StompPacketConsumer<ServerCreatePacket> {

    @Override
    public void onReceive(ServerCreatePacket packet) {
        log.info("Created server with id {} and name {}.", packet.getServerId(), packet.getServerName());
        if (packet.getGameType() == GameType.ARK_ASCENDED || packet.getGameType() == GameType.ARK_EVOLVED) {

            SettingProfile settings = ServerUtils.getSettingProfile(packet.getSettingProfileId());
            if (settings == null) {
                log.error("Setting profile '{}' not found for game server '{}'. Using default setting profile.", packet.getSettingProfileId(), packet.getServerName());
                settings = new SettingProfile();
                //TODO handle default profile
                return;
            }

            ArkServer server = null;

            if (packet.getGameType() == GameType.ARK_ASCENDED) {
                server = new AsaServer(packet.getServerId(), packet.getServerName(), settings, true);
            } else {
                server = new AseServer(packet.getServerId(), packet.getServerName(), settings, true);
            }

            server.install().complete();
            log.info("Installed server with id {} and name {} successfully.", packet.getServerId(), packet.getServerName());
        } else {
            log.error("Game {} is not supported!", packet.getGameType());
        }
    }
}
