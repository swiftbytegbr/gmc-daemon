package de.swiftbyte.gmc.utils;

import de.swiftbyte.gmc.Application;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.shell.component.context.ComponentContext;
import org.springframework.shell.component.flow.ComponentFlow;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class NodeUtils {

    public static final String TMP_PATH = "tmp/",
            STEAM_CMD_DIR = "steamcmd/",
            STEAM_CMD_PATH = STEAM_CMD_DIR + "steamcmd.exe",
            STEAM_CMD_DOWNLOAD_URL = "https://steamcdn-a.akamaihd.net/client/installer/steamcmd.zip";

    public static Path getSteamCmdPath() {
        return Paths.get(STEAM_CMD_PATH);
    }

    public static Integer getValidatedToken(String token) {

        log.debug("Validating token '" + token + "'...");

        String normalizedToken = token.replace("-", "");

        log.debug("Token was normalized to '" + normalizedToken + "'. Checking length...");

        if (normalizedToken.length() != 6) {
            log.debug("Token was not expected size.");
            return null;
        }

        log.debug("Token was expected size. Checking if token is a valid integer...");

        try {

            return Integer.parseInt(normalizedToken);

        } catch (NumberFormatException ignore) {

            log.debug("Convert token to integer failed.");
            return null;

        }
    }

    public static ComponentContext<?> promptForInviteToken() {
        ComponentFlow flow = Application.getComponentFlowBuilder().clone().reset()
                .withStringInput("inviteToken")
                .name("Please enter the Invite Token. You can find the Invite Token in the create node window in the web panel:")
                .and().build();
        return flow.run().getContext();
    }

    public static void checkInstallation() {
        //TODO Check if installation is valid

        if (Files.exists(Paths.get(STEAM_CMD_PATH))) {
            log.debug("SteamCMD installation found.");
        } else {
            log.error("SteamCMD installation not found. Try to install...");
            installSteamCmd();
        }

    }

    private static void installSteamCmd() {
        log.debug("Downloading SteamCMD from " + STEAM_CMD_DOWNLOAD_URL + "...");

        try {
            FileUtils.copyURLToFile(
                    new URL(STEAM_CMD_DOWNLOAD_URL),
                    new File(TMP_PATH + "steamcmd.zip"));

            File tmp = new File(TMP_PATH);
            if (!CommonUtils.unzip(TMP_PATH + "steamcmd.zip", STEAM_CMD_DIR)) {
                FileUtils.deleteDirectory(tmp);
                System.exit(1);
            } else {
                FileUtils.deleteDirectory(tmp);
            }

        } catch (IOException e) {
            log.error("An error occurred while downloading SteamCMD. Please check your internet connection!", e);
            System.exit(1);
        }
    }
}
