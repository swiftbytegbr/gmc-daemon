package de.swiftbyte.gmc.daemon.service;

import de.swiftbyte.gmc.daemon.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

@Slf4j
public class FirewallService {

    public static void allowPort(String serverName, Path executablePath, List<Integer> ports) {

        String portsString = ports.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        String ruleName = String.format("ARK GMC: %s", serverName);

        log.debug("Adding firewall rule for ports {}.", portsString);

        String executable = CommonUtils.convertPathSeparator(executablePath.toAbsolutePath());

        List<String> commandTcp = List.of(
                "powershell",
                "New-NetFirewallRule",
                "-DisplayName", "'"+ruleName+"'",
                "-Direction", "Inbound",
                "-LocalPort", portsString,
                "-Protocol", "TCP",
                "-Action", "Allow",
                "-Program", "'"+executable+"'",
                "-Group", "'GameManagerCloud Server Port'"
        );

        List<String> commandUdp = List.of(
                "powershell",
                "New-NetFirewallRule",
                "-DisplayName", "'"+ruleName+"'",
                "-Direction", "Inbound",
                "-LocalPort", portsString,
                "-Protocol", "UDP",
                "-Action", "Allow",
                "-Program", "'"+executable+"'",
                "-Group", "'GameManagerCloud Server Port'"
        );

        try {
            log.debug("Using tcp command: {}", String.join(" ", commandTcp));
            Process tcpProcess = new ProcessBuilder(commandTcp).redirectErrorStream(true).start();
            Scanner scanner = new Scanner(tcpProcess.getInputStream());
            while (scanner.hasNextLine()) {
                log.debug("Firewall: {}", scanner.nextLine());
            }

            log.debug("Using udp command: {}", String.join(" ", commandUdp));
            Process udpProcess = new ProcessBuilder(commandUdp).start();

            tcpProcess.waitFor();
            udpProcess.waitFor();

            if (tcpProcess.exitValue() != 0 || udpProcess.exitValue() != 0) {
                log.warn("Adding firewall rule returned non-zero exit value. (tcp: {}, udp: {})", tcpProcess.exitValue(), udpProcess.exitValue());
            }

        } catch (IOException | InterruptedException e) {
            log.error("Error while adding firewall rule.", e);
        }

    }

    public static void removePort(String serverName) {

        String ruleName = String.format("ARK GMC: %s", serverName);

        log.debug("Removing firewall rule {}.", ruleName);

        List<String> command = List.of(
                "powershell",
                "Remove-NetFirewallRule",
                "-DisplayName",
                "'"+ruleName+"'"
        );

        try {
            Process process = new ProcessBuilder(command).start();
            process.waitFor();

            if (process.exitValue() != 0) {
                log.warn("Removing firewall rule returned non-zero exit value.");
            }

            Process process2 = new ProcessBuilder(command).start();
            process2.waitFor();

        } catch (IOException | InterruptedException e) {
            log.error("Error while removing firewall rule.", e);
        }
    }
}
