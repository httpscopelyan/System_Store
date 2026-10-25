package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.model.Customer;
import br.com.scopel.pfv.beta.model.Receivable;
import br.com.scopel.pfv.beta.service.CustomerService;
import br.com.scopel.pfv.beta.service.ReceivableService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class ReceivableListController {

    private final ReceivableService receivableService;
    private final CustomerService customerService;

    @FXML private TextField customerIdField;
    @FXML private Label debtLabel;
    @FXML private TableView<Receivable> table;
    @FXML private TableColumn<Receivable, String> colDesc;
    @FXML private TableColumn<Receivable, String> colDue;
    @FXML private TableColumn<Receivable, String> colAmount;
    @FXML private TableColumn<Receivable, String> colRemaining;
    @FXML private TextField receiveField;
    @FXML private TextField installmentsField;
    @FXML private DatePicker firstDueField;
    @FXML private TextField intervalField;
    @FXML private Label messageLabel;

    public ReceivableListController(ReceivableService receivableService, CustomerService customerService) {
        this.receivableService = receivableService;
        this.customerService = customerService;
    }

    @FXML
    public void initialize() {
        colDesc.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDescription()));
        colDue.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getDueDate())));
        colAmount.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAmount().toPlainString()));
        colRemaining.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRemaining().toPlainString()));
    }

    private Long customerId() {
        return Long.parseLong(customerIdField.getText().trim());
    }

    @FXML
    public void onLoad() {
        try {
            Long id = customerId();
            table.setItems(FXCollections.observableArrayList(receivableService.openByCustomer(id)));
            debtLabel.setText("Dívida: R$ " + receivableService.customerDebt(id).toPlainString());
            messageLabel.setText("");
        } catch (NumberFormatException e) {
            messageLabel.setText("ID de cliente inválido");
        }
    }

    @FXML
    public void onReceive() {
        Receivable selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            messageLabel.setText("Selecione uma parcela");
            return;
        }
        try {
            BigDecimal value = new BigDecimal(receiveField.getText().trim().replace(',', '.'));
            receivableService.receive(selected.getId(), value);
            receiveField.clear();
            messageLabel.setText("Recebimento registrado");
            onLoad();
        } catch (NumberFormatException e) {
            messageLabel.setText("Valor inválido");
        } catch (Exception e) {
            messageLabel.setText(e.getMessage());
        }
    }

    @FXML
    public void onRenegotiate() {
        try {
            Customer customer = customerService.findById(customerId());
            int n = Integer.parseInt(installmentsField.getText().trim());
            LocalDate firstDue = firstDueField.getValue();
            int interval = Integer.parseInt(intervalField.getText().trim());
            if (firstDue == null) {
                messageLabel.setText("Informe a data da 1ª parcela");
                return;
            }
            receivableService.renegotiate(customer, n, firstDue, interval);
            messageLabel.setText("Dívida renegociada");
            onLoad();
        } catch (NumberFormatException e) {
            messageLabel.setText("Parcelas/intervalo inválidos");
        } catch (Exception e) {
            messageLabel.setText(e.getMessage());
        }
    }
}
