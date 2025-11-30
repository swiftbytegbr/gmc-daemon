package de.swiftbyte.gmc.daemon.utils;

import java.io.File;

public final class PathValidationUtils {

    private PathValidationUtils() {
    }

    // Returns true if `path` is an existing directory (or can be created)
    // and a dummy file can be created and removed inside it (indicating write access).
    public static boolean isWritableDirectory(String path) {
        if (CommonUtils.isNullOrEmpty(path)) {
            return false;
        }
        File dir = new File(path);
        boolean createdDir = false;
        if (!dir.exists()) {
            try {
                createdDir = dir.mkdirs();
            } catch (Exception ignored) {
            }
            if (!createdDir) {
                return false;
            }
        }
        if (!dir.isDirectory()) {
            return false;
        }

        File dummy = new File(dir, ".gmc-path-check-" + System.nanoTime());
        try {
            if (dummy.createNewFile()) {
                return true;
            }
            return dir.canWrite();
        } catch (Exception ignored) {
            return false;
        } finally {
            try {
                if (dummy.exists()) {
                    dummy.delete();
                }
            } catch (Exception ignored) {
            }
            // Intentionally keep the directory if we created it successfully;
            // callers expect the path to exist if validation passed.
        }
    }

    // Returns canonical path if available, else absolute path
    public static String canonicalizeOrAbsolute(String path) {
        try {
            return new File(path).getCanonicalPath();
        } catch (Exception e) {
            return new File(path).getAbsolutePath();
        }
    }
}
