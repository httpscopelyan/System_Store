package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.model.Payable;
import br.com.scopel.pfv.beta.service.PayableService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PayableListController {

    private final PayableService payableService;

    @FXML private TextField descField;
    @FXML private DatePicker dueField;
    @FXML private TextField amountField;
    @FXML private TextField payField;
    @FXML private TableView<Payable> table;
    @FXML private TableColumn<Payable, String> colDesc;
    @FXML private TableColumn<Payable, String> colDue;
    @FXML private TableColumn<Payable, String> colAmount;
    @FXML private TableColumn<Payable, String> colRemaining;
    @FXML private TableColumn<Payable, String> colStatus;
    @FXML private Label messageLabel;

    public PayableListController(PayableService payableService) {
        this.payableService = payableService;
    }

    @FXML
    public void initialize() {
        colDesc.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDescription()));
        colDue.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getDueDate())));
        colAmount.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAmount().toPlainString()));
        colRemaining.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRemaining().toPlainString()));
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus().name()));
        onRefresh();
    }

    @FXML
    public void onRefresh() {
        table.setItems(FXCollections.observableArrayList(payableService.open()));
    }

    @FXML
    public void onAdd() {
        try {
            if (dueField.getValue() == null) {
                messageLabel.setText("Informe o vencimento");
                return;
            }
            BigDecimal amount = new BigDecimal(amountField.getText().trim().replace(',', '.'));
            payableService.create(null, null, descField.getText(), dueField.getValue(), amount);
            descField.clear();
            amountField.clear();
            dueField.setValue(null);
            messageLabel.setText("Conta adicionada");
            onRefresh();
        } catch (NumberFormatException e) {
            messageLabel.setText("Valor inválido");
        } catch (Exception e) {
            messageLabel.setText("Verifique os campos");
        }
    }

    @FXML
    public void onPay() {
        Payable selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            messageLabel.setText("Selecione uma conta");
            return;
        }
        try {
            BigDecimal value = new BigDecimal(payField.getText().trim().replace(',', '.'));
            payableService.pay(selected.getId(), value);
            payField.clear();
            messageLabel.setText("Pagamento registrado");
            onRefresh();
        } catch (NumberFormatException e) {
            messageLabel.setText("Valor inválido");
        } catch (Exception e) {
            messageLabel.setText(e.getMessage());
        }
    }
}
