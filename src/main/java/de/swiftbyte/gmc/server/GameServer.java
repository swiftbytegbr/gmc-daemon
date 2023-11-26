package de.swiftbyte.gmc.server;

import de.swiftbyte.gmc.Application;
import de.swiftbyte.gmc.packet.entity.GameServerState;
import de.swiftbyte.gmc.packet.entity.ServerSettings;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class GameServer {

    private static final HashMap<String, GameServer> GAME_SERVERS = new HashMap<>();

    private final int updateInterval = 10;

    @Getter
    protected String PID;

    @Getter
    protected String friendlyName;

    @Getter
    protected String serverId;

    @Getter
    protected Path installDir;

    @Getter
    protected GameServerState state = GameServerState.OFFLINE;

    @Getter
    protected int gamePort;

    @Getter
    protected int rawPort;

    @Getter
    protected int queryPort;

    @Getter
    protected int rconPort;

    @Getter
    protected boolean isAutoRestartEnabled;

    protected String rconPassword;

    protected List<String> startPreArguments = new ArrayList<>();
    protected List<String> startPostArguments1 = new ArrayList<>();
    protected List<String> startPostArguments2 = new ArrayList<>();

    protected Process serverProcess;

    public abstract void installServer();

    public abstract void start();

    public abstract void stop();

    public abstract void update();

    public void restart() {
        stop();
        start();
    }

    public abstract void writeStartupBatch();

    public abstract String sendRconCommand(String command);

    public GameServer(String id, String friendlyName, Path installDir) {

        this.serverId = id;
        this.friendlyName = friendlyName;
        this.installDir = installDir;

        GAME_SERVERS.put(id, this);
        Application.getExecutor().scheduleAtFixedRate(this::update, 0, updateInterval, TimeUnit.SECONDS);
    }

    public void setState(GameServerState state) {

        log.debug("Changing state of server '" + friendlyName + "' from '" + this.state + "' to '" + state + "'.");

        this.state = state;
    }

    public void setSettings(ServerSettings settings) {
        this.gamePort = settings.getGamePort();
        this.rawPort = settings.getRawPort();
        this.queryPort = settings.getQueryPort();
        this.rconPort = settings.getRconPort();
        this.rconPassword = settings.getRconPassword();
        this.isAutoRestartEnabled = false;
        this.startPreArguments = settings.getLaunchParameters1();
        this.startPostArguments1 = settings.getLaunchParameters2();
        this.startPostArguments2 = settings.getLaunchParameters3();
    }

    public static GameServer getServerById(String id) {
        return GAME_SERVERS.get(id);
    }

    public static List<GameServer> getAllServers() {
        return new ArrayList<>(GAME_SERVERS.values());
    }

}
