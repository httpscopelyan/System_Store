ALTER TABLE sale ALTER COLUMN status TYPE VARCHAR(20);

CREATE TABLE payment (
    id BIGSERIAL PRIMARY KEY,
    sale_id BIGINT NOT NULL REFERENCES sale(id),
    method VARCHAR(20) NOT NULL,
    amount_received NUMERIC(10,2) NOT NULL,
    change NUMERIC(10,2) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
