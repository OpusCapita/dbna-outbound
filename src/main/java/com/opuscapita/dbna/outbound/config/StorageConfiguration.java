package com.opuscapita.dbna.outbound.config;

import com.opuscapita.dbna.common.storage.Storage;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration for Storage bean
 * Provides file system based storage implementation
 */
@Configuration
@Getter
public class StorageConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(StorageConfiguration.class);

    @Value("${storage.base-path:${java.io.tmpdir}/dbna-outbound}")
    private String basePath;

    @Bean
    public Storage storage() {
        logger.info("Initializing file system storage with base path: {}", basePath);
        
        // Ensure base directory exists
        try {
            Path path = Paths.get(basePath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                logger.info("Created storage directory: {}", basePath);
            }
        } catch (IOException e) {
            logger.warn("Could not create storage directory: {}", basePath, e);
        }
        
        return new Storage() {
            @Override
            public InputStream get(String filename) {
                try {
                    Path filePath = resolveFilePath(filename);
                    logger.debug("Getting file from storage: {}", filePath);
                    
                    if (!Files.exists(filePath)) {
                        logger.error("File not found: {}", filename);
                        return null;
                    }
                    
                    return new FileInputStream(filePath.toFile());
                } catch (IOException e) {
                    logger.error("Error reading file: {}", filename, e);
                    return null;
                }
            }

            @Override
            public Long size(String filename) {
                try {
                    Path filePath = resolveFilePath(filename);
                    
                    if (!Files.exists(filePath)) {
                        logger.error("File not found: {}", filename);
                        return 0L;
                    }
                    
                    long size = Files.size(filePath);
                    logger.debug("File size for {}: {} bytes", filename, size);
                    return size;
                } catch (IOException e) {
                    logger.error("Error getting file size: {}", filename, e);
                    return 0L;
                }
            }

            @Override
            public String put(InputStream content, String prefix, String suffix) {
                try {
                    // Generate a unique filename with prefix and suffix
                    String filename = prefix + System.currentTimeMillis() + suffix;
                    Path filePath = resolveFilePath(filename);
                    logger.debug("Putting file to storage with generated name: {}", filePath);
                    
                    // Ensure parent directories exist
                    Files.createDirectories(filePath.getParent());
                    
                    Files.copy(content, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    logger.info("Stored file with generated name: {}", filename);
                    return filename;
                } catch (IOException e) {
                    logger.error("Error storing file with prefix {} and suffix {}", prefix, suffix, e);
                    return null;
                }
            }

            @Override
            public void update(InputStream content, String filename) {
                try {
                    Path filePath = resolveFilePath(filename);
                    logger.debug("Updating file in storage: {}", filePath);
                    
                    if (!Files.exists(filePath)) {
                        logger.error("Cannot update non-existent file: {}", filename);
                        return;
                    }
                    
                    Files.copy(content, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    logger.info("Updated file: {}", filename);
                } catch (IOException e) {
                    logger.error("Error updating file: {}", filename, e);
                }
            }

            @Override
            public java.util.List<String> list(String directory) {
                try {
                    Path dirPath = resolveFilePath(directory);
                    logger.debug("Listing files in directory: {}", dirPath);
                    
                    if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
                        logger.warn("Directory not found or not a directory: {}", directory);
                        return java.util.Collections.emptyList();
                    }
                    
                    java.util.List<String> files = new java.util.ArrayList<>();
                    try (java.util.stream.Stream<Path> stream = Files.list(dirPath)) {
                        stream.filter(Files::isRegularFile)
                              .forEach(path -> files.add(path.getFileName().toString()));
                    }
                    
                    logger.debug("Found {} files in directory: {}", files.size(), directory);
                    return files;
                } catch (IOException e) {
                    logger.error("Error listing files in directory: {}", directory, e);
                    return java.util.Collections.emptyList();
                }
            }

            @Override
            public void remove(String filename) {
                try {
                    Path filePath = resolveFilePath(filename);
                    logger.debug("Removing file from storage: {}", filePath);
                    
                    if (Files.exists(filePath)) {
                        Files.delete(filePath);
                        logger.info("Removed file: {}", filename);
                    } else {
                        logger.warn("File not found for removal: {}", filename);
                    }
                } catch (IOException e) {
                    logger.error("Error removing file: {}", filename, e);
                }
            }

            @Override
            public String move(String source, String target) {
                try {
                    Path sourcePath = resolveFilePath(source);
                    Path targetPath = resolveFilePath(target);
                    logger.debug("Moving file from {} to {}", sourcePath, targetPath);
                    
                    if (!Files.exists(sourcePath)) {
                        logger.error("Source file not found: {}", source);
                        return null;
                    }
                    
                    // Ensure parent directories exist for target
                    Files.createDirectories(targetPath.getParent());
                    
                    Files.move(sourcePath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    logger.info("Moved file from {} to {}", source, target);
                    return target;
                } catch (IOException e) {
                    logger.error("Error moving file from {} to {}", source, target, e);
                    return null;
                }
            }

            @Override
            public boolean exists(String filename) {
                Path filePath = resolveFilePath(filename);
                boolean fileExists = Files.exists(filePath);
                logger.debug("File exists check for {}: {}", filename, fileExists);
                return fileExists;
            }

            /**
             * Resolve the full file path from the filename
             */
            private Path resolveFilePath(String filename) {
                // Handle absolute paths and relative paths
                Path path = Paths.get(filename);
                if (path.isAbsolute()) {
                    return path;
                }
                return Paths.get(basePath, filename);
            }
        };
    }
}











