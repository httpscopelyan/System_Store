-- Contas a Pagar (PDV Prático: CAP). Opcionalmente ligada a um fornecedor e/ou
-- a uma compra de origem.
CREATE TABLE payable (
    id          BIGSERIAL PRIMARY KEY,
    supplier_id BIGINT REFERENCES supplier(id),
    purchase_id BIGINT REFERENCES purchase(id),
    description VARCHAR(150),
    due_date    DATE NOT NULL,
    amount      NUMERIC(10, 2) NOT NULL,
    paid_amount NUMERIC(10, 2) NOT NULL DEFAULT 0,
    status      VARCHAR(10) NOT NULL,              -- ABERTA | PAGA | CANCELADA
    created_at  TIMESTAMP NOT NULL
);

CREATE INDEX idx_payable_status ON payable (status);
