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

/**
 * Implementación de {@link FileStorageService} sobre el sistema de ficheros
 * local. La ubicación raíz se configura con {@code attachments.storage.path}
 * (default {@code ./attachments}). En Docker se monta como volumen.
 */
@Service
@Slf4j
public class LocalFileStorageService implements FileStorageService {

    private final Path root;

    public LocalFileStorageService(@Value("${attachments.storage.path:./attachments}") String basePath) {
        this.root = Paths.get(basePath).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(root);
        log.info("Attachment storage root initialised at {}", root);
    }

    @Override
    public String store(String storageKey, InputStream content, long contentLength) throws IOException {
        Path target = resolveSafe(storageKey);
        Files.createDirectories(target.getParent());
        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        return storageKey;
    }

    @Override
    public InputStream load(String storageKey) throws IOException {
        Path source = resolveSafe(storageKey);
        return Files.newInputStream(source);
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Path target = resolveSafe(storageKey);
        Files.deleteIfExists(target);
    }

    /**
     * Asegura que la key resuelve dentro del directorio raíz (mitiga path
     * traversal: una key como {@code ../../etc/passwd} se rechaza).
     */
    private Path resolveSafe(String storageKey) {
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new SecurityException("Resolved path escapes storage root: " + storageKey);
        }
        return resolved;
    }
}
