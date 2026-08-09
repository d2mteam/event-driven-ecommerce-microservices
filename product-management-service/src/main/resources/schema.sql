CREATE FULLTEXT INDEX IF NOT EXISTS ft_products_name
    ON products (name);

CREATE INDEX IF NOT EXISTS idx_products_category
    ON products (category);
