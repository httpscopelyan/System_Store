package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.exception.ChangeNotAvailableException;
import br.com.scopel.pfv.beta.exception.IllegalSaleStateException;
import br.com.scopel.pfv.beta.exception.InsufficientPaymentException;
import br.com.scopel.pfv.beta.exception.InvalidDiscountException;
import br.com.scopel.pfv.beta.exception.InvalidQuantityException;
import br.com.scopel.pfv.beta.exception.ManagerAuthorizationRequiredException;
import br.com.scopel.pfv.beta.exception.PaymentMethodNotAvailableException;
import br.com.scopel.pfv.beta.exception.SaleItemNotFoundException;
import br.com.scopel.pfv.beta.model.*;
import br.com.scopel.pfv.beta.repository.PaymentRepository;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.repository.SaleRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SaleService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final PaymentRepository paymentRepository;
    private final AppSettingService appSettingService;

    public SaleService(SaleRepository saleRepository, ProductService productService,
                       ProductRepository productRepository, PaymentRepository paymentRepository,
                       AppSettingService appSettingService) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
        this.productService = productService;
        this.paymentRepository = paymentRepository;
        this.appSettingService = appSettingService;
    }

    public Sale openSale(User operator, CashRegister cashRegister) {
        Sale sale = new Sale();
        sale.setOperator(operator);
        sale.setCashRegister(cashRegister);
        sale.setCreatedAt(LocalDateTime.now());
        sale.setDiscount(BigDecimal.ZERO);
        sale.setSurcharge(BigDecimal.ZERO);
        sale.setTotal(BigDecimal.ZERO);
        sale.setStatus(Sale.Status.ABERTA);
        return saleRepository.save(sale);
    }

    /**
     * Decide entre restaurar a venda em andamento ou abrir uma nova. Reusa a
     * {@code sessionSale} (inclusive ABERTA sem itens) SOMENTE quando ela pertence
     * a ESTE operador e a ESTE caixa (comparação por id) e está ABERTA ou
     * EM_PAGAMENTO. Nos demais casos (nula, de outro operador/caixa, FINALIZADA ou
     * CANCELADA) abre uma venda nova. Garante o invariante de nunca abrir venda nova
     * havendo uma ABERTA/EM_PAGAMENTO do próprio dono, evitando vendas órfãs.
     */
    public Sale resumeOrOpenSale(User operator, CashRegister cashRegister, Sale sessionSale) {
        if (canResume(operator, cashRegister, sessionSale)) {
            return sessionSale;
        }
        return openSale(operator, cashRegister);
    }

    private boolean canResume(User operator, CashRegister cashRegister, Sale sessionSale) {
        if (sessionSale == null) {
            return false;
        }
        if (sessionSale.getStatus() != Sale.Status.ABERTA
                && sessionSale.getStatus() != Sale.Status.EM_PAGAMENTO) {
            return false;
        }
        return sameEntity(sessionSale.getOperator(), operator)
                && sameEntity(sessionSale.getCashRegister(), cashRegister);
    }

    private boolean sameEntity(User sessionOperator, User operator) {
        return sessionOperator != null && operator != null
                && sessionOperator.getId() != null
                && sessionOperator.getId().equals(operator.getId());
    }

    private boolean sameEntity(CashRegister sessionCashRegister, CashRegister cashRegister) {
        return sessionCashRegister != null && cashRegister != null
                && sessionCashRegister.getId() != null
                && sessionCashRegister.getId().equals(cashRegister.getId());
    }

    public Sale addItem(Sale sale, String productCode, BigDecimal quantity) {
        if (sale.getStatus() != Sale.Status.ABERTA) {
            throw new IllegalSaleStateException();
        }

        Product product = productService.findCode(productCode);

        // RN05: valida/normaliza a quantidade ANTES de somar ou criar o item,
        // para que a soma de um item existente (RN07) nunca aceite uma fração
        // proibida por entrar como parcela.
        BigDecimal normalizedQuantity = validateQuantity(product, quantity);

        SaleItem existing = findItem(sale, product.getId());

        if (existing != null) {
            existing.setQuantity(existing.getQuantity().add(normalizedQuantity));
            existing.setTotalPrice(lineTotal(existing));
        } else {
            SaleItem item = new SaleItem();
            item.setSale(sale);
            item.setProduct(product);
            item.setQuantity(normalizedQuantity);
            item.setUnitPrice(product.getPrice());
            item.setDiscount(BigDecimal.ZERO);
            item.setTotalPrice(lineTotal(item));
            sale.getItems().add(item);
        }

        sale.setTotal(calculateTotal(sale));
        return saleRepository.save(sale);
    }

    /** Vincula um cliente à venda (tele-entrega / venda a prazo). */
    public Sale setCustomer(Sale sale, Customer customer) {
        requireNotClosed(sale);
        sale.setCustomer(customer);
        return saleRepository.save(sale);
    }

    /** Desconto em um item (RF19). Acima do limite percentual exige gerente (RN08). */
    public Sale applyItemDiscount(Sale sale, String productCode, BigDecimal discount, User authorizedBy) {
        if (sale.getStatus() != Sale.Status.ABERTA) {
            throw new IllegalSaleStateException();
        }
        requirePositive(discount, "não pode ser negativo");

        Product product = productService.findCode(productCode);
        SaleItem item = findItem(sale, product.getId());
        if (item == null) {
            throw new SaleItemNotFoundException(productCode);
        }

        BigDecimal gross = item.getUnitPrice().multiply(item.getQuantity()).setScale(2, RoundingMode.HALF_UP);
        if (discount.compareTo(gross) > 0) {
            throw new InvalidDiscountException("maior que o valor do item");
        }
        if (percent(discount, gross).compareTo(discountLimitPercent()) > 0) {
            requireManager(authorizedBy);
            sale.setAuthorizedBy(authorizedBy);
        }

        item.setDiscount(discount.setScale(2, RoundingMode.HALF_UP));
        item.setTotalPrice(lineTotal(item));
        sale.setTotal(calculateTotal(sale));
        return saleRepository.save(sale);
    }

    /** Desconto no total da venda (RF20). Acima do limite percentual exige gerente (RN08). */
    public Sale applySaleDiscount(Sale sale, BigDecimal discount, User authorizedBy) {
        if (sale.getStatus() != Sale.Status.ABERTA && sale.getStatus() != Sale.Status.EM_PAGAMENTO) {
            throw new IllegalSaleStateException();
        }
        requirePositive(discount, "não pode ser negativo");

        BigDecimal subtotal = subtotal(sale);
        if (discount.compareTo(subtotal) > 0) {
            throw new InvalidDiscountException("maior que o subtotal da venda");
        }
        if (percent(discount, subtotal).compareTo(discountLimitPercent()) > 0) {
            requireManager(authorizedBy);
            sale.setAuthorizedBy(authorizedBy);
        }

        sale.setDiscount(discount.setScale(2, RoundingMode.HALF_UP));
        sale.setTotal(calculateTotal(sale));
        return saleRepository.save(sale);
    }

    /** Acréscimo no total da venda (RF19 — acréscimo). */
    public Sale applySurcharge(Sale sale, BigDecimal surcharge) {
        if (sale.getStatus() != Sale.Status.ABERTA && sale.getStatus() != Sale.Status.EM_PAGAMENTO) {
            throw new IllegalSaleStateException();
        }
        requirePositive(surcharge, "acréscimo não pode ser negativo");

        sale.setSurcharge(surcharge.setScale(2, RoundingMode.HALF_UP));
        sale.setTotal(calculateTotal(sale));
        return saleRepository.save(sale);
    }

    /** Remove um item da venda (RF18) — exige autorização de gerente. */
    public Sale cancelItem(Sale sale, String productCode, User authorizedBy) {
        if (sale.getStatus() != Sale.Status.ABERTA) {
            throw new IllegalSaleStateException();
        }
        requireManager(authorizedBy);

        Product product = productService.findCode(productCode);
        SaleItem item = findItem(sale, product.getId());
        if (item == null) {
            throw new SaleItemNotFoundException(productCode);
        }

        sale.getItems().remove(item);          // orphanRemoval apaga o item
        sale.setAuthorizedBy(authorizedBy);
        sale.setTotal(calculateTotal(sale));
        return saleRepository.save(sale);
    }

    /** Cancela a venda (RF21) — exige gerente e mantém o registro (RN17). */
    public Sale cancelSale(Sale sale, String reason, User authorizedBy) {
        if (sale.getStatus() == Sale.Status.FINALIZADA || sale.getStatus() == Sale.Status.CANCELADA) {
            throw new IllegalSaleStateException();
        }
        requireManager(authorizedBy);

        sale.setStatus(Sale.Status.CANCELADA);
        sale.setCancelReason(reason);
        sale.setCanceledAt(LocalDateTime.now());
        sale.setAuthorizedBy(authorizedBy);
        return saleRepository.save(sale);     // RN17: registro preservado
    }

    private Sale finalizeSale(Sale sale) {
        decreaseStock(sale);

        sale.setStatus(Sale.Status.FINALIZADA);
        return saleRepository.save(sale);
    }

    /**
     * Reabre para edição uma venda que está EM_PAGAMENTO, voltando-a para ABERTA
     * (RN21: nova transição EM_PAGAMENTO → ABERTA). Seguro porque EM_PAGAMENTO não
     * grava Payment — o pagamento é atômico (pay/payCard/payPix) — logo não há
     * Payment órfão ao reverter. Uma tabela só, sem {@code @Transactional}.
     */
    public Sale reopenForEditing(Sale sale) {
        if (sale.getStatus() != Sale.Status.EM_PAGAMENTO) {
            throw new IllegalSaleStateException();
        }
        sale.setStatus(Sale.Status.ABERTA);
        return saleRepository.save(sale);
    }

    public Sale goToPayment(Sale sale) {
        if (sale.getStatus() != Sale.Status.ABERTA || sale.getItems().isEmpty()) {
            throw new IllegalSaleStateException();
        }

        sale.setStatus(Sale.Status.EM_PAGAMENTO);
        return saleRepository.save(sale);
    }

    public BigDecimal calculateChange(BigDecimal total, BigDecimal amountReceived) {
        return amountReceived.subtract(total).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public Sale pay(Sale sale, PaymentMethod method, BigDecimal amountReceived) {
        if (sale.getStatus() != Sale.Status.EM_PAGAMENTO) {
            throw new IllegalSaleStateException();
        }

        if (method != PaymentMethod.DINHEIRO) {
            throw new PaymentMethodNotAvailableException();
        }

        if (amountReceived.compareTo(sale.getTotal()) < 0) {
            throw new InsufficientPaymentException();
        }

        BigDecimal change = calculateChange(sale.getTotal(), amountReceived);

        Payment payment = new Payment();
        payment.setSale(sale);
        payment.setMethod(method);
        payment.setAmountReceived(amountReceived);
        payment.setChange(change);
        payment.setCreatedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        return finalizeSale(sale);
    }

    /**
     * Pagamento em cartão (débito/crédito). Chamado SÓ após aprovação da
     * maquininha — o resultado (aprovado/negado) é responsabilidade da UI/gateway.
     * Cartão não gera troco: change = 0 e amountReceived = total.
     */
    @Transactional
    public Sale payCard(Sale sale, CardType cardType) {
        if (sale.getStatus() != Sale.Status.EM_PAGAMENTO) {
            throw new IllegalSaleStateException();
        }

        Payment payment = new Payment();
        payment.setSale(sale);
        payment.setMethod(PaymentMethod.CARTAO);
        payment.setCardType(cardType);
        payment.setAmountReceived(sale.getTotal());
        payment.setChange(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        payment.setCreatedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        return finalizeSale(sale);
    }

    /**
     * Pagamento via PIX. Não gera troco: change = 0 e amountReceived = total.
     */
    @Transactional
    public Sale payPix(Sale sale) {
        if (sale.getStatus() != Sale.Status.EM_PAGAMENTO) {
            throw new IllegalSaleStateException();
        }

        Payment payment = new Payment();
        payment.setSale(sale);
        payment.setMethod(PaymentMethod.PIX);
        payment.setAmountReceived(sale.getTotal());
        payment.setChange(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        payment.setCreatedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        return finalizeSale(sale);
    }

    /** Histórico de vendas finalizadas, mais recentes primeiro (REVISÕES2 item 4). */
    public List<Sale> history() {
        return saleRepository.findByStatusOrderByCreatedAtDesc(Sale.Status.FINALIZADA);
    }

    /**
     * Pagamento múltiplo / dividido (RN18): soma das formas ≥ total; só o
     * dinheiro gera troco, e o troco não pode exceder o dinheiro recebido.
     * Diferente de {@link #pay}, aceita CARTAO/PIX como recebidos (confirmação
     * manual; a integração com maquininha/PSP é fase posterior).
     */
    @Transactional
    public Sale payMixed(Sale sale, List<PaymentInput> inputs) {
        if (sale.getStatus() != Sale.Status.EM_PAGAMENTO) {
            throw new IllegalSaleStateException();
        }
        if (inputs == null || inputs.isEmpty()) {
            throw new InsufficientPaymentException();
        }

        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal cash = BigDecimal.ZERO;
        for (PaymentInput in : inputs) {
            if (in.method() == null || in.amount() == null
                    || in.amount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidDiscountException("valor de pagamento inválido");
            }
            BigDecimal amount = in.amount().setScale(2, RoundingMode.HALF_UP);
            sum = sum.add(amount);
            if (in.method() == PaymentMethod.DINHEIRO) {
                cash = cash.add(amount);
            }
        }

        if (sum.compareTo(sale.getTotal()) < 0) {
            throw new InsufficientPaymentException();
        }
        BigDecimal change = sum.subtract(sale.getTotal()).setScale(2, RoundingMode.HALF_UP);
        if (change.compareTo(BigDecimal.ZERO) > 0 && cash.compareTo(change) < 0) {
            throw new ChangeNotAvailableException();
        }

        boolean changeAssigned = false;
        for (PaymentInput in : inputs) {
            Payment payment = new Payment();
            payment.setSale(sale);
            payment.setMethod(in.method());
            payment.setAmountReceived(in.amount().setScale(2, RoundingMode.HALF_UP));

            BigDecimal thisChange = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            if (!changeAssigned && in.method() == PaymentMethod.DINHEIRO
                    && change.compareTo(BigDecimal.ZERO) > 0) {
                thisChange = change;
                changeAssigned = true;
            }
            payment.setChange(thisChange);
            payment.setCreatedAt(LocalDateTime.now());
            paymentRepository.save(payment);
        }

        return finalizeSale(sale);
    }

    /**
     * Fecha a venda a prazo / crediário: registra o tender PRAZO e finaliza
     * (baixa estoque). Os títulos a receber são gerados pelo ReceivableService,
     * orquestrado por CreditSaleService — por isso este método não valida limite.
     */
    @Transactional
    public Sale finalizeOnCredit(Sale sale) {
        if (sale.getStatus() != Sale.Status.EM_PAGAMENTO) {
            throw new IllegalSaleStateException();
        }
        Payment payment = new Payment();
        payment.setSale(sale);
        payment.setMethod(PaymentMethod.PRAZO);
        payment.setAmountReceived(sale.getTotal());
        payment.setChange(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        payment.setCreatedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        return finalizeSale(sale);
    }

    private void decreaseStock(Sale sale) {
        for (SaleItem item : sale.getItems()) {
            Product product = item.getProduct();
            product.setQuantity(product.getQuantity().subtract(item.getQuantity()));
            productRepository.save(product);
        }
    }

    private BigDecimal calculateTotal(Sale sale) {
        BigDecimal total = subtotal(sale)
                .subtract(nz(sale.getDiscount()))
                .add(nz(sale.getSurcharge()));
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            total = BigDecimal.ZERO;               // RN04: total nunca negativo
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal subtotal(Sale sale) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (SaleItem item : sale.getItems()) {
            subtotal = subtotal.add(item.getTotalPrice());
        }
        return subtotal.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal lineTotal(SaleItem item) {
        BigDecimal gross = item.getUnitPrice().multiply(item.getQuantity());
        BigDecimal net = gross.subtract(nz(item.getDiscount()));
        if (net.compareTo(BigDecimal.ZERO) < 0) {
            net = BigDecimal.ZERO;
        }
        return net.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Valida a quantidade/peso de um item (RN05) e devolve o valor normalizado.
     * Produto por unidade (UN): precisa ser inteiro e maior que zero. Produto por
     * peso (KG): maior que zero, com no máximo 3 casas decimais, normalizado em 3
     * casas (HALF_UP). Esta regra vive no service de propósito (ADR 0010/RN21): a
     * UI pode validar antes, mas o service é o portão final.
     */
    private BigDecimal validateQuantity(Product product, BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidQuantityException("Quantidade deve ser maior que zero");
        }

        if (product.getUnit() == Product.Unidade.UN) {
            if (quantity.stripTrailingZeros().scale() > 0) {
                throw new InvalidQuantityException("Produto vendido por unidade não aceita fração");
            }
            return quantity.setScale(0, RoundingMode.UNNECESSARY);
        }

        if (quantity.stripTrailingZeros().scale() > 3) {
            throw new InvalidQuantityException("Peso aceita no máximo 3 casas decimais");
        }
        return quantity.setScale(3, RoundingMode.HALF_UP);
    }

    private SaleItem findItem(Sale sale, Long productId) {
        for (SaleItem item : sale.getItems()) {
            if (item.getProduct().getId().equals(productId)) {
                return item;
            }
        }
        return null;
    }

    private void requireManager(User authorizedBy) {
        if (authorizedBy == null || authorizedBy.getRole() != User.Role.GERENTE) {
            throw new ManagerAuthorizationRequiredException();
        }
    }

    private void requireNotClosed(Sale sale) {
        if (sale.getStatus() == Sale.Status.FINALIZADA || sale.getStatus() == Sale.Status.CANCELADA) {
            throw new IllegalSaleStateException();
        }
    }

    private void requirePositive(BigDecimal value, String detail) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidDiscountException(detail);
        }
    }

    private BigDecimal discountLimitPercent() {
        return appSettingService.getBigDecimal(
                AppSettingService.SALE_DISCOUNT_LIMIT_PERCENT, new BigDecimal("10"));
    }

    private BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (whole.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
