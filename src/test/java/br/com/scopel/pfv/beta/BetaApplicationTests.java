package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.exception.ProductNotfindException;
import br.com.scopel.pfv.beta.exception.RegisterProductDuplicateException;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.repository.ProductRepository;
import br.com.scopel.pfv.beta.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BetaApplicationTests {

	@Autowired

	private ProductService productService;

	@Test
	void SaveProduct() {
		Product p = new Product();

		p.setCode("1231");
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
		Product p = new Product();

		p.setCode("7777");
		p.setDescription("Test");
		p.setPrice(new BigDecimal("1.00"));
		p.setQuantity(new BigDecimal("50.2"));
		p.setUnit(Product.Unidade.UN);
		p.setActive(true);
		productService.save(p);


		Product encontrado = productService.findCode("7777");

		assertNotNull(encontrado);
		assertEquals("7777", encontrado.getCode());
	}

	@Test
	void SaveDuplicateProduct() {
		Product p1 = new Product();
		p1.setCode("8888");
		p1.setDescription("Test");
		p1.setPrice(new BigDecimal("1.00"));
		p1.setQuantity(new BigDecimal("50.2"));
		p1.setUnit(Product.Unidade.UN);
		p1.setActive(true);
		productService.save(p1);

		Product p2 = new Product();
		p2.setCode("8888");
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
