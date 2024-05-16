package de.swiftbyte.gmc.service;

import de.swiftbyte.gmc.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class FirewallService {

    public static void allowPort(String serverName, Path executablePath, List<Integer> ports) {

        String portsString = ports.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        String ruleName = String.format("ARK GMC: %s", serverName);

        log.debug("Adding firewall rule for ports {}.", portsString);

        String commandTcp = String.format("powershell New-NetFirewallRule -DisplayName \\\"%s\\\" -Direction Inbound -LocalPort %s -Protocol TCP -Action Allow -Program \\\"%s\\\" -Group \\\"GameManagerCloud Server Port\\\"", ruleName, portsString, CommonUtils.convertPathSeparator(executablePath.toAbsolutePath()));
        String commandUdp = String.format("powershell New-NetFirewallRule -DisplayName \\\"%s\\\" -Direction Inbound -LocalPort %s -Protocol UDP -Action Allow -Program \\\"%s\\\" -Group \\\"GameManagerCloud Server Port\\\"", ruleName, portsString, CommonUtils.convertPathSeparator(executablePath.toAbsolutePath()));

        try {
            Process tcpProcess = Runtime.getRuntime().exec(commandTcp);
            Process udpProcess = Runtime.getRuntime().exec(commandUdp);

            tcpProcess.waitFor();
            udpProcess.waitFor();

            if (tcpProcess.exitValue() != 0 || udpProcess.exitValue() != 0) {
                log.warn("Adding firewall rule returned non-zero exit value.");
            }

        } catch (IOException | InterruptedException e) {
            log.error("Error while adding firewall rule.", e);
        }

    }

    public static void removePort(String serverName) {

        String ruleName = String.format("ARK GMC: %s", serverName);

        log.debug("Removing firewall rule {}.", ruleName);

        String command = String.format("powershell Remove-NetFirewallRule -DisplayName \\\"%s\\\"", ruleName);

        try {
            Process process = Runtime.getRuntime().exec(command);
            process.waitFor();

            if (process.exitValue() != 0) {
                log.warn("Removing firewall rule returned non-zero exit value.");
            }

            Process process2 = Runtime.getRuntime().exec(command);
            process2.waitFor();

        } catch (IOException | InterruptedException e) {
            log.error("Error while removing firewall rule.", e);
        }
    }
}
