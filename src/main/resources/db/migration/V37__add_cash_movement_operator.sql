ALTER TABLE cash_movement ADD COLUMN operator_id BIGINT REFERENCES users(id);
