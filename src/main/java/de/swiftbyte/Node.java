package de.swiftbyte;

import de.swiftbyte.utils.NodeUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.shell.component.context.ComponentContext;
import org.springframework.shell.component.flow.ComponentFlow;
import org.springframework.shell.component.flow.ResultMode;
import org.springframework.shell.standard.commands.Help;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;

@Getter
@Slf4j
public class Node extends Thread {

    public static Node INSTANCE;

    private ConnectionState connectionState;
    private String nodeName;
    private String teamName;

    public Node() {

        INSTANCE = this;

        //TODO Check if node has already joined
        this.connectionState = ConnectionState.NOT_JOINED;

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
            log.error("The prompt to enter the Invite Token could not be created due to an error. Please try to invite the node with the command 'join <token>'.");
            connectionState = ConnectionState.NOT_JOINED;
        }
    }

    public void joinTeam(String token) {
        connectionState = ConnectionState.JOINING;
        log.debug("Start joining a team with Invite Token '" + token + "'...");

        Integer realToken = NodeUtils.getValidatedToken(token);

        if(realToken == null) {
            log.error("The entered token does not match the expected pattern. Please enter it as follows: XXX-XXX");
            connectionState = ConnectionState.NOT_JOINED;
            return;
        }

        joinTeamWithValidatedToken(realToken);
    }

    private void joinTeamWithValidatedToken(int token) {
        log.debug("Start joining with Invite Token '"+ token + "'...");
        //Todo verify invite token and obtain secret and nodeId from backend
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
