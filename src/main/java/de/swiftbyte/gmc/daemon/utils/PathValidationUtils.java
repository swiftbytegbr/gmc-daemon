package de.swiftbyte.gmc.daemon.utils;

import java.io.File;

public final class PathValidationUtils {

    private PathValidationUtils() {}

    // Returns true if `path` is an existing directory and a dummy file can be
    // created and removed inside it (indicating write access and basic validity).
    public static boolean isWritableDirectory(String path) {
        if (CommonUtils.isNullOrEmpty(path)) return false;
        File dir = new File(path);
        File dummy = new File(dir, ".gmc-path-check-" + System.nanoTime());
        boolean ok = false;
        try {
            if (dir.isDirectory() && dir.canWrite() && dummy.createNewFile()) ok = true;
        } catch (Exception ignored) {
        } finally {
            try { if (dummy.exists()) dummy.delete(); } catch (Exception ignored) {}
        }
        return ok;
    }
}

