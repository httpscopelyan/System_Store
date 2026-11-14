package br.com.scopel.pfv.beta.model;

import jakarta.persistence.*;

@Entity
@Table(name = "cash_group")
public class CashGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 60)
    private String name;

    protected CashGroup() {
    }

    public CashGroup(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
