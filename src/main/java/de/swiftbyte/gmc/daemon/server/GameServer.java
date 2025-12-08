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
import de.swiftbyte.gmc.daemon.utils.PathUtils;
import de.swiftbyte.gmc.daemon.utils.ServerUtils;
import de.swiftbyte.gmc.daemon.utils.action.AsyncAction;
import lombok.CustomLog;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@CustomLog
public abstract class GameServer {

    private static final @NonNull ConcurrentHashMap<@NonNull String, @NonNull GameServer> GAME_SERVERS = new ConcurrentHashMap<>();

    protected final @NonNull Node node;
    protected final @NonNull ScheduledFuture<?> updateScheduler;

    protected @Nullable String PID;

    protected @Nullable Process serverProcess;

    @Getter
    protected @NonNull GameServerState state;

    @Getter
    protected @NonNull String friendlyName;

    @Getter
    protected @NonNull String serverId;

    @Getter
    protected @NonNull Path installDir;

    @Getter
    protected @NonNull SettingProfile settings;

    @Getter
    protected int currentOnlinePlayers = 0;


    public abstract @NonNull String getGameId();

    public GameServer(@NonNull String id, @NotNull Path installDir, @NonNull String friendlyName, @NonNull SettingProfile settings) {

        if (Node.INSTANCE == null) {
            throw new IllegalStateException("Node was not initialized yet");
        }
        node = Node.INSTANCE;

        this.serverId = id;
        this.friendlyName = friendlyName;
        this.installDir = installDir.toAbsolutePath().normalize();
        this.state = GameServerState.UNKNOWN;

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
    private void initSettings(@NonNull SettingProfile settings) {

        if (node.isManageFirewallAutomatically()) {
            FirewallService.removePort(friendlyName);
        }
        this.settings = settings;
        allowFirewallPorts();
        BackupService.updateAutoBackupSettings(serverId);
        AutoRestartService.updateAutoRestartSettings(serverId);
    }

    public abstract @NonNull AsyncAction<@NonNull Boolean> install();

    public abstract @NonNull AsyncAction<@NonNull Boolean> delete();

    public abstract @NonNull AsyncAction<@NonNull Boolean> abandon();

    public abstract @NonNull AsyncAction<@NonNull Boolean> start();

    public @NonNull AsyncAction<@NonNull Boolean> stop() {
        return stop(false, false);
    }

    public @NonNull AsyncAction<@NonNull Boolean> stop(boolean isRestart) {
        return stop(isRestart, false);
    }

    public abstract @NonNull AsyncAction<@NonNull Boolean> stop(boolean isRestart, boolean isKill);

    public abstract @NonNull AsyncAction<@NonNull Boolean> restart();

    public abstract void update();

    public abstract @Nullable String sendRconCommand(@NonNull String command);

    public abstract @NonNull List<@NonNull Integer> getNeededPorts();

    public abstract void allowFirewallPorts();

    protected abstract void gatherPID();

    public void setInstallDir(@NonNull Path newInstallDir) {
        this.installDir = PathUtils.getAbsolutPath(newInstallDir);
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
        if (node.isManageFirewallAutomatically()) {
            FirewallService.removePort(friendlyName);
        }

        Path parent = this.installDir.getParent();
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

    public void setState(@NonNull GameServerState state) {

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

    public void setSettings(@NonNull SettingProfile settings) {

        if (this.state == GameServerState.CREATING) {
            log.warn("Server '{}' is busy (CREATING). Settings change ignored.", friendlyName);
            return;
        }
        if (node.isManageFirewallAutomatically()) {
            FirewallService.removePort(friendlyName);
        }
        this.settings = settings;
        allowFirewallPorts();
        BackupService.updateAutoBackupSettings(serverId);
        AutoRestartService.updateAutoRestartSettings(serverId);
    }

    protected static void removeServerById(@NonNull String id) {
        GAME_SERVERS.remove(id);
    }

    public static @Nullable GameServer getServerById(@NonNull String id) {
        return GAME_SERVERS.get(id);
    }

    public static @NonNull List<@NonNull GameServer> getAllServers() {
        return new ArrayList<>(GAME_SERVERS.values());
    }

    public static void abandonAll() {
        getAllServers().forEach((server -> server.abandon().complete()));
    }
}
