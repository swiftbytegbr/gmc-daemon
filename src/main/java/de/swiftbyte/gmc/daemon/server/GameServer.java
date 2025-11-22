package de.swiftbyte.gmc.daemon.server;

import de.swiftbyte.gmc.common.entity.GameServerState;
import de.swiftbyte.gmc.common.model.SettingProfile;
import de.swiftbyte.gmc.common.packet.from.daemon.server.ServerStatePacket;
import de.swiftbyte.gmc.daemon.Application;
import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.daemon.service.BackupService;
import de.swiftbyte.gmc.daemon.service.FirewallService;
import de.swiftbyte.gmc.daemon.stomp.StompHandler;
import de.swiftbyte.gmc.daemon.utils.action.AsyncAction;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class GameServer {

    private static final ConcurrentHashMap<String, GameServer> GAME_SERVERS = new ConcurrentHashMap<>();

    protected ScheduledFuture<?> updateScheduler;

    @Getter
    protected String PID;

    protected Process serverProcess;

    @Getter
    protected GameServerState state;

    @Getter
    protected String friendlyName;

    @Getter
    protected String serverId;

    @Getter
    protected Path installDir;

    @Getter
    protected SettingProfile settings;

    @Getter
    protected int currentOnlinePlayers = 0;

    public abstract String getGameId();

    public GameServer(String id, @NotNull Path installDir, String friendlyName, SettingProfile settings) {

        this.serverId = id;
        this.friendlyName = friendlyName;
        this.installDir = installDir.toAbsolutePath();
        this.settings = settings;

        setState(GameServerState.OFFLINE);

        GAME_SERVERS.put(id, this);
        updateScheduler = Application.getExecutor().scheduleWithFixedDelay(() -> {
            try {
                this.update();
            } catch (Exception e) {
                log.error("Unhandled exception in server '{}'.", friendlyName, e);
            }
        }, 0, 10, TimeUnit.SECONDS);

        BackupService.updateAutoBackupSettings(serverId);

        //Generate server aliases
        Path aliasPath = installDir.getParent().resolve(friendlyName + " - Link");
        if(Files.exists(aliasPath)) return;

        try {
            Files.createSymbolicLink(aliasPath, installDir);
        } catch (IOException e) {
            log.warn("Failed to create symbolic link for '{}'.", friendlyName, e);
        }
    }

    public abstract AsyncAction<Boolean> install();

    public abstract AsyncAction<Boolean> delete();

    public abstract AsyncAction<Boolean> abandon();

    public abstract AsyncAction<Boolean> start();

    public AsyncAction<Boolean> stop() {
        return stop(false, false);
    }

    public AsyncAction<Boolean> stop(boolean isRestart) {
        return stop(isRestart, false);
    }

    public abstract AsyncAction<Boolean> stop(boolean isRestart, boolean isKill);

    public abstract AsyncAction<Boolean> restart();

    public abstract void update();

    public abstract String sendRconCommand(String command);

    public abstract List<Integer> getNeededPorts();

    public abstract void allowFirewallPorts();

    public void setState(GameServerState state) {

        if (this.state == GameServerState.DELETING) {
            return;
        }

        log.debug("Changing state of server '{}' from '{}' to '{}'.", friendlyName, this.state, state);

        if (state == GameServerState.OFFLINE || state == GameServerState.INITIALIZING) {
            this.PID = null;
        }

        synchronized (this) {
            this.state = state;
            this.notifyAll();
        }

        ServerStatePacket packet = new ServerStatePacket();
        packet.setServerId(serverId);
        packet.setState(state);

        StompHandler.send("/app/server/state", packet);
    }

    public void setSettings(SettingProfile settings) {
        if (Node.INSTANCE.isManageFirewallAutomatically()) {
            FirewallService.removePort(friendlyName);
        }
        this.settings = settings;
        allowFirewallPorts();
        BackupService.updateAutoBackupSettings(serverId);
    }

    protected static void removeServerById(String id) {
        GAME_SERVERS.remove(id);
    }

    public static GameServer getServerById(String id) {
        return GAME_SERVERS.get(id);
    }

    public static List<GameServer> getAllServers() {
        return new ArrayList<>(GAME_SERVERS.values());
    }

    public static void abandonAll() {
        getAllServers().forEach((server -> server.abandon().complete()));
    }
}
