package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.exception.IllegalSaleStateException;
import br.com.scopel.pfv.beta.exception.InvalidDiscountException;
import br.com.scopel.pfv.beta.exception.InvalidQuantityException;
import br.com.scopel.pfv.beta.exception.ManagerAuthorizationRequiredException;
import br.com.scopel.pfv.beta.exception.SaleItemNotFoundException;
import br.com.scopel.pfv.beta.model.*;
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
    private static final BigDecimal DEFAULT_MAX_QUANTITY_PER_ITEM = new BigDecimal("999");

    private final SaleRepository saleRepository;
    private final ProductService productService;
    private final SalePaymentService salePaymentService;
    private final AppSettingService appSettingService;

    public SaleService(SaleRepository saleRepository, ProductService productService,
                       SalePaymentService salePaymentService, AppSettingService appSettingService) {
        this.saleRepository = saleRepository;
        this.productService = productService;
        this.salePaymentService = salePaymentService;
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

        BigDecimal normalizedQuantity = validateQuantity(product, quantity);

        SaleItem existing = findItem(sale, product.getId());
        requireAvailableStock(product, existing, normalizedQuantity);

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

    public Sale setCustomer(Sale sale, Customer customer) {
        requireNotClosed(sale);
        sale.setCustomer(customer);
        return saleRepository.save(sale);
    }

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

    public Sale applySurcharge(Sale sale, BigDecimal surcharge) {
        if (sale.getStatus() != Sale.Status.ABERTA && sale.getStatus() != Sale.Status.EM_PAGAMENTO) {
            throw new IllegalSaleStateException();
        }
        requirePositive(surcharge, "acréscimo não pode ser negativo");

        sale.setSurcharge(surcharge.setScale(2, RoundingMode.HALF_UP));
        sale.setTotal(calculateTotal(sale));
        return saleRepository.save(sale);
    }

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

        sale.getItems().remove(item);
        sale.setAuthorizedBy(authorizedBy);
        sale.setTotal(calculateTotal(sale));
        return saleRepository.save(sale);
    }

    public Sale cancelSale(Sale sale, String reason, User authorizedBy) {
        if (sale.getStatus() == Sale.Status.FINALIZADA || sale.getStatus() == Sale.Status.CANCELADA) {
            throw new IllegalSaleStateException();
        }
        requireManager(authorizedBy);

        sale.setStatus(Sale.Status.CANCELADA);
        sale.setCancelReason(reason);
        sale.setCanceledAt(LocalDateTime.now());
        sale.setAuthorizedBy(authorizedBy);
        return saleRepository.save(sale);
    }

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
        return salePaymentService.calculateChange(total, amountReceived);
    }

    @Transactional
    public Sale pay(Sale sale, PaymentMethod method, BigDecimal amountReceived) {
        return salePaymentService.pay(sale, method, amountReceived);
    }

    @Transactional
    public Sale payCard(Sale sale, CardType cardType) {
        return salePaymentService.payCard(sale, cardType);
    }

    @Transactional
    public Sale payPix(Sale sale) {
        return salePaymentService.payPix(sale);
    }

    @Transactional
    public Sale payMixed(Sale sale, List<PaymentInput> inputs) {
        return salePaymentService.payMixed(sale, inputs);
    }

    @Transactional
    public Sale finalizeOnCredit(Sale sale) {
        return salePaymentService.finalizeOnCredit(sale);
    }

    public List<Sale> history() {
        return saleRepository.findByStatusOrderByCreatedAtDesc(Sale.Status.FINALIZADA);
    }

    private BigDecimal calculateTotal(Sale sale) {
        BigDecimal total = subtotal(sale)
                .subtract(nz(sale.getDiscount()))
                .add(nz(sale.getSurcharge()));
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            total = BigDecimal.ZERO;
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

    public boolean asksQuantityAfterScan() {
        return appSettingService.getBoolean(AppSettingService.SALE_ASK_QUANTITY_AFTER_SCAN, true);
    }

    public BigDecimal maxQuantityPerItem() {
        BigDecimal configured = appSettingService.getBigDecimal(
                AppSettingService.SALE_MAX_QUANTITY_PER_ITEM, DEFAULT_MAX_QUANTITY_PER_ITEM);
        return configured.signum() <= 0 ? DEFAULT_MAX_QUANTITY_PER_ITEM : configured;
    }

    public BigDecimal previewLineTotal(BigDecimal unitPrice, BigDecimal quantity) {
        if (unitPrice == null || quantity == null || quantity.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return unitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
    }

    private void requireAvailableStock(Product product, SaleItem existing, BigDecimal added) {
        if (appSettingService.getBoolean(AppSettingService.SALE_ALLOW_NEGATIVE_STOCK, false)) {
            return;
        }
        BigDecimal inSale = existing == null ? BigDecimal.ZERO : existing.getQuantity();
        BigDecimal available = product.getQuantity() == null ? BigDecimal.ZERO : product.getQuantity();
        if (inSale.add(added).compareTo(available) > 0) {
            String unit = product.getUnit() == Product.Unidade.UN ? "" : " " + product.getUnit().name().toLowerCase();
            throw new InvalidQuantityException("Estoque insuficiente: " + product.getDescription() + " tem apenas "
                    + available.stripTrailingZeros().toPlainString() + unit + " (já na venda: "
                    + inSale.stripTrailingZeros().toPlainString() + ")");
        }
    }

    private BigDecimal validateQuantity(Product product, BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidQuantityException("Quantidade deve ser maior que zero");
        }
        if (quantity.compareTo(maxQuantityPerItem()) > 0) {
            throw new InvalidQuantityException("Quantidade acima do limite — confirme");
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
