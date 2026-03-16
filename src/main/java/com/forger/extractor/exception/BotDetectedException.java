package com.forger.extractor.exception;

public class BotDetectedException extends RuntimeException {

    public BotDetectedException(String message) {
        super(message);
    }

    public BotDetectedException(String message, Throwable cause) {
        super(message, cause);
    }

    public BotDetectedException(Throwable cause) {
        super(cause);
    }
}
