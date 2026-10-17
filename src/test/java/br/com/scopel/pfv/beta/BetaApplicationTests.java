package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.ProductNotfindException;
import br.com.scopel.pfv.beta.exception.RegisterProductDuplicateException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class BetaApplicationTests {

	@Autowired

	private ProductService productService;

	private static String uniqueCode() {
		return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
	}

	@Test
	void SaveProduct() {
		Product p = new Product();

		p.setCode(uniqueCode());
		p.setDescription("Test");
		p.setPrice(new BigDecimal("1.00"));
		p.setQuantity(new BigDecimal("50.2"));
		p.setUnit(Product.Unidade.UN);
		p.setActive(true);


		Product salvo = productService.save(p);

		assertNotNull(salvo.getId());
	}

	@Test
	void FindProduct() {
		assertThrows(ProductNotfindException.class, () -> {
			productService.findCode("dasdadas");
		});
	}

	@Test
	void FindExistingProduct(){
		String code = uniqueCode();
		Product p = new Product();

		p.setCode(code);
		p.setDescription("Test");
		p.setPrice(new BigDecimal("1.00"));
		p.setQuantity(new BigDecimal("50.2"));
		p.setUnit(Product.Unidade.UN);
		p.setActive(true);
		productService.save(p);


		Product encontrado = productService.findCode(code);

		assertNotNull(encontrado);
		assertEquals(code, encontrado.getCode());
	}

	@Test
	void SaveDuplicateProduct() {
		String code = uniqueCode();
		Product p1 = new Product();
		p1.setCode(code);
		p1.setDescription("Test");
		p1.setPrice(new BigDecimal("1.00"));
		p1.setQuantity(new BigDecimal("50.2"));
		p1.setUnit(Product.Unidade.UN);
		p1.setActive(true);
		productService.save(p1);

		Product p2 = new Product();
		p2.setCode(code);
		p2.setDescription("Test");
		p2.setPrice(new BigDecimal("1.00"));
		p2.setQuantity(new BigDecimal("50.2"));
		p2.setUnit(Product.Unidade.UN);
		p2.setActive(true);

		assertThrows(RegisterProductDuplicateException.class, () -> {
			productService.save(p2);
		});
	}




}
