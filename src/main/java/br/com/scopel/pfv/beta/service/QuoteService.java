package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.exception.IllegalQuoteStateException;
import br.com.scopel.pfv.beta.exception.QuoteNotFoundException;
import br.com.scopel.pfv.beta.model.*;
import br.com.scopel.pfv.beta.repository.QuoteRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Orçamentos (PDV Prático: Orcamentos). Monta um orçamento e, quando o cliente
 * fecha, converte em venda (ABERTA) pronta para pagamento. A conversão usa o
 * preço atual do produto (o orçamento tem validade); RN19 congela no item da venda.
 */
@Service
public class QuoteService {

    private final QuoteRepository quoteRepository;
    private final ProductService productService;
    private final SaleService saleService;

    public QuoteService(QuoteRepository quoteRepository,
                        ProductService productService,
                        SaleService saleService) {
        this.quoteRepository = quoteRepository;
        this.productService = productService;
        this.saleService = saleService;
    }

    public Quote openQuote(Customer customer, User operator, LocalDate validUntil) {
        Quote quote = new Quote();
        quote.setCustomer(customer);
        quote.setOperator(operator);
        quote.setCreatedAt(LocalDateTime.now());
        quote.setValidUntil(validUntil);
        quote.setTotal(BigDecimal.ZERO);
        quote.setStatus(Quote.Status.ABERTO);
        return quoteRepository.save(quote);
    }

    public Quote findById(Long id) {
        return quoteRepository.findById(id).orElseThrow(() -> new QuoteNotFoundException(id));
    }

    /** Adiciona item ao orçamento; repetir o produto soma a quantidade (RN07). */
    public Quote addItem(Quote quote, String productCode, BigDecimal quantity) {
        if (quote.getStatus() != Quote.Status.ABERTO) {
            throw new IllegalQuoteStateException();
        }
        Product product = productService.findCode(productCode);

        QuoteItem existing = null;
        for (QuoteItem item : quote.getItems()) {
            if (item.getProduct().getId().equals(product.getId())) {
                existing = item;
                break;
            }
        }

        if (existing != null) {
            existing.setQuantity(existing.getQuantity().add(quantity));
            existing.setTotalPrice(existing.getUnitPrice().multiply(existing.getQuantity())
                    .setScale(2, RoundingMode.HALF_UP));
        } else {
            QuoteItem item = new QuoteItem();
            item.setQuote(quote);
            item.setProduct(product);
            item.setQuantity(quantity);
            item.setUnitPrice(product.getPrice());
            item.setTotalPrice(product.getPrice().multiply(quantity).setScale(2, RoundingMode.HALF_UP));
            quote.getItems().add(item);
        }

        quote.setTotal(calculateTotal(quote));
        return quoteRepository.save(quote);
    }

    /**
     * Converte o orçamento em venda ABERTA (pronta para pagamento). Marca o
     * orçamento como CONVERTIDO. Os itens entram pela via normal da venda,
     * pegando o preço atual (RN19 congela no item).
     */
    @Transactional
    public Sale convertToSale(Quote quote, CashRegister cashRegister, User operator) {
        if (quote.getStatus() != Quote.Status.ABERTO || quote.getItems().isEmpty()) {
            throw new IllegalQuoteStateException();
        }

        Sale sale = saleService.openSale(operator, cashRegister);
        if (quote.getCustomer() != null) {
            sale = saleService.setCustomer(sale, quote.getCustomer());
        }
        for (QuoteItem item : quote.getItems()) {
            sale = saleService.addItem(sale, item.getProduct().getCode(), item.getQuantity());
        }

        quote.setStatus(Quote.Status.CONVERTIDO);
        quoteRepository.save(quote);
        return sale;
    }

    public Quote cancel(Quote quote) {
        if (quote.getStatus() == Quote.Status.CONVERTIDO) {
            throw new IllegalQuoteStateException();
        }
        quote.setStatus(Quote.Status.CANCELADO);
        return quoteRepository.save(quote);
    }

    private BigDecimal calculateTotal(Quote quote) {
        BigDecimal total = BigDecimal.ZERO;
        for (QuoteItem item : quote.getItems()) {
            total = total.add(item.getTotalPrice());
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }
}
