package org.cottoevia.catalog.application.services;

import org.cottoevia.catalog.application.dto.CatalogResponse;
import org.cottoevia.catalog.application.ports.in.CatalogQueryService;
import org.cottoevia.catalog.application.queries.GetFullCatalogQuery;
import org.springframework.stereotype.Service;

/**
 * Inbound port implementation — the orchestration layer.
 *
 * This class has one job: implement the inbound port and route each
 * query to the correct handler. It contains no business logic and no
 * SQL. It is a router.
 *
 * Handlers are injected as constructor dependencies, not inherited.
 * This was the core design correction from the previous version:
 *
 *   BEFORE (wrong):
 *     class CatalogQueryServiceImpl
 *         implements CatalogQueryService, QueryHandler<GetFullCatalogQuery, CatalogResponse>
 *     — the service IS a handler. Adding a second use case means the
 *       same class implements a second QueryHandler, overrides handle()
 *       twice (which Java does not allow for the same generic signature),
 *       and becomes impossible to extend cleanly.
 *
 *   AFTER (correct):
 *     class CatalogQueryServiceImpl implements CatalogQueryService
 *     — the service HAS handlers. Adding a second use case means
 *       injecting a second handler and adding one delegation method.
 *       No existing code changes.
 *
 * Package-private: the controller depends on CatalogQueryService (the port),
 * never on this class. Spring resolves the bean by interface at runtime.
 */
@Service
class CatalogQueryServiceImpl implements CatalogQueryService {

    private final GetFullCatalogQueryHandler getFullCatalogHandler;

    CatalogQueryServiceImpl(GetFullCatalogQueryHandler getFullCatalogHandler) {
        this.getFullCatalogHandler = getFullCatalogHandler;
    }

    @Override
    public CatalogResponse getFullCatalog(GetFullCatalogQuery query) {
        return getFullCatalogHandler.handle(query);
    }

    /*
     * When a second use case arrives, the extension looks like this:
     *
     *   private final GetCatalogByTypeQueryHandler getByTypeHandler;
     *
     *   CatalogQueryServiceImpl(
     *           GetFullCatalogQueryHandler getFullCatalogHandler,
     *           GetCatalogByTypeQueryHandler getByTypeHandler) { ... }
     *
     *   @Override
     *   public CatalogResponse getCatalogByType(GetCatalogByTypeQuery query) {
     *       return getByTypeHandler.handle(query);
     *   }
     *
     * Nothing existing changes. The new handler is independently testable.
     */
}
