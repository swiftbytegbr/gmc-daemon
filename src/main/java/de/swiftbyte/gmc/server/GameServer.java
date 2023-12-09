package de.swiftbyte.gmc.server;

import de.swiftbyte.gmc.Application;
import de.swiftbyte.gmc.Node;
import de.swiftbyte.gmc.packet.entity.GameServerState;
import de.swiftbyte.gmc.packet.entity.ServerSettings;
import de.swiftbyte.gmc.packet.server.ServerStatePacket;
import de.swiftbyte.gmc.stomp.StompHandler;
import de.swiftbyte.gmc.utils.AsyncAction;
import de.swiftbyte.gmc.utils.CommonUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class GameServer {

    private static final HashMap<String, GameServer> GAME_SERVERS = new HashMap<>();

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
    @Setter
    protected ServerSettings settings;

    public GameServer(String id, String friendlyName, ServerSettings settings) {

        this.serverId = id;
        this.friendlyName = friendlyName;
        this.installDir = Path.of(Node.INSTANCE.getServerPath() + "/" + friendlyName.toLowerCase()).toAbsolutePath();
        this.settings = settings;

        GAME_SERVERS.put(id, this);
        updateScheduler = Application.getExecutor().scheduleAtFixedRate(this::update, 0, 10, TimeUnit.SECONDS);

        setState(GameServerState.OFFLINE);
    }

    public abstract AsyncAction<Boolean> install();

    public abstract AsyncAction<Boolean> delete();

    public abstract AsyncAction<Boolean> start();

    public abstract AsyncAction<Boolean> stop(boolean isRestart);

    public AsyncAction<Boolean> restart() {
        if(!CommonUtils.isNullOrEmpty(Node.INSTANCE.getServerStopMessage())) sendRconCommand("serverchat " + Node.INSTANCE.getServerRestartMessage());
        return () -> (stop(true).complete() && start().complete());
    }

    public abstract void update();

    public abstract String sendRconCommand(String command);

    public void setState(GameServerState state) {

        log.debug("Changing state of server '" + friendlyName + "' from '" + this.state + "' to '" + state + "'.");

        this.state = state;

        ServerStatePacket packet = new ServerStatePacket();
        packet.setServerId(serverId);
        packet.setState(state);

        StompHandler.send("/app/server/state", packet);
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

}
