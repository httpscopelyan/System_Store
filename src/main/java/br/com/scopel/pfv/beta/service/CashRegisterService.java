package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.exception.CashRegisterAlreadyOpenException;
import br.com.scopel.pfv.beta.exception.CashRegisterClosedException;
import br.com.scopel.pfv.beta.exception.InsufficientCashException;
import br.com.scopel.pfv.beta.exception.ManagerAuthorizationRequiredException;
import br.com.scopel.pfv.beta.exception.RegisterNotFindException;
import br.com.scopel.pfv.beta.exception.SplitPaymentInProgressException;
import br.com.scopel.pfv.beta.model.AccountSummary;
import br.com.scopel.pfv.beta.model.AuditAction;
import br.com.scopel.pfv.beta.model.AuditEntityType;
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
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.audit.AuditLogService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CashRegisterService {

    private static final int MAX_REASON_LENGTH = 155;

    private final CashRegisterRepository registerRepository;
    private final CashMovementRepository movementRepository;
    private final SaleRepository saleRepository;
    private final PaymentRepository paymentRepository;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;

    public CashRegisterService(CashRegisterRepository registerRepository,
                               CashMovementRepository movementRepository,
                               SaleRepository saleRepository,
                               PaymentRepository paymentRepository,
                               AuditLogService auditLogService,
                               UserRepository userRepository) {
        this.registerRepository = registerRepository;
        this.movementRepository = movementRepository;
        this.saleRepository = saleRepository;
        this.paymentRepository = paymentRepository;
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
    }

    public List<Payment> payments(CashRegister cashRegister) {
        return paymentRepository.findBySale_CashRegister_Id(cashRegister.getId());
    }

    public List<CashRegister> history(User operator) {
        if (!SharedCashRegisterMembers.includes(operator)) {
            return registerRepository.findTop20ByOperatorOrderByOpenedAtDesc(operator);
        }
        return registerRepository.findTop20ByOperator_IdInOrderByOpenedAtDesc(sharedRegisterOperatorIds(operator));
    }

    public List<CashMovement> movements(CashRegister cashRegister) {
        return movementRepository.findByCashRegister(cashRegister);
    }

    public List<Sale> sales(CashRegister cashRegister) {
        return saleRepository.findByCashRegister_IdOrderByCreatedAtAsc(cashRegister.getId());
    }

    public CashRegister open(User operator, BigDecimal openingAmount) {
        if (findOpenByOperator(operator).isPresent()) {
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
        if (!SharedCashRegisterMembers.includes(operator)) {
            return registerRepository.findByOperatorAndStatus(operator, CashRegister.Status.OPEN);
        }
        return registerRepository.findFirstByOperator_IdInAndStatusOrderByOpenedAtAsc(
                sharedRegisterOperatorIds(operator), CashRegister.Status.OPEN);
    }

    private Set<Long> sharedRegisterOperatorIds(User operator) {
        Set<Long> sharedRegisterOperatorIds = userRepository.findActiveAccounts().stream()
                .filter(account -> SharedCashRegisterMembers.includesName(account.name())
                        || SharedCashRegisterMembers.includesName(account.login()))
                .map(AccountSummary::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (operator.getId() != null) {
            sharedRegisterOperatorIds.add(operator.getId());
        }
        return sharedRegisterOperatorIds;
    }

    private void requireOperator(User operator) {
        if (operator == null) {
            throw new IllegalArgumentException("Operador não identificado.");
        }
    }

    public BigDecimal currentBalance(CashRegister cashRegister) {
        BigDecimal balance = cashRegister.getOpeningAmount();

        for (CashMovement movement : movementRepository.findByCashRegister(cashRegister)) {
            switch (movement.getType()) {
                case SUPRIMENTO, RETIFICACAO -> balance = balance.add(movement.getAmount());
                case SANGRIA -> balance = balance.subtract(movement.getAmount());
            }
        }

        for (Payment p : paymentRepository.findBySale_CashRegister_Id(cashRegister.getId())) {
            if (p.getMethod() == PaymentMethod.DINHEIRO && p.getSale().getStatus() == Sale.Status.FINALIZADA) {
                BigDecimal net = nz(p.getAmountReceived()).subtract(nz(p.getChange()));
                balance = balance.add(net);
            }
        }

        return balance;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public CashMovement supplement(Long cashRegisterId, BigDecimal amount, String reason, User operator) {
        requireOperator(operator);
        String normalizedReason = MovementReason.require(reason, MAX_REASON_LENGTH);
        CashRegister r = registerRepository.findById(cashRegisterId)
                .orElseThrow(() -> new RegisterNotFindException("Caixa não registrado!"));
        if (r.getStatus() != CashRegister.Status.OPEN) {
            throw new CashRegisterClosedException();
        }

        CashMovement cashMovement = new CashMovement();

        cashMovement.setType(CashMovement.Type.SUPRIMENTO);
        cashMovement.setAmount(amount);
        cashMovement.setReason(normalizedReason);
        cashMovement.setCreatedAt(LocalDateTime.now());

        cashMovement.setCashRegister(r);
        cashMovement.setOperator(operator);
        return movementRepository.save(cashMovement);

    }

    public CashMovement withdrawal(Long cashRegisterId, BigDecimal amount, String reason, User operator) {
        requireOperator(operator);
        String normalizedReason = MovementReason.require(reason, MAX_REASON_LENGTH);
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
        cashMovement.setReason(normalizedReason);
        cashMovement.setCreatedAt(LocalDateTime.now());
        cashMovement.setCashRegister(r);
        cashMovement.setOperator(operator);

        return movementRepository.save(cashMovement);
    }

    @Transactional
    public CashMovement rectify(Long movementId, BigDecimal correctAmount, String reason, User authorizedBy) {
        if (authorizedBy == null || authorizedBy.getRole() != User.Role.GERENTE) {
            throw new ManagerAuthorizationRequiredException();
        }
        String normalizedReason = MovementReason.require(reason, MAX_REASON_LENGTH);
        if (correctAmount == null || correctAmount.signum() < 0) {
            throw new IllegalArgumentException("O valor corrigido não pode ser negativo.");
        }
        CashMovement original = movementRepository.findById(movementId)
                .orElseThrow(() -> new RegisterNotFindException("Movimentação não encontrada!"));
        if (original.getType() == CashMovement.Type.RETIFICACAO) {
            throw new IllegalArgumentException("Uma retificação não pode ser retificada.");
        }
        CashRegister register = original.getCashRegister();
        if (register.getStatus() != CashRegister.Status.OPEN) {
            throw new CashRegisterClosedException();
        }
        if (movementRepository.existsByCorrectedMovement(original)) {
            throw new IllegalArgumentException("Esta movimentação já foi retificada.");
        }
        BigDecimal corrected = correctAmount.setScale(2, RoundingMode.HALF_UP);
        if (corrected.compareTo(original.getAmount()) == 0) {
            throw new IllegalArgumentException("O valor corrigido é igual ao original.");
        }
        BigDecimal delta = original.getType() == CashMovement.Type.SANGRIA
                ? original.getAmount().subtract(corrected)
                : corrected.subtract(original.getAmount());
        if (currentBalance(register).add(delta).signum() < 0) {
            throw new InsufficientCashException();
        }

        CashMovement rectification = new CashMovement();
        rectification.setType(CashMovement.Type.RETIFICACAO);
        rectification.setAmount(delta);
        rectification.setReason(normalizedReason);
        rectification.setCreatedAt(LocalDateTime.now());
        rectification.setCashRegister(register);
        rectification.setCorrectedMovement(original);
        rectification.setAuthorizedBy(authorizedBy);
        rectification.setOperator(authorizedBy);
        CashMovement savedRectification = movementRepository.save(rectification);
        auditLogService.record(authorizedBy, AuditAction.CASH_RECTIFICATION, AuditEntityType.CASH_MOVEMENT,
                original.getId(), "Retificação de " + original.getType().name() + " #" + original.getId()
                        + " do caixa #" + register.getId() + " - motivo: " + normalizedReason,
                original.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString(), corrected.toPlainString());
        return savedRectification;
    }

    public CashRegister close(Long cashRegisterId, BigDecimal countedAmount) {

        CashRegister r = registerRepository.findById(cashRegisterId)
                .orElseThrow(() -> new RegisterNotFindException("Caixa não registrado!"));
        if (r.getStatus() != CashRegister.Status.OPEN) {
            throw new CashRegisterClosedException();
        }
        if (paymentRepository.existsBySale_CashRegister_IdAndSale_Status(cashRegisterId, Sale.Status.EM_PAGAMENTO)) {
            throw new SplitPaymentInProgressException(
                    "Há venda com pagamento dividido em andamento neste caixa. Conclua a venda antes de fechar.");
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
