package com.blastfurnace.backend.service.trainer;

public class TrainingException extends RuntimeException {
    public TrainingException(String message) {
        super(message);
    }

    public TrainingException(String message, Throwable cause) {
        super(message, cause);
    }
}
