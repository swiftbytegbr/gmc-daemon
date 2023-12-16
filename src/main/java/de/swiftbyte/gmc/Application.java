package de.swiftbyte.gmc;

import ch.qos.logback.classic.LoggerContext;
import de.swiftbyte.gmc.utils.ConfigUtils;
import de.swiftbyte.gmc.utils.ConnectionState;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jline.terminal.Terminal;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.shell.command.annotation.CommandScan;
import org.springframework.shell.component.flow.ComponentFlow;
import ch.qos.logback.classic.Level;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@SpringBootApplication
@CommandScan
@Slf4j
public class Application {

    public static String getBackendDomain() {
        return ConfigUtils.get("backend-domain", "api.gamemanager.cloud");
    }
    public static boolean isSecure() {
        return ConfigUtils.get("backend-secure", "true").equals("true");
    };
    public static String getBackendUrl() {
        if(isSecure()) return "https://" + getBackendDomain();
        else return "http://" + getBackendDomain();
    }
    public static String getWebsocketUrl() {
        if(isSecure()) return "wss://"+ getBackendDomain() + "/websocket-nodes";
        else return "ws://" + getBackendDomain() + "/websocket-nodes";
    }

    @Getter
    private static String version;

    @Getter
    private static Node node;

    @Getter
    private static Terminal terminal;

    @Getter
    private static ComponentFlow.Builder componentFlowBuilder;

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

        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger("de.swiftbyte");

        if(List.of(args).contains("-debug")) rootLogger.setLevel(Level.DEBUG);
        else rootLogger.setLevel(Level.INFO);

        SpringApplication.run(Application.class);
    }

    @EventListener(ApplicationStartedEvent.class)
    public void onReady() {

        ConfigUtils.initialiseConfigSystem();

        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger("de.swiftbyte");

        if(Boolean.parseBoolean(ConfigUtils.get("debug", "false"))) rootLogger.setLevel(Level.DEBUG);
        else rootLogger.setLevel(Level.INFO);

        log.debug("Daemon ready... Version: " + version);

        node = new Node();
        node.start();

        if (node.getConnectionState() == ConnectionState.NOT_JOINED) node.joinTeam();
        else if (node.getConnectionState() == ConnectionState.NOT_CONNECTED) node.connect();
        else {
            log.error("Illegal ConnectionState set... Start is aborted!");
            System.exit(1);
        }
    }

    public static ScheduledExecutorService getExecutor() {
        return Executors.newScheduledThreadPool(1);
    }

    @Value("${spring.application.version}")
    public void setVersion(String version) {
        Application.version = version;
    }
}
