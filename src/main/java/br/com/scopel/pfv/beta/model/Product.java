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
}