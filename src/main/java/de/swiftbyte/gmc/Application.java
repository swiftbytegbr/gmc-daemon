package de.swiftbyte.gmc;

import de.swiftbyte.gmc.utils.ConfigUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jline.terminal.Terminal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.shell.Shell;
import org.springframework.shell.ShellApplicationRunner;
import org.springframework.shell.command.annotation.CommandScan;
import org.springframework.shell.component.flow.ComponentFlow;

@SpringBootApplication
@CommandScan
@Slf4j
public class Application {

    private static Node node;

    @Getter
    private static Terminal terminal;

    @Getter
    private static ComponentFlow.Builder componentFlowBuilder;

    private static final Thread shutdownHook = new Thread(() ->{
        //TODO add shutdown logic
    });

    public Application(ComponentFlow.Builder componentFlowBuilder, Terminal terminal) {
        Application.componentFlowBuilder = componentFlowBuilder;
        Application.terminal = terminal;
    }

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        SpringApplication.run(Application.class);
    }

    @EventListener(ApplicationStartedEvent.class)
    public void onReady() {

        log.debug("Daemon ready...");

        ConfigUtils.initialiseConfigSystem();

        node = new Node();
        node.start();

        if (node.getConnectionState() == ConnectionState.NOT_JOINED) node.joinTeam();
        else if (node.getConnectionState() == ConnectionState.NOT_CONNECTED) node.connect();
        else {
            log.error("Illegal ConnectionState set... Start is aborted!");
            System.exit(1);
        }
    }
}
