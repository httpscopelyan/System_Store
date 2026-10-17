package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.exception.CashRegisterAlreadyOpenException;
import br.com.scopel.pfv.beta.exception.CashRegisterClosedException;
import br.com.scopel.pfv.beta.exception.InsufficientCashException;
import br.com.scopel.pfv.beta.exception.RegisterNotFindException;
import br.com.scopel.pfv.beta.model.CashMovement;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.CashMovementRepository;
import br.com.scopel.pfv.beta.repository.CashRegisterRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class CashRegisterService {

    private final CashRegisterRepository registerRepository;
    private final CashMovementRepository movementRepository;


    public CashRegisterService(CashRegisterRepository registerRepository, CashMovementRepository movementRepository) {
        this.registerRepository = registerRepository;
        this.movementRepository = movementRepository;
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

    public BigDecimal currentBalance(CashRegister cashRegister) {
        BigDecimal balance = cashRegister.getOpeningAmount();

        for (CashMovement movement : movementRepository.findByCashRegister(cashRegister)) {
            if (movement.getType() == CashMovement.Type.SUPRIMENTO) {
                balance = balance.add(movement.getAmount());
            } else {
                balance = balance.subtract(movement.getAmount());
            }
        }

        return balance;
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
