package br.com.scopel.pfv.beta.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Registro de uma movimentação de estoque (entrada, saída ou ajuste).
 * Guarda o estoque resultante para auditoria (RF13 — histórico do produto).
 */
@Entity
@Table(name = "stock_movement")
@Getter
@Setter
public class StockMovement {

    public enum Type {
        ENTRADA,
        SAIDA,
        AJUSTE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Type type;

    // Sempre positivo; o campo type dá o sentido.
    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_cost", precision = 10, scale = 2)
    private BigDecimal unitCost;

    @Column(length = 120)
    private String reason;

    @ManyToOne
    @JoinColumn(name = "operator_id")
    private User operator;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "resulting_stock", nullable = false, precision = 10, scale = 3)
    private BigDecimal resultingStock;
}
