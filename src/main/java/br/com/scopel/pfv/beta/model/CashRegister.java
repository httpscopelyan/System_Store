package br.com.scopel.pfv.beta.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
public class CashRegister {

    public enum Status {
        OPEN,
        CLOSED
    }


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "operator_id")
    private User operator;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal openingAmount;

    @Column(nullable = false)
    private LocalDateTime openedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 7)
    private Status status;

    @Column(precision = 10, scale = 2)
    private BigDecimal expectedAmount;

    @Column(precision = 10, scale = 2)
    private BigDecimal closingAmount;

    @Column(precision = 10, scale = 2)
    private BigDecimal difference;

    @Column
    private LocalDateTime closedAt;

    //getters


    public Long getId() {
        return id;
    }

    public User getOperator() {
        return operator;
    }

    public BigDecimal getOpeningAmount() {
        return openingAmount;
    }

    public Status getStatus() {
        return status;
    }

    public BigDecimal getExpectedAmount() {
        return expectedAmount;
    }

    public BigDecimal getClosingAmount() {
        return closingAmount;
    }

    public BigDecimal getDifference() {
        return difference;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public LocalDateTime getOpenedAt() {
        return openedAt;
    }

    // setter


    public void setId(Long id) {
        this.id = id;
    }

    public void setOperator(User operator) {
        this.operator = operator;
    }

    public void setOpeningAmount(BigDecimal openingAmount) {
        this.openingAmount = openingAmount;
    }

    public void setOpenedAt(LocalDateTime openedAt) {
        this.openedAt = openedAt;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void setExpectedAmount(BigDecimal expectedAmount) {
        this.expectedAmount = expectedAmount;
    }

    public void setClosingAmount(BigDecimal closingAmount) {
        this.closingAmount = closingAmount;
    }

    public void setDifference(BigDecimal difference) {
        this.difference = difference;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }
}
