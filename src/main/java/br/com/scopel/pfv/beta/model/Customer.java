package br.com.scopel.pfv.beta.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    // CPF (11) ou CNPJ (14) só dígitos; texto, nunca número. Pode ser nulo.
    @Column(name = "cpf_cnpj", unique = true, length = 14)
    private String cpfCnpj;

    @Column(length = 20)
    private String phone;

    @Column(length = 150)
    private String address;

    // Crediário: limite de compra a prazo.
    @Column(name = "credit_limit", nullable = false, precision = 10, scale = 2)
    private BigDecimal creditLimit = BigDecimal.ZERO;

    // Bloqueia venda a prazo (ex.: cliente em atraso).
    @Column(name = "credit_blocked", nullable = false)
    private Boolean creditBlocked = false;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
