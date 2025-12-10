package de.swiftbyte.gmc.daemon.utils;

import de.swiftbyte.gmc.common.entity.NodeData;
import lombok.CustomLog;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import oshi.SystemInfo;

import java.io.File;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@CustomLog
public final class SystemUtils {

    private SystemUtils() {
    }

    /**
     * Finds the process ID of the first process whose command contains the provided substring.
     *
     * @param command substring to search for in running process commands
     * @return PID as string or {@code null} when not found
     */
    public static @Nullable String getProcessPID(@NonNull String command) {
        Optional<ProcessHandle> processHandle = ProcessHandle.allProcesses()
                .filter(ph -> ph.info().command().isPresent() && ph.info().command().get().contains(command))
                .findFirst();

        return processHandle.map(handle -> String.valueOf(handle.pid())).orElse(null);
    }

    /**
     * Collects all non-IPv6 addresses assigned to the local machine.
     *
     * @return list of IPv4 addresses as strings
     */
    public static @NonNull List<@NonNull String> getSystemIpAddresses() {

        List<String> ipAddresses = new ArrayList<>();

        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();

            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();

                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();

                    if (inetAddress instanceof Inet6Address) {
                        continue;
                    }

                    ipAddresses.add(inetAddress.getHostAddress());
                }
            }
        } catch (SocketException e) {
            log.warn("Failed to fetch IP addresses.", e);
        }
        return ipAddresses;
    }

    /**
     * Returns a map of system storage devices keyed by root path.
     *
     * @return map of storages containing usage data
     */
    public static @NonNull HashMap<@NonNull String, NodeData.@NonNull Storage> getSystemStorages() {

        HashMap<String, NodeData.Storage> storages = new HashMap<>();

        File[] roots = File.listRoots();
        for (File root : roots) {
            NodeData.Storage storage = new NodeData.Storage();
            storage.setName(root.getAbsolutePath());
            storage.setTotalBytes(root.getFreeSpace());
            storage.setUsedBytes(root.getTotalSpace() - root.getFreeSpace());
            storages.put(root.getAbsolutePath(), storage);
        }
        return storages;
    }

    /**
     * Collects CPU metadata for the current host.
     *
     * @return CPU information filled into {@link NodeData.Cpu}
     */
    public static NodeData.@NonNull Cpu getSystemCpu() {
        SystemInfo systemInfo = new SystemInfo();
        NodeData.Cpu cpu = new NodeData.Cpu();
        cpu.setName(systemInfo.getHardware().getProcessor().getProcessorIdentifier().getName());
        cpu.setCores(systemInfo.getHardware().getProcessor().getPhysicalProcessorCount());
        cpu.setThreads(systemInfo.getHardware().getProcessor().getLogicalProcessorCount());
        cpu.setFrequency(systemInfo.getHardware().getProcessor().getMaxFreq());
        return cpu;
    }

    /**
     * Attempts to terminate a process by PID.
     *
     * @param PID process id as string
     */
    public static void killSystemProcess(@NonNull String PID) {
        Optional<ProcessHandle> handle = ProcessHandle.of(Long.parseLong(PID));
        if (handle.isPresent()) {
            log.debug("Server process running... Killing process...");
            handle.get().destroy();
        }
    }
}
