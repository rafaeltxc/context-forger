package com.forger.extractor.exception;

public class UriConnectException extends RuntimeException {

    public UriConnectException(String message) {
        super(message);
    }

    public UriConnectException(String message, Throwable cause) {
        super(message, cause);
    }

    public UriConnectException(Throwable cause) {
        super(cause);
    }
}
