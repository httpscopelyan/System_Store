package br.com.scopel.pfv.beta.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Tabela de preço (PDV Prático: TabelasPreco). Ex.: Varejo, Atacado.
 */
@Entity
@Table(name = "price_table")
@Getter
@Setter
public class PriceTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String name;

    @Column(nullable = false)
    private Boolean active = true;
}
