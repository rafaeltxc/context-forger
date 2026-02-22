package com.forger.extractor.exception;

public class WebContentExtractionException extends RuntimeException {

    public WebContentExtractionException(String message) {
        super(message);
    }

    public WebContentExtractionException(String message, Throwable cause) {
        super(message, cause);
    }

    public WebContentExtractionException(Throwable cause) {
        super(cause);
    }
}
