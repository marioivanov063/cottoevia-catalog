package org.cottoevia.catalog.application.queries;

/**
 * Marker interface for all query objects in the catalog aggregate.
 *
 * Lives here rather than in a shared commons package because this aggregate
 * is intentionally self-contained under org.cottoevia.catalog.
 * If a cross-aggregate commons package exists in the broader project,
 * move this there and update imports — the contract does not change.
 *
 * Its only job is to give the type system a way to enforce that
 * QueryHandler implementations only accept legitimate query objects,
 * not arbitrary method arguments.
 */
public interface Query {}
