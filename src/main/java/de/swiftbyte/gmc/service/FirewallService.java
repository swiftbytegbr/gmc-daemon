package de.swiftbyte.gmc.service;

import de.swiftbyte.gmc.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
public class FirewallService {

    public static void allowPort(String ruleName, Path executablePath, int[] ports) {

        String portsString = Arrays.stream(ports)
                .mapToObj(Integer::toString)
                .collect(Collectors.joining(","));

        log.debug("Adding firewall rule for ports " + portsString + ".");

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

    public static void removePort(String ruleName) {

        log.debug("Removing firewall rule " + ruleName + ".");

        String command = String.format("powershell Remove-NetFirewallRule -DisplayName \\\"%s\\\"", ruleName);

        try {
            Process process = Runtime.getRuntime().exec(command);
            process.waitFor();

            if (process.exitValue() != 0) {
                log.warn("Removing firewall rule returned non-zero exit value.");
            }

        } catch (IOException | InterruptedException e) {
            log.error("Error while removing firewall rule.", e);
        }
    }
}
