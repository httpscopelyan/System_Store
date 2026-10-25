-- Cadastro de clientes (PDV Prático: Clientes). Inclui dados de crediário
-- (limite e bloqueio), usados nas vendas a prazo (CAR/crediário, Inc 6).
CREATE TABLE customer (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(100) NOT NULL,
    cpf_cnpj       VARCHAR(14) UNIQUE,
    phone          VARCHAR(20),
    address        VARCHAR(150),
    credit_limit   NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    credit_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW()
);
