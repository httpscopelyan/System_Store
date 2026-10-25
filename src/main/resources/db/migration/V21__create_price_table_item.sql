-- Preço de um produto dentro de uma tabela de preço.
CREATE TABLE price_table_item (
    id             BIGSERIAL PRIMARY KEY,
    price_table_id BIGINT NOT NULL REFERENCES price_table(id),
    product_id     BIGINT NOT NULL REFERENCES product(id),
    price          NUMERIC(10, 2) NOT NULL,
    UNIQUE (price_table_id, product_id)
);
