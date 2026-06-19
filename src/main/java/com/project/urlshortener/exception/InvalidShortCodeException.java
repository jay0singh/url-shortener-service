package com.project.urlshortener.exception;

public class InvalidShortCodeException extends RuntimeException {
    public InvalidShortCodeException(String message) {
        super(message);
    }
}