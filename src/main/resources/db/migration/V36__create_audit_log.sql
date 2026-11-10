CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMP NOT NULL,
    actor_id BIGINT REFERENCES users(id),
    actor_name VARCHAR(50) NOT NULL,
    action VARCHAR(40) NOT NULL,
    entity_type VARCHAR(30) NOT NULL,
    entity_id BIGINT,
    description VARCHAR(255) NOT NULL,
    previous_value VARCHAR(255),
    new_value VARCHAR(255)
);

CREATE INDEX idx_audit_log_occurred_at ON audit_log (occurred_at DESC, id DESC);
CREATE INDEX idx_audit_log_actor ON audit_log (actor_id, occurred_at DESC, id DESC);
