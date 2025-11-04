package de.swiftbyte.gmc.daemon;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import de.swiftbyte.gmc.daemon.migration.MigrationScript;
import de.swiftbyte.gmc.daemon.utils.ConfigUtils;
import de.swiftbyte.gmc.daemon.utils.ConnectionState;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jline.terminal.Terminal;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.shell.command.annotation.CommandScan;
import org.springframework.shell.component.flow.ComponentFlow;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@SpringBootApplication(
        exclude = {
                MongoAutoConfiguration.class,
                MongoDataAutoConfiguration.class
        }
)
@CommandScan
@Slf4j
public class Application {


    private static final int MIGRATION_LEVEL = 0;


    public static String getBackendDomain() {
        return ConfigUtils.get("backend-domain", "api.gamemanager.cloud");
    }

    public static boolean isSecure() {
        return ConfigUtils.get("backend-secure", "true").equals("true");
    }

    public static String getBackendUrl() {
        if (isSecure()) {
            return "https://" + getBackendDomain();
        } else {
            return "http://" + getBackendDomain();
        }
    }

    public static String getWebsocketUrl() {
        if (isSecure()) {
            return "wss://" + getBackendDomain() + "/websocket-nodes";
        } else {
            return "ws://" + getBackendDomain() + "/websocket-nodes";
        }
    }

    private static final HashMap<Integer, MigrationScript> migrationScripts = new HashMap<>();

    @Getter
    private static String version;

    @Getter
    private static Node node;

    @Getter
    private static Terminal terminal;

    @Getter
    private static ComponentFlow.Builder componentFlowBuilder;

    private static ScheduledExecutorService executorService;

    private static final Thread shutdownHook = new Thread(() -> {
        if (node == null) {
            return;
        }
        log.debug("Shutting down...");
        if(executorService != null) {
            executorService.shutdown();
        }
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

        if (List.of(args).contains("-debug")) {
            rootLogger.setLevel(Level.DEBUG);
        } else {
            rootLogger.setLevel(Level.INFO);
        }

        SpringApplication.run(Application.class);
    }

    @EventListener(ApplicationStartedEvent.class)
    public void onReady() {

        ConfigUtils.initialiseConfigSystem();

        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger("de.swiftbyte");

        if (Boolean.parseBoolean(ConfigUtils.get("debug", "false"))) {
            rootLogger.setLevel(Level.DEBUG);
        } else {
            rootLogger.setLevel(Level.INFO);
        }

        log.debug("Daemon ready... Version: {}", version);


        log.debug("Checking whether migration scripts need to be executed...");
        int oldMigrationLevel = ConfigUtils.getInt("migrationLevel", MIGRATION_LEVEL);
        while (oldMigrationLevel < MIGRATION_LEVEL) {
            log.debug("Version mismatch, searching for migration script for level {}", oldMigrationLevel);
            MigrationScript migrationScript = migrationScripts.get(oldMigrationLevel);

            if (migrationScript != null) {
                log.info("Executing migration script for level {}", oldMigrationLevel);
                migrationScript.run();
                ConfigUtils.store("migrationLevel", ++oldMigrationLevel);
            } else {
                log.debug("No migration script found. Assume that no migration is necessary.");
                oldMigrationLevel++;
            }
        }

        ConfigUtils.store("migrationLevel", MIGRATION_LEVEL);


        node = new Node();

        if (node.getConnectionState() == ConnectionState.NOT_JOINED) {
            node.joinTeam();
        } else if (node.getConnectionState() == ConnectionState.NOT_CONNECTED) {
            node.connect();
        } else {
            log.error("Illegal ConnectionState set... Start is aborted!");
            System.exit(1);
        }
    }

    public static ScheduledExecutorService getExecutor() {
        if (executorService == null) {
            executorService = Executors.newScheduledThreadPool(ConfigUtils.getInt("override-thread-pool-size", 32));
        }
        return executorService;
    }

    @Value("${spring.application.version}")
    public void setVersion(String version) {
        Application.version = version;
    }
}
