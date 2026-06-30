-- ================================================================
--  ДОБАВКИ — Catalog Aggregate
--  MySQL 8+ / InnoDB / utf8mb4
--
--  Tables:
--    product_type
--    catalog_item
--    product
--    catalog_option_group
--    catalog_option_item
--    product_option_rule
--
--  Aggregate boundary decision:
--    catalog_item  → catalog aggregate (read-mostly, menu definition)
--    product       → inventory/availability aggregate (operational state)
--
--    catalog_item answers: "What can be ordered, how is it
--    customised, and what does it cost?"
--    It is written to only by admins updating the menu.
--
--    product answers: "Right now, can this specific item be ordered
--    and how many are left?"
--    It is written to by order processing and admin toggles.
--
--    Keeping these concerns in separate tables means:
--      • No order-processing writes touch the catalog aggregate.
--      • catalog_item stays read-heavy and clean.
--      • The boundary is ready for stock management without
--        any ALTER TABLE when that feature is built.
--
--  Extensibility rules we agreed on:
--    • New catalog item       → INSERT into catalog_item +
--                               INSERT into product +
--                               INSERTs into product_option_rule.
--                               Zero code changes.
--    • New option group       → INSERT into catalog_option_group +
--                               seed catalog_option_item +
--                               INSERT into product_option_rule
--                               for each catalog_item that uses it.
--    • New option item        → INSERT into catalog_option_item.
--                               Immediately available to all rules
--                               that reference its group.
--    • Stock management       → populate product.stock_quantity +
--                               add a stock_log table. No ALTER TABLE
--                               on any existing table.
--    • Photos                 → populate catalog_item.image_url.
--
--  Surcharge rule we agreed on:
--    price_for_count_greater_than_min_selectable lives on
--    catalog_option_item (the ingredient), NOT on product_option_rule
--    (the slot). Reason: the cost of adding a second filling is a
--    property of WHAT you are adding. Changing "Пилешко филе" from
--    1.80 to 2.00 touches exactly one row regardless of how many
--    catalog items reference it through rules.
--
--  Run:
--    mysql -u root -p < dobavki_schema.sql
-- ================================================================

CREATE DATABASE IF NOT EXISTS foodcart
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE foodcart;

-- ================================================================
--  1. PRODUCT TYPE
--     The three top-level nav sections of the menu.
--
--     Deliberately lean: just an id, an internal code, a display
--     label, and a sort order. Nothing operational lives here.
--
--     Adding a new top-level section in the future = one INSERT.
-- ================================================================
CREATE TABLE product_type (
    id          TINYINT UNSIGNED    AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(40)         NOT NULL UNIQUE
                COMMENT 'Internal code: MAIN | DRINK | DESSERT',
    label       VARCHAR(80)         NOT NULL
                COMMENT 'Display label shown to the client',
    sort_order  TINYINT UNSIGNED    NOT NULL DEFAULT 0
) ENGINE=InnoDB
  COMMENT='Top-level menu sections (nav tabs)';

INSERT INTO product_type (name, label, sort_order) VALUES
    ('MAIN',    'Основни ястия', 1),
    ('DRINK',   'Напитки',       2),
    ('DESSERT', 'Десерти',       3);


