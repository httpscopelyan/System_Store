package br.com.scopel.pfv.beta.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment")
@Getter
@Setter
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "sale_id")
    private Sale sale;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private PaymentMethod method;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal amountReceived;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal change;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
