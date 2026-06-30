package org.cottoevia.catalog.application.exceptions;

/**
 * Thrown when the catalog repository cannot read or assemble catalog data.
 *
 * This is an unchecked exception (extends RuntimeException) for two reasons:
 *
 *   1. SQLException is a checked exception that belongs to the persistence
 *      layer. Leaking it through the port boundary would mean the service
 *      and controller know that SQL exists — a direct violation of hexagonal
 *      architecture. The adapter catches it and rethrows as this type.
 *
 *   2. There is no recovery path for the caller. If the catalog cannot be
 *      read, the request fails. Forcing callers to declare a checked
 *      exception they cannot handle adds noise without value.
 *
 * Spring's @RestControllerAdvice can catch this and map it to a
 * 503 Service Unavailable or 500 Internal Server Error response.
 * That handler lives in the adapter-in layer — not here.
 */
public class CatalogDataAccessException extends RuntimeException {

    public CatalogDataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