-- ================================================================
--  2. CATALOG ITEM  [catalog aggregate]
--     Every orderable item defined on the menu.
--     This is the anchor for option rules and display data.
--     Written to ONLY by admin menu changes — never by orders.
--
--     is_hot:
--       A definitional property of the item, not operational state.
--       "Кафе" is inherently hot — that does not change order by order.
--       NULL  = not a drink
--       TRUE  = hot drink
--       FALSE = cold drink
--
--     image_url:
--       Nullable now. Populate when photos are added to the menu.
--       No schema change needed at that point.
--
--     What does NOT live here:
--       is_available and stock_quantity are operational state.
--       They are written to by order processing, not menu editing.
--       They live on the product table (see below).
-- ================================================================
CREATE TABLE catalog_item (
    id              SMALLINT UNSIGNED   AUTO_INCREMENT PRIMARY KEY,
    product_type_id TINYINT UNSIGNED    NOT NULL,
    name            VARCHAR(120)        NOT NULL,
    description     TEXT                DEFAULT NULL,
    base_price      DECIMAL(7,2)        NOT NULL
                    COMMENT 'Price before any extra options are added',
    is_hot          BOOLEAN             DEFAULT NULL
                    COMMENT 'NULL = not a drink | TRUE = hot | FALSE = cold',
    image_url       VARCHAR(255)        DEFAULT NULL
                    COMMENT 'Optional photo URL; populate when photos are ready',
    sort_order      TINYINT UNSIGNED    NOT NULL DEFAULT 0,
    created_at      DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP
                                        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ci_product_type
        FOREIGN KEY (product_type_id)
        REFERENCES product_type(id)
        ON DELETE RESTRICT
) ENGINE=InnoDB
  COMMENT='Menu item definitions — catalog aggregate, admin-written only';

-- ── MAINS (product_type_id = 1) ──────────────────────────────────
INSERT INTO catalog_item (product_type_id, name, base_price, sort_order) VALUES
    (1, 'Бургер',           8.90, 1),
    (1, 'Тост',             6.50, 2),
    (1, 'Солена палачинка', 5.90, 3),
    (1, 'Тортиля',          7.50, 4),
    (1, 'Хот дог',          5.50, 5),
    (1, 'Странджанка',      9.90, 6);

-- ── DRINKS (product_type_id = 2) ─────────────────────────────────
INSERT INTO catalog_item (product_type_id, name, base_price, is_hot, sort_order) VALUES
    (2, 'Кафе',             2.20, TRUE,  7),
    (2, 'Чай',              1.80, TRUE,  8),
    (2, 'Вода',             1.50, FALSE, 9),
    (2, 'Кола',             2.50, FALSE, 10),
    (2, 'Портокалов сок',   2.80, FALSE, 11),
    (2, 'Протеинов шейк',   4.50, FALSE, 12);

-- ── DESSERTS (product_type_id = 3) ───────────────────────────────
INSERT INTO catalog_item (product_type_id, name, base_price, sort_order) VALUES
    (3, 'Сладка палачинка', 5.50, 13);


-- ================================================================
--  3. PRODUCT  [inventory / availability aggregate]
--     The operational twin of catalog_item.
--     One product row per catalog_item (1-to-1 for now).
--     Written to by order processing and admin availability toggles.
--
--     Why separated from catalog_item:
--       is_available and stock_quantity change constantly as orders
--       are placed and stock is managed. Putting them on catalog_item
--       would mean two different concerns (menu definition vs live
--       inventory state) writing to the same table, dirtying the
--       catalog aggregate boundary and creating contention on a
--       table that should be read-heavy.
--
--     is_available:
--       FALSE = item is hidden from the client menu.
--       Toggled by admin (e.g. "sold out today") or automatically
--       when stock_quantity reaches 0.
--
--     stock_quantity:
--       NULL  = stock tracking not enabled for this item.
--       >= 0  = units currently available.
--       When stock management is implemented, add a
--       product_stock_log table for history and decrement this
--       column on each order. No ALTER TABLE needed at that point.
-- ================================================================
CREATE TABLE product (
    id              INT UNSIGNED        AUTO_INCREMENT PRIMARY KEY,
    catalog_item_id SMALLINT UNSIGNED   NOT NULL UNIQUE
                    COMMENT '1-to-1 with catalog_item for now; unique enforces that',
    is_available    BOOLEAN             NOT NULL DEFAULT TRUE
                    COMMENT 'FALSE = hidden from client; toggled by admin or stock logic',
    stock_quantity  SMALLINT UNSIGNED   DEFAULT NULL
                    COMMENT 'NULL = tracking disabled | >= 0 = units in stock',
    created_at      DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP
                                        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_catalog_item
        FOREIGN KEY (catalog_item_id)
        REFERENCES catalog_item(id)
        ON DELETE CASCADE
) ENGINE=InnoDB
  COMMENT='Operational state per menu item — availability and stock';

