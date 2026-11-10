package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.StaffPermissions;
import javafx.scene.Node;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

public final class ViewOnlyMode {

    private static final String EDIT_ACTION_STYLE = "edit-action";
    private static final String QUANTITY_ACTION_STYLE = "quantity-action";

    private ViewOnlyMode() {
    }

    public static void apply(User user, TableView<?> table, TableColumn<?, ?> stockQuantityColumn) {
        boolean stockQuantityOnly = StaffPermissions.isStockQuantityOnly(user);
        if (!stockQuantityOnly && !StaffPermissions.isFullyReadOnly(user)) {
            return;
        }
        if (stockQuantityOnly) {
            table.getColumns().stream()
                    .filter(column -> column != stockQuantityColumn)
                    .forEach(column -> column.setEditable(false));
        } else {
            table.setEditable(false);
        }
        Node top = table;
        while (top.getParent() != null) {
            top = top.getParent();
        }
        top.lookupAll("." + EDIT_ACTION_STYLE).stream()
                .filter(node -> !(stockQuantityOnly && node.getStyleClass().contains(QUANTITY_ACTION_STYLE)))
                .forEach(node -> {
                    node.setVisible(false);
                    node.setManaged(false);
                });
    }
}
