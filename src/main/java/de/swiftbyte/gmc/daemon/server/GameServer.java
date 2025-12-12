package de.swiftbyte.gmc.daemon.server;

import de.swiftbyte.gmc.common.entity.GameServerState;
import de.swiftbyte.gmc.common.model.SettingProfile;
import de.swiftbyte.gmc.common.packet.from.daemon.server.ServerStatePacket;
import de.swiftbyte.gmc.daemon.Application;
import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.daemon.service.AutoRestartService;
import de.swiftbyte.gmc.daemon.service.BackupService;
import de.swiftbyte.gmc.daemon.service.FirewallService;
import de.swiftbyte.gmc.daemon.stomp.StompHandler;
import de.swiftbyte.gmc.daemon.utils.ServerUtils;
import de.swiftbyte.gmc.daemon.utils.action.AsyncAction;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

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
    protected volatile GameServerState state;

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
        this.installDir = installDir.toAbsolutePath().normalize();

        // Register first so downstream lookups (e.g., in setSettings -> BackupService) can resolve
        GAME_SERVERS.put(id, this);

        // Initialize baseline settings from cache when available to support delta detection
        SettingProfile cached = ServerUtils.getCachedGameServerSettings(id);
        SettingProfile baseline = cached != null ? cached : settings;

        // Initialize settings without invoking subclass overrides during construction
        initSettings(baseline);

        setState(GameServerState.OFFLINE);
        updateScheduler = Application.getExecutor().scheduleWithFixedDelay(() -> {
            try {
                // Skip update cycle while the server is in CREATING state (used to block operations during moves)
                if (this.state != GameServerState.CREATING) {
                    this.update();
                }
            } catch (Exception e) {
                log.error("Unhandled exception in server '{}'.", friendlyName, e);
            }
        }, 0, 10, TimeUnit.SECONDS);

        // setSettings already triggers backup and auto-restart scheduling

        //Generate server aliases when server is installed on alias is not present
        Path aliasPath = this.installDir.getParent().resolve(friendlyName + " - Link");
        if (!Files.exists(this.installDir) || Files.exists(aliasPath)) {
            return;
        }

        try {
            Files.createSymbolicLink(aliasPath, this.installDir);
        } catch (IOException e) {
            log.warn("Failed to create symbolic link for '{}'.", friendlyName, e);
        }
    }

    // Internal initializer to avoid virtual dispatch during construction
    private void initSettings(SettingProfile settings) {
        if (Node.INSTANCE.isManageFirewallAutomatically()) {
            FirewallService.removePort(friendlyName);
        }
        this.settings = settings;
        allowFirewallPorts();
        BackupService.updateAutoBackupSettings(serverId);
        AutoRestartService.updateAutoRestartSettings(serverId);
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

    public void setInstallDir(Path newInstallDir) {
        this.installDir = newInstallDir.toAbsolutePath().normalize();
    }

    /**
     * Updates the server's friendly name and refreshes the symbolic link under the parent directory
     * from the old display name to the new one.
     * <p>
     * The symlink format follows the convention used elsewhere in the daemon:
     * "<DisplayName> - Link" -> <installDir>
     */
    public void changeFriendlyName(@NotNull String newFriendlyName) {
        if (newFriendlyName.equals(this.friendlyName)) {
            return;
        }

        //Remove Firewall rules and recreate them after the name change
        if(Node.INSTANCE.isManageFirewallAutomatically()) {
            FirewallService.removePort(friendlyName);
        }

        Path parent = this.installDir != null ? this.installDir.getParent() : null;
        if (parent == null) {
            this.friendlyName = newFriendlyName;
            return;
        }

        String oldName = this.friendlyName;
        Path oldAlias = parent.resolve(oldName + " - Link");
        Path newAlias = parent.resolve(newFriendlyName + " - Link");

        try {
            try {
                Files.deleteIfExists(oldAlias);
            } catch (Exception e) {
                log.warn("Failed to delete old symbolic link '{}' for '{}'.", oldAlias, oldName, e);
            }

            try {
                // Ensure any stale newAlias is removed before re-creating
                Files.deleteIfExists(newAlias);
            } catch (Exception ignored) {
            }

            try {
                Files.createSymbolicLink(newAlias, this.installDir);
            } catch (IOException e) {
                log.warn("Failed to create symbolic link for '{}' at '{}'.", newFriendlyName, newAlias, e);
            }
        } catch (Exception e) {
            log.warn("Symlink refresh failed during name change from '{}' to '{}'.", oldName, newFriendlyName, e);
        }

        this.friendlyName = newFriendlyName;

        //Set settings to initialize setting firewall rules
        setSettings(settings);
    }

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
        AutoRestartService.updateAutoRestartSettings(serverId);
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
