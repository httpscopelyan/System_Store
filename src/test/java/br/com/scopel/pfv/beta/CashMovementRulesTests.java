package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.CashRegisterClosedException;
import br.com.scopel.pfv.beta.exception.InsufficientCashException;
import br.com.scopel.pfv.beta.exception.ManagerAuthorizationRequiredException;
import br.com.scopel.pfv.beta.exception.MissingReasonException;
import br.com.scopel.pfv.beta.model.CashMovement;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.CashMovementRepository;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class CashMovementRulesTests {

    @Autowired private CashRegisterService cashRegisterService;
    @Autowired private CashMovementRepository movementRepository;
    @Autowired private UserRepository userRepository;

    private User operator;
    private User manager;
    private CashRegister register;

    private User user(User.Role role) {
        User user = new User();
        user.setName(role.name());
        user.setLogin(role.name().toLowerCase() + "_" + UUID.randomUUID());
        user.setPassword("x");
        user.setRole(role);
        user.setActive(true);
        return userRepository.save(user);
    }

    @BeforeEach
    void prepare() {
        operator = user(User.Role.OPERADOR);
        manager = user(User.Role.GERENTE);
        register = cashRegisterService.open(operator, new BigDecimal("100.00"));
    }

    private BigDecimal balance() {
        return cashRegisterService.currentBalance(register);
    }

    @Test
    void withdrawalWithoutReasonIsRefused_RN28() {
        assertThrows(MissingReasonException.class,
                () -> cashRegisterService.withdrawal(register.getId(), new BigDecimal("10"), null, register.getOperator()));
        assertThrows(MissingReasonException.class,
                () -> cashRegisterService.withdrawal(register.getId(), new BigDecimal("10"), "   ", register.getOperator()));
        assertThrows(MissingReasonException.class,
                () -> cashRegisterService.withdrawal(register.getId(), new BigDecimal("10"), "ab", register.getOperator()));
        assertEquals(0, new BigDecimal("100.00").compareTo(balance()));
    }

    @Test
    void supplementWithoutReasonIsRefused_RN28() {
        assertThrows(MissingReasonException.class,
                () -> cashRegisterService.supplement(register.getId(), new BigDecimal("10"), "", register.getOperator()));
        assertEquals(0, new BigDecimal("100.00").compareTo(balance()));
    }

    @Test
    void reasonIsTrimmedAndStored_RN28() {
        CashMovement movement = cashRegisterService.withdrawal(register.getId(), new BigDecimal("10"),
                "  Pagamento fornecedor  ", register.getOperator());
        assertEquals("Pagamento fornecedor", movement.getReason());
    }

    @Test
    void reasonLongerThanTheColumnIsRefused_RN28() {
        assertThrows(MissingReasonException.class,
                () -> cashRegisterService.supplement(register.getId(), new BigDecimal("10"), "x".repeat(156), register.getOperator()));
    }

    @Test
    void rectifyingAWithdrawalDownReturnsTheDifferenceToTheDrawer_RN29() {
        CashMovement withdrawal = cashRegisterService.withdrawal(register.getId(), new BigDecimal("100"), "retirada errada", register.getOperator());
        assertEquals(0, BigDecimal.ZERO.compareTo(balance()));
        CashMovement rectification = cashRegisterService.rectify(withdrawal.getId(), new BigDecimal("10"),
                "Valor digitado errado", manager);
        assertEquals(0, new BigDecimal("90.00").compareTo(rectification.getAmount()));
        assertEquals(0, new BigDecimal("90.00").compareTo(balance()));
    }

    @Test
    void rectifyingASupplementUpAndDown_RN29() {
        CashMovement supplement = cashRegisterService.supplement(register.getId(), new BigDecimal("50"), "reforço", register.getOperator());
        cashRegisterService.rectify(supplement.getId(), new BigDecimal("80"), "faltou lançar 30", manager);
        assertEquals(0, new BigDecimal("180.00").compareTo(balance()));
        CashMovement other = cashRegisterService.supplement(register.getId(), new BigDecimal("50"), "outro reforço", register.getOperator());
        cashRegisterService.rectify(other.getId(), new BigDecimal("20"), "sobrou 30 lançado", manager);
        assertEquals(0, new BigDecimal("200.00").compareTo(balance()));
    }

    @Test
    void theOriginalMovementIsNeverChanged_RN29() {
        CashMovement withdrawal = cashRegisterService.withdrawal(register.getId(), new BigDecimal("40"), "retirada", register.getOperator());
        cashRegisterService.rectify(withdrawal.getId(), new BigDecimal("4"), "erro de digitação", manager);
        CashMovement original = movementRepository.findById(withdrawal.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("40").compareTo(original.getAmount()));
        assertEquals("retirada", original.getReason());
        assertEquals(CashMovement.Type.SANGRIA, original.getType());
    }

    @Test
    void rectificationRecordsWhoAuthorizedAndWhatItCorrects_RN29() {
        CashMovement withdrawal = cashRegisterService.withdrawal(register.getId(), new BigDecimal("40"), "retirada", register.getOperator());
        CashMovement rectification = cashRegisterService.rectify(withdrawal.getId(), new BigDecimal("4"),
                "erro de digitação", manager);
        assertEquals(CashMovement.Type.RETIFICACAO, rectification.getType());
        assertEquals(manager.getId(), rectification.getAuthorizedBy().getId());
        assertEquals(withdrawal.getId(), rectification.getCorrectedMovement().getId());
        assertEquals("erro de digitação", rectification.getReason());
        assertNotNull(rectification.getCreatedAt());
    }

    @Test
    void operatorCannotRectify_RN29() {
        CashMovement withdrawal = cashRegisterService.withdrawal(register.getId(), new BigDecimal("40"), "retirada", register.getOperator());
        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> cashRegisterService.rectify(withdrawal.getId(), new BigDecimal("4"), "erro", operator));
        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> cashRegisterService.rectify(withdrawal.getId(), new BigDecimal("4"), "erro", null));
    }

    @Test
    void rectificationNeedsAReason_RN29() {
        CashMovement withdrawal = cashRegisterService.withdrawal(register.getId(), new BigDecimal("40"), "retirada", register.getOperator());
        assertThrows(MissingReasonException.class,
                () -> cashRegisterService.rectify(withdrawal.getId(), new BigDecimal("4"), " ", manager));
    }

    @Test
    void aMovementCanBeRectifiedOnlyOnce_RN29() {
        CashMovement withdrawal = cashRegisterService.withdrawal(register.getId(), new BigDecimal("40"), "retirada", register.getOperator());
        cashRegisterService.rectify(withdrawal.getId(), new BigDecimal("4"), "erro de digitação", manager);
        assertThrows(IllegalArgumentException.class,
                () -> cashRegisterService.rectify(withdrawal.getId(), new BigDecimal("5"), "outro erro", manager));
    }

    @Test
    void aRectificationCannotBeRectified_RN29() {
        CashMovement withdrawal = cashRegisterService.withdrawal(register.getId(), new BigDecimal("40"), "retirada", register.getOperator());
        CashMovement rectification = cashRegisterService.rectify(withdrawal.getId(), new BigDecimal("4"),
                "erro de digitação", manager);
        assertThrows(IllegalArgumentException.class,
                () -> cashRegisterService.rectify(rectification.getId(), new BigDecimal("1"), "de novo", manager));
    }

    @Test
    void sameOrNegativeCorrectedValueIsRefused_RN29() {
        CashMovement withdrawal = cashRegisterService.withdrawal(register.getId(), new BigDecimal("40"), "retirada", register.getOperator());
        assertThrows(IllegalArgumentException.class,
                () -> cashRegisterService.rectify(withdrawal.getId(), new BigDecimal("40.00"), "igual", manager));
        assertThrows(IllegalArgumentException.class,
                () -> cashRegisterService.rectify(withdrawal.getId(), new BigDecimal("-1"), "negativo", manager));
    }

    @Test
    void rectifyingAWithdrawalUpwardBeyondTheDrawerIsRefused_RN29() {
        CashMovement withdrawal = cashRegisterService.withdrawal(register.getId(), new BigDecimal("40"), "retirada", register.getOperator());
        assertThrows(InsufficientCashException.class,
                () -> cashRegisterService.rectify(withdrawal.getId(), new BigDecimal("500"), "valor maior", manager));
    }

    @Test
    void closedRegisterCannotBeRectified_RN29() {
        CashMovement withdrawal = cashRegisterService.withdrawal(register.getId(), new BigDecimal("40"), "retirada", register.getOperator());
        cashRegisterService.close(register.getId(), new BigDecimal("60.00"));
        assertThrows(CashRegisterClosedException.class,
                () -> cashRegisterService.rectify(withdrawal.getId(), new BigDecimal("4"), "tarde demais", manager));
    }

    @Test
    void unknownMovementIsRefused_RN29() {
        assertThrows(RuntimeException.class,
                () -> cashRegisterService.rectify(-1L, new BigDecimal("4"), "inexistente", manager));
    }
}
