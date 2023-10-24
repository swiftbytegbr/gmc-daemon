package de.swiftbyte.gmc.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

@Slf4j
public class ConfigUtils {

    private static final String CONFIG_NAME = "gmc.properties";
    private static final File CONFIG_FILE = new File(CONFIG_NAME);

    private static Properties properties;

    public static void initialiseConfigSystem() {

        log.debug("Start initialising of config system...");

        if(!CONFIG_FILE.exists()) {
            try {

                log.debug("Creating config file...");

                if(!CONFIG_FILE.createNewFile()) {
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

    public static boolean store(String key, String value) {

        if(key == null || value == null) {
            log.error("Tried to store null value or key.");
            return false;
        }

        log.debug("Storing key '" + key + "' with value '" + value + "'...");

        properties.setProperty(key, value);
        try {
            properties.store(new FileWriter(CONFIG_FILE), "Do not make any changes! If the file gets, edited it can lead to malfunctions, unexpected behavior and data loss.");
        } catch (IOException e) {
            log.error("An unknown error occurred while saving the value.", e);
        }

        log.debug("Value successfully stored.");
        return true;
    }

    public static boolean store(String key, int value) {
        return store(key, String.valueOf(value));
    }

    public static boolean store(String key, boolean value) {
        return store(key, String.valueOf(value));
    }

    public static String get(String key, String defaultValue) {

        if(key == null) {
            log.error("Tried to get null key.");
            return null;
        }

        log.debug("Getting key '" + key + "'...");

        return properties.getProperty(key, defaultValue);
    }

    public static String get(String key) {
        return get(key, null);
    }

    public static boolean hasKey(String key) {

        if(key == null) {
            log.error("Tried to check null key.");
            return false;
        }

        log.debug("Checking key '" + key + "'...");

        return properties.containsKey(key);

    }
}