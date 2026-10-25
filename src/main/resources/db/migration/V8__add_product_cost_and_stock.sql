-- Campos vindos do PDV Prático: preço de custo (p/ cálculo de lucro),
-- estoque mínimo (farol de reposição) e categoria/departamento.
ALTER TABLE product
    ADD COLUMN cost_price NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN min_stock  NUMERIC(10, 3) NOT NULL DEFAULT 0,
    ADD COLUMN category   VARCHAR(50);
