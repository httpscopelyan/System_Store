-- ===========================================================================
-- Seed de DESENVOLVIMENTO para a automacao E2E do G2 (quantidade/peso).
-- Roteiro: tests/e2e/roteiros/G2_quantidade_peso.md
--
-- NAO e migration Flyway. NAO colocar em src/main/resources/db/migration.
-- Rodar A MAO no banco de dev (container Docker db_scopel), por exemplo:
--     docker exec -i db_scopel psql -U <user> -d <db> < tests/e2e/seed_g2.sql
--
-- IMPORTANTE (regra inviolavel CLAUDE.md §4.3): usuario NUNCA por SQL cru,
-- senha sempre via UserService/BCrypt. Por isso este seed cria APENAS produtos
-- (nao ha senha envolvida). O operador caixa1 e o caixa ABERTO sao pre-condicoes
-- preparadas por outro caminho -- ver o cabecalho de g2_quantidade_peso.py e o
-- README.md (reaproveitar o caixa1 ja seedado nas rodadas do G1).
--
-- Os INSERTs sao idempotentes (ON CONFLICT no code): pode rodar varias vezes.
-- Estoque proposital FOLGADO: o script faz varias adicoes e nao deve esbarrar
-- em recusa por falta de estoque (o foco do G2 nao e estoque).
-- ===========================================================================

-- Produto UN: Refrigerante Lata 350ml, R$ 5,00, vendido por UNIDADE.
--   6 unidades = R$ 30,00 (fluxo A); recusa fracao (fluxo D).
INSERT INTO product (code, description, price, quantity, unit, active, cost_price, min_stock, category)
VALUES ('7891234', 'Refrigerante Lata 350ml', 5.00, 500.000, 'UN', TRUE, 0.00, 0.000, 'Bebidas')
ON CONFLICT (code) DO UPDATE SET
    description = EXCLUDED.description,
    price       = EXCLUDED.price,
    quantity    = EXCLUDED.quantity,
    unit        = EXCLUDED.unit,
    active      = TRUE;

-- Produto KG: Banana Prata (granel), R$ 6,00/kg, vendido por PESO.
--   1,250 kg = R$ 7,50 (fluxo B); aceita ate 3 casas decimais.
INSERT INTO product (code, description, price, quantity, unit, active, cost_price, min_stock, category)
VALUES ('7899', 'Banana Prata (granel)', 6.00, 200.000, 'KG', TRUE, 0.00, 0.000, 'Hortifruti')
ON CONFLICT (code) DO UPDATE SET
    description = EXCLUDED.description,
    price       = EXCLUDED.price,
    quantity    = EXCLUDED.quantity,
    unit        = EXCLUDED.unit,
    active      = TRUE;
