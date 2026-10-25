-- Orçamentos (PDV Prático: Orcamentos). Pode virar venda (convertToSale).
CREATE TABLE quote (
    id          BIGSERIAL PRIMARY KEY,
    customer_id BIGINT REFERENCES customer(id),
    operator_id BIGINT REFERENCES users(id),
    created_at  TIMESTAMP NOT NULL,
    valid_until DATE,
    total       NUMERIC(10, 2) NOT NULL,
    status      VARCHAR(12) NOT NULL          -- ABERTO | CONVERTIDO | CANCELADO
);
