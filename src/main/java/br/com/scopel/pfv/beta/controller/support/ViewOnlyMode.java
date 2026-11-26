package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.StaffPermissions;
import javafx.scene.Node;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

public final class ViewOnlyMode {

    private static final String EDIT_ACTION_STYLE = "edit-action";
    private static final String QUANTITY_ACTION_STYLE = "quantity-action";
    private static final String REGISTER_ACTION_STYLE = "register-action";

    private ViewOnlyMode() {
    }

    public static void apply(User user, TableView<?> table, TableColumn<?, ?> stockQuantityColumn,
                             TableColumn<?, ?> priceColumn) {
        if (!StaffPermissions.isViewOnly(user)) {
            return;
        }
        boolean editsQuantity = StaffPermissions.canEditStockQuantity(user);
        boolean editsPrice = StaffPermissions.canEditPrices(user);
        boolean canRegister = StaffPermissions.canRegisterProducts(user);
        table.getColumns().stream()
                .filter(column -> !(editsQuantity && column == stockQuantityColumn))
                .filter(column -> !(editsPrice && column == priceColumn))
                .forEach(column -> column.setEditable(false));
        if (!editsQuantity && !editsPrice) {
            table.setEditable(false);
        }
        Node top = table;
        while (top.getParent() != null) {
            top = top.getParent();
        }
        top.lookupAll("." + EDIT_ACTION_STYLE).stream()
                .filter(node -> !(editsQuantity && node.getStyleClass().contains(QUANTITY_ACTION_STYLE)))
                .filter(node -> !(canRegister && node.getStyleClass().contains(REGISTER_ACTION_STYLE)))
                .forEach(CostVisibility::hide);
    }
}
