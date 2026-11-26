ALTER TABLE product ALTER COLUMN unit TYPE VARCHAR(4);

ALTER TABLE sale_item ADD COLUMN unit VARCHAR(4);

UPDATE sale_item SET unit = product.unit FROM product WHERE product.id = sale_item.product_id;
