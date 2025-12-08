package de.swiftbyte.gmc.daemon.utils;

import lombok.CustomLog;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

@SuppressWarnings("UnusedReturnValue")
@CustomLog
public final class ConfigUtils {

    private static final @NonNull String CONFIG_NAME = "gmc.properties";
    private static final @NonNull File CONFIG_FILE = new File(CONFIG_NAME);

    private static @Nullable Properties properties;

    public static void initialiseConfigSystem() {

        log.debug("Start initialising of config system...");

        if (!CONFIG_FILE.exists()) {
            try {

                log.debug("Creating config file...");

                if (!CONFIG_FILE.createNewFile()) {
                    log.error("The configuration file has already been created, but the existence could not be confirmed by the program. Please delete the file manually.");
                    System.exit(1);
                }

                properties = new Properties();
                properties.load(new FileInputStream(CONFIG_FILE));
                properties.store(new FileWriter(CONFIG_FILE), "Do not make any changes! If the file gets edited, it can lead to malfunctions, unexpected behavior and data loss.");

            } catch (IOException | SecurityException e) {
                log.error("The configuration file could not be created due to an error.", e);
                System.exit(1);
            }
        } else {
            log.debug("Config file found.");
        }

        log.debug("Loading daemon configuration...");

        try {
            properties = new Properties();
            properties.load(new FileInputStream(CONFIG_FILE));
        } catch (IOException e) {
            log.error("An unknown error occurred while loading the configuration file.", e);
        }
    }

    public static boolean store(@NonNull String key, @NonNull String value) {

        if (properties == null) {
            throw new IllegalStateException("The configuration file has not been initialised yet.");
        }

        properties.setProperty(key, value);
        try {
            properties.store(new FileWriter(CONFIG_FILE), "Do not make any changes! If the file gets, edited it can lead to malfunctions, unexpected behavior and data loss.");
        } catch (IOException e) {
            log.error("An unknown error occurred while saving the value.", e);
            return false;
        }
        return true;
    }

    public static boolean store(@NonNull String key, int value) {
        return store(key, String.valueOf(value));
    }

    public static boolean store(@NonNull String key, boolean value) {
        return store(key, String.valueOf(value));
    }

    public static @NonNull String get(@NonNull String key, @NonNull String defaultValue) {

        if (properties == null) {
            throw new IllegalStateException("The configuration file has not been initialised yet.");
        }

        return properties.getProperty(key, defaultValue);
    }

    public static @Nullable String get(@NonNull String key) {

        if (properties == null) {
            throw new IllegalStateException("The configuration file has not been initialised yet.");
        }

        return properties.getProperty(key);
    }

    public static int getInt(@NonNull String key, int defaultValue) {
        return Integer.parseInt(get(key, String.valueOf(defaultValue)));
    }

    public static int getInt(@NonNull String key) {
        return getInt(key, 0);
    }

    public static void remove(@NonNull String key) {

        if (properties == null) {
            throw new IllegalStateException("The configuration file has not been initialised yet.");
        }

        properties.remove(key);
        try {
            properties.store(new FileWriter(CONFIG_FILE), "Do not make any changes! If the file gets, edited it can lead to malfunctions, unexpected behavior and data loss.");
        } catch (IOException e) {
            log.error("An unknown error occurred while saving the value.", e);
        }
    }

    public static boolean hasKey(@NonNull String key) {

        if (properties == null) {
            throw new IllegalStateException("The configuration file has not been initialised yet.");
        }

        return properties.containsKey(key);

    }
}
