package br.com.scopel.pfv.beta.repository;

import br.com.scopel.pfv.beta.model.Payment;
import br.com.scopel.pfv.beta.model.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // Pagamentos de todas as vendas de um caixa (para a movimentação por forma).
    List<Payment> findBySale_CashRegister_Id(Long cashRegisterId);

    // Total recebido por forma de pagamento (líquido do troco) no período.
    @Query("""
            select p.method, coalesce(sum(p.amountReceived - p.change), 0) from Payment p
            where p.sale.status = :status and p.createdAt between :start and :end
            group by p.method
            """)
    List<Object[]> totalByMethod(@Param("status") Sale.Status status,
                                 @Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end);
}
