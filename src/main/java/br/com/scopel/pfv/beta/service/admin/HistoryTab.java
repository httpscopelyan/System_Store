package br.com.scopel.pfv.beta.service.admin;

public enum HistoryTab {

    AUDIT("Auditoria geral", true, """
            SELECT a.id AS "Nº", a.occurred_at AS "Data/hora", a.actor_name AS "Quem", a.action AS "Ação",
                   a.entity_type AS "Tipo", a.entity_id AS "Registro", a.description AS "Descrição",
                   a.previous_value AS "Antes", a.new_value AS "Depois"
            FROM audit_log a
            """),

    SALES("Vendas", true, """
            SELECT s.id AS "Nº", s.created_at AS "Data/hora", u.name AS "Vendedor", s.status AS "Situação",
                   s.total AS "Total", s.discount AS "Desconto", s.surcharge AS "Acréscimo",
                   c.name AS "Cliente",
                   (SELECT string_agg(DISTINCT p.method, ', ') FROM payment p WHERE p.sale_id = s.id) AS "Pagamento",
                   s.cash_register_id AS "Caixa", au.name AS "Autorizado por", s.cancel_reason AS "Motivo do cancelamento"
            FROM sale s
            JOIN users u ON u.id = s.operator_id
            LEFT JOIN customer c ON c.id = s.customer_id
            LEFT JOIN users au ON au.id = s.authorized_by_id
            """),

    SOLD_ITEMS("Itens vendidos", true, """
            SELECT si.id AS "Nº", s.created_at AS "Data/hora", u.name AS "Vendedor", s.id AS "Venda",
                   p.code AS "Código", p.description AS "Produto", si.quantity AS "Quantidade",
                   si.unit_price AS "Preço unitário", si.discount AS "Desconto", si.total_price AS "Total",
                   s.status AS "Situação da venda"
            FROM sale_item si
            JOIN sale s ON s.id = si.sale_id
            JOIN users u ON u.id = s.operator_id
            JOIN product p ON p.id = si.product_id
            """),

    PAYMENTS("Pagamentos", true, """
            SELECT pay.id AS "Nº", pay.created_at AS "Data/hora", u.name AS "Operador", pay.sale_id AS "Venda",
                   pay.method AS "Forma", pay.card_type AS "Cartão", pay.amount_received AS "Valor recebido",
                   pay.change AS "Troco"
            FROM payment pay
            JOIN sale s ON s.id = pay.sale_id
            JOIN users u ON u.id = s.operator_id
            """),

    STOCK_MOVEMENTS("Alterações de estoque", true, """
            SELECT sm.id AS "Nº", sm.created_at AS "Data/hora", p.code AS "Código", p.description AS "Produto",
                   sm.type AS "Tipo", sm.quantity AS "Quantidade", sm.resulting_stock AS "Estoque resultante",
                   sm.unit_cost AS "Custo unitário", sm.reason AS "Motivo", u.name AS "Operador",
                   au.name AS "Autorizado por", sm.corrected_movement_id AS "Retifica"
            FROM stock_movement sm
            JOIN product p ON p.id = sm.product_id
            LEFT JOIN users u ON u.id = sm.operator_id
            LEFT JOIN users au ON au.id = sm.authorized_by_id
            """),

    CASH_REGISTERS("Caixas", true, """
            SELECT cr.id AS "Nº", cr.opened_at AS "Data/hora", u.name AS "Operador", cr.status AS "Situação",
                   cr.opening_amount AS "Abertura", cr.expected_amount AS "Esperado", cr.closing_amount AS "Contado",
                   cr.difference AS "Diferença", cr.closed_at AS "Fechado em"
            FROM cash_register cr
            JOIN users u ON u.id = cr.operator_id
            """),

    CASH_MOVEMENTS("Movimentações de caixa", true, """
            SELECT cm.id AS "Nº", cm.created_at AS "Data/hora", cm.cash_register_id AS "Caixa", cm.type AS "Tipo",
                   cm.amount AS "Valor", cm.reason AS "Motivo", u.name AS "Operador", au.name AS "Autorizado por"
            FROM cash_movement cm
            LEFT JOIN users u ON u.id = cm.operator_id
            LEFT JOIN users au ON au.id = cm.authorized_by_id
            """),

