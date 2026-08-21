CREATE FULLTEXT INDEX IF NOT EXISTS ft_products_name
    ON products (name);

-- Keep the old text column for a safe one-time migration. Product code now uses
-- category_id only.
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS category VARCHAR(100) NULL;

SET @needs_category_migration = (
    SELECT COUNT(*) = 0
    FROM categories
    WHERE system_category = TRUE
);

INSERT IGNORE INTO categories (name, active, system_category)
SELECT DISTINCT TRIM(product.category), TRUE, FALSE
FROM products product
WHERE @needs_category_migration = TRUE
  AND NULLIF(TRIM(product.category), '') IS NOT NULL;

INSERT IGNORE INTO categories (name, active, system_category)
SELECT DISTINCT TRIM(JSON_UNQUOTE(JSON_EXTRACT(product.attributes, '$.category'))),
                TRUE,
                FALSE
FROM products product
WHERE @needs_category_migration = TRUE
  AND product.attributes IS NOT NULL
  AND NULLIF(TRIM(product.category), '') IS NULL
  AND NULLIF(
          TRIM(JSON_UNQUOTE(JSON_EXTRACT(product.attributes, '$.category'))),
          ''
      ) IS NOT NULL;

UPDATE products product
JOIN categories category ON category.name = TRIM(product.category)
SET product.category_id = category.id
WHERE @needs_category_migration = TRUE
  AND product.category_id IS NULL
  AND NULLIF(TRIM(product.category), '') IS NOT NULL;

UPDATE products product
JOIN categories category
  ON category.name = TRIM(
      JSON_UNQUOTE(JSON_EXTRACT(product.attributes, '$.category'))
  )
SET product.category_id = category.id
WHERE @needs_category_migration = TRUE
  AND product.category_id IS NULL
  AND product.attributes IS NOT NULL;

INSERT IGNORE INTO categories (name, active, system_category)
VALUES ('Khác', TRUE, TRUE);

UPDATE categories
SET active = TRUE,
    system_category = TRUE
WHERE @needs_category_migration = TRUE
  AND name = 'Khác';

UPDATE products product
JOIN categories fallback ON fallback.system_category = TRUE
SET product.category_id = fallback.id
WHERE product.category_id IS NULL;

DROP INDEX IF EXISTS idx_products_category ON products;

CREATE INDEX IF NOT EXISTS idx_products_category_id
    ON products (category_id);

DROP INDEX IF EXISTS uk_products_source_product_id ON products;

ALTER TABLE products
    DROP COLUMN IF EXISTS source_product_id,
    DROP COLUMN IF EXISTS bullet_points,
    DROP COLUMN IF EXISTS product_type_id,
    DROP COLUMN IF EXISTS product_length;
