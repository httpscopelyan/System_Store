package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.exception.CashRegisterAlreadyOpenException;
import br.com.scopel.pfv.beta.exception.InsufficientCashException;
import br.com.scopel.pfv.beta.model.*;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import br.com.scopel.pfv.beta.service.ReceiptService;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Duration;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class CaixaController {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DecimalFormat MONEY =
            new DecimalFormat("#,##0.00", new DecimalFormatSymbols(new Locale("pt", "BR")));

    private final CashRegisterService cashRegisterService;
    private final UserSession userSession;
    private final Navigator navigator;
    private final ReceiptService receiptService;

    private CashRegister caixaAtual;
    // Caixa exibido na tela (aberto atual ou o último do histórico): alvo da impressão.
    private CashRegister displayed;
    private Timeline autoRefresh;

    public record MovRow(LocalDateTime at, String dataHora, boolean entrada,
                         String dinheiro, String cheque, String credito, String debito,
                         String total, String venda, String historico) {
    }

    @FXML private ComboBox<String> terminalBox;
    @FXML private Label statusBigLabel;
    @FXML private TableView<CashRegister> historyTable;
    @FXML private TableColumn<CashRegister, String> colHistDate;
    @FXML private TableColumn<CashRegister, String> colHistBalance;
    @FXML private Label openingInfoLabel;
    @FXML private Label closingInfoLabel;
    @FXML private TextField openingAmountField;
    @FXML private Label periodLabel;

    @FXML private TableView<MovRow> movTable;
    @FXML private TableColumn<MovRow, String> colMovDate;
    @FXML private TableColumn<MovRow, String> colMovOp;
    @FXML private TableColumn<MovRow, String> colMovDinheiro;
    @FXML private TableColumn<MovRow, String> colMovCheque;
    @FXML private TableColumn<MovRow, String> colMovCredito;
    @FXML private TableColumn<MovRow, String> colMovDebito;
    @FXML private TableColumn<MovRow, String> colMovTotal;
    @FXML private TableColumn<MovRow, String> colMovVenda;
    @FXML private TableColumn<MovRow, String> colMovHist;

    @FXML private TextField resSaldoInicial;
    @FXML private TextField resEntDinheiro;
    @FXML private TextField resEntCheque;
    @FXML private TextField resEntCredito;
    @FXML private TextField resEntDebito;
    @FXML private TextField resEntTotal;
    @FXML private TextField resSaiDinheiro;
    @FXML private TextField resSaiTotal;
    @FXML private TextField resSaldoFinal;

    @FXML private TextField movementAmountField;
    @FXML private TextField countedAmountField;
    @FXML private Label statusLabel;

    public CaixaController(CashRegisterService cashRegisterService, UserSession userSession, Navigator navigator,
                           ReceiptService receiptService) {
        this.cashRegisterService = cashRegisterService;
        this.userSession = userSession;
        this.navigator = navigator;
        this.receiptService = receiptService;
    }

    @FXML
    public void initialize() {
        terminalBox.setItems(FXCollections.observableArrayList("CAIXA 01"));
        terminalBox.setValue("CAIXA 01");

        colHistDate.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getOpenedAt() == null ? "" : DT.format(c.getValue().getOpenedAt())));
        colHistBalance.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getClosingAmount() == null ? "-" : money(c.getValue().getClosingAmount())));

        colMovDate.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().dataHora()));
        colMovDinheiro.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().dinheiro()));
        colMovCheque.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().cheque()));
        colMovCredito.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().credito()));
        colMovDebito.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().debito()));
        colMovTotal.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().total()));
        colMovVenda.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().venda()));
        colMovHist.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().historico()));
        for (TableColumn<MovRow, String> col : List.of(colMovDinheiro, colMovCheque, colMovCredito,
                colMovDebito, colMovTotal)) {
            col.setStyle("-fx-alignment: CENTER-RIGHT;");
        }

        // Coluna Op.: bolinha azul (crédito/entrada) ou vermelha (débito/saída).
        colMovOp.setCellValueFactory(c -> new SimpleStringProperty("●"));
        colMovOp.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("legend-credito", "legend-debito");
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                    return;
                }
                MovRow row = (MovRow) getTableRow().getItem();
                setText("●");
                getStyleClass().add(row.entrada() ? "legend-credito" : "legend-debito");
            }
        });

        onRefresh();

        // Atualização automática (sem clicar em Atualizar). Pausa fora da tela.
        if (autoRefresh != null) {
            autoRefresh.stop();
        }
        autoRefresh = new Timeline(new KeyFrame(Duration.seconds(4), e -> onRefresh()));
        autoRefresh.setCycleCount(Animation.INDEFINITE);
        movTable.sceneProperty().addListener((o, oldS, newS) -> {
            if (newS == null) {
                autoRefresh.pause();
            } else {
                autoRefresh.play();
            }
        });
        autoRefresh.play();
    }

    @FXML
    public void onRefresh() {
        caixaAtual = userSession.getCaixaAtual();
        User operator = userSession.getLoggedUser();

        List<CashRegister> history = operator == null ? List.of() : cashRegisterService.history(operator);
        historyTable.setItems(FXCollections.observableArrayList(history));

        CashRegister display = caixaAtual != null ? caixaAtual
                : (history.isEmpty() ? null : history.get(0));
        this.displayed = display;

        if (display == null) {
            statusBigLabel.setText("FECHADO");
            statusBigLabel.getStyleClass().setAll("status-closed");
            openingInfoLabel.setText("-");
            closingInfoLabel.setText("-");
            periodLabel.setText("Movimentação do Caixa");
            movTable.setItems(FXCollections.observableArrayList());
            clearResumo();
            return;
        }

        boolean aberto = display.getStatus() == CashRegister.Status.OPEN;
        statusBigLabel.setText(aberto ? "ABERTO" : "FECHADO");
        statusBigLabel.getStyleClass().setAll(aberto ? "status-open" : "status-closed");
        openingInfoLabel.setText(DT.format(display.getOpenedAt())
                + (display.getOperator() != null ? "\n" + display.getOperator().getName().toUpperCase() : ""));
        closingInfoLabel.setText(display.getClosedAt() == null ? "-"
                : DT.format(display.getClosedAt()) + "\nDiferença: " + money(nz(display.getDifference())));
        periodLabel.setText("Movimentação do Caixa - Abertura: " + DT.format(display.getOpenedAt()));

        buildMovimentacaoAndResumo(display);
    }

    private void buildMovimentacaoAndResumo(CashRegister display) {
        // Pagamentos por venda, somados por forma de recebimento (dinheiro/crédito/débito).
        Map<Long, SaleReceiptTotals> totalsBySale = new HashMap<>();
        for (Payment payment : cashRegisterService.payments(display)) {
            Long saleId = payment.getSale().getId();
            BigDecimal net = nz(payment.getAmountReceived()).subtract(nz(payment.getChange()));
            SaleReceiptTotals totals = totalsBySale.computeIfAbsent(saleId, key -> new SaleReceiptTotals());
            if (payment.getMethod() == PaymentMethod.DINHEIRO) {
                totals.addCash(net);
            } else if (payment.getMethod() == PaymentMethod.CARTAO) {
                // Cartão de débito entra no débito; crédito — ou cartão sem tipo
                // (dado legado/null) — cai no crédito como fallback seguro.
                if (payment.getCardType() == CardType.DEBITO) {
                    totals.addDebit(net);
                } else {
                    totals.addCredit(net);
                }
            }
        }

        List<MovRow> rows = new ArrayList<>();
        BigDecimal entradaDinheiro = BigDecimal.ZERO;
        BigDecimal entradaCredito = BigDecimal.ZERO;
        BigDecimal entradaDebito = BigDecimal.ZERO;

        for (Sale sale : cashRegisterService.sales(display)) {
            if (sale.getStatus() != Sale.Status.FINALIZADA) {
                continue;
            }
            SaleReceiptTotals totals = totalsBySale.getOrDefault(sale.getId(), new SaleReceiptTotals());
            entradaDinheiro = entradaDinheiro.add(totals.getCash());
            entradaCredito = entradaCredito.add(totals.getCredit());
            entradaDebito = entradaDebito.add(totals.getDebit());
            rows.add(new MovRow(sale.getCreatedAt(), DT.format(sale.getCreatedAt()), true,
                    money(totals.getCash()), money(BigDecimal.ZERO), money(totals.getCredit()),
                    money(totals.getDebit()), money(sale.getTotal()), String.valueOf(sale.getId()),
                    sale.getCustomer() != null ? sale.getCustomer().getName() : "CONSUMIDOR"));
        }

        BigDecimal supr = BigDecimal.ZERO;
        BigDecimal sangr = BigDecimal.ZERO;
        for (CashMovement m : cashRegisterService.movements(display)) {
            boolean entrada = m.getType() == CashMovement.Type.SUPRIMENTO;
            if (entrada) {
                supr = supr.add(m.getAmount());
            } else {
                sangr = sangr.add(m.getAmount());
            }
            rows.add(new MovRow(m.getCreatedAt(), DT.format(m.getCreatedAt()), entrada,
                    money(m.getAmount()), money(BigDecimal.ZERO), money(BigDecimal.ZERO), money(BigDecimal.ZERO),
                    money(m.getAmount()), "", m.getType().name() + (m.getReason() != null ? " - " + m.getReason() : "")));
        }

        rows.sort(Comparator.comparing(MovRow::at));
        movTable.setItems(FXCollections.observableArrayList(rows));

        // Resumo
        BigDecimal entradaDinheiroTotal = entradaDinheiro.add(supr);
        BigDecimal entradaTotal = entradaDinheiroTotal.add(entradaCredito).add(entradaDebito);
        resSaldoInicial.setText(money(display.getOpeningAmount()));
        resEntDinheiro.setText(money(entradaDinheiroTotal));
        resEntCheque.setText(money(BigDecimal.ZERO));
        resEntCredito.setText(money(entradaCredito));
        resEntDebito.setText(money(entradaDebito));
        resEntTotal.setText(money(entradaTotal));
        resSaiDinheiro.setText(money(sangr));
        resSaiTotal.setText(money(sangr));
        resSaldoFinal.setText(money(nz(display.getOpeningAmount()).add(entradaDinheiroTotal).subtract(sangr)));
    }

    /**
     * Totais de uma venda agrupados pela forma de recebimento, para o resumo do
     * caixa. Substitui o antigo array indexado por posição: cada valor tem nome
     * de domínio (dinheiro/crédito/débito) e acumula via {@code .add} (HALF_UP
     * preservado no {@code BigDecimal}).
     */
    private static final class SaleReceiptTotals {
        private BigDecimal cash = BigDecimal.ZERO;
        private BigDecimal credit = BigDecimal.ZERO;
        private BigDecimal debit = BigDecimal.ZERO;

        private void addCash(BigDecimal value) {
            cash = cash.add(value);
        }

        private void addCredit(BigDecimal value) {
            credit = credit.add(value);
        }

        private void addDebit(BigDecimal value) {
            debit = debit.add(value);
        }

        private BigDecimal getCash() {
            return cash;
        }

        private BigDecimal getCredit() {
            return credit;
        }

        private BigDecimal getDebit() {
            return debit;
        }
    }

    private void clearResumo() {
        for (TextField f : List.of(resSaldoInicial, resEntDinheiro, resEntCheque, resEntCredito,
                resEntDebito, resEntTotal, resSaiDinheiro, resSaiTotal, resSaldoFinal)) {
            f.setText(money(BigDecimal.ZERO));
        }
    }

    @FXML
    public void onOpen() {
        try {
            User operator = userSession.getLoggedUser();
            BigDecimal openingAmount = new BigDecimal(openingAmountField.getText().trim().replace(',', '.'));
            caixaAtual = cashRegisterService.open(operator, openingAmount);
            userSession.setCaixaAtual(caixaAtual);
            openingAmountField.clear();
            statusLabel.setText("Caixa aberto!");
            onRefresh();
        } catch (NumberFormatException e) {
            statusLabel.setText("Valor inválido");
        } catch (CashRegisterAlreadyOpenException e) {
            statusLabel.setText("Já existe um caixa aberto");
        }
    }

    /** Busca o caixa aberto do operador direto no banco (evita estado desatualizado). */
    private CashRegister openRegister() {
        User operator = userSession.getLoggedUser();
        if (operator == null) {
            return null;
        }
        return cashRegisterService.findOpenByOperator(operator).orElse(null);
    }

    @FXML
    public void onSupplement() {
        CashRegister aberto = openRegister();
        if (aberto == null) {
            statusLabel.setText("Abra um caixa primeiro.");
            return;
        }
        try {
            BigDecimal amount = new BigDecimal(movementAmountField.getText().trim().replace(',', '.'));
            cashRegisterService.supplement(aberto.getId(), amount, "reforço");
            movementAmountField.clear();
            statusLabel.setText("Suprimento registrado!");
            onRefresh();
        } catch (NumberFormatException e) {
            statusLabel.setText("Valor inválido.");
        } catch (Exception e) {
            statusLabel.setText("Não foi possível: " + e.getMessage());
        }
    }

    @FXML
    public void onWithdrawal() {
        CashRegister aberto = openRegister();
        if (aberto == null) {
            statusLabel.setText("Abra um caixa primeiro.");
            return;
        }
        try {
            BigDecimal amount = new BigDecimal(movementAmountField.getText().trim().replace(',', '.'));
            cashRegisterService.withdrawal(aberto.getId(), amount, "retirada");
            movementAmountField.clear();
            statusLabel.setText("Sangria registrada!");
            onRefresh();
        } catch (NumberFormatException e) {
            statusLabel.setText("Valor inválido.");
        } catch (InsufficientCashException e) {
            statusLabel.setText("Saldo insuficiente para a sangria.");
        } catch (Exception e) {
            statusLabel.setText("Não foi possível: " + e.getMessage());
        }
    }

    @FXML
    public void onClose() {
        CashRegister aberto = openRegister();
        if (aberto == null) {
            statusLabel.setText("Não há caixa aberto para fechar.");
            return;
        }
        String txt = countedAmountField.getText();
        if (txt == null || txt.isBlank()) {
            statusLabel.setText("Informe o valor contado para fechar o caixa.");
            countedAmountField.requestFocus();
            return;
        }
        try {
            BigDecimal counted = new BigDecimal(txt.trim().replace(',', '.'));
            CashRegister fechado = cashRegisterService.close(aberto.getId(), counted);
            userSession.setCaixaAtual(null);
            caixaAtual = null;
            countedAmountField.clear();
            String resumo = "Caixa FECHADO!  Esperado: " + money(nz(fechado.getExpectedAmount()))
                    + "  |  Contado: " + money(nz(fechado.getClosingAmount()))
                    + "  |  Diferença: " + money(nz(fechado.getDifference()));
            // REVISÕES2 item 2: imprime o fechamento automaticamente. Falha de
            // impressão NÃO derruba o fechamento já gravado (RNF06).
            try {
                receiptService.printCashCloseReceipt(fechado);
            } catch (Exception ex) {
                resumo += "  |  Aviso: impressão falhou (" + ex.getMessage() + ")";
            }
            statusLabel.setText(resumo);
            onRefresh();
        } catch (NumberFormatException e) {
            statusLabel.setText("Valor contado inválido.");
        } catch (Exception e) {
            statusLabel.setText("Não foi possível fechar: " + e.getMessage());
        }
    }

    @FXML
    public void onPrintMovimentacao() {
        statusLabel.setText("Impressão da movimentação: use a impressora configurada (em ajuste).");
    }

    @FXML
    public void onPrintFechamento() {
        CashRegister alvo = displayed;
        if (alvo == null) {
            statusLabel.setText("Nenhum caixa para imprimir o fechamento.");
            return;
        }
        if (alvo.getStatus() != CashRegister.Status.CLOSED) {
            statusLabel.setText("Feche o caixa antes de imprimir o fechamento.");
            return;
        }
        try {
            receiptService.printCashCloseReceipt(alvo);
            statusLabel.setText("Fechamento enviado para impressão.");
        } catch (Exception e) {
            statusLabel.setText("Não foi possível imprimir: " + e.getMessage());
        }
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String money(BigDecimal v) {
        return "R$ " + MONEY.format(nz(v));
    }
}
