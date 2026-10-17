package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.CashRegisterAlreadyOpenException;
import br.com.scopel.pfv.beta.exception.CashRegisterClosedException;
import br.com.scopel.pfv.beta.exception.InsufficientCashException;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class CashRegisterServiceTests {

    @Autowired
    private CashRegisterService cashRegisterService;

    @Autowired
    private UserRepository userRepository;

    private User newOperator() {
        User user = new User();
        user.setName("Operador");
        user.setLogin("op_" + UUID.randomUUID());
        user.setPassword("x");
        user.setRole(User.Role.OPERADOR);
        user.setActive(true);
        return userRepository.save(user);
    }

    @Test
    void openCreatesOpenCashRegister() {
        User operator = newOperator();

        CashRegister caixa = cashRegisterService.open(operator, new BigDecimal("100.00"));

        assertNotNull(caixa.getId());
        assertEquals(CashRegister.Status.OPEN, caixa.getStatus());
        assertNotNull(caixa.getOpenedAt());
    }

    @Test
    void openTwiceThrows() {
        User operator = newOperator();
        cashRegisterService.open(operator, new BigDecimal("100.00"));

        assertThrows(CashRegisterAlreadyOpenException.class, () -> {
            cashRegisterService.open(operator, new BigDecimal("50.00"));
        });
    }

    @Test
    void supplementAndWithdrawalAffectBalance() {
        User operator = newOperator();
        CashRegister caixa = cashRegisterService.open(operator, new BigDecimal("100.00"));

        cashRegisterService.supplement(caixa.getId(), new BigDecimal("50.00"), "reforço");
        cashRegisterService.withdrawal(caixa.getId(), new BigDecimal("30.00"), "retirada");

        assertEquals(0, new BigDecimal("120.00").compareTo(cashRegisterService.currentBalance(caixa)));
    }

    @Test
    void withdrawalBeyondBalanceThrows() {
        User operator = newOperator();
        CashRegister caixa = cashRegisterService.open(operator, new BigDecimal("100.00"));

        assertThrows(InsufficientCashException.class, () -> {
            cashRegisterService.withdrawal(caixa.getId(), new BigDecimal("150.00"), "retirada grande");
        });
    }

    @Test
    void closeRecordsDifferenceAndCloses() {
        User operator = newOperator();
        CashRegister caixa = cashRegisterService.open(operator, new BigDecimal("100.00"));
        cashRegisterService.supplement(caixa.getId(), new BigDecimal("50.00"), "reforço");

        CashRegister fechado = cashRegisterService.close(caixa.getId(), new BigDecimal("140.00"));

        assertEquals(CashRegister.Status.CLOSED, fechado.getStatus());
        assertEquals(0, new BigDecimal("150.00").compareTo(fechado.getExpectedAmount()));
        assertEquals(0, new BigDecimal("140.00").compareTo(fechado.getClosingAmount()));
        assertEquals(0, new BigDecimal("-10.00").compareTo(fechado.getDifference()));  // diferença negativa
        assertNotNull(fechado.getClosedAt());
    }

    @Test
    void movementOnClosedThrows() {
        User operator = newOperator();
        CashRegister caixa = cashRegisterService.open(operator, new BigDecimal("100.00"));
        cashRegisterService.close(caixa.getId(), new BigDecimal("100.00"));

        assertThrows(CashRegisterClosedException.class, () -> {
            cashRegisterService.withdrawal(caixa.getId(), new BigDecimal("10.00"), "tardia");
        });
    }

    @Test
    void findOpenByOperatorReturnsOpenCashRegister() {
        User operator = newOperator();
        CashRegister caixa = cashRegisterService.open(operator, new BigDecimal("100.00"));

        Optional<CashRegister> encontrado = cashRegisterService.findOpenByOperator(operator);

        assertTrue(encontrado.isPresent());
        assertEquals(caixa.getId(), encontrado.get().getId());
        assertEquals(CashRegister.Status.OPEN, encontrado.get().getStatus());
    }

    @Test
    void findOpenByOperatorWithoutCashRegisterReturnsEmpty() {
        User operator = newOperator();

        Optional<CashRegister> encontrado = cashRegisterService.findOpenByOperator(operator);

        assertTrue(encontrado.isEmpty());
    }

    @Test
    void findOpenByOperatorAfterCloseReturnsEmpty() {
        User operator = newOperator();
        CashRegister caixa = cashRegisterService.open(operator, new BigDecimal("100.00"));
        cashRegisterService.close(caixa.getId(), new BigDecimal("100.00"));

        Optional<CashRegister> encontrado = cashRegisterService.findOpenByOperator(operator);

        assertTrue(encontrado.isEmpty());
    }

    @Test
    void findOpenByOperatorIsolatedPerOperator() {
        User operadorA = newOperator();
        User operadorB = newOperator();
        cashRegisterService.open(operadorA, new BigDecimal("100.00"));

        Optional<CashRegister> encontrado = cashRegisterService.findOpenByOperator(operadorB);

        assertTrue(encontrado.isEmpty());
    }
}