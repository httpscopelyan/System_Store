CREATE TABLE cash_register (
    id BIGSERIAL NOT NULL PRIMARY KEY,
    operator_id BIGINT NOT NULL REFERENCES users(id),
    opening_amount NUMERIC(10,2) NOT NULL ,
    opened_at TIMESTAMP NOT NULL,
    status VARCHAR(7) NOT NULL,
    expected_amount NUMERIC(10, 2),
    closing_amount NUMERIC(10, 2),
    difference NUMERIC(10, 2),
    closed_at TIMESTAMP
)