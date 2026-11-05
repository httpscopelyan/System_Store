package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.DirectCharge;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.DirectChargeService;
import br.com.scopel.pfv.beta.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class DirectChargeServiceTests {

    @Autowired private DirectChargeService service;
    @Autowired private UserService userService;

    private User operator() {
        User user = new User();
        user.setLogin("op" + UUID.randomUUID().toString().substring(0, 8));
        user.setName(user.getLogin());
        user.setPassword("senha1");
        user.setRole(User.Role.OPERADOR);
        return userService.save(user);
    }

    @Test
    void registersCardChargeWithOperatorAndTimestamp() {
        DirectCharge charge = service.register(new BigDecimal("12.5"), PaymentMethod.CARTAO, CardType.DEBITO,
                operator(), null);

        assertNotNull(charge.getId());
        assertEquals(0, new BigDecimal("12.50").compareTo(charge.getAmount()));
        assertEquals(CardType.DEBITO, charge.getCardType());
        assertNotNull(charge.getCreatedAt());
    }

    @Test
    void pixChargeHasNoCardType() {
        DirectCharge charge = service.register(new BigDecimal("3.00"), PaymentMethod.PIX, CardType.CREDITO,
                operator(), null);

        assertNull(charge.getCardType());
    }

    @Test
    void refusesInvalidCharges() {
        User operator = operator();

        assertThrows(IllegalArgumentException.class,
                () -> service.register(BigDecimal.ZERO, PaymentMethod.PIX, null, operator, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.register(new BigDecimal("5"), PaymentMethod.DINHEIRO, null, operator, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.register(new BigDecimal("5"), PaymentMethod.CARTAO, null, operator, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.register(new BigDecimal("5"), PaymentMethod.PIX, null, null, null));
    }
}
