package br.com.scopel.pfv.beta.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "direct_charge")
@Getter
@Setter
public class DirectCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", length = 20)
    private CardType cardType;

    @ManyToOne(optional = false)
    @JoinColumn(name = "operator_id")
    private User operator;

    @ManyToOne
    @JoinColumn(name = "cash_register_id")
    private CashRegister cashRegister;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(length = 155)
    private String note;
}
