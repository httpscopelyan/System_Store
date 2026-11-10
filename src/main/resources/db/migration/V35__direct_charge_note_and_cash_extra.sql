ALTER TABLE direct_charge ADD COLUMN note VARCHAR(155);

ALTER TABLE cash_register ADD COLUMN expected_extra NUMERIC(10,2);
ALTER TABLE cash_register ADD COLUMN counted_extra NUMERIC(10,2);
ALTER TABLE cash_register ADD COLUMN extra_note VARCHAR(155);
