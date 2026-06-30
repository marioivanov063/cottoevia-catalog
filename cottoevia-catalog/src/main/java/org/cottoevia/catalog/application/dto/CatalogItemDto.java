package org.cottoevia.catalog.application.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A single orderable menu item with its full option configuration.
 *
 * isHot is Boolean (boxed) not boolean (primitive) because the database
 * column is nullable: NULL = not a drink, TRUE = hot, FALSE = cold.
 * A primitive collapses NULL to false, silently making every food item
 * look like a cold drink. The boxed type preserves the three-state reality.
 *
 * imageUrl is String (nullable). It is NULL until photos are added to
 * the menu. A non-null wrapper would require a sentinel value which is worse.
 *
 * Drinks have an empty optionGroups list — they carry no option rules.
 */
public record CatalogItemDto(
        int id,
        String name,
        String description,
        BigDecimal basePrice,
        Boolean isHot,
        String imageUrl,
        List<OptionGroupDto> optionGroups
) {}
