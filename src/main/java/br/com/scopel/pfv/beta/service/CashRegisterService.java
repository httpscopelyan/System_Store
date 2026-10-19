package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.exception.CashRegisterAlreadyOpenException;
import br.com.scopel.pfv.beta.exception.CashRegisterClosedException;
import br.com.scopel.pfv.beta.exception.InsufficientCashException;
import br.com.scopel.pfv.beta.exception.RegisterNotFindException;
import br.com.scopel.pfv.beta.model.CashMovement;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.Payment;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.CashMovementRepository;
import br.com.scopel.pfv.beta.repository.CashRegisterRepository;
import br.com.scopel.pfv.beta.repository.PaymentRepository;
import br.com.scopel.pfv.beta.repository.SaleRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class CashRegisterService {

    private final CashRegisterRepository registerRepository;
    private final CashMovementRepository movementRepository;
    private final SaleRepository saleRepository;
    private final PaymentRepository paymentRepository;


    public CashRegisterService(CashRegisterRepository registerRepository,
                               CashMovementRepository movementRepository,
                               SaleRepository saleRepository,
                               PaymentRepository paymentRepository) {
        this.registerRepository = registerRepository;
        this.movementRepository = movementRepository;
        this.saleRepository = saleRepository;
        this.paymentRepository = paymentRepository;
    }

    /** Pagamentos de todas as vendas do caixa (para a movimentação por forma). */
    public List<Payment> payments(CashRegister cashRegister) {
        return paymentRepository.findBySale_CashRegister_Id(cashRegister.getId());
    }

    /** Últimas aberturas do operador (para o painel esquerdo da tela de Caixa). */
    public List<CashRegister> history(User operator) {
        return registerRepository.findTop20ByOperatorOrderByOpenedAtDesc(operator);
    }

    /** Sangrias/suprimentos do caixa. */
    public List<CashMovement> movements(CashRegister cashRegister) {
        return movementRepository.findByCashRegister(cashRegister);
    }

    /** Vendas do caixa (movimentação). */
    public List<Sale> sales(CashRegister cashRegister) {
        return saleRepository.findByCashRegister_IdOrderByCreatedAtAsc(cashRegister.getId());
    }

    public CashRegister open(User operator, BigDecimal openingAmount) {
        if (registerRepository.findByOperatorAndStatus(operator, CashRegister.Status.OPEN).isPresent()) {
            throw new CashRegisterAlreadyOpenException(operator);
        }

        CashRegister cashRegister = new CashRegister();

        cashRegister.setOperator(operator);
        cashRegister.setStatus(CashRegister.Status.OPEN);
        cashRegister.setOpeningAmount(openingAmount);
        cashRegister.setOpenedAt(LocalDateTime.now());

        return registerRepository.save(cashRegister);

    }


    public Optional<CashRegister> findOpenByOperator(User operator) {
        return registerRepository.findByOperatorAndStatus(operator, CashRegister.Status.OPEN);
    }

    /**
     * Saldo em dinheiro esperado no caixa:
     *   fundo + dinheiro das vendas + suprimentos − sangrias.
     * (O dinheiro das vendas = pagamentos em DINHEIRO, líquidos do troco.)
     */
    public BigDecimal currentBalance(CashRegister cashRegister) {
        BigDecimal balance = cashRegister.getOpeningAmount();

        for (CashMovement movement : movementRepository.findByCashRegister(cashRegister)) {
            if (movement.getType() == CashMovement.Type.SUPRIMENTO) {
                balance = balance.add(movement.getAmount());
            } else {
                balance = balance.subtract(movement.getAmount());
            }
        }

        // + dinheiro recebido nas vendas do caixa (líquido do troco)
        for (Payment p : paymentRepository.findBySale_CashRegister_Id(cashRegister.getId())) {
            if (p.getMethod() == PaymentMethod.DINHEIRO) {
                BigDecimal net = nz(p.getAmountReceived()).subtract(nz(p.getChange()));
                balance = balance.add(net);
            }
        }

        return balance;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public CashMovement supplement(Long cashRegisterId, BigDecimal amount, String reason) {
        CashRegister r = registerRepository.findById(cashRegisterId)
                .orElseThrow(() -> new RegisterNotFindException("Caixa não registrado!"));
        if (r.getStatus() != CashRegister.Status.OPEN) {
            throw new CashRegisterClosedException();
        }

        CashMovement cashMovement = new CashMovement();

        cashMovement.setType(CashMovement.Type.SUPRIMENTO);
        cashMovement.setAmount(amount);
        cashMovement.setReason(reason);
        cashMovement.setCreatedAt(LocalDateTime.now());

        cashMovement.setCashRegister(r);
        return movementRepository.save(cashMovement);

    }

    public CashMovement withdrawal(Long cashRegisterId, BigDecimal amount, String reason) {
        CashRegister r = registerRepository.findById(cashRegisterId)
                .orElseThrow(() -> new RegisterNotFindException("Caixa não registrado!"));
        BigDecimal balance = currentBalance(r);

        if (r.getStatus() != CashRegister.Status.OPEN) {
            throw new CashRegisterClosedException();
        }

        if (balance.compareTo(amount) < 0) {
            throw new InsufficientCashException();
        }

        CashMovement cashMovement = new CashMovement();

        cashMovement.setType(CashMovement.Type.SANGRIA);
        cashMovement.setAmount(amount);
        cashMovement.setReason(reason);
        cashMovement.setCreatedAt(LocalDateTime.now());
        cashMovement.setCashRegister(r);

        return movementRepository.save(cashMovement);
    }

    public CashRegister close(Long cashRegisterId, BigDecimal countedAmount) {

        CashRegister r = registerRepository.findById(cashRegisterId)
                .orElseThrow(() -> new RegisterNotFindException("Caixa não registrado!"));
        if (r.getStatus() != CashRegister.Status.OPEN) {
            throw new CashRegisterClosedException();
        }

        BigDecimal expected = currentBalance(r);

        r.setExpectedAmount(expected);
        r.setClosingAmount(countedAmount);
        r.setDifference(countedAmount.subtract(expected));
        r.setClosedAt(LocalDateTime.now());
        r.setStatus(CashRegister.Status.CLOSED);

        return registerRepository.save(r);
    }
}
