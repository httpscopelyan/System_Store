package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.controller.support.ShoppingListPresenter;
import br.com.scopel.pfv.beta.exception.NotificationException;
import br.com.scopel.pfv.beta.service.alert.StockAlert;
import br.com.scopel.pfv.beta.service.alert.StockAlertMailService;
import br.com.scopel.pfv.beta.service.alert.StockAlertService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Component
public class ShoppingListController {

    private final StockAlertService stockAlertService;
    private final StockAlertMailService mailService;
    private final Navigator navigator;
    private final UserSession userSession;

    private ShoppingListPresenter presenter;

    @FXML private TableView<StockAlert> table;
    @FXML private TableColumn<StockAlert, String> colCode;
    @FXML private TableColumn<StockAlert, String> colDescription;
    @FXML private TableColumn<StockAlert, String> colStock;
    @FXML private TableColumn<StockAlert, String> colMinStock;
    @FXML private TableColumn<StockAlert, String> colDifference;
    @FXML private TableColumn<StockAlert, String> colRestock;
    @FXML private TableColumn<StockAlert, String> colStatus;
    @FXML private Label messageLabel;
    @FXML private Button sendButton;

    public ShoppingListController(StockAlertService stockAlertService, StockAlertMailService mailService,
                                  Navigator navigator, UserSession userSession) {
        this.stockAlertService = stockAlertService;
        this.mailService = mailService;
        this.navigator = navigator;
        this.userSession = userSession;
    }

    @FXML
    public void initialize() {
        presenter = new ShoppingListPresenter(table, colCode, colDescription, colStock, colMinStock,
                colDifference, colRestock, colStatus);
        onRefresh();
    }

    @FXML
    public void onRefresh() {
        List<StockAlert> alerts = stockAlertService.lowStockItems();
        presenter.show(alerts);
        sendButton.setDisable(!mailService.hasRecipients());
        messageLabel.setText(alerts.size() + " item(ns) para repor — margem de aviso: "
                + stockAlertService.alertMargin().stripTrailingZeros().toPlainString());
    }

    @FXML
    public void onSendNow() {
        sendButton.setDisable(true);
        messageLabel.setText("Enviando e-mail...");
        CompletableFuture.supplyAsync(mailService::sendNow)
                .whenComplete((sentCount, failure) -> Platform.runLater(() -> finishSend(sentCount, failure)));
    }

    @FXML
    public void onEntry() {
        StockAlert selected = presenter.selected();
        if (selected == null) {
            messageLabel.setText("Selecione um item para dar entrada.");
            return;
        }
        userSession.setStockEntryPrefillCode(selected.code());
        navigator.navigate(Page.ENTRADA_ESTOQUE);
    }

    private void finishSend(Integer sentCount, Throwable failure) {
        sendButton.setDisable(!mailService.hasRecipients());
        if (failure != null) {
            Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                    ? failure.getCause()
                    : failure;
            messageLabel.setText(cause instanceof NotificationException
                    ? cause.getMessage()
                    : "Não foi possível enviar o e-mail.");
            return;
        }
        messageLabel.setText(sentCount == 0 ? "Nada a enviar: nenhum item abaixo do mínimo." : "E-mail enviado.");
    }
}
