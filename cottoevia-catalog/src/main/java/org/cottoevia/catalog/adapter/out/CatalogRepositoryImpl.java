package org.cottoevia.catalog.adapter.out;

import org.cottoevia.catalog.application.dto.*;
import org.cottoevia.catalog.application.exceptions.CatalogDataAccessException;
import org.cottoevia.catalog.application.ports.out.CatalogRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Persistence adapter — the only class in the catalog aggregate that
 * knows MySQL exists.
 *
 * ── Why ResultSetExtractor and not RowMapper? ─────────────────────
 * RowMapper produces one object per row. It cannot fold a flat result
 * set into a tree. ResultSetExtractor receives the full ResultSet and
 * controls iteration, which is necessary for the grouping logic below.
 *
 * ── SQLException handling ─────────────────────────────────────────
 * rs.getString(), rs.getBigDecimal(), etc. all declare throws SQLException.
 * Inside a computeIfAbsent lambda the Function.apply() does not declare
 * any checked exception, so the lambda does not compile if it calls
 * those methods directly.
 *
 * The solution: read ALL column values from the ResultSet into local
 * variables BEFORE any computeIfAbsent call. This happens at the top
 * of the while-loop where SQLException is declared by the enclosing
 * extractData() method. The lambdas then close over plain locals
 * (no checked exception) and compile cleanly.
 *
 * The ResultSetExtractor.extractData() signature declares throws SQLException,
 * so any SQLException that escapes from rs.* calls at the top of the loop
 * propagates naturally. Spring's JdbcTemplate catches it and wraps it in
 * a DataAccessException. We catch that in fetchFullCatalog() and rethrow
 * as CatalogDataAccessException — an unchecked domain exception that does
 * not leak the persistence technology through the port boundary.
 *
 * ── Items with no option rules (drinks) ──────────────────────────
 * Drinks have no product_option_rule rows so they produce no rows in
 * the main query. A second query fetches them and merges them in with
 * an empty optionGroups list. This keeps the primary query and its
 * folding logic free of NULL-handling from a LEFT JOIN.
 *
 * ── LinkedHashMap ────────────────────────────────────────────────
 * All accumulator maps use LinkedHashMap to preserve insertion order,
 * which mirrors the ORDER BY in each query. The final DTO lists are
 * in the correct display sequence without a second sort pass.
 */
@Repository
class CatalogRepositoryImpl implements CatalogRepository {

    private static final String ITEMS_WITH_RULES_SQL = """
            SELECT
                ci.id                                                   AS catalog_item_id,
                pt.name                                                 AS product_type_code,
                pt.label                                                AS type_label,
                pt.sort_order                                           AS type_sort_order,
                ci.name                                                 AS item_name,
                ci.description                                          AS item_description,
                ci.base_price,
                ci.is_hot,
                ci.image_url,
                ci.sort_order                                           AS item_sort_order,
                cog.id                                                  AS option_group_id,
                cog.name                                                AS option_group_code,
                cog.label                                               AS option_group_label,
                por.min_selectable_option_items,
                por.max_selectable_option_items,
                por.is_required,
                coi.id                                                  AS option_item_id,
                coi.name                                                AS option_item_name,
                coi.price_for_count_greater_than_min_selectable,
                coi.sort_order                                          AS option_sort_order
            FROM product_option_rule    por
            JOIN catalog_item           ci  ON por.catalog_item_id         = ci.id
            JOIN product_type           pt  ON ci.product_type_id          = pt.id
            JOIN catalog_option_group   cog ON por.catalog_option_group_id = cog.id
            JOIN catalog_option_item    coi ON coi.catalog_option_group_id = cog.id
            JOIN product                p   ON p.catalog_item_id           = ci.id
            WHERE p.is_available   = TRUE
              AND coi.is_available = TRUE
            ORDER BY pt.sort_order, ci.sort_order, cog.id, coi.sort_order
            """;

