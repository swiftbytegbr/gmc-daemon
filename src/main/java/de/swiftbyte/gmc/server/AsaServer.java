package de.swiftbyte.gmc.server;

import de.swiftbyte.gmc.packet.entity.GameServerState;
import de.swiftbyte.gmc.utils.CommonUtils;
import de.swiftbyte.gmc.utils.NodeUtils;
import lombok.extern.slf4j.Slf4j;
import xyz.astroark.Rcon;
import xyz.astroark.exception.AuthenticationException;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;
import java.util.Scanner;
import java.util.UUID;

@Slf4j
public class AsaServer extends GameServer {

    private static final String STEAM_CMD_ID = "2430930";

    public AsaServer(String id, String friendlyName) {

        super(id, friendlyName);

        this.map = "TheIsland_WP";
        this.gamePort = 29012;
        this.queryPort = 29014;
        this.rconPort = 30004;
        this.rconPassword = "1234";
        this.isAutoRestartEnabled = true;

    }

    @Override
    public void installServer() {
        super.setState(GameServerState.CREATING);
        new Thread(() -> {
            String installCommand = "\"" + CommonUtils.convertPathSeparator(NodeUtils.getSteamCmdPath().toAbsolutePath()) + "\""
                    + " +force_install_dir \"" + CommonUtils.convertPathSeparator(installDir.toAbsolutePath()) + "\""
                    + " +login anonymous +app_update " + STEAM_CMD_ID + " validate +quit";
            log.debug("Starting server installation with command " + installCommand);
            try {
                Process process = Runtime.getRuntime().exec(installCommand.replace("/", "\\"));

                Scanner scanner = new Scanner(process.getInputStream());

                while (scanner.hasNextLine()) {

                    String line = scanner.nextLine();
                    log.debug(line);

                }

                if (process.exitValue() == 0) {
                    log.debug("Server was installed successfully!");
                } else {
                    log.error("Server installation returned error code " + process.exitValue() + ".");
                }

            } catch (IOException e) {
                log.error("An unknown exception occurred while installing the server '" + friendlyName + "'.", e);
            }
        }).start();
    }

    @Override
    public void start() {
        super.setState(GameServerState.INITIALIZING);

        killServerProcess();

        new Thread(() -> {
            writeStartupBatch();
            try {
                log.debug("cmd /c start \"" + CommonUtils.convertPathSeparator(installDir + "/start.bat\""));
                serverProcess = Runtime.getRuntime().exec("cmd /c start /min \"" + "\" \"" + CommonUtils.convertPathSeparator(installDir + "/start.bat\""));
                Scanner scanner = new Scanner(serverProcess.getInputStream());
                while (scanner.hasNextLine()) {
                }

                if (isAutoRestartEnabled && state != GameServerState.OFFLINE) {
                    super.setState(GameServerState.RESTARTING);
                } else {
                    super.setState(GameServerState.OFFLINE);
                }

            } catch (IOException e) {
                log.error("An unknown exception occurred while starting the server '" + friendlyName + "'.", e);
            }
        }).start();
    }

    @Override
    public void stop() {
        super.setState(GameServerState.OFFLINE);
        new Thread(() -> {
            if (sendRconCommand("saveworld") == null) {
                log.debug("No connection to server '" + friendlyName + "'. Killing process...");
                killServerProcess();
            } else {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ignored) {
                }
                sendRconCommand("doexit");
            }
        }).start();
    }

    @Override
    public void update() {

        if (PID == null) PID = CommonUtils.getProcessPID(String.valueOf(installDir));

        switch (state) {
            case INITIALIZING -> {
                if (sendRconCommand("ping") == null) {
                    log.debug("Server '" + friendlyName + "' is still initializing...");
                } else {
                    log.debug("Server '" + friendlyName + "' is ready!");
                    super.setState(GameServerState.ONLINE);
                }
            }
            case ONLINE -> {
                if (sendRconCommand("ping") == null) {

                    log.warn("Server crash detected! Restarting server...");

                    killServerProcess();

                    if (isAutoRestartEnabled) {
                        log.debug("Restarting server '" + friendlyName + "'...");
                        start();
                    } else {
                        super.setState(GameServerState.OFFLINE);
                    }
                }
            }
            case RESTARTING -> {
                log.debug("Server '" + friendlyName + "' is restarting...");
                stop();
                start();
            }
        }
    }

    @Override
    protected void killServerProcess() {

        if (PID == null) return;

        Optional<ProcessHandle> handle = ProcessHandle.of(Long.parseLong(PID));
        if (handle.isPresent()) {
            log.debug("Server process still running... Killing process...");
            handle.get().destroy();
        }
    }

    @Override
    public void writeStartupBatch() {

        if (map == null || map.isEmpty()) {
            log.error("Map is not set for server '" + friendlyName + "'. Falling back to default map.");
            map = "TheIsland_WP";
        }

        if (rconPassword == null || rconPassword.isEmpty()) {
            rconPassword = "gmc-rp-" + UUID.randomUUID();
        }

        String realStartPostArguments = CommonUtils.generateServerArgs(startPostArguments1, startPostArguments2, rconPassword, map, "listen", "Port=" + gamePort, "QueryPort=" + queryPort, "RCONEnabled=True", "RCONPort=" + rconPort);
        String realStartPreArguments = String.join(" ", startPreArguments);

        String changeDirectoryCommand = "cd /d \"" + CommonUtils.convertPathSeparator(installDir) + "\\ShooterGame\\Binaries\\Win64\"";


        String startCommand = "start \"" + friendlyName + "\""
                + (realStartPreArguments.isEmpty() ? "" : " " + realStartPreArguments)
                + " \"" + CommonUtils.convertPathSeparator(installDir + "/ShooterGame/Binaries/Win64/ArkAscendedServer.exe") + "\""
                + " " + realStartPostArguments;
        log.debug("Starting server with command " + startCommand);

        try {
            FileWriter fileWriter = new FileWriter(installDir + "/start.bat");
            PrintWriter printWriter = new PrintWriter(fileWriter);

            printWriter.println(changeDirectoryCommand);
            printWriter.println(startCommand);
            printWriter.println("exit");
            printWriter.close();

        } catch (IOException e) {
            log.error("An unknown exception occurred while writing the startup batch for server '" + friendlyName + "'.", e);
        }
    }

    @Override
    public String sendRconCommand(String command) {
        try {
            Rcon rcon = new Rcon("127.0.0.1", rconPort, rconPassword.getBytes());
            return rcon.command(command);
        } catch (IOException e) {
            log.debug("Server '" + friendlyName + "' is offline.");
            return null;
        } catch (AuthenticationException e) {
            log.error("Rcon authentication failed for server '" + friendlyName + "'.");
            return null;
        }
    }
}
