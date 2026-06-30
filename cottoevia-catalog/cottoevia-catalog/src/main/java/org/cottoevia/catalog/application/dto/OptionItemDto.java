package org.cottoevia.catalog.application.dto;

import java.math.BigDecimal;

/**
 * A single selectable option within an option group.
 * e.g. "Кюфте", "Млечна салата", "Пържени картофи".
 *
 * BigDecimal is mandatory for all monetary values.
 * double/float use binary floating-point and cannot represent most
 * decimal fractions exactly — 1.50 becomes 1.4999999... internally.
 * That is unacceptable for prices that will be summed and displayed.
 */
public record OptionItemDto(
        int id,
        String name,
        BigDecimal priceForCountGreaterThanMinSelectable
) {}
