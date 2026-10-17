package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.exception.IllegalSaleStateException;
import br.com.scopel.pfv.beta.exception.InsufficientPaymentException;
import br.com.scopel.pfv.beta.exception.PaymentMethodNotAvailableException;
import br.com.scopel.pfv.beta.model.*;
import br.com.scopel.pfv.beta.repository.PaymentRepository;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.repository.SaleRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
public class SaleService {

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final PaymentRepository paymentRepository;

    public SaleService(SaleRepository saleRepository, ProductService productService, ProductRepository productRepository, PaymentRepository paymentRepository) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
        this.productService = productService;
        this.paymentRepository = paymentRepository;
    }

    public Sale openSale(User operator, CashRegister cashRegister) {
        Sale sale = new Sale();
        sale.setOperator(operator);
        sale.setCashRegister(cashRegister);
        sale.setCreatedAt(LocalDateTime.now());
        sale.setTotal(BigDecimal.ZERO);
        sale.setStatus(Sale.Status.ABERTA);
        return saleRepository.save(sale);
    }

    public Sale addItem(Sale sale, String productCode, BigDecimal quantity) {
        if (sale.getStatus() != Sale.Status.ABERTA) {
            throw new IllegalSaleStateException();
        }

        Product product = productService.findCode(productCode);

        SaleItem existing = null;
        for (SaleItem item : sale.getItems()) {
            if (item.getProduct().getId().equals(product.getId())) {
                existing = item;
                break;
            }
        }

        if (existing != null) {

            existing.setQuantity(existing.getQuantity().add(quantity));
            existing.setTotalPrice(existing.getUnitPrice().multiply(existing.getQuantity()).setScale(2, RoundingMode.HALF_UP));

        } else {
            SaleItem item = new SaleItem();
            item.setSale(sale);
            item.setProduct(product);
            item.setQuantity(quantity);
            item.setUnitPrice(product.getPrice());
            item.setTotalPrice(product.getPrice().multiply(quantity).setScale(2, RoundingMode.HALF_UP));
            sale.getItems().add(item);
        }

        sale.setTotal(calculateTotal(sale));
        return saleRepository.save(sale);
    }

    private Sale finalizeSale(Sale sale) {
        decreaseStock(sale);

        sale.setStatus(Sale.Status.FINALIZADA);
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

    private void decreaseStock(Sale sale) {
        for (SaleItem item : sale.getItems()) {
            Product product = item.getProduct();
            product.setQuantity(product.getQuantity().subtract(item.getQuantity()));
            productRepository.save(product);
        }
    }

    private BigDecimal calculateTotal(Sale sale) {
        BigDecimal total = BigDecimal.ZERO;
        for (SaleItem item : sale.getItems()) {
            total = total.add(item.getTotalPrice());
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }
}