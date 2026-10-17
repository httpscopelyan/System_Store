CREATE TABLE cash_movement (

    id BIGSERIAL PRIMARY KEY,
    cash_register_id BIGINT NOT NULL REFERENCES cash_register(id),
    type VARCHAR(11) NOT NULL,
    amount NUMERIC(10, 2) NOT NULL,
    reason VARCHAR(155),
    created_at TIMESTAMP NOT NULL

);