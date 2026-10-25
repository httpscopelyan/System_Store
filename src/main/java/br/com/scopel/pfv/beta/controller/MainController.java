package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.User;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Controller da casca persistente (main_layout). Monta a barra de ícones,
 * o menu lateral e a barra de status; só orquestra a navegação via Navigator.
 */
@Component
public class MainController {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM 'de' yyyy  HH:mm:ss", PT_BR);

    private final Navigator navigator;
    private final UserSession userSession;

    @FXML private BorderPane root;
    @FXML private Button configButton;
    @FXML private Label dateTimeLabel;
    @FXML private Label userLabel;
    @FXML private Label roleLabel;
    @FXML private Label caixaStatusLabel;
    @FXML private Label terminalLabel;
    @FXML private Label hintLabel;

    // Limpa a dica discreta da barra de status depois de alguns segundos.
    private Timeline hintClear;

    public MainController(Navigator navigator, UserSession userSession) {
        this.navigator = navigator;
        this.userSession = userSession;
    }

    @FXML
    public void initialize() {
        navigator.bind(root);

        User user = userSession.getLoggedUser();
        userLabel.setText("Usuário: " + (user != null ? user.getName() : "-"));
        roleLabel.setText("Grupo: " + (user != null ? user.getRole() : "-"));
        terminalLabel.setText("Terminal: CAIXA 01");

        refreshCaixaStatus();

        // Config (ADM) só para GERENTE.
        boolean gerente = user != null && user.getRole() == User.Role.GERENTE;
        if (configButton != null) {
            configButton.setVisible(gerente);
            configButton.setManaged(gerente);
        }

        startClock();
        setupShortcuts();
    }

    private void refreshCaixaStatus() {
        boolean caixaAberto = userSession.getCaixaAtual() != null;
        caixaStatusLabel.setText(caixaAberto ? "Caixa aberto" : "Caixa fechado");
    }

    private void startClock() {
        Timeline clock = new Timeline(new KeyFrame(Duration.ZERO,
                e -> dateTimeLabel.setText(LocalDateTime.now().format(DATE_TIME))),
                new KeyFrame(Duration.seconds(1)));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();
    }

    private void setupShortcuts() {
        // A scene ainda não existe no initialize(); registra os atalhos uma vez,
        // quando o shell entra numa cena.
        root.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene == null) return;
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F1), this::onConsultaPreco);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F2), this::onVender);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F3), this::onEstoque);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F4), this::onCaixa);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F5), this::onCompras);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F6), this::onClientes);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F7), this::onFornecedores);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F8), this::onRelatorios);
        });
    }

    /**
     * F1 — Consulta de preço rápida (RF07). RN21: NÃO interrompe uma venda em
     * andamento. Considera-se "em andamento" uma venda com itens lançados OU já
     * em pagamento; uma venda inexistente ou apenas aberta e vazia pode ser
     * deixada para consultar o preço. Quando bloqueado, só mostra uma dica
     * discreta e ignora — nada é navegado nem alterado.
     */
    @FXML
    public void onConsultaPreco() {
        if (hasSaleInProgress()) {
            showHint("Finalize ou cancele a venda para consultar preço (F1)");
            return;
        }
        navigator.navigate(Page.CONSULTA_PRECO);
    }

    private boolean hasSaleInProgress() {
        Sale sale = userSession.getVendaAtual();
        if (sale == null) {
            return false;
        }
        boolean hasItems = sale.getItems() != null && !sale.getItems().isEmpty();
        boolean inPayment = sale.getStatus() == Sale.Status.EM_PAGAMENTO;
        return hasItems || inPayment;
    }

    private void showHint(String message) {
        if (hintLabel == null) {
            return;
        }
        hintLabel.setText(message);
        if (hintClear != null) {
            hintClear.stop();
        }
        hintClear = new Timeline(new KeyFrame(Duration.seconds(4), e -> hintLabel.setText("")));
        hintClear.play();
    }

    @FXML
    public void onVender() {
        refreshCaixaStatus();
        // Vender sempre abre a tela de Venda; se não houver caixa aberto, a própria
        // tela avisa (RN14), em vez de redirecionar o operador para o Caixa.
        navigator.navigate(Page.VENDA);
    }

    @FXML
    public void onCaixa() {
        navigator.navigate(Page.CAIXA);
    }

    @FXML
    public void onProdutos() {
        navigator.navigate(Page.PRODUTOS);
    }

    @FXML
    public void onEstoque() {
        navigator.navigate(Page.ESTOQUE);
    }

    @FXML
    public void onClientes() {
        navigator.navigate(Page.CLIENTES);
    }

    @FXML
    public void onFornecedores() {
        navigator.navigate(Page.FORNECEDORES);
    }

    @FXML
    public void onEntradaEstoque() {
        navigator.navigate(Page.ENTRADA_ESTOQUE);
    }

    @FXML
    public void onAjusteEstoque() {
        navigator.navigate(Page.AJUSTE_ESTOQUE);
    }

    @FXML
    public void onLotes() {
        navigator.navigate(Page.LOTES);
    }

    @FXML
    public void onEtiquetas() {
        navigator.navigate(Page.ETIQUETAS);
    }

    @FXML
    public void onAjustePrecos() {
        navigator.navigate(Page.AJUSTE_PRECOS);
    }

    @FXML
    public void onConfig() {
        navigator.navigate(Page.CONFIG);
    }

    @FXML
    public void onCompras() {
        navigator.navigate(Page.COMPRAS);
    }

    @FXML
    public void onOrcamento() {
        navigator.navigate(Page.ORCAMENTO);
    }

    @FXML
    public void onContasReceber() {
        navigator.navigate(Page.CONTAS_RECEBER);
    }

    @FXML
    public void onContasPagar() {
        navigator.navigate(Page.CONTAS_PAGAR);
    }

    @FXML
    public void onTabelasPreco() {
        navigator.navigate(Page.TABELAS_PRECO);
    }

    @FXML
    public void onRelatorios() {
        navigator.navigate(Page.RELATORIOS);
    }

    @FXML
    public void onHistoricoVendas() {
        navigator.navigate(Page.HISTORICO_VENDAS);
    }
}
