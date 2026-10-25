package br.com.scopel.pfv.beta.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "price_table_item",
        uniqueConstraints = @UniqueConstraint(columnNames = {"price_table_id", "product_id"}))
@Getter
@Setter
public class PriceTableItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "price_table_id")
    private PriceTable priceTable;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
}
