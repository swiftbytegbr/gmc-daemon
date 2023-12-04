package de.swiftbyte.gmc.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.swiftbyte.gmc.cache.CacheModel;
import de.swiftbyte.gmc.packet.entity.NodeData;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SystemUtils;
import org.zeroturnaround.zip.ZipUtil;
import oshi.SystemInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Slf4j
public class CommonUtils {

    public static boolean isNullOrEmpty(String string) {
        return string == null || string.isEmpty();
    }

    public static String convertPathSeparator(String path) {

        if (SystemUtils.IS_OS_WINDOWS) {

            return path.replace("/", "\\");

        }

        return path;
    }

    public static String convertPathSeparator(Path path) {
        return convertPathSeparator(path.toString());
    }

    public static String getProcessPID(String command) {
        Optional<ProcessHandle> processHandle = ProcessHandle.allProcesses()
                .filter(ph -> ph.info().command().isPresent() && ph.info().command().get().contains(command))
                .findFirst();

        return processHandle.map(handle -> String.valueOf(handle.pid())).orElse(null);
    }

    public static List<String> getSystemIpAddresses() {
        List<String> ipAddresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    if (inetAddress instanceof Inet6Address) continue;
                    ipAddresses.add(inetAddress.getHostAddress());
                }
            }
        } catch (SocketException e) {
            log.warn("Failed to fetch IP addresses.", e);
        }
        return ipAddresses;
    }

    public static HashMap<String, NodeData.Storage> getSystemStorages() {
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

    public static NodeData.Cpu getSystemCpu() {
        NodeData.Cpu cpu = new NodeData.Cpu();
        SystemInfo systemInfo = new SystemInfo();
        cpu.setName(systemInfo.getHardware().getProcessor().getProcessorIdentifier().getName());
        cpu.setCores(systemInfo.getHardware().getProcessor().getPhysicalProcessorCount());
        cpu.setThreads(systemInfo.getHardware().getProcessor().getLogicalProcessorCount());
        cpu.setFrequency(systemInfo.getHardware().getProcessor().getMaxFreq());
        return cpu;
    }

    public static ObjectReader getObjectReader() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModules(new JavaTimeModule());
        return mapper.reader();
    }
}
