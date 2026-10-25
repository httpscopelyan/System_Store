-- Compras / entrada de mercadoria (PDV Prático: Compras). Ao receber, dá
-- entrada no estoque e atualiza o custo dos produtos.
CREATE TABLE purchase (
    id             BIGSERIAL PRIMARY KEY,
    supplier_id    BIGINT NOT NULL REFERENCES supplier(id),
    operator_id    BIGINT REFERENCES users(id),
    invoice_number VARCHAR(44),              -- nº da nota do fornecedor
    created_at     TIMESTAMP NOT NULL,
    received_at    TIMESTAMP,
    total          NUMERIC(10, 2) NOT NULL,
    status         VARCHAR(10) NOT NULL       -- ABERTA | RECEBIDA | CANCELADA
);