-- catalog_item IDs from inserts above:
--   1=Бургер           2=Тост              3=Солена палачинка
--   4=Тортиля          5=Хот дог           6=Странджанка
--   7=Кафе             8=Чай               9=Вода
--   10=Кола            11=Портокалов сок   12=Протеинов шейк
--   13=Сладка палачинка

INSERT INTO product (catalog_item_id, is_available) VALUES
    (1,  TRUE),   -- Бургер
    (2,  TRUE),   -- Тост
    (3,  TRUE),   -- Солена палачинка
    (4,  TRUE),   -- Тортиля
    (5,  TRUE),   -- Хот дог
    (6,  TRUE),   -- Странджанка
    (7,  TRUE),   -- Кафе
    (8,  TRUE),   -- Чай
    (9,  TRUE),   -- Вода
    (10, TRUE),   -- Кола
    (11, TRUE),   -- Портокалов сок
    (12, TRUE),   -- Протеинов шейк
    (13, TRUE);   -- Сладка палачинка


-- ================================================================
--  4. CATALOG OPTION GROUP  [catalog aggregate]
--     A named category of selectable extras shown to the client.
--     Examples: Салати, Плънки, Сосове, Сладки добавки, Гарнитура.
--
--     Adding a new group = one INSERT here +
--                          seed catalog_option_item +
--                          INSERT into product_option_rule for each
--                          catalog_item that should offer the group.
-- ================================================================
CREATE TABLE catalog_option_group (
    id      SMALLINT UNSIGNED   AUTO_INCREMENT PRIMARY KEY,
    name    VARCHAR(60)         NOT NULL UNIQUE
            COMMENT 'Internal code: SALAD | ASSORTMENT | CONDIMENT | SWEET_TOPPING | SIDE',
    label   VARCHAR(80)         NOT NULL
            COMMENT 'Display label shown to the client'
) ENGINE=InnoDB
  COMMENT='Named categories of selectable extras — catalog aggregate';

INSERT INTO catalog_option_group (name, label) VALUES
    ('SALAD',         'Салати'),
    ('ASSORTMENT',    'Плънки и асортимент'),
    ('CONDIMENT',     'Сосове и подправки'),
    ('SWEET_TOPPING', 'Сладки добавки'),
    ('SIDE',          'Гарнитура'),
    ('BURGER_BREAD', 'Вид хляб за бургер'),
    ('TOAST_BREAD', 'Вид хляб за тост'),
    ('TORTILA_BREAD', 'Вид питка за тортия');
    
-- ================================================================
--  5. CATALOG OPTION ITEM  [catalog aggregate]
--     A single selectable extra within an option group.
--
--     price_for_count_greater_than_min_selectable:
--       Surcharge added PER UNIT when the client selects MORE items
--       than the minimum defined by the product_option_rule.
--
--       Lives on the ITEM (not on the rule) because the cost is a
--       property of what is being added, not of the slot that allows
--       it. Changing the surcharge for one ingredient = one row
--       updated, regardless of how many catalog items reference it.
--
--       0.00 = free even when chosen above the minimum.
--       > 0  = charged per unit chosen above the rule minimum.
--
--     Adding a new option = one INSERT here.
--     Immediately available to every rule that references its group.
-- ================================================================
CREATE TABLE catalog_option_item (
    id                                          SMALLINT UNSIGNED   AUTO_INCREMENT PRIMARY KEY,
    catalog_option_group_id                     SMALLINT UNSIGNED   NOT NULL,
    name                                        VARCHAR(80)         NOT NULL,
    price_for_count_greater_than_min_selectable DECIMAL(7,2)        NOT NULL DEFAULT 0.00
                                                COMMENT 'Surcharge per unit chosen above the rule minimum',
    is_available                                BOOLEAN             NOT NULL DEFAULT TRUE,
    sort_order                                  TINYINT UNSIGNED    NOT NULL DEFAULT 0,
    CONSTRAINT fk_coi_group
        FOREIGN KEY (catalog_option_group_id)
        REFERENCES catalog_option_group(id)
        ON DELETE RESTRICT
) ENGINE=InnoDB
  COMMENT='Individual selectable extras with above-minimum surcharge — catalog aggregate';

