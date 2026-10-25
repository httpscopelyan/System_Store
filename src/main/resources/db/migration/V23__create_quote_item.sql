-- Itens do orçamento.
CREATE TABLE quote_item (
    id          BIGSERIAL PRIMARY KEY,
    quote_id    BIGINT NOT NULL REFERENCES quote(id),
    product_id  BIGINT NOT NULL REFERENCES product(id),
    quantity    NUMERIC(10, 3) NOT NULL,
    unit_price  NUMERIC(10, 2) NOT NULL,
    total_price NUMERIC(10, 2) NOT NULL
);
