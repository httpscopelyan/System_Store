package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.exception.SupplierDuplicatedException;
import br.com.scopel.pfv.beta.model.Supplier;
import br.com.scopel.pfv.beta.service.SupplierService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.springframework.stereotype.Component;

@Component
public class SupplierFormController {

    private final SupplierService supplierService;

    @FXML private TextField nameField;
    @FXML private TextField cnpjField;
    @FXML private TextField phoneField;
    @FXML private TextField emailField;
    @FXML private CheckBox activeBox;
    @FXML private Label messageLabel;

    public SupplierFormController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @FXML
    public void initialize() {
        Platform.runLater(() -> nameField.requestFocus());
    }

    @FXML
    public void onSave() {
        try {
            String name = nameField.getText();
            if (name == null || name.isBlank()) {
                messageLabel.setText("Informe o nome do fornecedor");
                return;
            }
            Supplier s = new Supplier();
            s.setName(name.trim());
            s.setCnpj(cnpjField.getText());
            s.setPhone(blankToNull(phoneField.getText()));
            s.setEmail(blankToNull(emailField.getText()));
            s.setActive(activeBox.isSelected());

            supplierService.save(s);
            messageLabel.setText("Fornecedor cadastrado!");
            nameField.clear();
            cnpjField.clear();
            phoneField.clear();
            emailField.clear();
            activeBox.setSelected(true);
        } catch (SupplierDuplicatedException e) {
            messageLabel.setText("Já existe fornecedor com este CNPJ");
        } catch (Exception e) {
            messageLabel.setText("Verifique os campos e tente novamente");
        }
    }

    private String blankToNull(String text) {
        return (text == null || text.isBlank()) ? null : text.trim();
    }
}
