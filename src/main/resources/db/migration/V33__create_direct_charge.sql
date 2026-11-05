CREATE TABLE direct_charge (
    id BIGSERIAL PRIMARY KEY,
    amount NUMERIC(10,2) NOT NULL,
    method VARCHAR(20) NOT NULL,
    card_type VARCHAR(20),
    operator_id BIGINT NOT NULL REFERENCES users(id),
    cash_register_id BIGINT REFERENCES cash_register(id),
    created_at TIMESTAMP NOT NULL
);