-- catalog_option_group IDs:
--   1=SALAD  2=ASSORTMENT  3=CONDIMENT  4=SWEET_TOPPING  5=SIDE  6=BURGER_BREAD  7=TOAST_BREAD  8=TORTILLA_BREAD

-- ── SALADS (group 1) ─────────────────────────────────────────────
INSERT INTO catalog_option_item
    (catalog_option_group_id, name, price_for_count_greater_than_min_selectable, sort_order)
VALUES
    (1, 'Млечна салата', 0.00, 1),
    (1, 'Руска салата',  0.00, 2),
    (1, 'Катук салата',  0.00, 3);

-- ── ASSORTMENTS (group 2) ────────────────────────────────────────
INSERT INTO catalog_option_item
    (catalog_option_group_id, name, price_for_count_greater_than_min_selectable, sort_order)
VALUES
    (2, 'Кюфте',         1.50, 1),
    (2, 'Кебапче',       1.50, 2),
    (2, 'Шунка',         1.20, 3),
    (2, 'Кашкавал',      1.20, 4),
    (2, 'Сирене',        1.00, 5),
    (2, 'Пилешко филе',  1.80, 6),
    (2, 'Салам',         1.20, 7),
    (2, 'Гъби',          0.80, 8),
    (2, 'Домати',        0.50, 9),
    (2, 'Царевица',      0.50, 10),
    (2, 'Чушки',         0.50, 11);

-- ── CONDIMENTS (group 3) ─────────────────────────────────────────
INSERT INTO catalog_option_item
    (catalog_option_group_id, name, price_for_count_greater_than_min_selectable, sort_order)
VALUES
    (3, 'Шарена',        0.00, 1),
    (3, 'Ketchup',       0.00, 2),
    (3, 'Mayo',          0.00, 3),
    (3, 'Лют сос',       0.00, 4),
    (3, 'Горчица',       0.00, 5);

-- ── SWEET TOPPINGS (group 4) ─────────────────────────────────────
INSERT INTO catalog_option_item
    (catalog_option_group_id, name, price_for_count_greater_than_min_selectable, sort_order)
VALUES
    (4, 'Горски плодове конфитюр', 0.80, 1),
    (4, 'Шоколад',                 0.80, 2),
    (4, 'Сини сливи конфитюр',     0.80, 3),
    (4, 'Oreo чоко',               1.00, 4),
    (4, 'Мед',                     0.60, 5),
    (4, 'Орехи',                   0.70, 6);

-- ── SIDES (group 5) ──────────────────────────────────────────────
INSERT INTO catalog_option_item
    (catalog_option_group_id, name, price_for_count_greater_than_min_selectable, sort_order)
VALUES
    (5, 'Пържени к6ртофи', 1.50, 1);

-- ── BURGER_BREADS (group 6) ──────────────────────────────────────────────
INSERT INTO catalog_option_item
    (catalog_option_group_id, name, price_for_count_greater_than_min_selectable, sort_order)
VALUES
    (6, 'Чабата', 0, 1),
    (6, 'Фокача', 0, 1);

-- ── TOAST_BREADS (group 7) ──────────────────────────────────────────────
INSERT INTO catalog_option_item
    (catalog_option_group_id, name, price_for_count_greater_than_min_selectable, sort_order)
VALUES
    (7, 'Бял хляб', 0, 1),
    (7, 'Ръжен хляб', 0, 2);
    
 -- ── TORTILA_BREADS (group 7) ──────────────────────────────────────────────
INSERT INTO catalog_option_item
    (catalog_option_group_id, name, price_for_count_greater_than_min_selectable, sort_order)
VALUES   
    (8, 'Зеленчукова питка', 0, 1),
    (8, 'Питка с червено цвекло', 0, 2),
    (8, 'Пълнозърнеста питка', 0, 3),
    (8, 'Питка класик', 0, 4);
    
