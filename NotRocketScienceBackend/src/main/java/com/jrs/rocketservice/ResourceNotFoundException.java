package com.jrs.rocketservice.exception;

/**
 * Thrown when a requested resource does not exist in the database.
 * Maps to HTTP 404 Not Found in the GlobalExceptionHandler.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceName, Long id) {
        super(resourceName + " not found with id: " + id);
    }
}
