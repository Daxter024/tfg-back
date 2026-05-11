package com.agro.taskservice.service;

import java.io.IOException;
import java.io.InputStream;

/**
 * Abstraccion del backend de almacenamiento de evidencias (HU-TAR-02).
 * Identica a la de terrain-service para HU-TER-03.
 */
public interface FileStorageService {

    String store(InputStream content, long sizeBytes, String suggestedSubdir, String originalName) throws IOException;

    InputStream load(String storageKey) throws IOException;

    void delete(String storageKey);
}