-- ================================================================
--  6. PRODUCT OPTION RULE  [catalog aggregate]
--     The join between catalog_item and catalog_option_group.
--     Declares which option groups apply to each catalog item
--     and the exact selection cardinality.
--
--     min_selectable_option_items:
--       Client must pick AT LEAST this many from this group.
--       0 = the entire group is optional; client may skip it.
--
--     max_selectable_option_items:
--       Client may pick AT MOST this many from this group.
--
--     is_required:
--       TRUE  = client must reach min_selectable_option_items.
--       FALSE = the entire group is optional.
--
--     How the surcharge is calculated at order time:
--       For each option group, if the client picks N items and
--       N > min_selectable_option_items, each item chosen beyond
--       the minimum contributes its own
--       price_for_count_greater_than_min_selectable to the total.
--       Items chosen up to the minimum are covered by base_price.
--
--     The UNIQUE KEY on (catalog_item_id, catalog_option_group_id)
--     enforces that each group can only be wired to an item once.
--     If you need two separate groups of the same type on one item,
--     create a second catalog_option_group with a different name.
-- ================================================================
CREATE TABLE product_option_rule (
    id                          INT UNSIGNED        AUTO_INCREMENT PRIMARY KEY,
    catalog_item_id             SMALLINT UNSIGNED   NOT NULL,
    catalog_option_group_id     SMALLINT UNSIGNED   NOT NULL,
    min_selectable_option_items TINYINT UNSIGNED    NOT NULL DEFAULT 1,
    max_selectable_option_items TINYINT UNSIGNED    NOT NULL DEFAULT 1,
    is_required                 BOOLEAN             NOT NULL DEFAULT TRUE,
    UNIQUE KEY uq_item_group (catalog_item_id, catalog_option_group_id),
    CONSTRAINT fk_por_catalog_item
        FOREIGN KEY (catalog_item_id)
        REFERENCES catalog_item(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_por_option_group
        FOREIGN KEY (catalog_option_group_id)
        REFERENCES catalog_option_group(id)
        ON DELETE RESTRICT
) ENGINE=InnoDB
  COMMENT='Which option groups apply to each catalog item, with selection cardinality';

-- catalog_item IDs:
--   1=Бургер           2=Тост              3=Солена палачинка
--   4=Тортиля          5=Хот дог           6=Странджанка
--   7–12=Drinks (no rules)                 13=Сладка палачинка
--
-- catalog_option_group IDs:
--   1=SALAD  2=ASSORTMENT  3=CONDIMENT  4=SWEET_TOPPING  5=SIDE 6=BURGER_BREAD 7=TOAST_BREAD 8=TORTILA=BREAD

INSERT INTO product_option_rule
    (catalog_item_id, catalog_option_group_id,
     min_selectable_option_items, max_selectable_option_items, is_required)
VALUES
-- ── Бургер (1) ──────────────────────────────────────────────────
--   Exactly 2 salads required. Both within min → no surcharge.
    (1, 1, 2, 2, TRUE),
--   At least 1 filling required. A 2nd triggers price_for_count > min.
    (1, 2, 1, 2, TRUE),
--   Optional fries. min=0 → any selection is above the minimum
--   → price_for_count_greater_than_min_selectable (1.50) applies.
    (1, 5, 0, 1, FALSE),
--   at least one bread type.
	(1, 6, 1, 1, TRUE),

-- ── Тост (2) ────────────────────────────────────────────────────
--   Exactly 1 filling required.
    (2, 2, 1, 3, TRUE),
--   Optional fries.
    (2, 5, 0, 1, FALSE),
    --   at least one bread type.
	(2, 7, 1, 1, TRUE),

-- ── Солена палачинка (3) ────────────────────────────────────────
--   Exactly 1 filling required.
    (3, 2, 1, 2, TRUE),

-- ── Тортиля (4) ─────────────────────────────────────────────────
--   1 to 2 fillings required. 2nd triggers surcharge.
    (4, 2, 1, 2, TRUE),
--   Up to 2 optional condiments.
    (4, 3, 0, 2, FALSE),
    --   at least one bread type.
	(4, 8, 1, 1, TRUE),

-- ── Хот дог (5) ─────────────────────────────────────────────────
--   Up to 3 optional condiments.
    (5, 3, 0, 3, FALSE),

-- ── Странджанка (6) ─────────────────────────────────────────────
--   Exactly 1 filling required.
    (6, 2, 1, 1, TRUE),
--   Up to 2 optional condiments.
    (6, 3, 0, 2, FALSE),

-- ── Drinks (7–12) ───────────────────────────────────────────────
--   No option rules. Drinks are ordered as-is.

-- ── Сладка палачинка (13) ───────────────────────────────────────
--   Exactly 2 sweet toppings required.
--   min=2 means both selections are above the "0 free" threshold
--   → price_for_count_greater_than_min_selectable applies to both.
    (13, 4, 2, 2, TRUE);


-- ================================================================
--  VIEWS
--  These are what the Spring catalog query layer calls.
--  Three levels of detail depending on what the controller needs.
-- ================================================================

-- ── Full menu as seen by the client ──────────────────────────────
-- Joins product to filter out unavailable items.
-- The catalog controller calls this to build the menu page.
CREATE OR REPLACE VIEW v_catalog AS
SELECT
    ci.id               AS catalog_item_id,
    pt.name             AS product_type,
    pt.label            AS type_label,
    ci.name             AS item_name,
    ci.description,
    ci.base_price,
    ci.is_hot,
    ci.image_url,
    ci.sort_order,
    p.is_available,
    p.stock_quantity
FROM catalog_item   ci
JOIN product_type   pt ON ci.product_type_id = pt.id
JOIN product        p  ON p.catalog_item_id  = ci.id
WHERE p.is_available = TRUE
ORDER BY pt.sort_order, ci.sort_order;


-- ── Option rules per catalog item ────────────────────────────────
-- Used to know which groups to render and their cardinality.
CREATE OR REPLACE VIEW v_item_option_rules AS
SELECT
    ci.id                               AS catalog_item_id,
    ci.name                             AS catalog_item_name,
    cog.id                              AS option_group_id,
    cog.name                            AS option_group_code,
    cog.label                           AS option_group_label,
    por.min_selectable_option_items,
    por.max_selectable_option_items,
    por.is_required
FROM product_option_rule    por
JOIN catalog_item            ci  ON por.catalog_item_id         = ci.id
JOIN catalog_option_group    cog ON por.catalog_option_group_id = cog.id
ORDER BY ci.sort_order, cog.id;


-- ── Fully flattened: every option item per catalog item ───────────
-- One query gives the controller everything needed to render
-- the full catalog: items, their option groups, cardinality
-- rules, each selectable option, and its surcharge.
-- This is the primary query for the catalog aggregate read side.
CREATE OR REPLACE VIEW v_item_options_full AS
SELECT
    ci.id                                               AS catalog_item_id,
    ci.name                                             AS catalog_item_name,
    ci.base_price,
    cog.id                                              AS option_group_id,
    cog.name                                            AS option_group_code,
    cog.label                                           AS option_group_label,
    por.min_selectable_option_items,
    por.max_selectable_option_items,
    por.is_required,
    coi.id                                              AS option_item_id,
    coi.name                                            AS option_item_name,
    coi.price_for_count_greater_than_min_selectable,
    coi.sort_order                                      AS option_sort_order
FROM product_option_rule    por
JOIN catalog_item            ci  ON por.catalog_item_id         = ci.id
JOIN catalog_option_group    cog ON por.catalog_option_group_id = cog.id
JOIN catalog_option_item     coi ON coi.catalog_option_group_id = cog.id
JOIN product                 p   ON p.catalog_item_id           = ci.id
WHERE p.is_available   = TRUE
  AND coi.is_available = TRUE
ORDER BY ci.sort_order, cog.id, coi.sort_order;

-- ================================================================
--  DONE.
-- ================================================================