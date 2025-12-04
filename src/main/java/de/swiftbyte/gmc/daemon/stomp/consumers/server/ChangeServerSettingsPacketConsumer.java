package de.swiftbyte.gmc.daemon.stomp.consumers.server;

import de.swiftbyte.gmc.common.model.SettingProfile;
import de.swiftbyte.gmc.common.packet.from.backend.server.ServerSettingsPacket;
import de.swiftbyte.gmc.common.packet.from.daemon.server.ServerSettingsResponsePacket;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.stomp.StompHandler;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import de.swiftbyte.gmc.daemon.utils.ServerUtils;
import lombok.CustomLog;

@CustomLog
@StompPacketInfo(path = "/user/queue/server/settings", packetClass = ServerSettingsPacket.class)
public class ChangeServerSettingsPacketConsumer implements StompPacketConsumer<ServerSettingsPacket> {

    @Override
    public void onReceive(ServerSettingsPacket packet) {
        log.info("Changing settings of server with id {}.", packet.getServerId());
        GameServer server = GameServer.getServerById(packet.getServerId());

        if (server != null) {

            SettingProfile settings = ServerUtils.getSettingProfile(packet.getSettingProfileId());
            if (settings == null) {
                log.error("Setting profile '{}' not found for game server '{}'. Using default setting profile.", packet.getSettingProfileId(), server.getFriendlyName());
                settings = new SettingProfile();
            }

            server.setSettings(settings);

            ServerSettingsResponsePacket responsePacket = new ServerSettingsResponsePacket();
            responsePacket.setServerId(server.getServerId());

            StompHandler.send("/app/server/settings", responsePacket);

        } else {
            log.error("Server with id {} not found!", packet.getServerId());
        }
    }
}
