package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.service.ReportService;
import javafx.fxml.FXML;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Component
public class ReportViewController {

    private final ReportService reportService;

    @FXML private DatePicker startField;
    @FXML private DatePicker endField;
    @FXML private Label summaryLabel;
    @FXML private TextArea resultArea;
    @FXML private Label messageLabel;

    public ReportViewController(ReportService reportService) {
        this.reportService = reportService;
    }

    @FXML
    public void initialize() {
        startField.setValue(LocalDate.now().withDayOfMonth(1));
        endField.setValue(LocalDate.now());
    }

    @FXML
    public void onGenerate() {
        LocalDate s = startField.getValue();
        LocalDate e = endField.getValue();
        if (s == null || e == null) {
            messageLabel.setText("Informe o período");
            return;
        }
        LocalDateTime start = s.atStartOfDay();
        LocalDateTime end = e.atTime(LocalTime.MAX);

        ReportService.SalesSummary summary = reportService.summary(start, end);
        summaryLabel.setText(String.format(
                "Vendas: R$ %s   |   Lucro: R$ %s   |   Descontos: R$ %s   |   Cancelamentos: %d",
                summary.totalSales().toPlainString(),
                summary.grossProfit().toPlainString(),
                summary.totalDiscounts().toPlainString(),
                summary.canceledCount()));

        StringBuilder sb = new StringBuilder();
        sb.append("== Por forma de pagamento ==\n");
        reportService.byPaymentMethod(start, end)
                .forEach(na -> sb.append(na.name()).append(": R$ ").append(na.amount().toPlainString()).append('\n'));
        sb.append("\n== Por operador ==\n");
        reportService.byOperator(start, end)
                .forEach(na -> sb.append(na.name()).append(": R$ ").append(na.amount().toPlainString()).append('\n'));
        sb.append("\n== Mais vendidos (top 10) ==\n");
        reportService.topProducts(start, end, 10)
                .forEach(pq -> sb.append(pq.description()).append(": ").append(pq.quantity().toPlainString()).append('\n'));

        resultArea.setText(sb.toString());
        messageLabel.setText("");
    }
}
