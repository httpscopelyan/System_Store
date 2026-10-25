package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.exception.CreditLimitExceededException;
import br.com.scopel.pfv.beta.exception.CustomerCreditBlockedException;
import br.com.scopel.pfv.beta.exception.FinancialOperationException;
import br.com.scopel.pfv.beta.model.Customer;
import br.com.scopel.pfv.beta.model.Sale;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Venda a prazo / crediário (PDV Prático: venda a prazo + CAR). Valida as regras
 * de crédito e, aprovada, fecha a venda e gera as parcelas a receber, tudo numa
 * única transação. Orquestra SaleService + ReceivableService sem acoplá-los.
 */
@Service
public class CreditSaleService {

    private final SaleService saleService;
    private final ReceivableService receivableService;
    private final AppSettingService appSettingService;

    public CreditSaleService(SaleService saleService,
                             ReceivableService receivableService,
                             AppSettingService appSettingService) {
        this.saleService = saleService;
        this.receivableService = receivableService;
        this.appSettingService = appSettingService;
    }

    @Transactional
    public Sale sellOnCredit(Sale sale, int installments, LocalDate firstDueDate, int intervalDays) {
        Customer customer = sale.getCustomer();
        if (customer == null) {
            throw new FinancialOperationException("venda a prazo exige um cliente (tecla F9).");
        }
        if (!appSettingService.getBoolean(AppSettingService.CREDIT_ALLOW_CREDIT_SALE, false)) {
            throw new FinancialOperationException("venda a prazo está desabilitada nas configurações.");
        }

        // Bloqueio manual ou por atraso (config credit.block_overdue).
        boolean blocked = customer.getCreditBlocked();
        if (!blocked && appSettingService.getBoolean(AppSettingService.CREDIT_BLOCK_OVERDUE, true)) {
            blocked = receivableService.hasOverdue(customer.getId(), LocalDate.now());
        }
        if (blocked) {
            throw new CustomerCreditBlockedException();
        }

        // Limite de crédito: dívida atual + esta venda não pode passar do limite.
        BigDecimal debt = receivableService.customerDebt(customer.getId());
        BigDecimal limit = customer.getCreditLimit() == null ? BigDecimal.ZERO : customer.getCreditLimit();
        if (debt.add(sale.getTotal()).compareTo(limit) > 0) {
            throw new CreditLimitExceededException();
        }

        Sale finalized = saleService.finalizeOnCredit(sale);
        receivableService.createInstallments(
                customer, finalized, finalized.getTotal(), installments, firstDueDate, intervalDays);
        return finalized;
    }
}
