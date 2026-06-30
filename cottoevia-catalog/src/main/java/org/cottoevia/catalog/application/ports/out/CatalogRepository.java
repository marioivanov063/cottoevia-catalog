package org.cottoevia.catalog.application.ports.out;

import org.cottoevia.catalog.application.dto.CatalogResponse;

/**
 * Outbound port for the catalog aggregate.
 *
 * The service depends on this interface, never on the implementation.
 * Swapping MySQL for PostgreSQL, adding a Redis caching layer, or
 * returning test fixtures all happen by changing or decorating the
 * implementation — this interface and everything above it stay untouched.
 *
 * Returns CatalogResponse directly because this is a pure read side.
 * There is no domain model to protect and no business transformation
 * between what persistence returns and what the API serves.
 * An intermediate mapping layer would be indirection with no payoff.
 */
public interface CatalogRepository {

    CatalogResponse fetchFullCatalog();
}
