package org.cottoevia.catalog.application.ports.in;

import org.cottoevia.catalog.application.dto.CatalogResponse;
import org.cottoevia.catalog.application.queries.GetFullCatalogQuery;
import org.cottoevia.catalog.application.queries.Query;

/**
 * Inbound port for the catalog aggregate.
 *
 * This is the ONLY contract the adapter-in (CatalogController) depends on.
 * It knows nothing about persistence, SQL, or Spring internals.
 *
 * The method accepts GetFullCatalogQuery explicitly (rather than a raw Query)
 * so that:
 *   1. The Query marker interface has real enforcement at the port boundary —
 *      only a valid Query subtype can be passed here, not an arbitrary object.
 *   2. The compiler verifies the pairing of query type to response type at
 *      every call site in the adapter layer.
 *
 * When a second use case is needed (e.g. get catalog by product type),
 * add a second method here with its own query type. The implementation
 * adds a new handler property and delegates to it. No existing code changes.
 */
public interface CatalogQueryService {

    CatalogResponse getFullCatalog(GetFullCatalogQuery query);
}
