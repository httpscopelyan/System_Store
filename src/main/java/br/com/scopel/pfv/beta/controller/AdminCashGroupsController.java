package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.controller.support.AdminDialogs;
import br.com.scopel.pfv.beta.service.admin.AdminAccount;
import br.com.scopel.pfv.beta.service.admin.AdminAccountService;
import br.com.scopel.pfv.beta.service.admin.AdminCashGroupService;
import br.com.scopel.pfv.beta.service.admin.CashGroupView;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class AdminCashGroupsController {

    private final AdminCashGroupService groupService;
    private final AdminAccountService accountService;
    private final UserSession userSession;
    private final Map<Long, CheckBox> memberBoxes = new LinkedHashMap<>();

    @FXML private ListView<CashGroupView> groupList;
    @FXML private Label titleLabel;
    @FXML private TextField nameField;
    @FXML private VBox membersBox;
    @FXML private Button dissolveButton;
    @FXML private Label messageLabel;

    public AdminCashGroupsController(AdminCashGroupService groupService, AdminAccountService accountService,
                                     UserSession userSession) {
        this.groupService = groupService;
        this.accountService = accountService;
        this.userSession = userSession;
    }

    @FXML
    public void initialize() {
        groupList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(CashGroupView group, boolean empty) {
                super.updateItem(group, empty);
                setText(empty || group == null ? null : group.name() + "  (" + group.memberIds().size() + " contas)");
            }
        });
        groupList.getSelectionModel().selectedItemProperty().addListener((observable, previous, group) -> show(group));
    }

    public void refresh() {
        CashGroupView selected = groupList.getSelectionModel().getSelectedItem();
        try {
            List<CashGroupView> groups = groupService.groups(userSession.getLoggedUser());
            groupList.getItems().setAll(groups);
            CashGroupView reselect = selected == null ? null : groups.stream()
                    .filter(group -> group.id().equals(selected.id())).findFirst().orElse(null);
            if (reselect != null) {
                groupList.getSelectionModel().select(reselect);
            } else {
                show(null);
            }
        } catch (Exception failure) {
            messageLabel.setText(failure.getMessage());
        }
    }

    @FXML
    public void onNew() {
        groupList.getSelectionModel().clearSelection();
        show(null);
    }

    @FXML
    public void onSave() {
        CashGroupView selected = groupList.getSelectionModel().getSelectedItem();
        Set<Long> members = new HashSet<>();
        memberBoxes.forEach((accountId, checkBox) -> {
            if (checkBox.isSelected()) {
                members.add(accountId);
            }
        });
        try {
            groupService.save(userSession.getLoggedUser(), selected == null ? null : selected.id(),
                    nameField.getText(), members);
            messageLabel.setText("Caixa unificado salvo.");
            refresh();
        } catch (Exception failure) {
            messageLabel.setText(failure.getMessage());
        }
    }

    @FXML
    public void onDissolve() {
        CashGroupView selected = groupList.getSelectionModel().getSelectedItem();
        if (selected == null || !AdminDialogs.confirm("Desfazer o caixa unificado " + selected.name()
                + "? Cada conta volta a ter o próprio caixa.")) {
            return;
        }
        try {
            groupService.dissolve(userSession.getLoggedUser(), selected.id());
            messageLabel.setText("Caixa unificado desfeito.");
            groupList.getSelectionModel().clearSelection();
            refresh();
        } catch (Exception failure) {
            messageLabel.setText(failure.getMessage());
        }
    }

    private void show(CashGroupView group) {
        messageLabel.setText("");
        titleLabel.setText(group == null ? "Novo caixa unificado" : group.name());
        nameField.setText(group == null ? "" : group.name());
        dissolveButton.setDisable(group == null);
        membersBox.getChildren().clear();
        memberBoxes.clear();
        try {
            for (AdminAccount account : accountService.accounts(userSession.getLoggedUser())) {
                if (!account.active()) {
                    continue;
                }
                boolean inOtherGroup = account.cashGroupId() != null && (group == null || !account.cashGroupId().equals(group.id()));
                CheckBox checkBox = new CheckBox(account.name() + (inOtherGroup ? "  (hoje em: " + account.cashGroupName() + ")" : ""));
                checkBox.setSelected(group != null && group.memberIds().contains(account.id()));
                memberBoxes.put(account.id(), checkBox);
                membersBox.getChildren().add(checkBox);
            }
        } catch (Exception failure) {
            messageLabel.setText(failure.getMessage());
        }
    }
}
