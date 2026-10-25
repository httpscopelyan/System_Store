package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.exception.CustomerDuplicatedException;
import br.com.scopel.pfv.beta.model.Customer;
import br.com.scopel.pfv.beta.service.CustomerService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CustomerFormController {

    private final CustomerService customerService;

    @FXML private TextField nameField;
    @FXML private TextField cpfField;
    @FXML private TextField phoneField;
    @FXML private TextField addressField;
    @FXML private TextField creditLimitField;
    @FXML private CheckBox activeBox;
    @FXML private Label messageLabel;

    public CustomerFormController(CustomerService customerService) {
        this.customerService = customerService;
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
                messageLabel.setText("Informe o nome do cliente");
                return;
            }

            BigDecimal creditLimit = parseCreditLimit(creditLimitField.getText());

            Customer customer = new Customer();
            customer.setName(name.trim());
            customer.setCpfCnpj(cpfField.getText());      // service normaliza / vira nulo se vazio
            customer.setPhone(blankToNull(phoneField.getText()));
            customer.setAddress(blankToNull(addressField.getText()));
            customer.setCreditLimit(creditLimit);
            customer.setActive(activeBox.isSelected());

            customerService.save(customer);
            messageLabel.setText("Cliente cadastrado!");
            clearForm();

        } catch (NumberFormatException e) {
            messageLabel.setText("Limite de crédito inválido");
        } catch (CustomerDuplicatedException e) {
            messageLabel.setText("Já existe cliente com este CPF/CNPJ");
        } catch (Exception e) {
            messageLabel.setText("Verifique os campos e tente novamente");
        }
    }

    private BigDecimal parseCreditLimit(String text) {
        if (text == null || text.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(text.trim().replace(',', '.'));
    }

    private String blankToNull(String text) {
        return (text == null || text.isBlank()) ? null : text.trim();
    }

    private void clearForm() {
        nameField.clear();
        cpfField.clear();
        phoneField.clear();
        addressField.clear();
        creditLimitField.clear();
        activeBox.setSelected(true);
    }
}
