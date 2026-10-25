-- Itens da compra (PDV Prático: ItensCompra).
CREATE TABLE purchase_item (
    id          BIGSERIAL PRIMARY KEY,
    purchase_id BIGINT NOT NULL REFERENCES purchase(id),
    product_id  BIGINT NOT NULL REFERENCES product(id),
    quantity    NUMERIC(10, 3) NOT NULL,
    unit_cost   NUMERIC(10, 2) NOT NULL,
    total_cost  NUMERIC(10, 2) NOT NULL
);
