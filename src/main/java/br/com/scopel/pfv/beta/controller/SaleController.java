package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.exception.IllegalSaleStateException;
import br.com.scopel.pfv.beta.exception.InvalidDiscountException;
import br.com.scopel.pfv.beta.exception.InvalidQuantityException;
import br.com.scopel.pfv.beta.exception.ManagerAuthorizationRequiredException;
import br.com.scopel.pfv.beta.exception.ProductNotfindException;
import br.com.scopel.pfv.beta.exception.SaleItemNotFoundException;
import br.com.scopel.pfv.beta.model.Customer;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.SaleItem;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.CustomerService;
import br.com.scopel.pfv.beta.service.ReceiptService;
import br.com.scopel.pfv.beta.service.SaleService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Window;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Optional;

@Component
public class SaleController {

    private static final DecimalFormat MONEY =
            new DecimalFormat("#,##0.00", new DecimalFormatSymbols(new Locale("pt", "BR")));

    private final SaleService saleService;
    private final UserSession userSession;
    private final Navigator navigator;
    private final CustomerService customerService;
    private final ReceiptService receiptService;
    private final ManagerAuthorizationController managerAuthorizationController;
    private final QuickProductController quickProductController;

    private Sale vendaAtual;

    private final ObservableList<SaleItemRow> rows = FXCollections.observableArrayList();

    // G5: atalhos F da venda, sensíveis ao contexto. Ficam no filtro de eventos
    // da cena desta página (fase de captura, ANTES dos accelerators globais do
    // shell), e são removidos ao sair da tela. Guardamos a referência para poder
    // removê-la depois.
    private EventHandler<KeyEvent> saleShortcutFilter;

    @FXML private TextField codeField;
    @FXML private TableView<SaleItemRow> itemsTable;
    @FXML private TableColumn<SaleItemRow, String> descriptionCol;
    @FXML private TableColumn<SaleItemRow, BigDecimal> quantityCol;
    @FXML private TableColumn<SaleItemRow, BigDecimal> unitPriceCol;
    @FXML private TableColumn<SaleItemRow, BigDecimal> totalPriceCol;
    @FXML private Label totalLabel;
    @FXML private Label statusLabel;

    // Faixa fina do último item (só o nome, NÃO recebe foco — RNF03).
    @FXML private Label lastItemLabel;

    // Prévia do cupom (Item 1): texto monoespaçado, só-leitura.
    @FXML private TextArea receiptPreview;

    // Controles de Venda+ (Inc 4). Opcionais no FXML.
    @FXML private TextField discountField;
    @FXML private TextField customerIdField;
    @FXML private TextField cancelItemCodeField;
    @FXML private TextField cancelReasonField;

    public SaleController(SaleService saleService, UserSession userSession, Navigator navigator,
                          CustomerService customerService, ReceiptService receiptService,
                          ManagerAuthorizationController managerAuthorizationController,
                          QuickProductController quickProductController) {
        this.saleService = saleService;
        this.userSession = userSession;
        this.navigator = navigator;
        this.customerService = customerService;
        this.receiptService = receiptService;
        this.managerAuthorizationController = managerAuthorizationController;
        this.quickProductController = quickProductController;
    }

    /**
     * Executa uma ação da venda que pode exigir gerente (RN08/RF18/RF21). Tenta com
     * o operador logado; se o service recusar por falta de autorização, abre o diálogo
     * de supervisor e repete a ação com o gerente autenticado. Retorna a venda atualizada,
     * ou {@code null} quando o operador cancela o diálogo (nada é aplicado).
     */
    private Sale applyWithManagerFallback(AuthorizedSaleAction saleAction) {
        try {
            return saleAction.run(userSession.getLoggedUser());
        } catch (ManagerAuthorizationRequiredException needsManager) {
            Optional<User> authorizingManager = requestManagerAuthorization();
            if (authorizingManager.isEmpty()) {
                return null;
            }
            return saleAction.run(authorizingManager.get());
        }
    }

    private Optional<User> requestManagerAuthorization() {
        Window ownerWindow = statusLabel.getScene() == null ? null : statusLabel.getScene().getWindow();
        return managerAuthorizationController.requestAuthorization(ownerWindow);
    }

    @FunctionalInterface
    private interface AuthorizedSaleAction {
        Sale run(User authorizedBy);
    }

