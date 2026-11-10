package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.exception.ElectronicPaymentReceivedException;
import br.com.scopel.pfv.beta.exception.IllegalSaleStateException;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.service.SaleService;
import br.com.scopel.pfv.beta.service.SplitPaymentService;
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
    private final SplitPaymentService splitPaymentService;

    private Sale vendaAtual;

    private static final DecimalFormat MONEY =
            new DecimalFormat("#,##0.00", new DecimalFormatSymbols(new Locale("pt", "BR")));

    @FXML private BorderPane root;
    @FXML private Label totalLabel;
    @FXML private Label statusLabel;
    @FXML private Node dinheiroOption;

    public PaymentController(UserSession userSession, Navigator navigator, SaleService saleService,
                             SplitPaymentService splitPaymentService) {
        this.userSession = userSession;
        this.navigator = navigator;
        this.saleService = saleService;
        this.splitPaymentService = splitPaymentService;
    }

    @FXML
    public void initialize() {
        navigator.setPageBackHandler(this::onVoltar);
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
            case DIGIT4, NUMPAD4 -> onDividir();
            case ESCAPE -> onVoltar();
            default -> {
                return;
            }
        }
        e.consume();
    }

    @FXML
    public void onDinheiro() {
        navigateToSingleOrSplit(Page.PAGAMENTO_DINHEIRO);
    }

    @FXML
    public void onCartao() {
        navigateToSingleOrSplit(Page.PAGAMENTO_CARTAO);
    }

    @FXML
    public void onPix() {
        navigateToSingleOrSplit(Page.PAGAMENTO_PIX);
    }

    @FXML
    public void onDividir() {
        navigator.navigate(Page.PAGAMENTO_DIVIDIDO);
    }

    @FXML
    public void onVoltar() {
        try {
            if (vendaAtual != null && vendaAtual.getStatus() == Sale.Status.EM_PAGAMENTO) {
                vendaAtual = saleService.reopenForEditing(vendaAtual);
                userSession.setVendaAtual(vendaAtual);
            }
        } catch (ElectronicPaymentReceivedException electronicReceived) {
            statusLabel.setText(electronicReceived.getMessage());
            return;
        } catch (IllegalSaleStateException saleNotInPayment) {
            statusLabel.setText("Esta venda não está mais em pagamento. Abra a tela de venda novamente.");
            return;
        }
        navigator.navigate(Page.VENDA);
    }

    private void navigateToSingleOrSplit(Page singlePaymentPage) {
        boolean partsAlreadyRegistered = vendaAtual != null && splitPaymentService.hasRegisteredParts(vendaAtual);
        navigator.navigate(partsAlreadyRegistered ? Page.PAGAMENTO_DIVIDIDO : singlePaymentPage);
    }

    private String formatMoney(BigDecimal value) {
        return "R$ " + MONEY.format(value == null ? BigDecimal.ZERO : value);
    }
}
