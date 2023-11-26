package de.swiftbyte.gmc.commands;

import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.command.annotation.Command;

@Command
@Slf4j
public class DaemonStopCommand {

    @Command(command = "stop", alias = {"exit", "doexit"}, description = "Stop the daemon.", group = "Daemon Management")
    public void stopCommand() {
        System.exit(0);
    }

}
