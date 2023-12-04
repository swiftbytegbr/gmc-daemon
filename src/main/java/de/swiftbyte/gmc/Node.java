package de.swiftbyte.gmc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.swiftbyte.gmc.cache.CacheModel;
import de.swiftbyte.gmc.packet.entity.NodeSettings;
import de.swiftbyte.gmc.packet.entity.ResourceUsage;
import de.swiftbyte.gmc.packet.node.NodeHeartbeatPacket;
import de.swiftbyte.gmc.packet.node.NodeLogoutPacket;
import de.swiftbyte.gmc.stomp.StompHandler;
import de.swiftbyte.gmc.utils.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.io.FileUtils;
import org.jline.terminal.impl.DumbTerminal;
import org.springframework.shell.component.context.ComponentContext;
import oshi.SystemInfo;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
    @Setter
    private String serverPath;

    private NodeSettings.AutoBackup autoBackup;

    private String secret;
    private String nodeId;

    public Node() {

        INSTANCE = this;

        this.connectionState = (ConfigUtils.hasKey("node.id") && ConfigUtils.hasKey("node.secret")) ? ConnectionState.NOT_CONNECTED : ConnectionState.NOT_JOINED;

        this.nodeName = "daemon";
        this.teamName = "gmc";

        serverPath = "servers";

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
            serverPath = cacheModel.getServerPath();

            if (cacheModel.getAutoBackup() != null) autoBackup = cacheModel.getAutoBackup();
            else autoBackup = new NodeSettings.AutoBackup();

            log.debug("Got cached information.");

        } catch (IOException e) {
            log.error("An unknown error occurred while getting cached information.", e);
        }
    }

    public void shutdown() {
        NodeLogoutPacket logoutPacket = new NodeLogoutPacket();
        logoutPacket.setReason("Terminated by user");
        log.info("Sending shutdown packet...");
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
            setConnectionState(ConnectionState.CONNECTION_FAILED);
            ServerUtils.getCachedServerInformation();
        }
    }

    public void updateSettings(NodeSettings nodeSettings) {
        log.debug("Updating settings...");
        nodeName = nodeSettings.getName();

        serverPath = nodeSettings.getServerPath();

        if (nodeSettings.getAutoBackup() != null) autoBackup = nodeSettings.getAutoBackup();
        else autoBackup = new NodeSettings.AutoBackup();

        NodeUtils.cacheInformation(this);
        BackupService.updateAutoBackupSettings();
    }

    @Override
    public void run() {
        super.run();
        Application.getExecutor().scheduleAtFixedRate(updateRunnable, 0, 10, TimeUnit.SECONDS);
    }

    private final Runnable updateRunnable = () -> {

        if (connectionState == ConnectionState.CONNECTED) {

            NodeUtils.cacheInformation(this);

            SystemInfo systemInfo = new SystemInfo();
            NodeHeartbeatPacket heartbeatPacket = new NodeHeartbeatPacket();

            ResourceUsage resourceUsage = new ResourceUsage();
            resourceUsage.setRamBytes((systemInfo.getHardware().getMemory().getTotal() - systemInfo.getHardware().getMemory().getAvailable()) / (1024 * 1024));
            resourceUsage.setCpuPercentage(-1);
            resourceUsage.setCpuPercentage(-1);
            resourceUsage.setDiskBytes(-1);
            heartbeatPacket.setResourceUsage(resourceUsage);

            heartbeatPacket.setGameServers(new ArrayList<>());

            StompHandler.send("/app/node/heartbeat", heartbeatPacket);
        } else if (connectionState == ConnectionState.CONNECTION_FAILED) {
            log.info("Reconnecting to backend...");
            connect();
        }
    };

    public void setConnectionState(ConnectionState connectionState) {
        log.debug("Connection state changed from " + this.connectionState + " to " + connectionState.name());
        this.connectionState = connectionState;
    }
}
