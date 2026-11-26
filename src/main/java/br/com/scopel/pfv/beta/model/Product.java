package br.com.scopel.pfv.beta.model;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
public class Product {

    public enum Unidade {
        UN("UN", "UN", BigDecimal.ONE),
        KG("KG", "KG", BigDecimal.ONE),
        G100("100 g", "KG", BigDecimal.TEN);

        private static final int GRAMS_PER_KILO_DIGITS = 3;

        private final String label;
        private final String stockSymbol;
        private final BigDecimal pricedPortionsPerStockUnit;

        Unidade(String label, String stockSymbol, BigDecimal pricedPortionsPerStockUnit) {
            this.label = label;
            this.stockSymbol = stockSymbol;
            this.pricedPortionsPerStockUnit = pricedPortionsPerStockUnit;
        }

        public String stockSymbol() {
            return stockSymbol;
        }

        public boolean soldByWeight() {
            return this != UN;
        }

        public boolean pricedPerHundredGrams() {
            return this == G100;
        }

        public String priceSuffix() {
            return pricedPerHundredGrams() ? "/100g" : "";
        }

        public BigDecimal amountFor(BigDecimal unitPrice, BigDecimal stockQuantity) {
            return unitPrice.multiply(stockQuantity).multiply(pricedPortionsPerStockUnit);
        }

        public BigDecimal stockQuantityFromTyped(BigDecimal typed) {
            return pricedPerHundredGrams() ? typed.movePointLeft(GRAMS_PER_KILO_DIGITS) : typed;
        }

        public String soldQuantityText(BigDecimal stockQuantity) {
            BigDecimal shown = pricedPerHundredGrams()
                    ? stockQuantity.movePointRight(GRAMS_PER_KILO_DIGITS)
                    : stockQuantity;
            String text = shown.stripTrailingZeros().toPlainString();
            return pricedPerHundredGrams() ? text + "g" : text;
        }

        public BigDecimal rebaseAmount(BigDecimal amount, Unidade target) {
            return amount.multiply(pricedPortionsPerStockUnit)
                    .divide(target.pricedPortionsPerStockUnit, 2, RoundingMode.HALF_UP);
        }

        @Override
        public String toString() {
            return label;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 30)
    private String code;
    @Column(nullable = false, length = 100)
    private String description;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private Unidade unit;
    @Column(name = "cost_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal costPrice = BigDecimal.ZERO;
    @Column(name = "min_stock", nullable = false, precision = 10, scale = 3)
    private BigDecimal minStock = BigDecimal.ZERO;
    @Column(length = 50)
    private String category;
    @Column(nullable = false)
    private Boolean active = true;

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
    }

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
