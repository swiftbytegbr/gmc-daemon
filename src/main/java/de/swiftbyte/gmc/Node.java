package de.swiftbyte.gmc;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import de.swiftbyte.gmc.cache.CacheModel;
import de.swiftbyte.gmc.cache.GameServerCacheModel;
import de.swiftbyte.gmc.packet.entity.ResourceUsage;
import de.swiftbyte.gmc.packet.entity.ServerSettings;
import de.swiftbyte.gmc.packet.node.NodeHeartbeatPacket;
import de.swiftbyte.gmc.packet.node.NodeLogoutPacket;
import de.swiftbyte.gmc.server.AsaServer;
import de.swiftbyte.gmc.server.GameServer;
import de.swiftbyte.gmc.stomp.StompHandler;
import de.swiftbyte.gmc.utils.ConfigUtils;
import de.swiftbyte.gmc.utils.ConnectionState;
import de.swiftbyte.gmc.utils.NodeUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jline.terminal.impl.DumbTerminal;
import org.springframework.shell.component.context.ComponentContext;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

@Getter
@Slf4j
public class Node extends Thread {

    public static Node INSTANCE;

    private ConnectionState connectionState;

    @Setter
    private String nodeName;
    @Setter
    private String teamName;

    private String serverPath;

    private String secret;
    private String nodeId;

    public Node() {

        INSTANCE = this;

        //TODO Check if node has already joined

        this.connectionState = (ConfigUtils.hasKey("node.id") && ConfigUtils.hasKey("node.secret")) ? ConnectionState.NOT_CONNECTED : ConnectionState.NOT_JOINED;

        this.nodeName = "daemon";
        this.teamName = "gmc";

        serverPath = "test";

        getCachedNodeInformation();
        NodeUtils.checkInstallation();
    }

