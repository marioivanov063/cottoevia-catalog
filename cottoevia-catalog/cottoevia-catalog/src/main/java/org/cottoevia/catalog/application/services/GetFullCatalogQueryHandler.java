package org.cottoevia.catalog.application.services;

import org.cottoevia.catalog.application.dto.CatalogResponse;
import org.cottoevia.catalog.application.ports.out.CatalogRepository;
import org.cottoevia.catalog.application.queries.GetFullCatalogQuery;
import org.cottoevia.catalog.application.queries.QueryHandler;
import org.springframework.stereotype.Component;

/**
 * Handles the GetFullCatalogQuery use case.
 *
 * One handler, one use case. This class has a single reason to change:
 * the logic for fulfilling a full catalog request changes.
 *
 * It is a @Component (not @Service) because it is not a service in the
 * Spring sense — it is a collaborator injected into the service.
 * The service is the inbound port implementation; this is its worker.
 *
 * Package-private: it is an implementation detail of the services package.
 * Nothing outside this package references it directly — only the service
 * that holds it as a property and the Spring context that wires it.
 *
 * When a caching requirement arrives:
 *   Option A — wrap this handler with a CachingGetFullCatalogQueryHandler
 *              that checks the cache before delegating here.
 *   Option B — annotate fetchFullCatalog() in the repository with @Cacheable.
 * Either way, this class does not change.
 */
@Component
class GetFullCatalogQueryHandler implements QueryHandler<GetFullCatalogQuery, CatalogResponse> {

    private final CatalogRepository catalogRepository;

    GetFullCatalogQueryHandler(CatalogRepository catalogRepository) {
        this.catalogRepository = catalogRepository;
    }

    @Override
    public CatalogResponse handle(GetFullCatalogQuery query) {
        return catalogRepository.fetchFullCatalog();
    }
}
