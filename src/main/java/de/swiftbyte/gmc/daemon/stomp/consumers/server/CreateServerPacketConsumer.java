package de.swiftbyte.gmc.daemon.stomp.consumers.server;

import de.swiftbyte.gmc.common.entity.GameType;
import de.swiftbyte.gmc.common.model.SettingProfile;
import de.swiftbyte.gmc.common.packet.from.backend.server.ServerCreatePacket;
import de.swiftbyte.gmc.common.packet.from.daemon.server.ServerDeleteBackupResponsePacket;
import de.swiftbyte.gmc.common.packet.from.daemon.server.ServerDeleteResponse;
import de.swiftbyte.gmc.daemon.server.ArkServer;
import de.swiftbyte.gmc.daemon.server.AsaServer;
import de.swiftbyte.gmc.daemon.server.AseServer;
import de.swiftbyte.gmc.daemon.stomp.StompHandler;
import de.swiftbyte.gmc.daemon.stomp.StompPacketConsumer;
import de.swiftbyte.gmc.daemon.stomp.StompPacketInfo;
import de.swiftbyte.gmc.daemon.utils.ServerUtils;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;

@Slf4j
@StompPacketInfo(path = "/user/queue/server/create", packetClass = ServerCreatePacket.class)
public class CreateServerPacketConsumer implements StompPacketConsumer<ServerCreatePacket> {

    @Override
    public void onReceive(ServerCreatePacket packet) {
        log.info("Created server with id {} and name {}.", packet.getServerId(), packet.getServerName());
        if (packet.getGameType() == GameType.ARK_ASCENDED || packet.getGameType() == GameType.ARK_EVOLVED) {

            SettingProfile settings = ServerUtils.getSettingProfile(packet.getSettingProfileId());
            if (settings == null) {
                log.error("Setting profile '{}' not found for game server '{}'. Cancel server creation.", packet.getSettingProfileId(), packet.getServerName());

                //Delete server in backend
                ServerDeleteResponse deletePacket = new ServerDeleteResponse();
                deletePacket.setServerId(packet.getServerId());
                StompHandler.send("/app/server/delete", deletePacket);
                return;
            }

            ArkServer server;

            Path installDir = Path.of(packet.getServerDirectory(), packet.getServerId());

            if (packet.getGameType() == GameType.ARK_ASCENDED) {
                server = new AsaServer(packet.getServerId(), packet.getServerName(), installDir, settings, true);
            } else {
                server = new AseServer(packet.getServerId(), packet.getServerName(), installDir, settings, true);
            }

            server.install().queue(success -> {
                if(success) log.info("Installed server with id {} and name {} successfully.", packet.getServerId(), packet.getServerName());
                else {
                    log.error("Installing  server with id {} and name {} failed.", packet.getServerId(), packet.getServerName());

                    //Delete server in backend
                    ServerDeleteResponse deletePacket = new ServerDeleteResponse();
                    deletePacket.setServerId(packet.getServerId());
                    StompHandler.send("/app/server/delete", deletePacket);
                }
            });
        } else {
            log.error("Game {} is not supported!", packet.getGameType());
        }
    }
}
