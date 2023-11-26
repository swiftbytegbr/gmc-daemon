package de.swiftbyte.gmc;

import de.swiftbyte.gmc.utils.ConfigUtils;
import de.swiftbyte.gmc.utils.ConnectionState;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jline.terminal.Terminal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.shell.command.annotation.CommandScan;
import org.springframework.shell.component.flow.ComponentFlow;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
@CommandScan
@Slf4j
public class Application {

    public final static String
            BACKEND_DOMAIN = "api.gmc.system.astroark.xyz",
            BACKEND_URL = "https://" + BACKEND_DOMAIN,
            BACKEND_WS_URL = "wss://" + BACKEND_DOMAIN + "/websocket-nodes";

    @Getter
    private static String version;

    @Getter
    private static Node node;
    private static Thread watchdog;

    @Getter
    private static Terminal terminal;

    @Getter
    private static ComponentFlow.Builder componentFlowBuilder;

    @Getter
    private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

    private static final Thread shutdownHook = new Thread(() -> {
        if (node == null) return;
        log.debug("Shutting down...");
        node.shutdown();
        log.info("Goodbye!");
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

        log.debug("Daemon ready... Version: " + version);

        ConfigUtils.initialiseConfigSystem();

        node = new Node();
        node.start();

        executor.scheduleAtFixedRate(new Watchdog(), 0, 3, TimeUnit.SECONDS);

        if (node.getConnectionState() == ConnectionState.NOT_JOINED) node.joinTeam();
        else if (node.getConnectionState() == ConnectionState.NOT_CONNECTED) node.connect();
        else {
            log.error("Illegal ConnectionState set... Start is aborted!");
            System.exit(1);
        }
    }

    @Value("${spring.application.version}")
    public void setVersion(String version) {
        Application.version = version;
    }
}
