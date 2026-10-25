package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.exception.InvalidPasswordException;
import br.com.scopel.pfv.beta.exception.ManagerAuthorizationRequiredException;
import br.com.scopel.pfv.beta.exception.UserNotFindException;
import br.com.scopel.pfv.beta.model.*;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.repository.UserRepository;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import br.com.scopel.pfv.beta.service.SaleService;
import br.com.scopel.pfv.beta.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * G1 — Autorização por senha de supervisor.
 * Cobre {@link UserService#authenticateManager(String, String)} (RN08/RF18/RF21)
 * e complementa os caminhos autorizados de venda não cobertos por
 * {@link SaleDiscountAndCancelTests} (venda inalterada quando recusada; desconto
 * dentro do teto com operador explícito).
 */
@SpringBootTest
@Transactional
class ManagerAuthorizationTests {

    @Autowired private UserService userService;
    @Autowired private UserSession userSession;
    @Autowired private SaleService saleService;
    @Autowired private CashRegisterService cashRegisterService;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;

    /** Cria um usuário via UserService (senha com hash BCrypt — nunca SQL cru). */
    private User newUser(User.Role role, String login, String rawPassword) {
        User u = new User();
        u.setName(role == User.Role.GERENTE ? "Gerente" : "Operador");
        u.setLogin(login);
        u.setPassword(rawPassword);
        u.setRole(role);
        u.setActive(true);
        return userService.save(u);
    }

    /** Operador sem BCrypt — direto no repo, para os caminhos de venda. */
    private User repoUser(User.Role role) {
        User u = new User();
        u.setName(role == User.Role.GERENTE ? "Gerente" : "Operador");
        u.setLogin("u_" + UUID.randomUUID());
        u.setPassword("x");
        u.setRole(role);
        u.setActive(true);
        return userRepository.save(u);
    }

    private Product product(BigDecimal price) {
        Product p = new Product();
        p.setCode(UUID.randomUUID().toString().substring(0, 13));
        p.setDescription("Produto");
        p.setPrice(price);
        p.setQuantity(new BigDecimal("100.000"));
        p.setUnit(Product.Unidade.UN);
        p.setActive(true);
        return productRepository.save(p);
    }

    private Sale openWithItem(User operator, Product p, BigDecimal qty) {
        CashRegister caixa = cashRegisterService.open(operator, new BigDecimal("100.00"));
        Sale sale = saleService.openSale(operator, caixa);
        return saleService.addItem(sale, p.getCode(), qty);
    }

    // ---------- authenticateManager ----------

    // 1. Gerente + senha correta -> retorna o User (role GERENTE).
    @Test
    void authenticateManagerWithManagerAndCorrectPasswordReturnsUser() {
        String login = "ger_" + UUID.randomUUID();
        newUser(User.Role.GERENTE, login, "segredoGerente1");

        User result = userService.authenticateManager(login, "segredoGerente1");

        assertNotNull(result);
        assertEquals(login, result.getLogin());
        assertEquals(User.Role.GERENTE, result.getRole());
    }

    // 2. Senha errada -> InvalidPasswordException.
    @Test
    void authenticateManagerWithWrongPasswordThrows() {
        String login = "ger_" + UUID.randomUUID();
        newUser(User.Role.GERENTE, login, "certa123");

        assertThrows(InvalidPasswordException.class,
                () -> userService.authenticateManager(login, "errada999"));
    }

    // 3. Login inexistente -> UserNotFindException.
    @Test
    void authenticateManagerWithNonexistentLoginThrows() {
        assertThrows(UserNotFindException.class,
                () -> userService.authenticateManager("naoexiste_" + UUID.randomUUID(), "qualquer"));
    }

    // 4. Operador com senha certa -> ManagerAuthorizationRequiredException.
    @Test
    void authenticateManagerWithOperatorCorrectPasswordThrowsAuthorization() {
        String login = "op_" + UUID.randomUUID();
        newUser(User.Role.OPERADOR, login, "senhaOp123");

        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> userService.authenticateManager(login, "senhaOp123"));
    }

    // 5. Nenhuma das 3 exceções expõe a senha informada em getMessage()/toString().
    @Test
    void authenticateManagerExceptionsNeverLeakPassword() {
        String secret = "SenhaSuperSecreta#" + UUID.randomUUID();

        // senha errada (gerente existe)
        String gerLogin = "ger_" + UUID.randomUUID();
        newUser(User.Role.GERENTE, gerLogin, "outraSenha");
        InvalidPasswordException wrong = assertThrows(InvalidPasswordException.class,
                () -> userService.authenticateManager(gerLogin, secret));
        assertFalse(wrong.getMessage().contains(secret), "InvalidPasswordException.getMessage vazou a senha");
        assertFalse(wrong.toString().contains(secret), "InvalidPasswordException.toString vazou a senha");

        // login inexistente
        UserNotFindException notFound = assertThrows(UserNotFindException.class,
                () -> userService.authenticateManager("naoexiste_" + UUID.randomUUID(), secret));
        assertFalse(notFound.getMessage().contains(secret), "UserNotFindException.getMessage vazou a senha");
        assertFalse(notFound.toString().contains(secret), "UserNotFindException.toString vazou a senha");

        // operador com senha certa
        String opLogin = "op_" + UUID.randomUUID();
        newUser(User.Role.OPERADOR, opLogin, secret);
        ManagerAuthorizationRequiredException auth = assertThrows(ManagerAuthorizationRequiredException.class,
                () -> userService.authenticateManager(opLogin, secret));
        assertFalse(auth.getMessage().contains(secret), "ManagerAuthorizationRequiredException.getMessage vazou a senha");
        assertFalse(auth.toString().contains(secret), "ManagerAuthorizationRequiredException.toString vazou a senha");
    }

    // 6. UserSession.getLoggedUser() não muda após authenticateManager (sem efeito colateral de sessão).
    @Test
    void authenticateManagerDoesNotChangeSession() {
        String opLogin = "op_" + UUID.randomUUID();
        User operator = newUser(User.Role.OPERADOR, opLogin, "senhaOp");
        String gerLogin = "ger_" + UUID.randomUUID();
        newUser(User.Role.GERENTE, gerLogin, "senhaGer");

        User previous = userSession.getLoggedUser();
        try {
            userSession.setLoggedUser(operator);

            userService.authenticateManager(gerLogin, "senhaGer");

            assertNotNull(userSession.getLoggedUser());
            assertEquals(operator.getLogin(), userSession.getLoggedUser().getLogin(),
                    "authenticateManager não deve trocar o operador logado");
        } finally {
            userSession.setLoggedUser(previous); // não polui o singleton para outros testes
        }
    }

    // ---------- caminhos de venda (complementos a SaleDiscountAndCancelTests) ----------

    // 7. applySaleDiscount acima do teto com null -> recusa E venda fica inalterada.
    @Test
    void saleDiscountAboveLimitWithNullLeavesSaleUnchanged() {
        User op = repoUser(User.Role.OPERADOR);
        Product p = product(new BigDecimal("10.00"));
        Sale sale = openWithItem(op, p, new BigDecimal("10")); // subtotal 100.00

        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> saleService.applySaleDiscount(sale, new BigDecimal("20.00"), null));

        assertEquals(0, new BigDecimal("100.00").compareTo(sale.getTotal()), "total não deve mudar");
        assertEquals(0, BigDecimal.ZERO.compareTo(sale.getDiscount()), "desconto não deve ser aplicado");
        assertNull(sale.getAuthorizedBy(), "nenhum autorizador deve ser registrado");
    }

    // 7. cancelItem com null -> recusa E o item permanece na venda.
    @Test
    void cancelItemWithNullLeavesItemInSale() {
        User op = repoUser(User.Role.OPERADOR);
        Product p = product(new BigDecimal("10.00"));
        Sale sale = openWithItem(op, p, new BigDecimal("3")); // 30.00

        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> saleService.cancelItem(sale, p.getCode(), null));

        assertEquals(1, sale.getItems().size(), "item não deve ser removido");
        assertEquals(0, new BigDecimal("30.00").compareTo(sale.getTotal()), "total não deve mudar");
    }

    // 7. cancelSale com operador -> recusa E a venda permanece ABERTA.
    @Test
    void cancelSaleWithOperatorLeavesSaleOpen() {
        User op = repoUser(User.Role.OPERADOR);
        Product p = product(new BigDecimal("10.00"));
        Sale sale = openWithItem(op, p, new BigDecimal("1"));

        assertThrows(ManagerAuthorizationRequiredException.class,
                () -> saleService.cancelSale(sale, "motivo", op));

        assertEquals(Sale.Status.ABERTA, sale.getStatus(), "venda não deve ser cancelada");
        assertNull(sale.getCancelReason());
        assertNull(sale.getCanceledAt());
    }

    // 8. Desconto dentro do teto com OPERADOR explícito -> aplica sem exigir gerente.
    @Test
    void saleDiscountWithinLimitWithOperatorApplies() {
        User op = repoUser(User.Role.OPERADOR);
        Product p = product(new BigDecimal("10.00"));
        Sale sale = openWithItem(op, p, new BigDecimal("10")); // subtotal 100.00

        sale = saleService.applySaleDiscount(sale, new BigDecimal("5.00"), op); // 5% (<= 10%)

        assertEquals(0, new BigDecimal("95.00").compareTo(sale.getTotal()));
        assertNull(sale.getAuthorizedBy(), "desconto dentro do teto não registra autorizador");
    }
}
