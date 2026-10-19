package br.com.scopel.pfv.beta.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sale")
@Getter
@Setter
public class Sale {

    public enum Status {
        ABERTA,
        EM_PAGAMENTO,
        FINALIZADA,
        CANCELADA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "operator_id")
    private User operator;        

    @ManyToOne
    @JoinColumn(name = "cash_register_id")
    private CashRegister cashRegister;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    // Cliente da venda (tele-entrega / venda a prazo). Opcional.
    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    // Desconto e acréscimo no total da venda (RF19/RF20).
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal surcharge = BigDecimal.ZERO;

    // RN17: venda cancelada mantém registro (status, usuário, motivo).
    @Column(name = "cancel_reason", length = 200)
    private String cancelReason;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    // Gerente que autorizou operação sensível (desconto acima do limite, cancelamento).
    @ManyToOne
    @JoinColumn(name = "authorized_by_id")
    private User authorizedBy;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SaleItem> items = new ArrayList<>();
}