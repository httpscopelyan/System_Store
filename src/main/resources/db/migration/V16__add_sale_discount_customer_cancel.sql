-- Venda+: cliente (tele-entrega), desconto/acréscimo (RF19/RF20),
-- cancelamento com registro (RN17) e gerente autorizador (RN08/RF41).
ALTER TABLE sale
    ADD COLUMN customer_id      BIGINT REFERENCES customer(id),
    ADD COLUMN discount         NUMERIC(10, 2) NOT NULL DEFAULT 0,
    ADD COLUMN surcharge        NUMERIC(10, 2) NOT NULL DEFAULT 0,
    ADD COLUMN cancel_reason    VARCHAR(200),
    ADD COLUMN canceled_at      TIMESTAMP,
    ADD COLUMN authorized_by_id BIGINT REFERENCES users(id);

-- Desconto por item (RF19).
ALTER TABLE sale_item
    ADD COLUMN discount NUMERIC(10, 2) NOT NULL DEFAULT 0;