    private void getCachedNodeInformation() {

        log.debug("Getting cached information...");

        nodeId = ConfigUtils.get("node.id", "dummy");
        secret = ConfigUtils.get("node.secret", "dummy");

        File cacheFile = new File("./cache.json");

        if(!cacheFile.exists()) {
            log.debug("No cache file found. Skipping...");
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();
        try {
            CacheModel cacheModel = reader.readValue(new File("./cache.json"), CacheModel.class);
            nodeName = cacheModel.getNodeName();
            teamName = cacheModel.getTeamName();
            serverPath = cacheModel.getServerPath();
            log.debug("Got cached information.");
        } catch (IOException e) {
            log.error("An unknown error occurred while getting cached information.", e);
        }
    }

    private void getCachedServerInformation() {

        log.debug("Getting cached server information...");

        File cacheFile = new File("./cache.json");

        if(!cacheFile.exists()) {
            log.debug("No cache file found. Skipping...");
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        ObjectReader reader = mapper.reader();
        try {
            CacheModel cacheModel = reader.readValue(cacheFile, CacheModel.class);
            HashMap<String, GameServerCacheModel> gameServerCacheModelHashMap = cacheModel.getGameServerCacheModelHashMap();

            gameServerCacheModelHashMap.forEach((s, gameServerCacheModel) -> {

                    GameServer gameServer = new AsaServer(s, gameServerCacheModel.getFriendlyName());

                    ServerSettings serverSettings = new ServerSettings();

                    serverSettings.setMap(gameServerCacheModel.getMap());
                    serverSettings.setGamePort(gameServerCacheModel.getGamePort());
                    serverSettings.setRawPort(gameServerCacheModel.getRawPort());
                    serverSettings.setQueryPort(gameServerCacheModel.getQueryPort());
                    serverSettings.setRconPort(gameServerCacheModel.getRconPort());
                    serverSettings.setRconPassword(gameServerCacheModel.getRconPassword());
                    serverSettings.setLaunchParameters1(gameServerCacheModel.getStartPreArguments());
                    serverSettings.setLaunchParameters2(gameServerCacheModel.getStartPostArguments1());
                    serverSettings.setLaunchParameters3(gameServerCacheModel.getStartPostArguments2());

                    gameServer.setSettings(serverSettings);
            });

        } catch (IOException e) {
            log.error("An unknown error occurred while getting cached information.", e);
        }

    }

    public void cacheInformation() {

        log.debug("Caching information...");

        HashMap<String, GameServerCacheModel> gameServers = new HashMap<>();

        for (GameServer gameServer : GameServer.getAllServers()) {
            GameServerCacheModel gameServerCacheModel = GameServerCacheModel.builder()
                    .friendlyName(gameServer.getFriendlyName())
                    .installDir(gameServer.getInstallDir().toString())
                    .gamePort(gameServer.getGamePort())
                    .rawPort(gameServer.getRawPort())
                    .queryPort(gameServer.getQueryPort())
                    .rconPort(gameServer.getRconPort())
                    .isAutoRestartEnabled(gameServer.isAutoRestartEnabled())
                    .rconPassword(gameServer.getRconPassword())
                    .startPreArguments(gameServer.getStartPreArguments())
                    .startPostArguments1(gameServer.getStartPostArguments1())
                    .startPostArguments2(gameServer.getStartPostArguments2())
                    .map(gameServer.getMap())
                    .build();
            gameServers.put(gameServer.getServerId(), gameServerCacheModel);
        }

        CacheModel cacheModel = CacheModel.builder()
                .nodeName(nodeName)
                .teamName(teamName)
                .serverPath(serverPath)
                .gameServerCacheModelHashMap(gameServers)
                .build();

        ObjectMapper mapper = new ObjectMapper();
        ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
        try {
            File file = new File("./cache.json");
            if(!file.exists()) file.createNewFile();
            writer.writeValue(file, cacheModel);
        } catch (IOException e) {
            log.error("An unknown error occurred while caching information.", e);
        }

    }

    public void shutdown() {
        NodeLogoutPacket logoutPacket = new NodeLogoutPacket();
        logoutPacket.setReason("Terminated by user");
        log.info("Sending shutdown packet...");
        StompHandler.send("/app/node/logout", logoutPacket);
        log.info("Disconnecting from backend...");
        cacheInformation();
    }

    public void joinTeam() {
        log.debug("Start joining a team...");
        setConnectionState(ConnectionState.JOINING);

        ComponentContext<?> context = NodeUtils.promptForInviteToken();

        try {

            Integer token = NodeUtils.getValidatedToken(context.get("inviteToken"));

            while (token == null) {
                log.error("The entered token does not match the expected pattern. Please enter it as follows: XXX-XXX");
                token = NodeUtils.getValidatedToken(NodeUtils.promptForInviteToken().get("inviteToken"));
            }

            joinTeamWithValidatedToken(token);

        } catch (NoSuchElementException e) {

            setConnectionState(ConnectionState.NOT_JOINED);

            if (Application.getTerminal() instanceof DumbTerminal) {
                log.error("The prompt to enter the Invite Token could not be created due to an error. Please try to invite the node with the command 'join <token>'.");
            } else {
                log.debug("An empty entry was made. Restart the join process.");
                joinTeam();
            }
        }
    }

    public void joinTeam(String token) {
        setConnectionState(ConnectionState.JOINING);
        log.debug("Start joining a team with Invite Token '" + token + "'...");

        Integer realToken = NodeUtils.getValidatedToken(token);

        if (realToken == null) {
            log.error("The entered token does not match the expected pattern. Please enter it as follows: XXX-XXX");
            setConnectionState(ConnectionState.NOT_JOINED);
            return;
        }

        joinTeamWithValidatedToken(realToken);
    }

    private void joinTeamWithValidatedToken(int token) {

        log.debug("Start joining with Invite Token '" + token + "'...");

        OkHttpClient client = new OkHttpClient();

        FormBody body = new FormBody.Builder()
                .add("inviteToken", String.valueOf(token))
                .build();

        Request request = new Request.Builder()
                .url(Application.BACKEND_URL + "/node/register")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute();) {

            if (!response.isSuccessful()) {
                log.debug("Received error code " + response.code() + " with message '" + response.body().string() + "'");
                log.error("The specified Invite Token is incorrect. Please check your input.");
                joinTeam();
                return;
            }

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(response.body().string());
            nodeId = jsonNode.get("nodeId").asText();
            secret = jsonNode.get("nodeSecret").asText();

        } catch (IOException e) {
            log.error("An unknown error occurred.", e);
            joinTeam();
            return;
        }

        ConfigUtils.store("node.id", nodeId);
        ConfigUtils.store("node.secret", secret);

        setConnectionState(ConnectionState.NOT_CONNECTED);
        connect();
    }

    public void connect() {
        log.debug("Start connection to backend...");
        setConnectionState(ConnectionState.CONNECTING);
        if (!StompHandler.initialiseStomp()) {
            setConnectionState(ConnectionState.NOT_CONNECTED);
            getCachedServerInformation();
        } else {
            //TODO remove
            getCachedServerInformation();
        }
    }

    @Override
    public void run() {
        super.run();
        Application.getExecutor().scheduleAtFixedRate(updateRunnable, 0, 10, TimeUnit.SECONDS);
    }

    private final Runnable updateRunnable = () -> {

        if (connectionState == ConnectionState.CONNECTED) {

            SystemInfo systemInfo = new SystemInfo();
            NodeHeartbeatPacket heartbeatPacket = new NodeHeartbeatPacket();

            heartbeatPacket.setNodeId(nodeId);

            ResourceUsage resourceUsage = new ResourceUsage();
            resourceUsage.setRamUsage((int) ((systemInfo.getHardware().getMemory().getTotal() - systemInfo.getHardware().getMemory().getAvailable()) / (1024 * 1024)));
            resourceUsage.setCpuUsage(-1);
            resourceUsage.setNetworkUsage(-1);
            resourceUsage.setDiskUsage(-1);
            heartbeatPacket.setResourceUsage(resourceUsage);

            heartbeatPacket.setGameServers(new ArrayList<>());

            StompHandler.send("/app/node/heartbeat", heartbeatPacket);

        }
    };

    public void setConnectionState(ConnectionState connectionState) {
        log.debug("Connection state changed from " + this.connectionState + " to " + connectionState.name());
        this.connectionState = connectionState;
    }
}
