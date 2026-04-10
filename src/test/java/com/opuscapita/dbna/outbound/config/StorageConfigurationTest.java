package com.opuscapita.dbna.outbound.config;

import com.opuscapita.dbna.common.storage.Storage;
import com.opuscapita.dbna.common.storage.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StorageConfiguration
 */
@DisplayName("StorageConfiguration Unit Tests")
class StorageConfigurationTest {

    @TempDir
    Path tempDir;

    private Storage storage;

    @BeforeEach
    void setUp() {
        StorageConfiguration storageConfiguration = new StorageConfiguration();
        ReflectionTestUtils.setField(storageConfiguration, "basePath", tempDir.toString());
        storage = storageConfiguration.storage();
    }

    @Test
    @DisplayName("Should create storage with configured base path")
    void testStorageCreation() {
        // Assert
        assertNotNull(storage);
    }

    @Test
    @DisplayName("Should store and retrieve file")
    void testPutAndGet() throws Exception {
        // Arrange
        String filename = "test-file.xml";
        String content = "<?xml version=\"1.0\"?><test>content</test>";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        // Act - Store file
        String result = storage.put(inputStream, "test-", ".xml");
        
        // Assert - File was stored
        assertNotNull(result);
        assertTrue(result.startsWith("test-"));
        assertTrue(result.endsWith(".xml"));

        // Act - Retrieve file
        try (InputStream retrieved = storage.get(result)) {
            assertNotNull(retrieved);
            String retrievedContent = new String(retrieved.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(content, retrievedContent);
        }
    }

    @Test
    @DisplayName("Should check if file exists")
    void testExists() throws StorageException {
        // Arrange
        String content = "test content";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        // Act
        String filename = storage.put(inputStream, "exists-", ".txt");
        
        // Assert
        assertTrue(storage.exists(filename));
        assertFalse(storage.exists("non-existent-file.txt"));
    }

    @Test
    @DisplayName("Should return file size")
    void testSize() throws StorageException {
        // Arrange
        String content = "test content with specific length";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        String filename = storage.put(inputStream, "size-", ".txt");

        // Act
        Long size = storage.size(filename);

        // Assert
        assertNotNull(size);
        assertEquals(content.getBytes(StandardCharsets.UTF_8).length, size);
    }

    @Test
    @DisplayName("Should remove file")
    void testRemove() throws StorageException {
        // Arrange
        String content = "content to be removed";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        String filename = storage.put(inputStream, "remove-", ".txt");

        // Act
        assertTrue(storage.exists(filename));
        storage.remove(filename);

        // Assert
        assertFalse(storage.exists(filename));
    }

    @Test
    @DisplayName("Should move file from source to target")
    void testMove() throws StorageException {
        // Arrange
        String content = "movable content";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        String sourceFilename = storage.put(inputStream, "source-", ".txt");
        String targetFilename = "moved-file.txt";

        // Act
        String result = storage.move(sourceFilename, targetFilename);

        // Assert
        assertEquals(targetFilename, result);
        assertFalse(storage.exists(sourceFilename));
        assertTrue(storage.exists(targetFilename));
    }

    @Test
    @DisplayName("Should update existing file")
    void testUpdate() throws Exception {
        // Arrange
        String originalContent = "original content";
        String updatedContent = "updated content";
        
        InputStream originalStream = new ByteArrayInputStream(originalContent.getBytes(StandardCharsets.UTF_8));
        String filename = storage.put(originalStream, "update-", ".txt");

        // Act
        InputStream updateStream = new ByteArrayInputStream(updatedContent.getBytes(StandardCharsets.UTF_8));
        storage.update(updateStream, filename);

        // Assert
        try (InputStream retrieved = storage.get(filename)) {
            String retrievedContent = new String(retrieved.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(updatedContent, retrievedContent);
        }
    }

    @Test
    @DisplayName("Should list files in directory")
    void testList() throws StorageException {
        // Arrange
        storage.put(new ByteArrayInputStream("content1".getBytes()), "file1-", ".txt");
        storage.put(new ByteArrayInputStream("content2".getBytes()), "file2-", ".txt");
        storage.put(new ByteArrayInputStream("content3".getBytes()), "file3-", ".txt");

        // Act
        List<String> files = storage.list(tempDir.toString());

        // Assert
        assertNotNull(files);
        assertTrue(files.size() >= 3);
    }

    @Test
    @DisplayName("Should return empty list for non-existent directory")
    void testListNonExistentDirectory() throws Exception {
        // Act
        List<String> files = storage.list("/non/existent/directory");

        // Assert
        assertNotNull(files);
        assertTrue(files.isEmpty());
    }

    @Test
    @DisplayName("Should return null when getting non-existent file")
    void testGetNonExistentFile() throws StorageException {
        // Act
        InputStream result = storage.get("non-existent-file.xml");

        // Assert
        assertNull(result);
    }

    @Test
    @DisplayName("Should return 0 for size of non-existent file")
    void testSizeNonExistentFile() throws StorageException {
        // Act
        Long size = storage.size("non-existent-file.xml");

        // Assert
        assertEquals(0L, size);
    }

    @Test
    @DisplayName("Should handle absolute paths")
    void testAbsolutePath() throws Exception {
        // Arrange
        Path absolutePath = tempDir.resolve("absolute-test.xml");
        String content = "absolute path content";
        Files.writeString(absolutePath, content);

        // Act
        try (InputStream retrieved = storage.get(absolutePath.toString())) {
            // Assert
            assertNotNull(retrieved);
            String retrievedContent = new String(retrieved.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(content, retrievedContent);
        }
    }

    @Test
    @DisplayName("Should handle relative paths")
    void testRelativePath() throws StorageException {
        // Arrange
        String content = "relative path content";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        // Act
        String filename = storage.put(inputStream, "relative-", ".txt");

        // Assert
        assertTrue(storage.exists(filename));
        assertNotNull(storage.get(filename));
    }

    @Test
    @DisplayName("Should handle move with non-existent source")
    void testMoveNonExistentSource() throws StorageException {
        // Act
        String result = storage.move("non-existent-source.txt", "target.txt");

        // Assert
        assertNull(result);
    }

    @Test
    @DisplayName("Should handle update on non-existent file")
    void testUpdateNonExistentFile() {
        // Arrange
        InputStream updateStream = new ByteArrayInputStream("content".getBytes());

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> storage.update(updateStream, "non-existent.txt"));
    }

    @Test
    @DisplayName("Should create parent directories when storing file")
    void testCreateParentDirectories() throws Exception {
        // Arrange
        String content = "content with subdirectory";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        String filenameWithSubdir = "subdir/nested/file.txt";

        // Act
        String result = storage.put(inputStream, "subdir/nested/prefix-", ".txt");

        // Assert
        assertNotNull(result);
        assertTrue(storage.exists(result));
    }

    @Test
    @DisplayName("Should generate unique filenames with timestamp")
    void testUniqueFilenameGeneration() throws Exception {
        // Arrange
        InputStream stream1 = new ByteArrayInputStream("content1".getBytes());
        InputStream stream2 = new ByteArrayInputStream("content2".getBytes());

        // Act
        String filename1 = storage.put(stream1, "unique-", ".txt");
        Thread.sleep(10); // Ensure different timestamp
        String filename2 = storage.put(stream2, "unique-", ".txt");

        // Assert
        assertNotEquals(filename1, filename2);
    }
}



