CREATE TABLE user_permission (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permission VARCHAR(40) NOT NULL,
    PRIMARY KEY (user_id, permission)
);

CREATE TABLE cash_group (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(60) NOT NULL UNIQUE
);

ALTER TABLE users ADD COLUMN cash_group_id BIGINT REFERENCES cash_group(id) ON DELETE SET NULL;

CREATE TEMPORARY TABLE normalized_users ON COMMIT DROP AS
SELECT id, role,
       regexp_replace(translate(lower(trim(name)), 'ãáâàéêíóôõúç', 'aaaaeeiooouc'), '\s+', ' ', 'g') AS n_name,
       regexp_replace(translate(lower(trim(login)), 'ãáâàéêíóôõúç', 'aaaaeeiooouc'), '\s+', ' ', 'g') AS n_login
FROM users;

INSERT INTO user_permission (user_id, permission)
SELECT u.id, p.permission
FROM normalized_users u
CROSS JOIN (VALUES ('REGISTER_PRODUCTS'), ('SEE_COST_FIELDS'), ('EDIT_PRODUCT_DATA')) AS p(permission)
WHERE u.role = 'DEV' OR (u.n_name <> 'ana clara' AND u.n_login <> 'ana clara');

INSERT INTO user_permission (user_id, permission)
SELECT u.id, 'REGISTER_PRODUCTS'
FROM normalized_users u
WHERE u.role <> 'DEV' AND (u.n_name = 'ana clara' OR u.n_login = 'ana clara');

INSERT INTO user_permission (user_id, permission)
SELECT u.id, 'EDIT_PRICES'
FROM normalized_users u
WHERE u.role = 'DEV'
   OR (u.n_name <> 'ana clara' AND u.n_login <> 'ana clara'
       AND (u.role = 'GERENTE' OR u.n_name = 'dayana' OR u.n_login = 'dayana'));

INSERT INTO user_permission (user_id, permission)
SELECT u.id, 'EDIT_STOCK_QUANTITY'
FROM normalized_users u
WHERE u.role = 'DEV'
   OR u.n_name IN ('dayana', 'joao', 'ana clara') OR u.n_login IN ('dayana', 'joao', 'ana clara');

INSERT INTO cash_group (name)
SELECT 'Caixa unificado'
WHERE EXISTS (SELECT 1 FROM normalized_users WHERE n_name IN ('dayana', 'ana clara') OR n_login IN ('dayana', 'ana clara'));

UPDATE users SET cash_group_id = (SELECT id FROM cash_group WHERE name = 'Caixa unificado')
WHERE id IN (SELECT id FROM normalized_users
             WHERE n_name IN ('dayana', 'ana clara') OR n_login IN ('dayana', 'ana clara'))
  AND EXISTS (SELECT 1 FROM cash_group WHERE name = 'Caixa unificado');
