package org.cottoevia.catalog.application.queries;

/**
 * Generic contract for a single-use-case query handler.
 *
 * Q — the query object type (must be a Query)
 * R — the response type
 *
 * Each handler owns exactly one use case. The service delegates to
 * handlers rather than implementing them directly, which means:
 *   - Each handler is independently testable without the service.
 *   - Adding a new use case means adding a new handler and a new
 *     delegation in the service — nothing existing changes.
 *   - The handle() method is never overridden multiple times in the
 *     same class, which was the design smell in the previous version.
 */
@FunctionalInterface
public interface QueryHandler<Q extends Query, R> {
    R handle(Q query);
}
