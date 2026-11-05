package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.DirectCharge;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.DirectChargeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DirectChargeService {

    private final DirectChargeRepository repository;

    public DirectChargeService(DirectChargeRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public DirectCharge register(BigDecimal amount, PaymentMethod method, CardType cardType, User operator,
                                 CashRegister cashRegister) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("O valor da cobrança deve ser maior que zero.");
        }
        if (method != PaymentMethod.CARTAO && method != PaymentMethod.PIX) {
            throw new IllegalArgumentException("Cobrança direta só aceita cartão ou PIX.");
        }
        if (method == PaymentMethod.CARTAO && cardType == null) {
            throw new IllegalArgumentException("Informe débito ou crédito.");
        }
        if (operator == null) {
            throw new IllegalArgumentException("Operador não identificado.");
        }
        DirectCharge charge = new DirectCharge();
        charge.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        charge.setMethod(method);
        charge.setCardType(method == PaymentMethod.CARTAO ? cardType : null);
        charge.setOperator(operator);
        charge.setCashRegister(cashRegister);
        charge.setCreatedAt(LocalDateTime.now());
        return repository.save(charge);
    }

    public List<DirectCharge> recent() {
        return repository.findTop10ByOrderByCreatedAtDesc();
    }
}
