package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.StaffPermissions;
import javafx.scene.Node;
import javafx.scene.control.TableView;

public final class ViewOnlyMode {

    private static final String EDIT_ACTION_STYLE = "edit-action";

    private ViewOnlyMode() {
    }

    public static void apply(User user, TableView<?> table) {
        if (!StaffPermissions.isViewOnly(user)) {
            return;
        }
        table.setEditable(false);
        Node top = table;
        while (top.getParent() != null) {
            top = top.getParent();
        }
        top.lookupAll("." + EDIT_ACTION_STYLE).forEach(node -> {
            node.setVisible(false);
            node.setManaged(false);
        });
    }
}
