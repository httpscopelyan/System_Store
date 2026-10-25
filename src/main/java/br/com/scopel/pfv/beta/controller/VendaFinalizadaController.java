package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.model.PaymentMethod;
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
 * Tela de confirmação "Venda finalizada" (Item 3). Lê os holders da sessão
 * (última venda, forma e troco) preenchidos pelo controller de pagamento.
 * Enter / "Nova venda" abre nova venda; "Início" volta ao menu.
 */
@Component
public class VendaFinalizadaController {

    private final UserSession userSession;
    private final Navigator navigator;

    private static final DecimalFormat MONEY =
            new DecimalFormat("#,##0.00", new DecimalFormatSymbols(new Locale("pt", "BR")));

    @FXML private Label totalLabel;
    @FXML private Label methodLabel;
    @FXML private Label changeCaption;
    @FXML private Label changeLabel;
    @FXML private Node novaVendaButton;

    public VendaFinalizadaController(UserSession userSession, Navigator navigator) {
        this.userSession = userSession;
        this.navigator = navigator;
    }

    @FXML
    public void initialize() {
        Sale venda = userSession.getUltimaVenda();
        PaymentMethod forma = userSession.getUltimaForma();
        BigDecimal troco = userSession.getUltimoTroco();

        totalLabel.setText("R$ " + MONEY.format(venda == null ? BigDecimal.ZERO : venda.getTotal()));
        methodLabel.setText(formaLabel(forma));

        boolean mostrarTroco = forma == PaymentMethod.DINHEIRO && troco != null;
        changeCaption.setVisible(mostrarTroco);
        changeCaption.setManaged(mostrarTroco);
        changeLabel.setVisible(mostrarTroco);
        changeLabel.setManaged(mostrarTroco);
        if (mostrarTroco) {
            changeLabel.setText("R$ " + MONEY.format(troco));
        }

        Platform.runLater(() -> {
            if (novaVendaButton != null) {
                novaVendaButton.requestFocus();
            }
        });
    }

    @FXML
    public void onKey(KeyEvent e) {
        if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
            onNovaVenda();
            e.consume();
        }
    }

    @FXML
    public void onNovaVenda() {
        navigator.navigate(Page.VENDA);
    }

    @FXML
    public void onInicio() {
        navigator.navigate(Page.INICIO);
    }

    private String formaLabel(PaymentMethod forma) {
        if (forma == null) {
            return "PAGAMENTO DIVIDIDO";
        }
        return switch (forma) {
            case DINHEIRO -> "DINHEIRO";
            case CARTAO -> "CARTÃO";
            case PIX -> "PIX";
            case PRAZO -> "A PRAZO (CREDIÁRIO)";
        };
    }
}
