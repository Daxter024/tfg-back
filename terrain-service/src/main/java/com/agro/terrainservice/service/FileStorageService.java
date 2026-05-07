package com.agro.terrainservice.service;

import java.io.IOException;
import java.io.InputStream;

/**
 * Abstraccion del backend de almacenamiento de adjuntos (HU-TER-03).
 *
 * <p>La implementacion por defecto persiste en un volumen local
 * ({@link LocalFileStorageService}); en el futuro puede swap-earse por una
 * implementacion S3/MinIO sin tocar el resto del codigo.</p>
 */
public interface FileStorageService {

    /** Persiste el contenido y devuelve la clave opaca con la que recuperarlo. */
    String store(InputStream content, long sizeBytes, String suggestedSubdir, String originalName) throws IOException;

    /** Carga el contenido por su clave. */
    InputStream load(String storageKey) throws IOException;

    /** Borra el contenido. No falla si la clave no existe. */
    void delete(String storageKey);
}
