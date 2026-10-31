package br.com.scopel.pfv.beta.controller.support;

import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public final class TableEditKeys {

    private TableEditKeys() {
    }

    public static <T> void install(TableView<T> table) {
        table.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() != KeyCode.ENTER && event.getCode() != KeyCode.F2) {
                return;
            }
            TablePosition<T, ?> focused = table.getFocusModel().getFocusedCell();
            if (focused == null || focused.getRow() < 0 || focused.getTableColumn() == null
                    || !focused.getTableColumn().isEditable() || table.getEditingCell() != null) {
                return;
            }
            table.edit(focused.getRow(), focused.getTableColumn());
            event.consume();
        });
    }
}
