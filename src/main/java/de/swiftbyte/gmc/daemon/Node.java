package de.swiftbyte.gmc.daemon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.swiftbyte.gmc.daemon.cache.CacheModel;
import de.swiftbyte.gmc.common.entity.NodeSettings;
import de.swiftbyte.gmc.common.entity.ResourceUsage;
import de.swiftbyte.gmc.common.packet.node.NodeHeartbeatPacket;
import de.swiftbyte.gmc.common.packet.node.NodeLogoutPacket;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.service.BackupService;
import de.swiftbyte.gmc.daemon.stomp.StompHandler;
import de.swiftbyte.gmc.daemon.utils.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.io.FileUtils;
import org.jline.terminal.impl.DumbTerminal;
import org.springframework.shell.component.context.ComponentContext;
import oshi.SystemInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Getter
@Slf4j
public class Node extends Thread {

    public static Node INSTANCE;

    private volatile ConnectionState connectionState;

    @Setter
    private String nodeName;
    @Setter
    private String teamName;

    private String serverPath;
    private boolean manageFirewallAutomatically;

    private boolean isAutoUpdateEnabled;
    private String serverStopMessage;
    private String serverRestartMessage;

    private String secret;
    private String nodeId;

    private boolean isUpdating;

    @Getter
    @Setter
    private boolean firstStart = false;

    public Node() {

        INSTANCE = this;

        this.connectionState = (ConfigUtils.hasKey("node.id") && ConfigUtils.hasKey("node.secret")) ? ConnectionState.NOT_CONNECTED : ConnectionState.NOT_JOINED;

        this.nodeName = "daemon";
        this.teamName = "gmc";

        setServerPath("./servers");

        getCachedNodeInformation();
        BackupService.initialiseBackupService();
        NodeUtils.checkInstallation();
    }

    private void getCachedNodeInformation() {

        log.debug("Getting cached information...");

        nodeId = ConfigUtils.get("node.id", "dummy");
        secret = ConfigUtils.get("node.secret", "dummy");

        File cacheFile = new File("./cache.json");

        if (!cacheFile.exists()) {
            log.debug("No cache file found. Skipping...");
            return;
        }

        try {
            CacheModel cacheModel = CommonUtils.getObjectReader().readValue(new File("./cache.json"), CacheModel.class);
            nodeName = cacheModel.getNodeName();
            teamName = cacheModel.getTeamName();
            setServerPath(cacheModel.getServerPath());
            isAutoUpdateEnabled = cacheModel.isAutoUpdateEnabled();
            manageFirewallAutomatically = cacheModel.isManageFirewallAutomatically();

            serverStopMessage = cacheModel.getServerStopMessage();
            serverRestartMessage = cacheModel.getServerRestartMessage();

            log.debug("Got cached information.");

        } catch (IOException e) {
            log.error("An unknown error occurred while getting cached information.", e);
        }
    }

    public void shutdown() {
        if (getConnectionState() == ConnectionState.DELETING) return;
        NodeLogoutPacket logoutPacket = new NodeLogoutPacket();
        logoutPacket.setReason("Terminated by user");
        log.debug("Sending shutdown packet...");
        StompHandler.send("/app/node/logout", logoutPacket);
        log.info("Disconnecting from backend...");
        log.debug("Deleting temporary files...");
        try {
            FileUtils.deleteDirectory(new File(NodeUtils.TMP_PATH));
        } catch (IOException e) {
            log.warn("An error occurred while deleting the temporary directory.");
        }
        log.debug("Caching information...");
        NodeUtils.cacheInformation(this);
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
        log.debug("Start joining a team with Invite Token '{}'...", token);

        Integer realToken = NodeUtils.getValidatedToken(token);

        if (realToken == null) {
            log.error("The entered token does not match the expected pattern. Please enter it as follows: XXX-XXX");
            setConnectionState(ConnectionState.NOT_JOINED);
            return;
        }

        joinTeamWithValidatedToken(realToken);
    }

