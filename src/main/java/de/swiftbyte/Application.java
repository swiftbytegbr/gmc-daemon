package de.swiftbyte;

import de.swiftbyte.commands.JoinTeamCommand;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.shell.command.annotation.CommandScan;
import org.springframework.shell.command.annotation.EnableCommand;
import org.springframework.shell.component.flow.ComponentFlow;

@SpringBootApplication
@CommandScan
@Slf4j
public class Application {

    private static Node node;

    @Getter
    private static ComponentFlow.Builder componentFlowBuilder;

    public Application(ComponentFlow.Builder componentFlowBuilder) {
        Application.componentFlowBuilder = componentFlowBuilder;
    }

    public static void main(String[] args) {
        node = new Node();
        node.start();
        SpringApplication.run(Application.class, args);
    }

    @EventListener(ApplicationStartedEvent.class)
    public void onReady(ApplicationStartedEvent event) throws Exception {

        log.debug("Daemon ready...");

        if(node.getConnectionState() == ConnectionState.NOT_JOINED) node.joinTeam();
        else if(node.getConnectionState() == ConnectionState.NOT_CONNECTED) node.connect();
        else {
            log.error("Illegal ConnectionState set... Start is aborted!");
            System.exit(1);
        }
    }
}
