package de.swiftbyte.gmc.daemon.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.junit.jupiter.api.Assertions.*;

class PathUtilsTest {

    @Test
    void isWritableDirectoryCreatesMissingDirectory(@TempDir Path tempDir) {
        Path newDir = tempDir.resolve("newDir");

        assertTrue(PathUtils.isWritableDirectory(newDir));
        assertTrue(Files.isDirectory(newDir));
    }

    @Test
    void isWritableDirectoryRejectsFiles(@TempDir Path tempDir) throws IOException {
        Path filePath = Files.createFile(tempDir.resolve("file.txt"));

        assertFalse(PathUtils.isWritableDirectory(filePath));
        assertTrue(Files.exists(filePath));
    }

    @Test
    void convertPathSeparatorRespectsPlatform() {
        String unixPath = "foo/bar";
        if (IS_OS_WINDOWS) {
            assertEquals("foo\\bar", PathUtils.convertPathSeparator(unixPath));
        } else {
            assertEquals(unixPath, PathUtils.convertPathSeparator(unixPath));
        }
    }

    @Test
    void getAbsolutPathNormalizesInput(@TempDir Path tempDir) {
        Path withTraversal = tempDir.resolve("nested/..");

        assertEquals(tempDir.toAbsolutePath().normalize(), PathUtils.getAbsolutPath(withTraversal));
        assertEquals(tempDir.toAbsolutePath().normalize(), PathUtils.getAbsolutPath(withTraversal.toString()));
    }

    @Test
    void moveDirectoryContentsMovesAndMerges(@TempDir Path tempDir) throws Exception {
        Path srcDir = Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(srcDir.resolve("from.txt"), "src", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Path srcInner = Files.createDirectories(srcDir.resolve("inner"));
        Files.writeString(srcInner.resolve("inner.txt"), "inner", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        Path dstDir = Files.createDirectories(tempDir.resolve("dst"));
        Files.writeString(dstDir.resolve("from.txt"), "old", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Path dstInner = Files.createDirectories(dstDir.resolve("inner"));
        Files.writeString(dstInner.resolve("keep.txt"), "keep", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        PathUtils.moveDirectoryContents(srcDir, dstDir);

        assertEquals("src", Files.readString(dstDir.resolve("from.txt")));
        assertEquals("keep", Files.readString(dstInner.resolve("keep.txt")));
        assertEquals("inner", Files.readString(dstInner.resolve("inner.txt")));
        assertFalse(Files.exists(srcDir));
    }

    @Test
    void moveDirectoryToParentMergesExistingDirectory(@TempDir Path tempDir) throws Exception {
        Path srcDir = Files.createDirectories(tempDir.resolve("sourceDir"));
        Files.writeString(srcDir.resolve("new.txt"), "new", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        Path dstParent = Files.createDirectories(tempDir.resolve("parent"));
        Path existingDst = Files.createDirectories(dstParent.resolve("sourceDir"));
        Files.writeString(existingDst.resolve("existing.txt"), "existing", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        Path resultingPath = PathUtils.moveDirectoryToParent(srcDir, dstParent);

        assertEquals(existingDst.toAbsolutePath().normalize(), resultingPath);
        assertEquals("existing", Files.readString(existingDst.resolve("existing.txt")));
        assertEquals("new", Files.readString(existingDst.resolve("new.txt")));
        assertFalse(Files.exists(srcDir));
    }
}
