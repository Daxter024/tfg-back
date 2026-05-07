package com.agro.terrainservice.exception;

import org.springframework.http.HttpStatus;

/**
 * HU-TER-05: lanzada cuando la importacion desde el Catastro / SIGPAC falla.
 * Lleva el {@link HttpStatus} adecuado para distinguir 400 (formato malo),
 * 404 (referencia inexistente), 502 (fallo del proveedor) o 504 (timeout).
 */
public class CadastralImportException extends RuntimeException {
    private final HttpStatus status;

    public CadastralImportException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public CadastralImportException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
