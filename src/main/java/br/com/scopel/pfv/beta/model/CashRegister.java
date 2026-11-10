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

    @Column(precision = 10, scale = 2)
    private BigDecimal expectedPix;

    @Column(precision = 10, scale = 2)
    private BigDecimal countedPix;

    @Column(precision = 10, scale = 2)
    private BigDecimal expectedCredit;

    @Column(precision = 10, scale = 2)
    private BigDecimal countedCredit;

    @Column(precision = 10, scale = 2)
    private BigDecimal expectedDebit;

    @Column(precision = 10, scale = 2)
    private BigDecimal countedDebit;

    @Column(precision = 10, scale = 2)
    private BigDecimal expectedExtra;

    @Column(precision = 10, scale = 2)
    private BigDecimal countedExtra;

    @Column(length = 155)
    private String extraNote;

    public BigDecimal totalDifference() {
        BigDecimal total = difference == null ? BigDecimal.ZERO : difference;
        if (expectedPix != null) {
            total = total.add(nz(countedPix).subtract(expectedPix));
            total = total.add(nz(countedCredit).subtract(nz(expectedCredit)));
            total = total.add(nz(countedDebit).subtract(nz(expectedDebit)));
        }
        return total.add(extraDifference());
    }

    public BigDecimal extraDifference() {
        if (expectedExtra == null) {
            return BigDecimal.ZERO;
        }
        return nz(countedExtra).subtract(expectedExtra);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

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

    public BigDecimal getExpectedPix() {
        return expectedPix;
    }

    public void setExpectedPix(BigDecimal expectedPix) {
        this.expectedPix = expectedPix;
    }

    public BigDecimal getCountedPix() {
        return countedPix;
    }

    public void setCountedPix(BigDecimal countedPix) {
        this.countedPix = countedPix;
    }

    public BigDecimal getExpectedCredit() {
        return expectedCredit;
    }

    public void setExpectedCredit(BigDecimal expectedCredit) {
        this.expectedCredit = expectedCredit;
    }

    public BigDecimal getCountedCredit() {
        return countedCredit;
    }

    public void setCountedCredit(BigDecimal countedCredit) {
        this.countedCredit = countedCredit;
    }

    public BigDecimal getExpectedDebit() {
        return expectedDebit;
    }

    public void setExpectedDebit(BigDecimal expectedDebit) {
        this.expectedDebit = expectedDebit;
    }

    public BigDecimal getCountedDebit() {
        return countedDebit;
    }

    public void setCountedDebit(BigDecimal countedDebit) {
        this.countedDebit = countedDebit;
    }

    public BigDecimal getExpectedExtra() {
        return expectedExtra;
    }

    public void setExpectedExtra(BigDecimal expectedExtra) {
        this.expectedExtra = expectedExtra;
    }

    public BigDecimal getCountedExtra() {
        return countedExtra;
    }

    public void setCountedExtra(BigDecimal countedExtra) {
        this.countedExtra = countedExtra;
    }

    public String getExtraNote() {
        return extraNote;
    }

    public void setExtraNote(String extraNote) {
        this.extraNote = extraNote;
    }
}
