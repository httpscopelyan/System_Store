ALTER TABLE cash_register ADD COLUMN expected_pix NUMERIC(10,2);
ALTER TABLE cash_register ADD COLUMN counted_pix NUMERIC(10,2);
ALTER TABLE cash_register ADD COLUMN expected_credit NUMERIC(10,2);
ALTER TABLE cash_register ADD COLUMN counted_credit NUMERIC(10,2);
ALTER TABLE cash_register ADD COLUMN expected_debit NUMERIC(10,2);
ALTER TABLE cash_register ADD COLUMN counted_debit NUMERIC(10,2);

CREATE TABLE cash_register_reopening (
    id BIGSERIAL PRIMARY KEY,
    cash_register_id BIGINT NOT NULL REFERENCES cash_register(id),
    reopened_at TIMESTAMP NOT NULL,
    reason VARCHAR(155) NOT NULL,
    previous_closed_at TIMESTAMP,
    previous_expected_amount NUMERIC(10,2),
    previous_closing_amount NUMERIC(10,2),
    previous_difference NUMERIC(10,2),
    closing_receipt TEXT NOT NULL
);
