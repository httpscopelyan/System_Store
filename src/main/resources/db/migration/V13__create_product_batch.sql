-- Lotes de validade (PDV Prático: LotesProduto). Controle de vencimento por
-- lote do produto. Disponível = lote ainda vendável.
CREATE TABLE product_batch (
    id             BIGSERIAL PRIMARY KEY,
    product_id     BIGINT NOT NULL REFERENCES product(id),
    batch_code     VARCHAR(40),
    expiry_date    DATE NOT NULL,
    quantity_entry NUMERIC(10, 3) NOT NULL,
    quantity_sold  NUMERIC(10, 3) NOT NULL DEFAULT 0,
    available      BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_product_batch_expiry ON product_batch (expiry_date) WHERE available;
