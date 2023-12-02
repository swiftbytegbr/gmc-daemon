package de.swiftbyte.gmc.server;

import de.swiftbyte.gmc.Application;
import de.swiftbyte.gmc.Node;
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
    protected GameServerState state = GameServerState.OFFLINE;

    @Getter
    protected String friendlyName;

    @Getter
    protected String serverId;

    @Getter
    protected Path installDir;

    @Getter
    protected String map;

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

    @Getter
    protected String rconPassword;

    @Getter
    protected List<String> startPreArguments = new ArrayList<>();

    @Getter
    protected List<String> startPostArguments1 = new ArrayList<>();

    @Getter
    protected List<String> startPostArguments2 = new ArrayList<>();

    protected Process serverProcess;

    public abstract void installServer();

    public abstract void start();

    public abstract void stop();

    public abstract void update();

    protected abstract void killServerProcess();

    public void restart() {
        stop();
        start();
    }

    public abstract void writeStartupBatch();

    public abstract String sendRconCommand(String command);

    public GameServer(String id, String friendlyName) {

        this.serverId = id;
        this.friendlyName = friendlyName;
        this.installDir = Path.of(Node.INSTANCE.getServerPath() + "/" + friendlyName.toLowerCase()).toAbsolutePath();

        GAME_SERVERS.put(id, this);
        Application.getExecutor().scheduleAtFixedRate(this::update, 0, updateInterval, TimeUnit.SECONDS);
    }

    public void setState(GameServerState state) {

        log.debug("Changing state of server '" + friendlyName + "' from '" + this.state + "' to '" + state + "'.");

        this.state = state;
    }

    public void setSettings(ServerSettings settings) {
        this.map = settings.getMap();
        this.gamePort = settings.getGamePort();
        this.rawPort = settings.getRawPort();
        this.queryPort = settings.getQueryPort();
        this.rconPort = settings.getRconPort();
        if (rconPassword != null) this.rconPassword = settings.getRconPassword();
        this.isAutoRestartEnabled = false;
        if (settings.getLaunchParameters1() != null) this.startPreArguments = settings.getLaunchParameters1();
        if (settings.getLaunchParameters2() != null) this.startPostArguments1 = settings.getLaunchParameters2();
        if (settings.getLaunchParameters3() != null) this.startPostArguments2 = settings.getLaunchParameters3();
    }

    public static GameServer getServerById(String id) {
        return GAME_SERVERS.get(id);
    }

    public static List<GameServer> getAllServers() {
        return new ArrayList<>(GAME_SERVERS.values());
    }

}