    private void joinTeamWithValidatedToken(int token) {

        log.debug("Start joining with Invite Token '{}'...", token);

        OkHttpClient client = new OkHttpClient();

        String json = "{\"inviteToken\": \"" + token + "\"}";
        RequestBody body = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(Application.getBackendUrl() + "/node/register")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {

            if (!response.isSuccessful()) {
                log.debug("Received error code {} with message '{}'", response.code(), response.body().string());
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

        firstStart = true;

        setConnectionState(ConnectionState.NOT_CONNECTED);

        connect();
    }

    public void connect() {

        if (getConnectionState() == ConnectionState.RECONNECTING) {
            log.info("Reconnecting to backend...");
            StompHandler.initialiseStomp();
        } else {
            log.info("Connecting to backend...");
            setConnectionState(ConnectionState.CONNECTING);
            if (!StompHandler.initialiseStomp()) {
                setConnectionState(ConnectionState.RECONNECTING);
                ServerUtils.getCachedServerInformation();
            }
        }
    }

    public void updateDaemon() {
        try {
            if (isUpdating) {
                log.error("The daemon is already updating!");
                return;
            }
            isUpdating = true;
            log.debug("Start updating daemon to latest version! Downloading...");

            NodeUtils.downloadLatestDaemonInstaller();

            log.debug("Starting installer and restarting daemon...");

            try {
                String[] commands = {
                        CommonUtils.convertPathSeparator(Path.of(NodeUtils.TMP_PATH + "latest-installer.exe").toAbsolutePath().toString()),
                        "/SILENT",
                        "/SUPPRESSMSGBOXES",
                        "/LOG=" + CommonUtils.convertPathSeparator(Path.of("log/latest-installation.log").toAbsolutePath().toString())
                };
                log.debug("Starting installer with command: {}", String.join(" ", commands));
                new ProcessBuilder(commands).start();
                System.exit(0);
            } catch (IOException e) {
                log.error("An error occurred while starting the installer.", e);
                isUpdating = false;
            }
        } catch (Exception e) {
            log.error("An unknown error occurred while updating the daemon.", e);
            isUpdating = false;
        }

    }

    public void updateSettings(NodeSettings nodeSettings) {
        log.debug("Updating settings...");
        nodeName = nodeSettings.getName();

        if (!CommonUtils.isNullOrEmpty(nodeSettings.getServerPath())) setServerPath(nodeSettings.getServerPath());
        else setServerPath("./servers");

        isAutoUpdateEnabled = nodeSettings.isEnableAutoUpdate();
        serverStopMessage = nodeSettings.getStopMessage();
        serverRestartMessage = nodeSettings.getRestartMessage();
        manageFirewallAutomatically = nodeSettings.isManageFirewallAutomatically();

        NodeUtils.cacheInformation(this);
    }

    public void delete() {

        Node.INSTANCE.setConnectionState(ConnectionState.DELETING);

        log.info("Starting node deletion...");
        log.debug("Stopping all servers...");
        for (GameServer gameServer : GameServer.getAllServers()) {
            gameServer.stop(false).complete();
        }

        log.debug("Cleaning up...");

        try {
            FileUtils.deleteDirectory(new File(NodeUtils.TMP_PATH));
            FileUtils.deleteDirectory(new File(NodeUtils.STEAM_CMD_DIR));
            FileUtils.deleteDirectory(new File("logs"));

            try {
                FileUtils.delete(new File("cache.json"));
            } catch (Exception e) {
                log.debug("Could not delete cache.json directory.", e);
            }

            try {
                FileUtils.delete(new File("backups.json"));
            } catch (Exception e) {
                log.debug("Could not delete backups.json directory.", e);
            }
            ConfigUtils.remove("node.id");
            ConfigUtils.remove("node.secret");
        } catch (IOException e) {
            log.warn("An error occurred while cleaning up.", e);
        }

        log.info("Node deletion complete. Please note that the daemon must be uninstalled manually. Exiting...");

        try {
            Thread.sleep(5 * 1000);
        } catch (InterruptedException ignored) {}

        System.exit(0);
    }

    @Override
    public void run() {
        super.run();
        Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(updateRunnable, 0, 10, TimeUnit.SECONDS);
    }

    long lastUpdate = System.currentTimeMillis();
    private final Runnable updateRunnable = () -> {
        if((System.currentTimeMillis() - lastUpdate) / 1000 > 15) {
            log.warn("Last heartbeat was more than 15 seconds ago. Catching up...");
        }
        lastUpdate = System.currentTimeMillis();
        if (getConnectionState() == ConnectionState.CONNECTED) {

            NodeUtils.cacheInformation(this);

            NodeHeartbeatPacket heartbeatPacket = getNodeHeartbeatPacket();

            StompHandler.send("/app/node/heartbeat", heartbeatPacket);

            BackupService.deleteAllExpiredBackups();
        } else if (getConnectionState() == ConnectionState.RECONNECTING) {
            connect();
        }
    };

    private static NodeHeartbeatPacket getNodeHeartbeatPacket() {
        SystemInfo systemInfo = new SystemInfo();
        NodeHeartbeatPacket heartbeatPacket = new NodeHeartbeatPacket();

        ResourceUsage resourceUsage = new ResourceUsage();
        resourceUsage.setRamBytes((systemInfo.getHardware().getMemory().getTotal() - systemInfo.getHardware().getMemory().getAvailable()) / (1024 * 1024));
        resourceUsage.setCpuPercentage(-1);
        resourceUsage.setCpuPercentage(-1);
        resourceUsage.setDiskBytes(-1);
        heartbeatPacket.setResourceUsage(resourceUsage);


        ArrayList<NodeHeartbeatPacket.GameServerUpdate> gameServerUpdates = new ArrayList<>();
        for (GameServer server : GameServer.getAllServers()) {
            NodeHeartbeatPacket.GameServerUpdate gameServerUpdate = new NodeHeartbeatPacket.GameServerUpdate();
            gameServerUpdate.setState(server.getState());
            gameServerUpdate.setId(server.getServerId());
            gameServerUpdate.setPlayerCount(server.getCurrentOnlinePlayers());
            gameServerUpdate.setResourceUsage(new ResourceUsage());
            gameServerUpdates.add(gameServerUpdate);
        }
        heartbeatPacket.setGameServers(gameServerUpdates);
        return heartbeatPacket;
    }

    public synchronized void setConnectionState(ConnectionState connectionState) {
        log.debug("Connection state changed from {} to {}", this.connectionState, connectionState.name());
        this.connectionState = connectionState;
    }

    public synchronized ConnectionState getConnectionState() {
        return this.connectionState;
    }

    public void setServerPath(String serverPath) {
        this.serverPath = Path.of(serverPath).normalize().toString();
    }
}
