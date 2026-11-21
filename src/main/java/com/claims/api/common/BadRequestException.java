package com.claims.api.common;

/** Thrown for input that passes field validation but is still unacceptable. Mapped to 400. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
