-- Cadastro de fornecedores (PDV Prático: Fornecedores). Referenciado pelas
-- compras / entrada de mercadoria (Inc 3).
CREATE TABLE supplier (
    id     BIGSERIAL PRIMARY KEY,
    name   VARCHAR(100) NOT NULL,
    cnpj   VARCHAR(14) UNIQUE,
    phone  VARCHAR(20),
    email  VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT TRUE
);
