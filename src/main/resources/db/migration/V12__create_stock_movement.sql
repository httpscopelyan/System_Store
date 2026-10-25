-- Histórico de movimentação de estoque (PDV Prático: entrada/ajuste + histórico
-- do produto). Toda alteração de estoque fora da venda é auditada aqui.
CREATE TABLE stock_movement (
    id              BIGSERIAL PRIMARY KEY,
    product_id      BIGINT NOT NULL REFERENCES product(id),
    type            VARCHAR(10) NOT NULL,       -- ENTRADA | SAIDA | AJUSTE
    quantity        NUMERIC(10, 3) NOT NULL,    -- sempre positivo; o type dá o sentido
    unit_cost       NUMERIC(10, 2),             -- custo na entrada (opcional)
    reason          VARCHAR(120),
    operator_id     BIGINT REFERENCES users(id),
    created_at      TIMESTAMP NOT NULL,
    resulting_stock NUMERIC(10, 3) NOT NULL      -- estoque do produto após o movimento
);

CREATE INDEX idx_stock_movement_product ON stock_movement (product_id, created_at DESC);
