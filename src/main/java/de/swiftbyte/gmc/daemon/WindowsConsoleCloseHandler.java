package de.swiftbyte.gmc.daemon;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import lombok.CustomLog;
import org.jspecify.annotations.NonNull;

@CustomLog
final class WindowsConsoleCloseHandler {

    private static final int CTRL_C_EVENT = 0;
    private static final int CTRL_BREAK_EVENT = 1;
    private static final int CTRL_CLOSE_EVENT = 2;
    private static final int CTRL_LOGOFF_EVENT = 5;
    private static final int CTRL_SHUTDOWN_EVENT = 6;

    private static Kernel32.HandlerRoutine handler;

    private WindowsConsoleCloseHandler() {
    }

    static void install() {
        if (!isWindows()) {
            return;
        }

        handler = ctrlType -> {
            switch (ctrlType) {
                case CTRL_C_EVENT, CTRL_BREAK_EVENT, CTRL_CLOSE_EVENT, CTRL_LOGOFF_EVENT, CTRL_SHUTDOWN_EVENT -> {
                    log.info("Console control event received: {}", ctrlType);
                    System.exit(0);
                    return true;
                }
                default -> {
                    return false;
                }
            }
        };

        boolean registered = Kernel32.INSTANCE.SetConsoleCtrlHandler(handler, true);
        if (!registered) {
            log.warn("Failed to register console control handler.");
        }
    }

    private static boolean isWindows() {
        String osName = System.getProperty("os.name", "");
        return osName.toLowerCase().contains("win");
    }

    private interface Kernel32 extends Library {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);

        boolean SetConsoleCtrlHandler(HandlerRoutine handler, boolean add);

        interface HandlerRoutine extends Callback {
            boolean callback(int ctrlType);
        }
    }
}
