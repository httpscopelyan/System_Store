package br.com.scopel.pfv.beta.controller;

import javafx.fxml.FXML;
import javafx.scene.control.TabPane;
import org.springframework.stereotype.Component;

@Component
public class AdminController {

    @FXML private TabPane tabs;
    @FXML private AdminHistoryController adminHistoryController;
    @FXML private AdminPermissionsController adminPermissionsController;
    @FXML private AdminAccountsController adminAccountsController;
    @FXML private AdminCashGroupsController adminCashGroupsController;
    @FXML private AdminWebController adminWebController;

    @FXML
    public void initialize() {
        tabs.getSelectionModel().selectedIndexProperty().addListener((observable, previous, current) -> refresh(current.intValue()));
        refresh(0);
    }

    private void refresh(int tabIndex) {
        switch (tabIndex) {
            case 0 -> adminHistoryController.refresh();
            case 1 -> adminPermissionsController.refresh();
            case 2 -> adminAccountsController.refresh();
            case 3 -> adminCashGroupsController.refresh();
            default -> adminWebController.refresh();
        }
    }
}
