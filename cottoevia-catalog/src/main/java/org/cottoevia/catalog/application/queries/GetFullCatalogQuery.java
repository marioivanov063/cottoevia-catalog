package org.cottoevia.catalog.application.queries;

/**
 * Query object for retrieving the full catalog.
 *
 * A record is correct here:
 *   - Immutable by construction. A query must never be mutated after creation.
 *   - Structurally transparent — equals/hashCode/toString are derived from
 *     components, which matters when queries are used as cache keys or logged.
 *
 * Implements Query so the type system enforces that only valid query objects
 * can be passed to QueryHandler implementations. The marker interface is the
 * compile-time contract; this record is one fulfilment of it.
 *
 * No parameters now. When filtering is needed (by product type, locale, etc.)
 * add record components here. Nothing else in the stack changes.
 */
public record GetFullCatalogQuery() implements Query {}
