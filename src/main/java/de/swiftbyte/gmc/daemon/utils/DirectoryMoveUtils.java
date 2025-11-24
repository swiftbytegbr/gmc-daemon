package de.swiftbyte.gmc.daemon.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

@Slf4j
public class DirectoryMoveUtils {

    /**
     * Moves all entries from the source directory into the destination directory, merging when necessary.
     * - If destination does not exist, it will be created.
     * - If an entry already exists at destination, directories are merged and files are overwritten.
     * - The source directory will be removed if empty at the end.
     */
    public static void moveDirectoryContents(Path srcDirPath, Path dstDirPath) throws IOException {
        File srcDir = srcDirPath.toFile();
        File dstDir = dstDirPath.toFile();

        if (!srcDir.exists()) {
            log.info("Source directory '{}' does not exist. Nothing to move.", srcDir.getAbsolutePath());
            return;
        }

        if (!dstDir.exists()) {
            if (!dstDir.mkdirs()) {
                throw new IOException("Failed to create destination directory: " + dstDir.getAbsolutePath());
            }
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
     */
    public static Path moveDirectoryToParent(Path srcPath, Path dstParentPath) throws IOException {
        File src = srcPath.toFile();
        File dstParent = dstParentPath.toFile();

        if (!src.exists() || !src.isDirectory()) {
            throw new IOException("Source directory not found: " + src.getAbsolutePath());
        }
        if (!dstParent.exists() && !dstParent.mkdirs()) {
            throw new IOException("Failed to create destination parent: " + dstParent.getAbsolutePath());
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

