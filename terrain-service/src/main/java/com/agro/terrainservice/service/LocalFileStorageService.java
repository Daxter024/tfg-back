package com.agro.terrainservice.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Implementacion de {@link FileStorageService} sobre el sistema de archivos
 * local. La raiz se controla con {@code attachments.storage.root} (default
 * {@code ./attachments}). En Docker conviene montar un volumen persistente
 * sobre esa ruta.
 */
@Service
@Slf4j
public class LocalFileStorageService implements FileStorageService {

    private final Path root;

    public LocalFileStorageService(@Value("${attachments.storage.root:./attachments}") String rootDir) {
        this.root = Paths.get(rootDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(root);
        log.info("Local attachment storage initialized at {}", root);
    }

    @Override
    public String store(InputStream content, long sizeBytes, String suggestedSubdir, String originalName) throws IOException {
        Path subdir = (suggestedSubdir == null || suggestedSubdir.isBlank())
                ? root
                : root.resolve(sanitize(suggestedSubdir));
        Files.createDirectories(subdir);

        String safeName = sanitize(originalName == null ? "file" : originalName);
        String key = (subdir == root ? "" : subdir.getFileName().toString() + "/")
                + UUID.randomUUID() + "-" + safeName;
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new IOException("Storage key escapes the storage root");
        }

        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        return key;
    }

    @Override
    public InputStream load(String storageKey) throws IOException {
        Path file = resolveSafe(storageKey);
        return Files.newInputStream(file);
    }

    @Override
    public void delete(String storageKey) {
        try {
            Path file = resolveSafe(storageKey);
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Failed to delete attachment {}: {}", storageKey, e.getMessage());
        }
    }

    private Path resolveSafe(String storageKey) throws IOException {
        Path file = root.resolve(storageKey).normalize();
        if (!file.startsWith(root)) {
            throw new IOException("Storage key escapes the storage root");
        }
        return file;
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^A-Za-z0-9._\\-]", "_");
    }
}
