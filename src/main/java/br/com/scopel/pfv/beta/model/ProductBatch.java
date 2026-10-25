package br.com.scopel.pfv.beta.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Lote de validade de um produto (PDV Prático: LotesProduto).
 */
@Entity
@Table(name = "product_batch")
@Getter
@Setter
public class ProductBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "batch_code", length = 40)
    private String batchCode;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "quantity_entry", nullable = false, precision = 10, scale = 3)
    private BigDecimal quantityEntry;

    @Column(name = "quantity_sold", nullable = false, precision = 10, scale = 3)
    private BigDecimal quantitySold = BigDecimal.ZERO;

    @Column(nullable = false)
    private Boolean available = true;
}
