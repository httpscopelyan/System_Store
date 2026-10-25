package br.com.scopel.pfv.beta.config;

/**
 * Páginas que vivem no centro do shell (main_layout). Login não entra aqui:
 * ele é a raiz da Scene antes do shell existir.
 */
public enum Page {

    INICIO("/fxml/inicio.fxml"),
    VENDA("/fxml/sale.fxml"),
    CONSULTA_PRECO("/fxml/price_check.fxml"),
    CAIXA("/fxml/caixa.fxml"),
    PRODUTOS("/fxml/create_product.fxml"),
    EDITAR_PRODUTO("/fxml/edit_product.fxml"),
    ESTOQUE("/fxml/estoque.fxml"),
    CLIENTES("/fxml/customer_form.fxml"),
    FORNECEDORES("/fxml/supplier_form.fxml"),
    ENTRADA_ESTOQUE("/fxml/stock_entry.fxml"),
    AJUSTE_ESTOQUE("/fxml/stock_adjust.fxml"),
    LOTES("/fxml/batch_form.fxml"),
    ETIQUETAS("/fxml/label_form.fxml"),
    AJUSTE_PRECOS("/fxml/price_adjust.fxml"),
    TABELAS_PRECO("/fxml/price_table_form.fxml"),
    CONFIG("/fxml/config_form.fxml"),
    COMPRAS("/fxml/purchase_form.fxml"),
    ORCAMENTO("/fxml/quote_form.fxml"),
    CONTAS_RECEBER("/fxml/receivable_list.fxml"),
    CONTAS_PAGAR("/fxml/payable_list.fxml"),
    RELATORIOS("/fxml/report_view.fxml"),
    PAGAMENTO("/fxml/pagamento.fxml"),
    PAGAMENTO_DINHEIRO("/fxml/pagamento_dinheiro.fxml"),
    PAGAMENTO_CARTAO("/fxml/pagamento_cartao.fxml"),
    PAGAMENTO_ESPERA("/fxml/pagamento_espera.fxml"),
    PAGAMENTO_PIX("/fxml/pagamento_pix.fxml"),
    VENDA_FINALIZADA("/fxml/venda_finalizada.fxml"),
    HISTORICO_VENDAS("/fxml/historico_vendas.fxml");

    private final String path;

    Page(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
