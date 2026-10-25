-- Tabelas de preço (PDV Prático: TabelasPreco). Ex.: Varejo, Atacado.
CREATE TABLE price_table (
    id     BIGSERIAL PRIMARY KEY,
    name   VARCHAR(60) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE
);
