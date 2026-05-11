package com.agro.taskservice.exception;

public class TaskDeleteConflictException extends RuntimeException {
    public TaskDeleteConflictException(String message) {
        super(message);
    }
}
