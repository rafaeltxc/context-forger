package com.forger.extractor.exception;

public class WebUriExtractionException extends RuntimeException {

    public WebUriExtractionException(String message) {
        super(message);
    }

    public WebUriExtractionException(String message, Throwable cause) {
        super(message, cause);
    }

    public WebUriExtractionException(Throwable cause) {
        super(cause);
    }
}
