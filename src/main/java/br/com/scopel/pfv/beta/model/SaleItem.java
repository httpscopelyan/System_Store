package br.com.scopel.pfv.beta.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "sale_item")
@Getter
@Setter
public class SaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "sale_id")
    private Sale sale;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    // Desconto aplicado no item (RF19). Padrão zero.
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;
}