package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.controller.support.MoneyFormat;
import br.com.scopel.pfv.beta.service.cash.CashSummary;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import org.springframework.stereotype.Component;

@Component
public class CashSummaryController {

    @FXML private TextField resSaldoInicial;
    @FXML private TextField resEntDinheiro;
    @FXML private TextField resEntPix;
    @FXML private TextField resEntCredito;
    @FXML private TextField resEntDebito;
    @FXML private TextField resEntTotal;
    @FXML private TextField resSaiDinheiro;
    @FXML private TextField resSaiTotal;
    @FXML private TextField resSaldoFinal;

    public void show(CashSummary summary) {
        resSaldoInicial.setText(MoneyFormat.format(summary.opening()));
        resEntDinheiro.setText(MoneyFormat.format(summary.cashIn()));
        resEntPix.setText(MoneyFormat.format(summary.pix()));
        resEntCredito.setText(MoneyFormat.format(summary.credit()));
        resEntDebito.setText(MoneyFormat.format(summary.debit()));
        resEntTotal.setText(MoneyFormat.format(summary.totalIn()));
        resSaiDinheiro.setText(MoneyFormat.format(summary.cashOut()));
        resSaiTotal.setText(MoneyFormat.format(summary.cashOut()));
        resSaldoFinal.setText(MoneyFormat.format(summary.finalBalance()));
    }
}