    DIRECT_CHARGES("Cobranças (aba Cobrar)", true, """
            SELECT dc.id AS "Nº", dc.created_at AS "Data/hora", u.name AS "Operador", dc.amount AS "Valor",
                   dc.method AS "Forma", dc.card_type AS "Cartão", dc.note AS "Observação",
                   dc.cash_register_id AS "Caixa"
            FROM direct_charge dc
            JOIN users u ON u.id = dc.operator_id
            """),

    REOPENINGS("Reaberturas de caixa", true, """
            SELECT r.id AS "Nº", r.reopened_at AS "Data/hora", r.cash_register_id AS "Caixa", r.reason AS "Motivo",
                   r.previous_closed_at AS "Fechado antes em", r.previous_expected_amount AS "Esperado antes",
                   r.previous_closing_amount AS "Contado antes", r.previous_difference AS "Diferença antes"
            FROM cash_register_reopening r
            """),

    PURCHASES("Compras", true, """
            SELECT pu.id AS "Nº", pu.created_at AS "Data/hora", sp.name AS "Fornecedor", u.name AS "Operador",
                   pu.invoice_number AS "Nota", pu.total AS "Total", pu.status AS "Situação",
                   pu.received_at AS "Recebida em"
            FROM purchase pu
            JOIN supplier sp ON sp.id = pu.supplier_id
            LEFT JOIN users u ON u.id = pu.operator_id
            """),

    RECEIVABLES("Contas a receber", true, """
            SELECT r.id AS "Nº", r.created_at AS "Data/hora", c.name AS "Cliente", r.description AS "Descrição",
                   r.due_date AS "Vencimento", r.amount AS "Valor", r.paid_amount AS "Pago", r.status AS "Situação",
                   r.sale_id AS "Venda"
            FROM receivable r
            JOIN customer c ON c.id = r.customer_id
            """),

    PAYABLES("Contas a pagar", true, """
            SELECT pa.id AS "Nº", pa.created_at AS "Data/hora", sp.name AS "Fornecedor", pa.description AS "Descrição",
                   pa.due_date AS "Vencimento", pa.amount AS "Valor", pa.paid_amount AS "Pago", pa.status AS "Situação"
            FROM payable pa
            LEFT JOIN supplier sp ON sp.id = pa.supplier_id
            """),

    PRODUCTS("Produtos (situação atual)", false, """
            SELECT p.id AS "Nº", p.code AS "Código", p.description AS "Produto", p.unit AS "Unidade",
                   p.price AS "Preço de venda", p.cost_price AS "Custo", p.quantity AS "Quantidade",
                   p.min_stock AS "Estoque mínimo", p.category AS "Categoria",
                   CASE WHEN p.active THEN 'Ativo' ELSE 'Inativo' END AS "Situação"
            FROM product p
            """, "q.\"Produto\" ASC"),

    CUSTOMERS("Clientes", false, """
            SELECT c.id AS "Nº", c.name AS "Nome", c.cpf_cnpj AS "CPF/CNPJ", c.phone AS "Telefone",
                   c.address AS "Endereço", c.credit_limit AS "Limite de crédito",
                   CASE WHEN c.credit_blocked THEN 'Sim' ELSE 'Não' END AS "Crédito bloqueado",
                   CASE WHEN c.active THEN 'Ativo' ELSE 'Inativo' END AS "Situação"
            FROM customer c
            """, "q.\"Nome\" ASC"),

    SUPPLIERS("Fornecedores", false, """
            SELECT s.id AS "Nº", s.name AS "Nome", s.cnpj AS "CNPJ", s.phone AS "Telefone", s.email AS "E-mail",
                   CASE WHEN s.active THEN 'Ativo' ELSE 'Inativo' END AS "Situação"
            FROM supplier s
            """, "q.\"Nome\" ASC");

    private static final String TIMED_ORDER = "q.\"Data/hora\" DESC, q.\"Nº\" DESC";

    private final String title;
    private final boolean timed;
    private final String sql;
    private final String orderBy;

    HistoryTab(String title, boolean timed, String sql) {
        this(title, timed, sql, TIMED_ORDER);
    }

    HistoryTab(String title, boolean timed, String sql, String orderBy) {
        this.title = title;
        this.timed = timed;
        this.sql = sql;
        this.orderBy = orderBy;
    }

    public String title() {
        return title;
    }

    public boolean timed() {
        return timed;
    }

    public String sql() {
        return sql;
    }

    public String orderBy() {
        return orderBy;
    }
}
