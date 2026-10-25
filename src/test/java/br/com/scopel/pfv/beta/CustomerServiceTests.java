package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.CustomerDuplicatedException;
import br.com.scopel.pfv.beta.model.Customer;
import br.com.scopel.pfv.beta.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class CustomerServiceTests {

    @Autowired
    private CustomerService customerService;

    private Customer novo(String name) {
        Customer c = new Customer();
        c.setName(name);
        return c;
    }

    // Salvar define createdAt e mantém active=true por padrão.
    @Test
    void saveSetsCreatedAtAndActive() {
        Customer saved = customerService.save(novo("Maria " + UUID.randomUUID()));

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertTrue(saved.getActive());
    }

    // CPF/CNPJ é guardado só com dígitos (texto, nunca número).
    @Test
    void cpfIsNormalizedToDigits() {
        Customer c = novo("Joao");
        c.setCpfCnpj("123.456.789-09");

        Customer saved = customerService.save(c);

        assertEquals("12345678909", saved.getCpfCnpj());
    }

    // Documento vazio vira nulo (não quebra o índice unique com várias vendas sem CPF).
    @Test
    void blankDocumentBecomesNull() {
        Customer c = novo("Sem Doc");
        c.setCpfCnpj("   ");

        Customer saved = customerService.save(c);

        assertNull(saved.getCpfCnpj());
    }

    @Test
    void duplicateDocumentThrows() {
        String doc = String.valueOf(System.nanoTime()).substring(0, 11);

        Customer c1 = novo("Cliente 1");
        c1.setCpfCnpj(doc);
        customerService.save(c1);

        Customer c2 = novo("Cliente 2");
        c2.setCpfCnpj(doc);

        assertThrows(CustomerDuplicatedException.class, () -> customerService.save(c2));
    }

    @Test
    void setCreditBlockedTogglesFlag() {
        Customer saved = customerService.save(novo("Devedor " + UUID.randomUUID()));
        assertFalse(saved.getCreditBlocked());

        Customer blocked = customerService.setCreditBlocked(saved.getId(), true);

        assertTrue(blocked.getCreditBlocked());
    }
}