    private static final String ITEMS_WITHOUT_RULES_SQL = """
            SELECT
                ci.id           AS catalog_item_id,
                pt.name         AS product_type_code,
                pt.label        AS type_label,
                pt.sort_order   AS type_sort_order,
                ci.name         AS item_name,
                ci.description  AS item_description,
                ci.base_price,
                ci.is_hot,
                ci.image_url,
                ci.sort_order   AS item_sort_order
            FROM catalog_item   ci
            JOIN product_type   pt ON ci.product_type_id = pt.id
            JOIN product        p  ON p.catalog_item_id  = ci.id
            WHERE p.is_available = TRUE
              AND ci.id NOT IN (
                  SELECT DISTINCT catalog_item_id FROM product_option_rule
              )
            ORDER BY pt.sort_order, ci.sort_order
            """;

    private final JdbcTemplate jdbcTemplate;

    CatalogRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public CatalogResponse fetchFullCatalog() {
        try {
            // Step 1 — fold items-with-rules into the accumulator tree
            Map<String, TypeAccumulator> typeMap = jdbcTemplate.query(
                    ITEMS_WITH_RULES_SQL,
                    new CatalogResultSetExtractor()
            );

            // Step 2 — merge in items-without-rules (drinks, no option rules)
            jdbcTemplate.query(ITEMS_WITHOUT_RULES_SQL, (ResultSet rs) -> {
                // Read ALL columns before any computeIfAbsent call.
                // rs.getString() declares throws SQLException; lambda does not.
                // Locals close over fine — no checked exception in the lambda.
                String  typeCode      = rs.getString("product_type_code");
                String  typeLabel     = rs.getString("type_label");
                int     typeSortOrder = rs.getInt("type_sort_order");
                int     itemId        = rs.getInt("catalog_item_id");
                String  itemName      = rs.getString("item_name");
                String  itemDesc      = rs.getString("item_description");
                BigDecimal basePrice  = rs.getBigDecimal("base_price");
                Boolean isHot         = rs.getObject("is_hot", Boolean.class);
                String  imageUrl      = rs.getString("image_url");
                int     itemSort      = rs.getInt("item_sort_order");

                TypeAccumulator typeAcc = typeMap.computeIfAbsent(
                        typeCode,
                        k -> new TypeAccumulator(typeCode, typeLabel, typeSortOrder)
                );

                typeAcc.items.computeIfAbsent(
                        itemId,
                        k -> new ItemAccumulator(itemId, itemName, itemDesc,
                                basePrice, isHot, imageUrl, itemSort)
                );
            });

            // Step 3 — materialise into immutable DTO tree
            List<ProductTypeDto> menu = typeMap.values().stream()
                    .sorted(Comparator.comparingInt(t -> t.sortOrder))
                    .map(typeAcc -> new ProductTypeDto(
                            typeAcc.code,
                            typeAcc.label,
                            typeAcc.items.values().stream()
                                    .sorted(Comparator.comparingInt(i -> i.sortOrder))
                                    .map(itemAcc -> new CatalogItemDto(
                                            itemAcc.id,
                                            itemAcc.name,
                                            itemAcc.description,
                                            itemAcc.basePrice,
                                            itemAcc.isHot,
                                            itemAcc.imageUrl,
                                            itemAcc.groups.values().stream()
                                                    .sorted(Comparator.comparingInt(g -> g.id))
                                                    .map(g -> new OptionGroupDto(
                                                            g.id,
                                                            g.code,
                                                            g.label,
                                                            g.minSelectable,
                                                            g.maxSelectable,
                                                            g.isRequired,
                                                            List.copyOf(g.options)
                                                    ))
                                                    .toList()
                                    ))
                                    .toList()
                    ))
                    .toList();

            return CatalogResponse.of(menu);

        } catch (Exception ex) {
            /*
             * Spring's JdbcTemplate translates SQLExceptions into
             * DataAccessExceptions (unchecked). We catch broadly here
             * and rethrow as CatalogDataAccessException so that:
             *
             *   1. No persistence technology leaks through the port boundary.
             *      The service and controller never see a DataAccessException
             *      or an SQLException — they see a catalog domain exception.
             *
             *   2. The original cause is preserved for logging and debugging.
             *
             *   3. A @RestControllerAdvice in the adapter-in layer can catch
             *      CatalogDataAccessException and return a proper HTTP 503
             *      without any try-catch in the controller itself.
             */
            throw new CatalogDataAccessException(
                    "Failed to fetch the full catalog from the database", ex
            );
        }
    }

