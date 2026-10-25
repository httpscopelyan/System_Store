-- Contas a Receber / crediário (PDV Prático: CAR, DividaDoCliente, parcelas).
-- Cada linha é uma parcela (título) de um cliente, opcionalmente ligada à venda.
CREATE TABLE receivable (
    id                 BIGSERIAL PRIMARY KEY,
    customer_id        BIGINT NOT NULL REFERENCES customer(id),
    sale_id            BIGINT REFERENCES sale(id),
    description        VARCHAR(150),
    due_date           DATE NOT NULL,
    amount             NUMERIC(10, 2) NOT NULL,
    paid_amount        NUMERIC(10, 2) NOT NULL DEFAULT 0,
    status             VARCHAR(10) NOT NULL,        -- ABERTA | PAGA | CANCELADA
    installment_number INT NOT NULL DEFAULT 1,
    installment_total  INT NOT NULL DEFAULT 1,
    created_at         TIMESTAMP NOT NULL
);

CREATE INDEX idx_receivable_customer_status ON receivable (customer_id, status);
