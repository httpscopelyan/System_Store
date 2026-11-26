package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.StaffPermissions;
import javafx.scene.Node;
import javafx.scene.control.TableColumn;
import javafx.scene.control.ToggleButton;

public final class CostVisibility {

    private CostVisibility() {
    }

    public static boolean seesMargins(User user) {
        return user != null && user.hasManagerAccess() && StaffPermissions.canSeeCostFields(user);
    }

    public static void applyToStock(User user, ToggleButton marginsToggle, TableColumn<?, ?> minStockColumn,
                                    Node... costControls) {
        boolean margins = seesMargins(user);
        marginsToggle.managedProperty().bind(marginsToggle.visibleProperty());
        marginsToggle.setVisible(margins);
        marginsToggle.setSelected(margins);
        if (!StaffPermissions.canSeeCostFields(user)) {
            minStockColumn.setVisible(false);
            hide(costControls);
        }
    }

    public static void hide(Node... nodes) {
        for (Node node : nodes) {
            node.setVisible(false);
            node.setManaged(false);
        }
    }
}
