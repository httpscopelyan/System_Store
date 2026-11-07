package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.service.ReceiptService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class ConfigPrinterController {

    private final ReceiptService receiptService;

    @FXML private Button testButton;
    @FXML private Label messageLabel;

    public ConfigPrinterController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @FXML
    public void onPrintTest() {
        testButton.setDisable(true);
        messageLabel.setText("Enviando a página de teste para a impressora...");
        CompletableFuture.runAsync(receiptService::printPrinterTest).whenComplete((result, failure) ->
                Platform.runLater(() -> {
                    testButton.setDisable(false);
                    messageLabel.setText(failure == null
                            ? "Página de teste enviada. Confira se saiu o papel escrito; se não saiu, verifique energia, papel e cabo."
                            : "Falha ao imprimir: " + rootMessage(failure));
                }));
    }

    private String rootMessage(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
