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

    /**
     * Creates the properties file if missing and loads it into memory.
     * Exits the application when file creation fails.
     */
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

    /**
     * Stores a string value for a key in the configuration file.
     *
     * @param key   configuration key
     * @param value value to persist
     * @return {@code true} on success
     */
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

    /**
     * Stores an integer value for the given key.
     *
     * @param key   configuration key
     * @param value integer value to persist
     * @return {@code true} on success
     */
    public static boolean store(@NonNull String key, int value) {
        return store(key, String.valueOf(value));
    }

    /**
     * Stores a boolean value for the given key.
     *
     * @param key   configuration key
     * @param value boolean value to persist
     * @return {@code true} on success
     */
    public static boolean store(@NonNull String key, boolean value) {
        return store(key, String.valueOf(value));
    }

    /**
     * Fetches a string from the configuration, returning a default if absent.
     *
     * @param key          configuration key
     * @param defaultValue fallback value when missing
     * @return stored value or {@code defaultValue}
     */
    public static @NonNull String get(@NonNull String key, @NonNull String defaultValue) {

        if (properties == null) {
            throw new IllegalStateException("The configuration file has not been initialised yet.");
        }

        return properties.getProperty(key, defaultValue);
    }

    /**
     * Fetches a string from the configuration or {@code null} when missing.
     *
     * @param key configuration key
     * @return stored value or {@code null}
     */
    public static @Nullable String get(@NonNull String key) {

        if (properties == null) {
            throw new IllegalStateException("The configuration file has not been initialised yet.");
        }

        return properties.getProperty(key);
    }

    /**
     * Fetches an integer from the configuration with a default fallback.
     *
     * @param key          configuration key
     * @param defaultValue fallback value when missing
     * @return stored integer or {@code defaultValue}
     */
    public static int getInt(@NonNull String key, int defaultValue) {
        return Integer.parseInt(get(key, String.valueOf(defaultValue)));
    }

    /**
     * Fetches an integer from the configuration or returns 0 when missing.
     *
     * @param key configuration key
     * @return stored integer or 0
     */
    public static int getInt(@NonNull String key) {
        return getInt(key, 0);
    }

    /**
     * Removes a key from the configuration file.
     *
     * @param key configuration key to remove
     */
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

    /**
     * Checks whether the configuration contains the provided key.
     *
     * @param key key to test
     * @return {@code true} when a value exists
     */
    public static boolean hasKey(@NonNull String key) {

        if (properties == null) {
            throw new IllegalStateException("The configuration file has not been initialised yet.");
        }

        return properties.containsKey(key);

    }
}