    @FXML
    public void initialize() {
        // C3: o controller é bean singleton e o Navigator recarrega o FXML a cada
        // navegação, então initialize() roda de novo com a venda em andamento ainda
        // na sessão. Lemos essa venda ANTES de qualquer limpeza, para poder
        // restaurá-la (resumeOrOpenSale) em vez de descartá-la abrindo outra.
        Sale sessionSale = userSession.getVendaAtual();

        // Limpeza de UI: roda SEMPRE (campos, prévia, barra do último item). Só o
        // ramo "abrir/restaurar venda" é condicional.
        rows.clear();
        totalLabel.setText("R$ 0,00");
        statusLabel.setText("");
        codeField.clear();
        clearOptionalField(discountField);
        clearOptionalField(customerIdField);
        clearOptionalField(cancelItemCodeField);
        clearOptionalField(cancelReasonField);
        if (receiptPreview != null) {
            receiptPreview.clear();
        }
        updateLastItemDisplay(null);

        descriptionCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        quantityCol.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        unitPriceCol.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
        totalPriceCol.setCellValueFactory(new PropertyValueFactory<>("totalPrice"));

        itemsTable.setItems(rows);
        // O leitor age como teclado: a tabela não deve roubar o foco do código.
        itemsTable.setFocusTraversable(false);

        // RNF03/RNF07: o campo de código fica SEMPRE com o foco. Se perder (clique
        // na tabela, etc.), recupera — para o bipe sempre cair no campo certo.
        codeField.focusedProperty().addListener((obs, was, is) -> {
            if (!is && !codeField.isDisabled() && codeField.getScene() != null) {
                reclaimFocusUnlessTypingInSaleField();
            }
        });
        returnFocusToCodeOnEscape(discountField);
        returnFocusToCodeOnEscape(customerIdField);
        returnFocusToCodeOnEscape(cancelItemCodeField);
        returnFocusToCodeOnEscape(cancelReasonField);

        // G5: instala os atalhos F da venda quando a página entra em cena e os
        // remove quando sai, para não vazarem para outras telas (mesmo padrão de
        // sceneProperty usado em PagamentoEsperaController). Cada carga de FXML cria
        // nós novos, então o listener acompanha uma propriedade fresca sem acumular.
        bindSaleShortcutsToScene();

        // RN14: não há venda sem caixa aberto. Em vez de redirecionar, avisa aqui.
        if (userSession.getCaixaAtual() == null) {
            setVendaAtual(null);
            codeField.setDisable(true);
            statusLabel.setText("Abra o caixa antes de vender");
            return;
        }

        codeField.setDisable(false);
        statusLabel.setText("");

        // C1: a decisão de restaurar vs. abrir nova fica no service (testável). A
        // comparação por id lá dentro cobre caixa/operador trocado (C4): venda de
        // outro dono não é restaurada, abre nova.
        Sale venda = saleService.resumeOrOpenSale(
                userSession.getLoggedUser(),
                userSession.getCaixaAtual(),
                sessionSale
        );
        setVendaAtual(venda);

        // C2: o operador voltou da tela de pagamento sem concluir. Não seguimos o
        // fluxo normal da venda: devolvemos ao pagamento para concluir/cancelar lá
        // (deferido para evitar navegação reentrante durante o load do FXML).
        if (venda.getStatus() == Sale.Status.EM_PAGAMENTO) {
            Platform.runLater(() -> navigator.navigate(Page.PAGAMENTO));
            return;
        }

        // Ao restaurar uma venda ABERTA, a tabela, o TOTAL e a barra do último item
        // reaparecem preenchidos (RNF03/RNF04); para venda nova/vazia ficam neutros.
        refreshTable();
        updateLastItemDisplay(lastRemainingItem());
        focusCode();
    }

    /**
     * Ponto único para trocar a venda atual: mantém o campo local e a sessão
     * sincronizados. Outras telas (ex.: a Consulta de Preço via F1) leem
     * {@code userSession.getVendaAtual()} para decidir, por RN21, se há venda em
     * andamento — por isso a sessão precisa refletir SEMPRE o estado real daqui.
     */
    private void setVendaAtual(Sale sale) {
        this.vendaAtual = sale;
        userSession.setVendaAtual(sale);
    }

    private void clearOptionalField(TextField field) {
        if (field != null) {
            field.clear();
        }
    }

