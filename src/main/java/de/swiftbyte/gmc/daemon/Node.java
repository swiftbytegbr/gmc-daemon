package de.swiftbyte.gmc.daemon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.swiftbyte.gmc.common.entity.NodeSettings;
import de.swiftbyte.gmc.common.model.NodeTask;
import de.swiftbyte.gmc.common.entity.ResourceUsage;
import de.swiftbyte.gmc.common.packet.from.bidirectional.node.NodeSettingsPacket;
import de.swiftbyte.gmc.common.packet.from.daemon.node.NodeHeartbeatPacket;
import de.swiftbyte.gmc.common.packet.from.daemon.node.NodeLogoutPacket;
import de.swiftbyte.gmc.daemon.cache.CacheModel;
import de.swiftbyte.gmc.daemon.server.GameServer;
import de.swiftbyte.gmc.daemon.service.BackupService;
import de.swiftbyte.gmc.daemon.service.TaskService;
import de.swiftbyte.gmc.daemon.stomp.StompHandler;
import de.swiftbyte.gmc.daemon.tasks.consumers.BackupDirectoryChangeTaskConsumer;
import de.swiftbyte.gmc.daemon.utils.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Getter
@Slf4j
public class Node {

    public static Node INSTANCE;

    private volatile ConnectionState connectionState;

    private ScheduledExecutorService heartbeatExecutor;

    @Setter
    private String nodeName;
    @Setter
    private String teamName;

    private String serverPath;

    // Track current valid default server directory for rollback
    private Path defaultServerDirectory;

    @Setter
    private Path backupPath;
    private boolean manageFirewallAutomatically;

    private boolean isAutoUpdateEnabled;

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
        // Default backups to absolute ./backups until settings provide a path
        this.backupPath = Path.of(PathValidationUtils.canonicalizeOrAbsolute("./backups"));
        // Initialize current valid default dir to canonical ./servers
        this.defaultServerDirectory = Path.of(PathValidationUtils.canonicalizeOrAbsolute("./servers"));

        getCachedNodeInformation();
        BackupService.initialiseBackupService();
        NodeUtils.checkInstallation();

        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        heartbeatExecutor.scheduleWithFixedDelay(updateRunnable, 0, 10, TimeUnit.SECONDS);
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
            if (cacheModel.getDefaultServerDirectory() != null) {
                this.defaultServerDirectory = Path.of(PathValidationUtils.canonicalizeOrAbsolute(cacheModel.getDefaultServerDirectory())).normalize();
            }

            if(cacheModel.getBackupPath() != null) backupPath = Path.of(cacheModel.getBackupPath()).normalize();
            isAutoUpdateEnabled = cacheModel.isAutoUpdateEnabled();
            manageFirewallAutomatically = cacheModel.isManageFirewallAutomatically();

            log.debug("Got cached information.");

        } catch (IOException e) {
            log.error("An unknown error occurred while getting cached information.", e);
        }
    }

    public void shutdown() {
        if (getConnectionState() == ConnectionState.DELETING) {
            return;
        }
        shutdownHeartbeatExecutor();
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

        String defaultServerDirectory = PathValidationUtils.canonicalizeOrAbsolute("./servers");
        ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        ObjectNode jsonNode = mapper.createObjectNode();
        jsonNode.put("inviteToken", String.valueOf(token));
        jsonNode.put("defaultServerDirectory", defaultServerDirectory);
        String json;
        try {
            json = mapper.writeValueAsString(jsonNode);
        } catch (Exception e) {
            log.error("Failed to create registration payload.", e);
            setConnectionState(ConnectionState.NOT_JOINED);
            return;
        }
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
            JsonNode responseJson = objectMapper.readTree(response.body().string());
            nodeId = responseJson.get("nodeId").asText();
            secret = responseJson.get("nodeSecret").asText();

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
            TaskService.initializeTaskService();
        } else {
            log.info("Connecting to backend...");
            setConnectionState(ConnectionState.CONNECTING);
            if (!StompHandler.initialiseStomp()) {
                setConnectionState(ConnectionState.RECONNECTING);
                ServerUtils.getCachedServerInformation();
            }
            TaskService.initializeTaskService();
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

        // Validate/backfill default server directory (sets to absolute ./servers if null/invalid)
        String effectiveDefaultDir = NodeSettingsUtils.validateOrBackfillDefaultServerDirectory(nodeSettings, this.defaultServerDirectory);
        this.defaultServerDirectory = Path.of(PathValidationUtils.canonicalizeOrAbsolute(effectiveDefaultDir));

        // Apply server path from settings (default to ./servers)
        String incomingServerPath = !CommonUtils.isNullOrEmpty(nodeSettings.getServerPath()) ? nodeSettings.getServerPath() : "./servers";
        String newServerPath = Path.of(incomingServerPath).normalize().toString();
        setServerPath(newServerPath);

        // Resolve and validate backup paths
        Path currentBackupPath = this.backupPath;
        String effectiveBackupDir = NodeSettingsUtils.validateOrBackfillServerBackupsDirectory(nodeSettings, currentBackupPath);
        Path newBackupPath = Path.of(effectiveBackupDir).normalize();
        // Always update local cache value from settings resolution (covers cache->backend and backend->cache sync)
        this.backupPath = newBackupPath;

        isAutoUpdateEnabled = nodeSettings.isEnableAutoUpdate();
        // Deprecated: stop/restart messages now come from GMC settings per server
        manageFirewallAutomatically = nodeSettings.isManageFirewallAutomatically();

        // Persist updated backup path to cache so cache gets backfilled from backend when previously null
        NodeUtils.cacheInformation(this);

        // Move backups only when backup directory actually changes
        if (!currentBackupPath.equals(newBackupPath)) {
            log.info("Backup path changed from '{}' to '{}'. Scheduling backups move task...", currentBackupPath, newBackupPath);
            try {
                boolean created = TaskService.createTask(
                        NodeTask.Type.BACKUP_DIRECTORY_CHANGE,
                        new BackupDirectoryChangeTaskConsumer.BackupDirectoryChangeTaskPayload(currentBackupPath, newBackupPath),
                        this.nodeId
                );
                if (!created) {
                    log.warn("Failed to create backups move task. Backups may remain in old directory.");
                }
            } catch (Exception e) {
                log.error("Error while scheduling backups move task.", e);
            }
        }
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
        } catch (InterruptedException ignored) {
        }

        System.exit(0);
    }

    long lastUpdate = System.currentTimeMillis();
    private final Runnable updateRunnable = () -> {
        try {
            if ((System.currentTimeMillis() - lastUpdate) / 1000 > 15) {
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
        } catch (Exception e) {
            log.error("Unhandled exception in heartbeat executor.", e);
        }
    };

    private void shutdownHeartbeatExecutor() {
        if (heartbeatExecutor == null) {
            return;
        }
        heartbeatExecutor.shutdown();
        heartbeatExecutor = null;
    }

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
        log.debug("Connection state changed from {} to {}", this.connectionState, connectionState);
        this.connectionState = connectionState;
    }

    public synchronized ConnectionState getConnectionState() {
        return this.connectionState;
    }

    public void setServerPath(String serverPath) {
        this.serverPath = Path.of(serverPath).normalize().toString();
    }

    public String getBackupPath() {
        return backupPath != null
                ? backupPath.normalize().toString()
                : PathValidationUtils.canonicalizeOrAbsolute("./backups");
    }

}
