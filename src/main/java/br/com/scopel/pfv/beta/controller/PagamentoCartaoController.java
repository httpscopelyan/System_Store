package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.Sale;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Escolha do tipo de cartão (Item 5): DÉBITO (1) ou CRÉDITO (2). Guarda o
 * {@link CardType} na sessão e segue para a tela de espera da maquininha.
 */
@Component
public class PagamentoCartaoController {

    private final UserSession userSession;
    private final Navigator navigator;

    private static final DecimalFormat MONEY =
            new DecimalFormat("#,##0.00", new DecimalFormatSymbols(new Locale("pt", "BR")));

    @FXML private Label totalLabel;
    @FXML private Node debitoOption;

    public PagamentoCartaoController(UserSession userSession, Navigator navigator) {
        this.userSession = userSession;
        this.navigator = navigator;
    }

    @FXML
    public void initialize() {
        Sale venda = userSession.getVendaAtual();
        totalLabel.setText("R$ " + MONEY.format(venda == null ? BigDecimal.ZERO : venda.getTotal()));
        Platform.runLater(() -> {
            if (debitoOption != null) {
                debitoOption.requestFocus();
            }
        });
    }

    @FXML
    public void onKey(KeyEvent e) {
        switch (e.getCode()) {
            case DIGIT1, NUMPAD1 -> onDebito();
            case DIGIT2, NUMPAD2 -> onCredito();
            case ESCAPE -> onVoltar();
            default -> {
                return;
            }
        }
        e.consume();
    }

    @FXML
    public void onDebito() {
        selectAndWait(CardType.DEBITO);
    }

    @FXML
    public void onCredito() {
        selectAndWait(CardType.CREDITO);
    }

    @FXML
    public void onVoltar() {
        navigator.navigate(Page.PAGAMENTO);
    }

    private void selectAndWait(CardType type) {
        userSession.setCardTypeSelecionado(type);
        navigator.navigate(Page.PAGAMENTO_ESPERA);
    }
}
