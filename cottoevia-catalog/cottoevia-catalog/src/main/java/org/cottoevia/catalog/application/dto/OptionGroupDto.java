package org.cottoevia.catalog.application.dto;

import java.util.List;

/**
 * A named group of selectable options with its cardinality rules.
 * e.g. "Салати" (pick exactly 2), "Плънки" (pick 1-2), "Гарнитура" (optional).
 *
 * The cardinality fields travel with the group so the client has
 * everything needed to enforce selection rules without a second request.
 *
 * options is wrapped in List.copyOf() by the repository before construction,
 * making it unmodifiable. Records are shallow-immutable — the List reference
 * is final but its contents are not unless made unmodifiable explicitly.
 */
public record OptionGroupDto(
        int id,
        String groupCode,
        String groupLabel,
        int minSelectableOptionItems,
        int maxSelectableOptionItems,
        boolean isRequired,
        List<OptionItemDto> options
) {}
