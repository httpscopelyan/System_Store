package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.SupplierDuplicatedException;
import br.com.scopel.pfv.beta.model.Supplier;
import br.com.scopel.pfv.beta.service.SupplierService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class SupplierServiceTests {

    @Autowired
    private SupplierService supplierService;

    @Test
    void saveKeepsActiveByDefault() {
        Supplier s = new Supplier();
        s.setName("Distribuidora " + UUID.randomUUID());

        Supplier saved = supplierService.save(s);

        assertNotNull(saved.getId());
        assertTrue(saved.getActive());
    }

    @Test
    void cnpjIsNormalizedToDigits() {
        Supplier s = new Supplier();
        s.setName("Atacado");
        s.setCnpj("12.345.678/0001-95");

        Supplier saved = supplierService.save(s);

        assertEquals("12345678000195", saved.getCnpj());
    }

    @Test
    void duplicateCnpjThrows() {
        String cnpj = String.valueOf(System.nanoTime()).substring(0, 14);

        Supplier s1 = new Supplier();
        s1.setName("Forn 1");
        s1.setCnpj(cnpj);
        supplierService.save(s1);

        Supplier s2 = new Supplier();
        s2.setName("Forn 2");
        s2.setCnpj(cnpj);

        assertThrows(SupplierDuplicatedException.class, () -> supplierService.save(s2));
    }
}
