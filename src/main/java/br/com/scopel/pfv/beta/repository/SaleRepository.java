package br.com.scopel.pfv.beta.repository;

import br.com.scopel.pfv.beta.model.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    // Recuperar caixa/venda em aberto por operador (usado pela UI).
    Optional<Sale> findFirstByOperator_IdAndStatus(Long operatorId, Sale.Status status);

    // Movimentação do caixa: vendas do caixa, mais antigas primeiro.
    List<Sale> findByCashRegister_IdOrderByCreatedAtAsc(Long cashRegisterId);

    // --- Relatórios ---

    @Query("""
            select coalesce(sum(s.total), 0) from Sale s
            where s.status = :status and s.createdAt between :start and :end
            """)
    BigDecimal sumTotalByStatusAndPeriod(@Param("status") Sale.Status status,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end);

    @Query("""
            select coalesce(sum(s.discount), 0) from Sale s
            where s.status = :status and s.createdAt between :start and :end
            """)
    BigDecimal sumDiscountByStatusAndPeriod(@Param("status") Sale.Status status,
                                            @Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);

    @Query("""
            select count(s) from Sale s
            where s.status = :status and s.canceledAt between :start and :end
            """)
    long countByStatusAndCanceledPeriod(@Param("status") Sale.Status status,
                                        @Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);

    @Query("""
            select s.operator.name, coalesce(sum(s.total), 0) from Sale s
            where s.status = :status and s.createdAt between :start and :end
            group by s.operator.name
            order by sum(s.total) desc
            """)
    List<Object[]> totalByOperator(@Param("status") Sale.Status status,
                                   @Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);
}
