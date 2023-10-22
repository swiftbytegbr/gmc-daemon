package de.swiftbyte;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;

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
