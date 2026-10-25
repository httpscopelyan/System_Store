package br.com.scopel.pfv.beta.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Documento fiscal eletrônico (NFC-e) de uma venda. A emissão real exige
 * certificado digital + SEFAZ (Fase 3); aqui é o registro/armazenamento.
 */
@Entity
@Table(name = "fiscal_document")
@Getter
@Setter
public class FiscalDocument {

    public enum Status {
        PENDENTE,
        AUTORIZADA,
        REJEITADA,
        CANCELADA,
        CONTINGENCIA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "sale_id")
    private Sale sale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status = Status.PENDENTE;

    @Column(name = "access_key", length = 44)
    private String accessKey;

    @Column(length = 20)
    private String protocol;

    @Column(name = "consumer_cpf", length = 14)
    private String consumerCpf;

    private Long number;

    private Integer series;

    @Column(columnDefinition = "text")
    private String xml;

    @Column(name = "error_message", length = 255)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;
}
