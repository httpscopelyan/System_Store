-- Documento fiscal eletrônico (NFC-e) vinculado à venda (PDV Prático: cupom
-- fiscal). Guarda chave de acesso, protocolo, XML e status. A EMISSÃO real
-- (assinatura com certificado + SEFAZ) é integração externa da Fase 3; esta
-- tabela é o armazenamento legal (RF52 / RNF16: guardar XML >= 5 anos).
CREATE TABLE fiscal_document (
    id            BIGSERIAL PRIMARY KEY,
    sale_id       BIGINT NOT NULL REFERENCES sale(id),
    status        VARCHAR(12) NOT NULL,       -- PENDENTE|AUTORIZADA|REJEITADA|CANCELADA|CONTINGENCIA
    access_key    VARCHAR(44),
    protocol      VARCHAR(20),
    consumer_cpf  VARCHAR(14),
    number        BIGINT,
    series        INT,
    xml           TEXT,
    error_message VARCHAR(255),
    created_at    TIMESTAMP NOT NULL,
    issued_at     TIMESTAMP
);

CREATE INDEX idx_fiscal_document_sale ON fiscal_document (sale_id);
