CREATE TABLE sale (
    id BIGSERIAL PRIMARY KEY,
    operator_id BIGINT NOT NULL REFERENCES users(id),
    cash_register_id BIGINT NOT NULL REFERENCES cash_register(id),
    created_at TIMESTAMP NOT NULL,
    total NUMERIC(10,2) NOT NULL,
    status VARCHAR(10) NOT NULL
);