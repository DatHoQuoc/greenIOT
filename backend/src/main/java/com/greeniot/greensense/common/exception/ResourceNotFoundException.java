package com.greeniot.greensense.common.exception;

/** Thrown when an id does not resolve, or resolves to something the caller does not own. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, String id) {
        super(resource + " not found: " + id);
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
