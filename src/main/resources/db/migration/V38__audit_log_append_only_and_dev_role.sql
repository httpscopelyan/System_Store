CREATE FUNCTION audit_log_block_update() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log e somente de insercao: registros nao podem ser alterados';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_no_update
    BEFORE UPDATE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_block_update();

INSERT INTO audit_log (occurred_at, actor_id, actor_name, action, entity_type, entity_id, description, previous_value, new_value)
SELECT NOW(), NULL, 'Sistema', 'USER_ROLE_CHANGED', 'USER', id,
       'Perfil de ' || login || ' alterado para DEV', role, 'DEV'
FROM users
WHERE lower(login) = 'yan' AND role <> 'DEV';

UPDATE users SET role = 'DEV' WHERE lower(login) = 'yan';
