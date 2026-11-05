package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.controller.support.DrawerShortcut;
import br.com.scopel.pfv.beta.controller.support.FullscreenShortcut;
import br.com.scopel.pfv.beta.controller.support.UpdateNotice;
import br.com.scopel.pfv.beta.hardware.PrinterPort;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.UpdateService;
import br.com.scopel.pfv.beta.service.alert.StockAlertService;
import javafx.application.Platform;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
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
import java.util.concurrent.CompletableFuture;

@Component
public class MainController {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM 'de' yyyy  HH:mm:ss", PT_BR);

    private final Navigator navigator;
    private final UserSession userSession;
    private final UpdateService updateService;
    private final StockAlertService stockAlertService;

    @FXML private BorderPane root;
    @FXML private Button updateButton;
    @FXML private Button configButton;
    @FXML private Button backButton;
    @FXML private Button shoppingListButton;
    @FXML private Label dateTimeLabel;
    @FXML private Label userLabel;
    @FXML private Label roleLabel;
    @FXML private Label caixaStatusLabel;
    @FXML private Label terminalLabel;
    @FXML private Label hintLabel;

    private Timeline hintClear;
    private UpdateNotice updateNotice;
    private final PrinterPort printer;

    public MainController(Navigator navigator, UserSession userSession, UpdateService updateService,
                          StockAlertService stockAlertService, PrinterPort printer) {
        this.printer = printer;
        this.navigator = navigator;
        this.userSession = userSession;
        this.updateService = updateService;
        this.stockAlertService = stockAlertService;
    }

    @FXML
    public void initialize() {
        navigator.bind(root);
        navigator.addNavigationListener(this::refreshBackButton);
        navigator.addNavigationListener(this::refreshShoppingListCount);
        refreshBackButton();
        refreshShoppingListCount();

        User user = userSession.getLoggedUser();
        userLabel.setText("Usuário: " + (user != null ? user.getName() : "-"));
        roleLabel.setText("Grupo: " + (user != null ? user.getRole() : "-"));
        terminalLabel.setText("Terminal: CAIXA 01");

        refreshCaixaStatus();

        boolean gerente = user != null && user.getRole() == User.Role.GERENTE;
        if (configButton != null) {
            configButton.setVisible(gerente);
            configButton.setManaged(gerente);
        }

        startClock();
        setupShortcuts();
        updateNotice = new UpdateNotice(updateService, updateButton, this::hasSaleInProgress, this::showHint);
        updateNotice.setup();
    }

    @FXML
    public void onUpdateAndRestart() {
        updateNotice.updateAndRestart();
    }

    @FXML
    public void onNavigate(ActionEvent event) {
        Page page = Page.valueOf((String) ((Node) event.getSource()).getUserData());
        navigator.navigate(page);
    }

    @FXML
    public void onBack() {
        navigator.back();
    }

    private void refreshShoppingListCount() {
        if (shoppingListButton == null) {
            return;
        }
        CompletableFuture.supplyAsync(stockAlertService::countLowStockItems)
                .thenAccept(count -> Platform.runLater(() -> shoppingListButton.setText(
                        count > 0 ? "🧺\nLista d/ Compras (" + count + ")" : "🧺\nLista d/ Compras")))
                .exceptionally(failure -> null);
    }

    private void refreshBackButton() {
        if (backButton != null) {
            backButton.setDisable(!navigator.canGoBack());
        }
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
        root.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene == null) return;
            new DrawerShortcut(printer, this::showHint).install(scene);
            FullscreenShortcut.install(scene);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F1), this::onConsultaPreco);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F2), this::onVender);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F3), () -> navigator.navigate(Page.ESTOQUE));
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F4), () -> navigator.navigate(Page.CAIXA));
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F5), () -> navigator.navigate(Page.COMPRAS));
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F6), () -> navigator.navigate(Page.CLIENTES));
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F7), () -> navigator.navigate(Page.FORNECEDORES));
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F8), () -> navigator.navigate(Page.RELATORIOS));
        });
    }

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
        navigator.navigate(Page.VENDA);
    }

}
