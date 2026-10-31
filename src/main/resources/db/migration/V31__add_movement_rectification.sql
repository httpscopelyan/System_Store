ALTER TABLE cash_movement ADD COLUMN corrected_movement_id BIGINT REFERENCES cash_movement(id);
ALTER TABLE cash_movement ADD COLUMN authorized_by_id BIGINT REFERENCES users(id);

ALTER TABLE stock_movement ALTER COLUMN type TYPE VARCHAR(12);
ALTER TABLE stock_movement ADD COLUMN corrected_movement_id BIGINT REFERENCES stock_movement(id);
ALTER TABLE stock_movement ADD COLUMN authorized_by_id BIGINT REFERENCES users(id);
