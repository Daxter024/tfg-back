package com.agro.terrainservice.service;

import java.io.IOException;
import java.io.InputStream;

/**
 * Abstracción del backend de almacenamiento de adjuntos. La implementación
 * actual ({@code LocalFileStorageService}) usa el sistema de ficheros local
 * para simplificar el TFG; una eventual migración a MinIO/S3 cambia solo
 * esta capa sin tocar al service.
 */
public interface FileStorageService {

    /**
     * Persiste el contenido del input stream y devuelve la {@code storage_key}
     * (ruta relativa al raíz del storage o object key de S3).
     */
    String store(String storageKey, InputStream content, long contentLength) throws IOException;

    /** Abre un stream de lectura sobre la key. */
    InputStream load(String storageKey) throws IOException;

    /** Elimina el objeto. No falla si no existe (idempotente). */
    void delete(String storageKey) throws IOException;
}