    /**
     * Snap-back do foco ao código (RNF07), exceto quando o operador foi para um dos
     * campos de digitação da venda. Roda no próximo pulso: só então o novo dono do
     * foco já está definido na cena. Se o dono continua sendo o próprio código (ex.:
     * a janela perdeu o foco para um modal), não há o que recuperar — evita laço.
     */
    private void reclaimFocusUnlessTypingInSaleField() {
        Platform.runLater(() -> {
            Scene scene = codeField.getScene();
            if (scene == null) {
                return;
            }
            Node focusOwner = scene.getFocusOwner();
            if (focusOwner == codeField || isSaleInputField(focusOwner)) {
                return;
            }
            focusCode();
        });
    }

    private boolean isSaleInputField(Node node) {
        return node != null
                && (node == discountField
                || node == customerIdField
                || node == cancelItemCodeField
                || node == cancelReasonField);
    }

    /** Esc dentro do campo devolve o foco ao código sem agir (RNF03). Campo opcional pode ser null. */
    private void returnFocusToCodeOnEscape(TextField field) {
        if (field == null) {
            return;
        }
        field.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                focusCode();
                event.consume();
            }
        });
    }

    /** Executa a ação e, por qualquer caminho de saída (inclusive após modal), devolve o foco ao código. */
    private void runThenFocusCode(Runnable action) {
        try {
            action.run();
        } finally {
            focusCode();
        }
    }

    /** Põe o foco no campo de código e seleciona o conteúdo (bipe pronto). */
    private void focusCode() {
        // No setCenter o nó pode ainda não estar na cena; foca no próximo pulso.
        Platform.runLater(() -> {
            if (codeField.getScene() != null && !codeField.isDisabled()) {
                codeField.requestFocus();
                codeField.selectAll();
            }
        });
    }

    /**
     * Liga/desliga os atalhos da venda conforme a página entra e sai de cena.
     * Instala o filtro na cena nova e o remove da cena antiga, mantendo o estado
     * autocontido (não mexe no mapa global de accelerators do shell).
     */
    private void bindSaleShortcutsToScene() {
        statusLabel.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (oldScene != null) {
                uninstallSaleShortcuts(oldScene);
            }
            if (newScene != null) {
                installSaleShortcuts(newScene);
            }
        });
    }

    private void installSaleShortcuts(Scene scene) {
        if (saleShortcutFilter == null) {
            saleShortcutFilter = this::handleSaleKey;
        }
        // Fase de captura: roda ANTES dos accelerators globais do shell (F5/F6/F7).
        scene.addEventFilter(KeyEvent.KEY_PRESSED, saleShortcutFilter);
    }

    private void uninstallSaleShortcuts(Scene scene) {
        if (saleShortcutFilter != null) {
            scene.removeEventFilter(KeyEvent.KEY_PRESSED, saleShortcutFilter);
        }
    }

    /**
     * Trata SÓ as teclas de função da venda. Consome o evento apenas para essas
     * teclas — assim o atalho global do shell é suprimido sem afetar os dígitos e
     * o Enter do leitor (código + Enter continuam chegando intactos ao codeField),
     * nem os demais accelerators. O respeito ao estado (RN21) fica nos próprios
     * handlers onXxx, que ignoram quando não há venda ativa e deixam o service
     * recusar quando for o caso. O foco volta para o leitor (RNF03) dentro de cada
     * handler de campo (runThenFocusCode); F10 não tem campo, então devolve aqui.
     */
    private void handleSaleKey(KeyEvent event) {
        switch (event.getCode()) {
            case F5 -> { onApplyDiscount(); event.consume(); }
            case F6 -> { onCancelItem(); event.consume(); }
            case F7 -> { onCancelSale(); event.consume(); }
            case F9 -> { onSetCustomer(); event.consume(); }
            case F10 -> { onFinalize(); focusCode(); event.consume(); }
            default -> { /* não é atalho da venda: segue para o leitor/accelerators */ }
        }
    }

    @FXML
    public void onAddItem() {
        if (vendaAtual == null) return;
        QuantityAndCode parsed = null;
        try {
            parsed = parseQuantityAndCode(codeField.getText());
            setVendaAtual(saleService.addItem(vendaAtual, parsed.code(), parsed.quantity()));

            refreshTable();
            // Barra do último item: mostra o item recém-lançado (preço congelado,
            // RN19 — nada é recalculado na UI). Localiza pelo código tocado.
            updateLastItemDisplay(findItemByCode(parsed.code()));
            statusLabel.setText("");
            codeField.clear();
            focusCode();

        } catch (ProductNotfindException productNotFound) {
            // Código sem cadastro: oferece o cadastro rápido com SÓ o código bipado
            // (nunca o qtd*código), preservando a venda em andamento.
            offerQuickRegister(parsed == null ? null : parsed.code());
        } catch (InvalidQuantityException invalidQuantity) {
            // Cobre tanto o parse malformado (UI) quanto a recusa do service (RN05).
            showAddItemError(invalidQuantity.getMessage());
        }
    }

    /**
     * Abre o cadastro rápido modal para o código bipado inexistente. Ao salvar, a
     * venda (preservada) segue intacta e o operador precisa REBIPAR para vender —
     * nada é auto-adicionado (decisão do dev; o preço só congela no item no rebipe,
     * RN19). Ao cancelar, mantém a mensagem "Produto não encontrado" e a venda.
     */
    private void offerQuickRegister(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            showAddItemError("Produto não encontrado");
            return;
        }
        Window ownerWindow = statusLabel.getScene() == null ? null : statusLabel.getScene().getWindow();
        Optional<Product> registeredProduct = quickProductController.requestQuickRegister(ownerWindow, barcode);
        if (registeredProduct.isEmpty()) {
            showAddItemError("Produto não encontrado");
            return;
        }
        statusLabel.setText("Produto cadastrado — bipe novamente para vender");
        codeField.clear();
        focusCode();
    }

    private void showAddItemError(String message) {
        statusLabel.setText(message);
        codeField.clear();
        focusCode();
    }

    /**
     * Interpreta o texto cru do leitor no formato {@code quantidade*código}
     * (RN05/G2). Sem {@code *}, assume quantidade 1 e o texto inteiro como código
     * (comportamento clássico do bipe). Só separa no PRIMEIRO {@code *}; o código
     * segue cru para o service, preservando zeros à esquerda (código de barras é
     * String). A regra de UN/KG/positivo fica no service — aqui tratamos apenas o
     * formato da entrada.
     */
    private QuantityAndCode parseQuantityAndCode(String rawInput) {
        String trimmedInput = rawInput == null ? "" : rawInput.trim();
        int separatorIndex = trimmedInput.indexOf('*');

        if (separatorIndex < 0) {
            return new QuantityAndCode(BigDecimal.ONE, trimmedInput);
        }
        if (trimmedInput.indexOf('*', separatorIndex + 1) >= 0) {
            throw new InvalidQuantityException("Formato inválido: use quantidade*código");
        }

        String quantityText = trimmedInput.substring(0, separatorIndex).trim();
        String code = trimmedInput.substring(separatorIndex + 1).trim();

        if (quantityText.isEmpty()) {
            throw new InvalidQuantityException("Informe a quantidade antes do *");
        }
        if (code.isEmpty()) {
            throw new InvalidQuantityException("Informe o código do produto");
        }

        return new QuantityAndCode(parseQuantity(quantityText), code);
    }

    /** Converte a quantidade usando BigDecimal(String) e vírgula pt-BR como separador decimal. */
    private BigDecimal parseQuantity(String quantityText) {
        try {
            return new BigDecimal(quantityText.replace(',', '.'));
        } catch (NumberFormatException invalidNumber) {
            throw new InvalidQuantityException("Quantidade inválida");
        }
    }

    /** Resultado do parse do campo do leitor: quantidade/peso e código cru. */
    private record QuantityAndCode(BigDecimal quantity, String code) {
    }

    @FXML
    public void onFinalize() {
        if (vendaAtual == null) return;
        try {
            setVendaAtual(saleService.goToPayment(vendaAtual));

            navigator.navigate(Page.PAGAMENTO);

        } catch (IllegalSaleStateException e) {
            statusLabel.setText("Adicione itens antes de finalizar");
        }
    }

    @FXML
    public void onApplyDiscount() {
        runThenFocusCode(this::applyDiscount);
    }

    private void applyDiscount() {
        if (vendaAtual == null) return;
        try {
            BigDecimal discount = new BigDecimal(discountField.getText().trim().replace(',', '.'));
            Sale updatedSale = applyWithManagerFallback(
                    authorizedBy -> saleService.applySaleDiscount(vendaAtual, discount, authorizedBy));
            if (updatedSale == null) {
                statusLabel.setText("Desconto acima do limite: não autorizado");
                return;
            }
            setVendaAtual(updatedSale);
            refreshTable();
            discountField.clear();
            statusLabel.setText("Desconto aplicado");
        } catch (NumberFormatException e) {
            statusLabel.setText("Desconto inválido");
        } catch (InvalidDiscountException e) {
            statusLabel.setText(e.getMessage());
        }
    }

    @FXML
    public void onSetCustomer() {
        runThenFocusCode(this::setCustomer);
    }

    private void setCustomer() {
        if (vendaAtual == null) return;
        try {
            Customer customer = customerService.findById(Long.parseLong(customerIdField.getText().trim()));
            setVendaAtual(saleService.setCustomer(vendaAtual, customer));
            customerIdField.clear();
            statusLabel.setText("Cliente: " + customer.getName());
        } catch (NumberFormatException e) {
            statusLabel.setText("ID de cliente inválido");
        } catch (Exception e) {
            statusLabel.setText("Cliente não encontrado");
        }
    }

    @FXML
    public void onCancelItem() {
        runThenFocusCode(this::cancelItem);
    }

    private void cancelItem() {
        if (vendaAtual == null) return;
        String productCode = cancelItemCodeField.getText().trim();
        try {
            Sale updatedSale = applyWithManagerFallback(
                    authorizedBy -> saleService.cancelItem(vendaAtual, productCode, authorizedBy));
            if (updatedSale == null) {
                statusLabel.setText("Cancelamento de item não autorizado");
                return;
            }
            setVendaAtual(updatedSale);
            cancelItemCodeField.clear();
            refreshTable();
            // Sem "fantasma": a barra reflete o último item que restou, ou limpa.
            updateLastItemDisplay(lastRemainingItem());
            statusLabel.setText("Item cancelado");
        } catch (SaleItemNotFoundException | ProductNotfindException e) {
            statusLabel.setText("Item não está na venda");
        }
    }

    @FXML
    public void onCancelSale() {
        runThenFocusCode(this::cancelSale);
    }

    private void cancelSale() {
        if (vendaAtual == null) return;
        String reason = cancelReasonField.getText();
        try {
            Sale canceledSale = applyWithManagerFallback(
                    authorizedBy -> saleService.cancelSale(vendaAtual, reason, authorizedBy));
            if (canceledSale == null) {
                statusLabel.setText("Cancelamento de venda não autorizado");
                return;
            }
            cancelReasonField.clear();
            statusLabel.setText("Venda cancelada");
            // Abre nova venda para continuar operando.
            setVendaAtual(saleService.openSale(userSession.getLoggedUser(), userSession.getCaixaAtual()));
            refreshTable();
            // Nova venda vazia: zera a barra do último item (sem fantasma da venda anterior).
            updateLastItemDisplay(null);
        } catch (IllegalSaleStateException e) {
            statusLabel.setText("Não é possível cancelar nesta situação");
        }
    }

    private void refreshTable() {
        rows.clear();
        if (vendaAtual == null) {
            totalLabel.setText("R$ 0,00");
            if (receiptPreview != null) {
                receiptPreview.clear();
            }
            return;
        }
        for (SaleItem item : vendaAtual.getItems()) {
            rows.add(new SaleItemRow(
                    item.getProduct().getDescription(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.getTotalPrice()
            ));
        }
        totalLabel.setText("R$ " + vendaAtual.getTotal());
        // Item 1: a prévia mostra EXATAMENTE o que será impresso.
        if (receiptPreview != null) {
            receiptPreview.setText(receiptService.buildSaleReceipt(vendaAtual));
        }
    }

    /**
     * Atualiza a faixa fina do último item com o nome do produto recém-tocado
     * (só exibição, RNF04). {@code null} limpa a barra (placeholder neutro), para
     * não deixar "fantasma" de item removido / venda encerrada.
     */
    private void updateLastItemDisplay(SaleItem item) {
        if (lastItemLabel == null) {
            return;
        }
        if (item == null || item.getProduct() == null) {
            lastItemLabel.setText("—");
            return;
        }
        lastItemLabel.setText(item.getProduct().getDescription());
    }

    /** Item da venda atual cujo produto tem o código informado, ou {@code null}. */
    private SaleItem findItemByCode(String productCode) {
        if (vendaAtual == null || productCode == null) {
            return null;
        }
        String trimmed = productCode.trim();
        for (SaleItem item : vendaAtual.getItems()) {
            if (item.getProduct() != null && trimmed.equals(item.getProduct().getCode())) {
                return item;
            }
        }
        return null;
    }

    /** Último item que restou na venda atual, ou {@code null} se não houver itens. */
    private SaleItem lastRemainingItem() {
        if (vendaAtual == null || vendaAtual.getItems().isEmpty()) {
            return null;
        }
        return vendaAtual.getItems().get(vendaAtual.getItems().size() - 1);
    }
}