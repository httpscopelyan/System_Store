package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.service.SaleService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Tela de SELEÇÃO da forma de pagamento (Item 5). O TOTAL aparece grande e as
 * três formas em escopo (1-DINHEIRO, 2-CARTÃO, 3-PIX) levam a telas dedicadas.
 * O controller só orquestra a navegação; a regra de negócio é do service.
 */
@Component
public class PaymentController {

    private final UserSession userSession;
    private final Navigator navigator;
    private final SaleService saleService;

    private Sale vendaAtual;

    private static final DecimalFormat MONEY =
            new DecimalFormat("#,##0.00", new DecimalFormatSymbols(new Locale("pt", "BR")));

    @FXML private BorderPane root;
    @FXML private Label totalLabel;
    @FXML private Label statusLabel;
    @FXML private Node dinheiroOption;

    public PaymentController(UserSession userSession, Navigator navigator, SaleService saleService) {
        this.userSession = userSession;
        this.navigator = navigator;
        this.saleService = saleService;
    }

    @FXML
    public void initialize() {
        vendaAtual = userSession.getVendaAtual();
        if (vendaAtual != null) {
            totalLabel.setText(formatMoney(vendaAtual.getTotal()));
        }
        statusLabel.setText("");

        Platform.runLater(() -> {
            if (dinheiroOption != null) {
                dinheiroOption.requestFocus();
            }
        });
    }

    /** Teclas: 1/2/3 escolhem a forma de pagamento, ESC volta à venda. */
    @FXML
    public void onKey(KeyEvent e) {
        switch (e.getCode()) {
            case DIGIT1, NUMPAD1 -> onDinheiro();
            case DIGIT2, NUMPAD2 -> onCartao();
            case DIGIT3, NUMPAD3 -> onPix();
            case ESCAPE -> onVoltar();
            default -> {
                return;
            }
        }
        e.consume();
    }

    @FXML
    public void onDinheiro() {
        navigator.navigate(Page.PAGAMENTO_DINHEIRO);
    }

    @FXML
    public void onCartao() {
        navigator.navigate(Page.PAGAMENTO_CARTAO);
    }

    @FXML
    public void onPix() {
        navigator.navigate(Page.PAGAMENTO_PIX);
    }

    /**
     * Volta para a tela de venda reabrindo a venda para edição (RN21: transição
     * EM_PAGAMENTO → ABERTA via {@code reopenForEditing}). Tolerante a duplo
     * disparo (ESC repetido): se a venda já estiver ABERTA, apenas navega, sem
     * tentar reabrir de novo — evita que a IllegalSaleStateException quebre a UI.
     */
    @FXML
    public void onVoltar() {
        if (vendaAtual != null && vendaAtual.getStatus() == Sale.Status.EM_PAGAMENTO) {
            vendaAtual = saleService.reopenForEditing(vendaAtual);
            userSession.setVendaAtual(vendaAtual);
        }
        navigator.navigate(Page.VENDA);
    }

    private String formatMoney(BigDecimal value) {
        return "R$ " + MONEY.format(value == null ? BigDecimal.ZERO : value);
    }
}
