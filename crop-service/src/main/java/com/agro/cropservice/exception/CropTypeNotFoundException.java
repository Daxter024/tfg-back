package com.agro.cropservice.exception;

public class CropTypeNotFoundException extends RuntimeException {
    public CropTypeNotFoundException(String message) {
        super(message);
    }
}
