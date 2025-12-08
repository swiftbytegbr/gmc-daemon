package de.swiftbyte.gmc.daemon;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import de.swiftbyte.gmc.daemon.migration.MigrateServerInstallDir;
import de.swiftbyte.gmc.daemon.migration.MigrationScript;
import de.swiftbyte.gmc.daemon.utils.ConfigUtils;
import de.swiftbyte.gmc.daemon.utils.ConnectionState;
import lombok.CustomLog;
import lombok.Getter;
import org.jline.terminal.Terminal;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
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
        }
)
@CommandScan
@CustomLog
public class Application {

    private static final int MIGRATION_LEVEL = 1;

    public static @NonNull String getBackendDomain() {
        return ConfigUtils.get("backend-domain", "api.gamemanager.cloud");
    }

    public static boolean isSecure() {
        return ConfigUtils.get("backend-secure", "true").equals("true");
    }

    public static @NonNull String getBackendUrl() {
        if (isSecure()) {
            return "https://" + getBackendDomain();
        } else {
            //noinspection HttpUrlsUsage
            return "http://" + getBackendDomain();
        }
    }

    public static @NonNull String getWebsocketUrl() {
        if (isSecure()) {
            return "wss://" + getBackendDomain() + "/websocket-nodes";
        } else {
            return "ws://" + getBackendDomain() + "/websocket-nodes";
        }
    }

    private static final @NonNull HashMap<@NonNull Integer, @NonNull MigrationScript> migrationScripts = new HashMap<>();

    @Getter
    private static @NonNull String version = "";

    @Getter
    private static @Nullable Node node;

    @Getter
    private static @Nullable Terminal terminal;

    @Getter
    private static ComponentFlow.@Nullable Builder componentFlowBuilder;

    private static @Nullable ScheduledExecutorService executorService;

    private static final @NonNull Thread shutdownHook = new Thread(Application::shutdownCleanly);

    private static void shutdownCleanly() {
        if (node == null) {
            return;
        }
        log.debug("Shutting down...");
        if (executorService != null) {
            executorService.shutdown();
        }
        node.shutdown();
        log.info("Goodbye!");
    }

    public Application(ComponentFlow.@NonNull Builder componentFlowBuilder, @NonNull Terminal terminal) {
        Application.componentFlowBuilder = componentFlowBuilder;
        Application.terminal = terminal;
    }

    static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger("de.swiftbyte");

        if (List.of(args).contains("-debug")) {
            rootLogger.setLevel(Level.DEBUG);
        } else {
            rootLogger.setLevel(Level.INFO);
        }

        migrationScripts.put(0, new MigrateServerInstallDir());

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

    public static @NonNull ScheduledExecutorService getExecutor() {
        if (executorService == null) {
            executorService = Executors.newScheduledThreadPool(ConfigUtils.getInt("override-thread-pool-size", 32));
        }
        return executorService;
    }

    @Value("${spring.application.version}")
    public void setVersion(@NonNull String version) {
        Application.version = version;
    }
}
