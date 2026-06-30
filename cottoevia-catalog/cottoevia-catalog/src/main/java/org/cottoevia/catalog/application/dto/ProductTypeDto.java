package org.cottoevia.catalog.application.dto;

import java.util.List;

/**
 * A top-level menu section (MAIN, DRINK, DESSERT) with its available items.
 *
 * typeCode is the internal identifier used by frontend routing/rendering logic.
 * typeLabel is the human-readable display string.
 */
public record ProductTypeDto(
        String typeCode,
        String typeLabel,
        List<CatalogItemDto> items
) {}
