ALTER TABLE product ALTER COLUMN code TYPE VARCHAR(30);

CREATE UNIQUE INDEX ux_product_code_lower ON product (lower(code));
