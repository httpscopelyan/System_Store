package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.model.Permission;
import br.com.scopel.pfv.beta.service.PermissionDefaults;
import br.com.scopel.pfv.beta.service.admin.AdminAccount;
import br.com.scopel.pfv.beta.service.admin.AdminAccountService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class AdminPermissionsController {

    private final AdminAccountService accountService;
    private final UserSession userSession;
    private final Map<Permission, CheckBox> checkBoxes = new EnumMap<>(Permission.class);

    @FXML private ListView<AdminAccount> accountList;
    @FXML private Label titleLabel;
    @FXML private VBox permissionsBox;
    @FXML private Label noteLabel;
    @FXML private Button saveButton;
    @FXML private Button defaultsButton;
    @FXML private Label messageLabel;

    public AdminPermissionsController(AdminAccountService accountService, UserSession userSession) {
        this.accountService = accountService;
        this.userSession = userSession;
    }

    @FXML
    public void initialize() {
        for (Permission permission : Permission.values()) {
            CheckBox checkBox = new CheckBox(permission.label());
            checkBoxes.put(permission, checkBox);
            permissionsBox.getChildren().add(checkBox);
        }
        accountList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(AdminAccount account, boolean empty) {
                super.updateItem(account, empty);
                setText(empty || account == null ? null : account.name() + "  (" + account.role()
                        + (account.active() ? "" : ", inativa") + ")");
            }
        });
        accountList.getSelectionModel().selectedItemProperty().addListener((observable, previous, account) -> show(account));
        show(null);
    }

    public void refresh() {
        AdminAccount selected = accountList.getSelectionModel().getSelectedItem();
        try {
            accountList.getItems().setAll(accountService.accounts(userSession.getLoggedUser()));
            if (selected != null) {
                accountList.getItems().stream().filter(account -> account.id().equals(selected.id())).findFirst()
                        .ifPresent(account -> accountList.getSelectionModel().select(account));
            }
        } catch (Exception failure) {
            messageLabel.setText(failure.getMessage());
        }
    }

    @FXML
    public void onSave() {
        AdminAccount account = accountList.getSelectionModel().getSelectedItem();
        if (account == null) {
            return;
        }
        Set<Permission> granted = EnumSet.noneOf(Permission.class);
        checkBoxes.forEach((permission, checkBox) -> {
            if (checkBox.isSelected()) {
                granted.add(permission);
            }
        });
        try {
            accountService.setPermissions(userSession.getLoggedUser(), account.id(), granted);
            messageLabel.setText("Permissões de " + account.name() + " salvas. Valem no próximo login da pessoa.");
            refresh();
        } catch (Exception failure) {
            messageLabel.setText(failure.getMessage());
        }
    }

    @FXML
    public void onRestoreDefaults() {
        AdminAccount account = accountList.getSelectionModel().getSelectedItem();
        if (account == null) {
            return;
        }
        Set<Permission> defaults = PermissionDefaults.forAccount(account.login(), account.name(), account.role());
        checkBoxes.forEach((permission, checkBox) -> checkBox.setSelected(defaults.contains(permission)));
        messageLabel.setText("Padrão do perfil carregado. Clique em Salvar para aplicar.");
    }

    private void show(AdminAccount account) {
        messageLabel.setText("");
        boolean none = account == null;
        boolean locked = none || account.protectedAccount();
        titleLabel.setText(none ? "Escolha uma conta" : "Permissões de " + account.name());
        checkBoxes.forEach((permission, checkBox) -> {
            checkBox.setSelected(!none && (account.protectedAccount() || account.permissions().contains(permission)));
            checkBox.setDisable(locked);
        });
        saveButton.setDisable(locked);
        defaultsButton.setDisable(locked);
        noteLabel.setText(none || !account.protectedAccount()
                ? "As alterações valem no próximo login da pessoa."
                : "A conta DEV tem acesso total e não pode ser limitada.");
    }
}
