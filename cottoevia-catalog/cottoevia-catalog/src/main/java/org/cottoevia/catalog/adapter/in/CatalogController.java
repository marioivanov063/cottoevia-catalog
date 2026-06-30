package org.cottoevia.catalog.adapter.in;

import org.cottoevia.catalog.application.dto.CatalogResponse;
import org.cottoevia.catalog.application.exceptions.CatalogDataAccessException;
import org.cottoevia.catalog.application.ports.in.CatalogQueryService;
import org.cottoevia.catalog.application.queries.GetFullCatalogQuery;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * Inbound HTTP adapter for the catalog aggregate.
 *
 * Single responsibility: translate HTTP requests into query objects,
 * hand them to the inbound port, and translate exceptions into HTTP responses.
 * It knows nothing about persistence, SQL, or how the response is assembled.
 *
 * Depends on CatalogQueryService (the interface), never the implementation.
 * Spring injects the correct bean at runtime.
 *
 * @RestControllerAdvice handles CatalogDataAccessException globally,
 * mapping it to 503 Service Unavailable. This keeps the controller
 * clean — no try-catch in the happy path.
 */
@Controller
@RequestMapping("/catalog")
public class CatalogController {

    private final CatalogQueryService catalogQueryService;

    public CatalogController(CatalogQueryService catalogQueryService) {
        this.catalogQueryService = catalogQueryService;
    }

    /**
     * Returns the full menu: all available items, their option groups,
     * selection cardinality rules, and per-option surcharges.
     *
     * GET /catalog
     */
    @GetMapping
    public ResponseEntity<CatalogResponse> getFullCatalog() {
        ResponseEntity<CatalogResponse> response = ResponseEntity.ok(
                catalogQueryService.getFullCatalog(new GetFullCatalogQuery())
        );
        return response;
    }

    /**
     * Maps CatalogDataAccessException to HTTP 503.
     *
     * Keeping the exception handler here (rather than a separate
     * @ControllerAdvice class) is intentional while there is only one
     * controller. When the project grows a global error-handling strategy,
     * move this to a shared @RestControllerAdvice and delete it from here.
     */
    @ExceptionHandler(CatalogDataAccessException.class)
    public ResponseEntity<String> handleDataAccessException(CatalogDataAccessException ex) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("The catalog is temporarily unavailable. Please try again shortly.");
    }
}
