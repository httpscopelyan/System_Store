package br.com.scopel.pfv.beta.model;
import jakarta.persistence.*;

import java.math.BigDecimal;


@Entity
public class Product {

    public enum Unidade {
        UN,
        KG
    };

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 15)
    private String code;
    @Column(nullable = false, length = 100)
    private String description;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private Unidade unit;
    @Column(name = "cost_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal costPrice = BigDecimal.ZERO;
    @Column(name = "min_stock", nullable = false, precision = 10, scale = 3)
    private BigDecimal minStock = BigDecimal.ZERO;
    @Column(length = 50)
    private String category;
    @Column(nullable = false)
    private Boolean active = true;

    //getters
    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public Unidade getUnit() {
        return unit;
    }

    public Boolean getActive() {
        return active;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public BigDecimal getMinStock() {
        return minStock;
    }

    public String getCategory() {
        return category;
    }



    // setters

    public void setCode(String code) {
        this.code = code;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public void setUnit(Unidade unit) {
        this.unit = unit;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public void setId(Long id) {
        this.id = id;
    }// n usado

    public void setCostPrice(BigDecimal costPrice) {
        this.costPrice = costPrice;
    }

    public void setMinStock(BigDecimal minStock) {
        this.minStock = minStock;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}