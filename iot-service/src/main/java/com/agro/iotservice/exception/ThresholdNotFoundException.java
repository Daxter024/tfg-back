package com.agro.iotservice.exception;

public class ThresholdNotFoundException extends RuntimeException {
    public ThresholdNotFoundException(String message) {
        super(message);
    }
}