    // ── ResultSetExtractor ────────────────────────────────────────────────────

    private static class CatalogResultSetExtractor
            implements ResultSetExtractor<Map<String, TypeAccumulator>> {

        @Override
        public Map<String, TypeAccumulator> extractData(ResultSet rs) throws SQLException {
            Map<String, TypeAccumulator> typeMap = new LinkedHashMap<>();

            while (rs.next()) {
                // Read every column into locals first — see class-level comment
                // on why this pattern is necessary for computeIfAbsent.
                String     typeCode      = rs.getString("product_type_code");
                String     typeLabel     = rs.getString("type_label");
                int        typeSortOrder = rs.getInt("type_sort_order");
                int        itemId        = rs.getInt("catalog_item_id");
                String     itemName      = rs.getString("item_name");
                String     itemDesc      = rs.getString("item_description");
                BigDecimal basePrice     = rs.getBigDecimal("base_price");
                Boolean    isHot         = rs.getObject("is_hot", Boolean.class);
                String     imageUrl      = rs.getString("image_url");
                int        itemSort      = rs.getInt("item_sort_order");
                int        groupId       = rs.getInt("option_group_id");
                String     groupCode     = rs.getString("option_group_code");
                String     groupLabel    = rs.getString("option_group_label");
                int        minSel        = rs.getInt("min_selectable_option_items");
                int        maxSel        = rs.getInt("max_selectable_option_items");
                boolean    isRequired    = rs.getBoolean("is_required");
                int        optionId      = rs.getInt("option_item_id");
                String     optionName    = rs.getString("option_item_name");
                BigDecimal optionPrice   = rs.getBigDecimal(
                        "price_for_count_greater_than_min_selectable");

                // All computeIfAbsent lambdas close over locals — no SQLException
                TypeAccumulator typeAcc = typeMap.computeIfAbsent(
                        typeCode,
                        k -> new TypeAccumulator(typeCode, typeLabel, typeSortOrder)
                );

                ItemAccumulator itemAcc = typeAcc.items.computeIfAbsent(
                        itemId,
                        k -> new ItemAccumulator(itemId, itemName, itemDesc,
                                basePrice, isHot, imageUrl, itemSort)
                );

                GroupAccumulator groupAcc = itemAcc.groups.computeIfAbsent(
                        groupId,
                        k -> new GroupAccumulator(groupId, groupCode, groupLabel,
                                minSel, maxSel, isRequired)
                );

                groupAcc.options.add(new OptionItemDto(optionId, optionName, optionPrice));
            }

            return typeMap;
        }
    }

    // ── Mutable accumulators — private, never escape this class ──────────────

    private static final class TypeAccumulator {
        final String code;
        final String label;
        final int sortOrder;
        final Map<Integer, ItemAccumulator> items = new LinkedHashMap<>();

        TypeAccumulator(String code, String label, int sortOrder) {
            this.code = code; this.label = label; this.sortOrder = sortOrder;
        }
    }

    private static final class ItemAccumulator {
        final int id;
        final String name;
        final String description;
        final BigDecimal basePrice;
        final Boolean isHot;
        final String imageUrl;
        final int sortOrder;
        final Map<Integer, GroupAccumulator> groups = new LinkedHashMap<>();

        ItemAccumulator(int id, String name, String description, BigDecimal basePrice,
                        Boolean isHot, String imageUrl, int sortOrder) {
            this.id = id; this.name = name; this.description = description;
            this.basePrice = basePrice; this.isHot = isHot;
            this.imageUrl = imageUrl; this.sortOrder = sortOrder;
        }
    }

    private static final class GroupAccumulator {
        final int id;
        final String code;
        final String label;
        final int minSelectable;
        final int maxSelectable;
        final boolean isRequired;
        final List<OptionItemDto> options = new ArrayList<>();

        GroupAccumulator(int id, String code, String label,
                         int minSelectable, int maxSelectable, boolean isRequired) {
            this.id = id; this.code = code; this.label = label;
            this.minSelectable = minSelectable; this.maxSelectable = maxSelectable;
            this.isRequired = isRequired;
        }
    }
}
