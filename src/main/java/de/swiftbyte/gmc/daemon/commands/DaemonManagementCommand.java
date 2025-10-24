package de.swiftbyte.gmc.daemon.commands;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import de.swiftbyte.gmc.daemon.Node;
import de.swiftbyte.gmc.daemon.utils.ConfigUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;

@Command
@Slf4j
public class DaemonManagementCommand {

    @Command(command = "stop daemon", alias = {"exit daemon"}, description = "Stop the daemon.", group = "Daemon Management")
    public void stopCommand() {
        System.exit(0);
    }

    @Command(command = "debug", description = "Toggle debug mode", group = "Daemon Management")
    public String debugCommand() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger("de.swiftbyte");

        if (rootLogger.getLevel() == ch.qos.logback.classic.Level.DEBUG) {
            rootLogger.setLevel(ch.qos.logback.classic.Level.INFO);
            ConfigUtils.store("debug", false);
            return "Debug mode disabled!";
        } else {
            ConfigUtils.store("debug", true);
            rootLogger.setLevel(Level.DEBUG);
            return "Debug mode enabled!";
        }
    }

    @Command(command = "delete", alias = "logout", description = "Remove the connection between the backend and the daemon (Also called 'deletion'). WARNING: This can not be undone!", group = "Daemon Management")
    public void deleteCommand(@Option(description = "Confirm the deletion") String confirm) {

        if (confirm != null && confirm.equals("confirm")) {

            log.debug("User requested node deletion with confirmation flag.");
            Node.INSTANCE.delete();

        } else {

            log.warn("This action will delete the connection between the backend and the daemon. This can not be undone! This will NOT delete your server files, but GMC will not be able to manage them anymore. Please execute 'delete confirm' to confirm.");
        }
    }

}
