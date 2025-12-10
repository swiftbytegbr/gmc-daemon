package de.swiftbyte.gmc.daemon.utils;

import lombok.CustomLog;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

@CustomLog
public final class PathUtils {

    private PathUtils() {
    }

    /**
     * Checks whether the given path is a writable directory, creating it if necessary.
     *
     * @param path path to validate
     * @return {@code true} if the directory exists (or was created) and is writable
     */
    public static boolean isWritableDirectory(@NonNull Path path) {
        if (Utils.isNullOrEmpty(path)) {
            return false;
        }

        File dir = path.toFile();
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

        File dummy = Path.of(dir.getPath(), ".gmc-path-check-" + System.nanoTime()).toFile();
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
                    //noinspection ResultOfMethodCallIgnored
                    dummy.delete();
                }
            } catch (Exception ignored) {
            }
            // Intentionally keep the directory if we created it successfully;
            // callers expect the path to exist if validation passed.
        }
    }

    /**
     * Normalizes the provided path to an absolute path.
     *
     * @param path path to normalize
     * @return absolute, normalized path
     */
    public static @NonNull Path getAbsolutPath(@NonNull Path path) {
        return path.toAbsolutePath().normalize();
    }

    /**
     * Normalizes the provided path string to an absolute path.
     *
     * @param path path string to normalize
     * @return absolute, normalized path
     */
    public static @NonNull Path getAbsolutPath(@NonNull String path) {
        return Path.of(path).toAbsolutePath().normalize();
    }

    /**
     * Converts path separators to the platform default.
     *
     * @param path path string to convert
     * @return path with correct separators for the current OS
     */
    public static @NonNull String convertPathSeparator(@NonNull String path) {

        if (SystemUtils.IS_OS_WINDOWS) {

            return path.replace("/", "\\");

        }

        return path;
    }

    /**
     * Converts a path to a platform-specific string, adjusting separators.
     *
     * @param path path to convert
     * @return string with correct separators for the current OS
     */
    public static @NonNull String convertPathSeparator(@NonNull Path path) {
        return convertPathSeparator(path.toString());
    }

    /**
     * Moves all entries from the source directory into the destination directory, merging when necessary.
     * - If destination does not exist, it will be created.
     * - If an entry already exists at destination, directories are merged and files are overwritten.
     * - The source directory will be removed if empty at the end.
     *
     * @param srcDirPath directory to move from
     * @param dstDirPath directory to move into (created if missing)
     * @throws IOException when creation or move fails
     */
    public static void moveDirectoryContents(@NonNull Path srcDirPath, @NonNull Path dstDirPath) throws IOException {
        File srcDir = srcDirPath.toFile();
        File dstDir = dstDirPath.toFile();

        if (!srcDir.exists()) {
            log.info("Source directory '{}' does not exist. Nothing to move.", srcDir.getAbsolutePath());
            return;
        }

        if (!dstDir.exists() && !dstDir.mkdirs()) {
            throw new IOException("Failed to create destination directory: " + dstDir.getAbsolutePath());
        }

        File[] entries = srcDir.listFiles();
        if (entries == null) {
            return;
        }

        for (File entry : entries) {
            File dest = new File(dstDir, entry.getName());
            if (entry.isDirectory()) {
                if (dest.exists()) {
                    // merge
                    FileUtils.copyDirectory(entry, dest);
                    FileUtils.deleteDirectory(entry);
                } else {
                    FileUtils.moveDirectory(entry, dest);
                }
            } else {
                if (dest.exists()) {
                    FileUtils.forceDelete(dest);
                }
                FileUtils.moveFile(entry, dest);
            }
        }

        // Try to remove old empty dir
        try {
            FileUtils.deleteDirectory(srcDir);
        } catch (Exception ignored) {
        }
    }

    /**
     * Moves the directory at srcPath into the destination parent directory, keeping the folder name.
     * For example: src=/old/servers/MyServer, dstParent=/new/servers -> /new/servers/MyServer
     * If destination exists, contents are merged.
     *
     * @param srcPath       directory to move
     * @param dstParentPath destination parent directory
     * @return normalized destination path of the moved directory
     * @throws IOException when moving or creating directories fails
     */
    public static @NonNull Path moveDirectoryToParent(@NonNull Path srcPath, @NonNull Path dstParentPath) throws IOException {
        File src = srcPath.toFile();
        File dstParent = dstParentPath.toFile();

        if (!src.exists() || !src.isDirectory()) {
            throw new IOException("Source directory not found: " + getAbsolutPath(srcPath));
        }
        if (!dstParent.exists() && !dstParent.mkdirs()) {
            throw new IOException("Failed to create destination parent: " + getAbsolutPath(srcPath));
        }

        File dst = new File(dstParent, src.getName());
        if (dst.exists()) {
            // Merge by moving contents
            moveDirectoryContents(src.toPath(), dst.toPath());
        } else {
            FileUtils.moveDirectory(src, dst);
        }
        return dst.toPath().toAbsolutePath().normalize();
    }
}
