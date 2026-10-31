package br.com.scopel.pfv.beta.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cash_movement")
@Getter
@Setter
public class CashMovement {

    public enum Type {
        SANGRIA,
        SUPRIMENTO,
        RETIFICACAO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cash_register_id")
    private CashRegister cashRegister;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 11)
    private Type type;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 155)
    private String reason;

    @ManyToOne
    @JoinColumn(name = "corrected_movement_id")
    private CashMovement correctedMovement;

    @ManyToOne
    @JoinColumn(name = "authorized_by_id")
    private User authorizedBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}