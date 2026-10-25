package br.com.scopel.pfv.beta.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Título a receber / parcela do crediário (PDV Prático: CAR / dívida do cliente).
 */
@Entity
@Table(name = "receivable")
@Getter
@Setter
public class Receivable {

    public enum Status {
        ABERTA,
        PAGA,
        CANCELADA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne
    @JoinColumn(name = "sale_id")
    private Sale sale;

    @Column(length = 150)
    private String description;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.ABERTA;

    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber = 1;

    @Column(name = "installment_total", nullable = false)
    private Integer installmentTotal = 1;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Transient
    public BigDecimal getRemaining() {
        return amount.subtract(paidAmount);
    }
}
