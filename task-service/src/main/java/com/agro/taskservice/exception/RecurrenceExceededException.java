package com.agro.taskservice.exception;

public class RecurrenceExceededException extends RuntimeException {
    public RecurrenceExceededException(String message) {
        super(message);
    }
}
