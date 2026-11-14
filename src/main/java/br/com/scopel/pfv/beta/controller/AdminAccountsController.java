package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.controller.support.AdminDialogs;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.admin.AdminAccount;
import br.com.scopel.pfv.beta.service.admin.AdminAccountService;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
public class AdminAccountsController {

    private final AdminAccountService accountService;
    private final UserSession userSession;

    @FXML private TableView<AdminAccount> table;
    @FXML private TableColumn<AdminAccount, String> nameColumn;
    @FXML private TableColumn<AdminAccount, String> roleColumn;
    @FXML private TableColumn<AdminAccount, String> statusColumn;
    @FXML private TableColumn<AdminAccount, String> groupColumn;
    @FXML private TextField newNameField;
    @FXML private PasswordField newPasswordField;
    @FXML private ComboBox<User.Role> newRoleBox;
    @FXML private Label selectedLabel;
    @FXML private ComboBox<User.Role> roleBox;
    @FXML private PasswordField resetPasswordField;
    @FXML private Button toggleActiveButton;
    @FXML private Label messageLabel;

    public AdminAccountsController(AdminAccountService accountService, UserSession userSession) {
        this.accountService = accountService;
        this.userSession = userSession;
    }

    @FXML
    public void initialize() {
        nameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().name()));
        roleColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().role().name()));
        statusColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().active() ? "Ativa" : "Inativa"));
        groupColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().cashGroupName() == null ? "-" : cell.getValue().cashGroupName()));
        newRoleBox.getItems().setAll(User.Role.OPERADOR, User.Role.GERENTE);
        newRoleBox.setValue(User.Role.OPERADOR);
        roleBox.getItems().setAll(User.Role.OPERADOR, User.Role.GERENTE);
        table.getSelectionModel().selectedItemProperty().addListener((observable, previous, account) -> showSelected(account));
        showSelected(null);
    }

    public void refresh() {
        AdminAccount selected = table.getSelectionModel().getSelectedItem();
        try {
            table.getItems().setAll(accountService.accounts(userSession.getLoggedUser()));
            if (selected != null) {
                table.getItems().stream().filter(account -> account.id().equals(selected.id())).findFirst()
                        .ifPresent(account -> table.getSelectionModel().select(account));
            }
        } catch (Exception failure) {
            messageLabel.setText(failure.getMessage());
        }
    }

    @FXML
    public void onCreate() {
        run(() -> {
            AdminAccount created = accountService.create(userSession.getLoggedUser(), newNameField.getText(),
                    newPasswordField.getText(), newRoleBox.getValue());
            newNameField.clear();
            newPasswordField.clear();
            messageLabel.setText("Conta " + created.name() + " criada.");
        });
    }

    @FXML
    public void onChangeRole() {
        withSelection(account -> {
            accountService.changeRole(userSession.getLoggedUser(), account.id(), roleBox.getValue());
            messageLabel.setText("Perfil de " + account.name() + " alterado. Revise as permissões na aba Permissões.");
        });
    }

    @FXML
    public void onResetPassword() {
        withSelection(account -> {
            accountService.resetPassword(userSession.getLoggedUser(), account.id(), resetPasswordField.getText());
            resetPasswordField.clear();
            messageLabel.setText("Senha de " + account.name() + " redefinida.");
        });
    }

    @FXML
    public void onToggleActive() {
        withSelection(account -> {
            accountService.setActive(userSession.getLoggedUser(), account.id(), !account.active());
            messageLabel.setText("Conta " + account.name() + (account.active() ? " inativada." : " reativada."));
        });
    }

    @FXML
    public void onDelete() {
        withSelection(account -> {
            if (!AdminDialogs.confirm("Excluir a conta " + account.name() + "? Só é possível se ela não tiver histórico.")) {
                return;
            }
            accountService.delete(userSession.getLoggedUser(), account.id());
            messageLabel.setText("Conta " + account.name() + " excluída.");
        });
    }

    private void withSelection(Consumer<AdminAccount> action) {
        AdminAccount account = table.getSelectionModel().getSelectedItem();
        if (account == null) {
            messageLabel.setText("Escolha uma conta na tabela.");
            return;
        }
        run(() -> action.accept(account));
    }

    private void run(Runnable action) {
        messageLabel.setText("");
        try {
            action.run();
            refresh();
        } catch (Exception failure) {
            messageLabel.setText(failure.getMessage() == null ? "Não foi possível concluir a operação." : failure.getMessage());
        }
    }

    private void showSelected(AdminAccount account) {
        boolean none = account == null;
        selectedLabel.setText(none ? "Conta selecionada" : "Conta: " + account.name());
        boolean locked = none || account.protectedAccount();
        roleBox.setDisable(locked);
        resetPasswordField.setDisable(none);
        toggleActiveButton.setDisable(locked);
        toggleActiveButton.setText(!none && !account.active() ? "Reativar" : "Inativar");
        if (!none && !account.protectedAccount()) {
            roleBox.setValue(account.role());
        }
    }
}
