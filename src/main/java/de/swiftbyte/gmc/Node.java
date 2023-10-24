package de.swiftbyte.gmc;

import de.swiftbyte.gmc.utils.ConfigUtils;
import de.swiftbyte.gmc.utils.NodeUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jline.terminal.impl.DumbTerminal;
import org.springframework.shell.component.context.ComponentContext;

import java.util.NoSuchElementException;

@Getter
@Slf4j
public class Node extends Thread {

    public static Node INSTANCE;

    private ConnectionState connectionState;
    private String nodeName;
    private String teamName;

    private String secret;
    private String nodeId;

    public Node() {

        INSTANCE = this;

        //TODO Check if node has already joined

        this.connectionState = (ConfigUtils.hasKey("node.id") && ConfigUtils.hasKey("node.secret")) ? ConnectionState.NOT_CONNECTED : ConnectionState.NOT_JOINED;

        this.nodeName = "daemon";
        this.teamName = "gmc";

        getCachedInformation();
    }

    private void getCachedInformation() {

        //TODO Check if cache exists and overwrite default values

    }

    public void joinTeam() {
        log.debug("Start joining a team...");
        connectionState = ConnectionState.JOINING;

        ComponentContext<?> context = NodeUtils.promptForInviteToken();

        try {

            Integer token = NodeUtils.getValidatedToken(context.get("inviteToken"));

            while (token == null) {
                log.error("The entered token does not match the expected pattern. Please enter it as follows: XXX-XXX");
                token = NodeUtils.getValidatedToken(NodeUtils.promptForInviteToken().get("inviteToken"));
            }

            joinTeamWithValidatedToken(token);

        } catch (NoSuchElementException e) {

            connectionState = ConnectionState.NOT_JOINED;

            if(Application.getTerminal() instanceof DumbTerminal) {
                log.error("The prompt to enter the Invite Token could not be created due to an error. Please try to invite the node with the command 'join <token>'.");
            } else {
                log.debug("An empty entry was made. Restart the join process.");
                joinTeam();
            }
        }
    }

    public void joinTeam(String token) {
        connectionState = ConnectionState.JOINING;
        log.debug("Start joining a team with Invite Token '" + token + "'...");

        Integer realToken = NodeUtils.getValidatedToken(token);

        if (realToken == null) {
            log.error("The entered token does not match the expected pattern. Please enter it as follows: XXX-XXX");
            connectionState = ConnectionState.NOT_JOINED;
            return;
        }

        joinTeamWithValidatedToken(realToken);
    }

    private void joinTeamWithValidatedToken(int token) {

        log.debug("Start joining with Invite Token '" + token + "'...");
        //Todo verify invite token and obtain secret and nodeId from backend

        secret = "secret";
        nodeId = "id";

        ConfigUtils.store("node.id", nodeId);
        ConfigUtils.store("node.secret", secret);

        //TODO Getting node information?

        connect();
    }

    public void connect() {
        log.debug("Start connection to backend...");
        connectionState = ConnectionState.CONNECTING;
    }

    @Override
    public void run() {
        super.run();
    }
}
