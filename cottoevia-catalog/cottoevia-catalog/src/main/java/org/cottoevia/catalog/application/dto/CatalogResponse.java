package org.cottoevia.catalog.application.dto;

import java.time.Instant;
import java.util.List;

/**
 * Top-level response object for GET /catalog.
 *
 * Wrapping the list rather than returning List<ProductTypeDto> directly:
 *   1. Allows metadata (timestamp, API version, etc.) to be added later
 *      without a breaking change to the response contract.
 *   2. Gives every endpoint a consistent root object structure.
 *   3. Makes the type self-documenting in method signatures.
 *
 * generatedAt uses Instant (always UTC) rather than LocalDateTime
 * (which has no timezone and serialises differently per JVM locale,
 * causing subtle bugs in distributed or containerised deployments).
 */
public record CatalogResponse(
        Instant generatedAt,
        List<ProductTypeDto> menu
) {
    /**
     * Factory method that stamps the current UTC instant automatically.
     * The repository calls this — callers never forget the timestamp.
     */
    public static CatalogResponse of(List<ProductTypeDto> menu) {
        return new CatalogResponse(Instant.now(), List.copyOf(menu));
    }
}
