-- Configurações do sistema (PDV Prático: tela ConfigADM). Armazenadas como
-- chave/valor em texto; os services leem com tipagem (boolean/int/decimal).
CREATE TABLE app_setting (
    id            BIGSERIAL PRIMARY KEY,
    setting_key   VARCHAR(60) NOT NULL UNIQUE,
    setting_value VARCHAR(255)
);

-- Valores padrão observados no PDV Prático.
INSERT INTO app_setting (setting_key, setting_value) VALUES
    ('sale.repeat_item',             'true'),   -- pode repetir item na venda?
    ('sale.allow_negative_stock',    'false'),  -- vender com estoque negativo?
    ('sale.show_stock_qty',          'true'),   -- exibir qtd. em estoque?
    ('sale.discount_limit_percent',  '10'),     -- limite de desconto (%) sem gerente
    ('stock.control_validity',       'false'),  -- controlar validade (lotes)?
    ('credit.allow_credit_sale',     'false'),  -- emitir vendas a prazo?
    ('credit.block_overdue',         'true');   -- bloquear venda a prazo p/ cliente em atraso?
