package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.BetaApplication;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.exception.CashRegisterAlreadyOpenException;
import br.com.scopel.pfv.beta.exception.InsufficientCashException;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;

@Component
public class CaixaController {

    private final CashRegisterService cashRegisterService;
    private final UserSession userSession;

    private CashRegister caixaAtual;

    @FXML private TextField openingAmountField;
    @FXML private TextField movementAmountField;
    @FXML private TextField countedAmountField;
    @FXML private Label statusLabel;

    public CaixaController(CashRegisterService cashRegisterService, UserSession userSession) {
        this.cashRegisterService = cashRegisterService;
        this.userSession = userSession;
    }

    @FXML
    public void onOpen() {
        try {
            User operator = userSession.getLoggedUser();
            BigDecimal openingAmount = new BigDecimal(openingAmountField.getText());

            caixaAtual = cashRegisterService.open(operator, openingAmount);
            userSession.setCaixaAtual(caixaAtual);
            statusLabel.setText("Caixa aberto! Saldo: " + cashRegisterService.currentBalance(caixaAtual));
            openingAmountField.clear();


            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/create_product.fxml"));
            loader.setControllerFactory(BetaApplication.springContext::getBean);
            Scene scene = new Scene(loader.load());
            Stage stage = (Stage) statusLabel.getScene().getWindow();
            stage.setScene(scene);

        } catch (NumberFormatException e) {
            statusLabel.setText("Valor inválido");
        } catch (CashRegisterAlreadyOpenException e) {
            statusLabel.setText("Já existe um caixa aberto");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @FXML
    public void onSupplement() {
        try {
            BigDecimal amount = new BigDecimal(movementAmountField.getText());
            cashRegisterService.supplement(caixaAtual.getId(), amount, "reforço");
            statusLabel.setText("Suprimento registrado! Saldo: " + cashRegisterService.currentBalance(caixaAtual));
            movementAmountField.clear();


        } catch (NumberFormatException e) {
            statusLabel.setText("Valor inválido");
        } catch (NullPointerException e) {
            statusLabel.setText("Abra um caixa primeiro");
        }
    }

    @FXML
    public void onWithdrawal() {
        try {
            BigDecimal amount = new BigDecimal(movementAmountField.getText());
            cashRegisterService.withdrawal(caixaAtual.getId(), amount, "retirada");
            statusLabel.setText("Sangria registrada! Saldo: " + cashRegisterService.currentBalance(caixaAtual));
            movementAmountField.clear();


        } catch (NumberFormatException e) {
            statusLabel.setText("Valor inválido");
        } catch (InsufficientCashException e) {
            statusLabel.setText("Saldo insuficiente para a sangria");
        } catch (NullPointerException e) {
            statusLabel.setText("Abra um caixa primeiro");
        }
    }

    @FXML
    public void onClose() {
        try {
            BigDecimal counted = new BigDecimal(countedAmountField.getText());
            CashRegister fechado = cashRegisterService.close(caixaAtual.getId(), counted);

            statusLabel.setText("Caixa fechado! Diferença: " + fechado.getDifference());
            caixaAtual = null;
            userSession.setCaixaAtual(null);
            countedAmountField.clear();

        } catch (NumberFormatException e) {
            statusLabel.setText("Valor inválido");
        } catch (NullPointerException e) {
            statusLabel.setText("Abra um caixa primeiro");
        }
    }
}