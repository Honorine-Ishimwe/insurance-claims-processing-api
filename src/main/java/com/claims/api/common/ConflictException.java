package com.claims.api.common;

/** Thrown when a request conflicts with existing data (e.g. a username that is already taken). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
