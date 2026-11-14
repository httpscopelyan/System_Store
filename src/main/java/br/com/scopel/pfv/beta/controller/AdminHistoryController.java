package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.service.admin.AdminHistoryService;
import br.com.scopel.pfv.beta.service.admin.HistoryPage;
import br.com.scopel.pfv.beta.service.admin.HistoryTab;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.util.StringConverter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AdminHistoryController {

    private final AdminHistoryService historyService;
    private final UserSession userSession;

    private int pageNumber;
    private HistoryPage current;

    @FXML private ComboBox<HistoryTab> tabBox;
    @FXML private DatePicker sincePicker;
    @FXML private TextField searchField;
    @FXML private TableView<List<String>> table;
    @FXML private Button previousButton;
    @FXML private Button nextButton;
    @FXML private Label pageLabel;
    @FXML private Label messageLabel;

    public AdminHistoryController(AdminHistoryService historyService, UserSession userSession) {
        this.historyService = historyService;
        this.userSession = userSession;
    }

    @FXML
    public void initialize() {
        tabBox.getItems().setAll(HistoryTab.values());
        tabBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(HistoryTab tab) {
                return tab == null ? "" : tab.title();
            }

            @Override
            public HistoryTab fromString(String text) {
                return null;
            }
        });
        tabBox.setValue(HistoryTab.AUDIT);
        tabBox.valueProperty().addListener((observable, previous, tab) -> {
            sincePicker.setDisable(!tab.timed());
            onRefresh();
        });
        searchField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                onRefresh();
            }
        });
        table.setPlaceholder(new Label("Nenhum registro."));
    }

    public void refresh() {
        load();
    }

    @FXML
    public void onRefresh() {
        pageNumber = 0;
        load();
    }

    @FXML
    public void onPrevious() {
        if (pageNumber > 0) {
            pageNumber--;
            load();
        }
    }

    @FXML
    public void onNext() {
        if (current != null && pageNumber < current.lastPage()) {
            pageNumber++;
            load();
        }
    }

    private void load() {
        messageLabel.setText("");
        try {
            current = historyService.page(userSession.getLoggedUser(), tabBox.getValue(),
                    sincePicker.getValue() == null || sincePicker.isDisabled() ? null : sincePicker.getValue().atStartOfDay(),
                    searchField.getText(), pageNumber);
            show(current);
        } catch (Exception failure) {
            messageLabel.setText("Não foi possível carregar o histórico: " + failure.getMessage());
        }
    }

    private void show(HistoryPage page) {
        table.getColumns().clear();
        for (int index = 0; index < page.columns().size(); index++) {
            int columnIndex = index;
            TableColumn<List<String>, String> column = new TableColumn<>(page.columns().get(index));
            column.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().get(columnIndex)));
            column.setPrefWidth(Math.max(90, Math.min(320, page.columns().get(index).length() * 12 + 40)));
            table.getColumns().add(column);
        }
        table.getItems().setAll(page.rows());
        pageLabel.setText("Página " + (page.page() + 1) + " de " + (page.lastPage() + 1) + " — " + page.total()
                + " registro(s)");
        previousButton.setDisable(page.page() == 0);
        nextButton.setDisable(page.page() >= page.lastPage());
    }
}
