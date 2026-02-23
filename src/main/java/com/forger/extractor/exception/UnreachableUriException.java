package com.forger.extractor.exception;

public class UnreachableUriException extends RuntimeException {

    public UnreachableUriException(String message) {
        super(message);
    }

    public UnreachableUriException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnreachableUriException(Throwable cause) {
        super(cause);
    }
}
