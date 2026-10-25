package br.com.scopel.pfv.beta.repository;

import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    @Query("""
            select i.product.description, sum(i.quantity) from SaleItem i
            where i.sale.status = :status and i.sale.createdAt between :start and :end
            group by i.product.description
            order by sum(i.quantity) desc
            """)
    List<Object[]> topProducts(@Param("status") Sale.Status status,
                               @Param("start") LocalDateTime start,
                               @Param("end") LocalDateTime end);

    // Lucro aproximado: (preço vendido - custo atual do produto) * quantidade.
    @Query("""
            select coalesce(sum(i.totalPrice - (i.product.costPrice * i.quantity)), 0) from SaleItem i
            where i.sale.status = :status and i.sale.createdAt between :start and :end
            """)
    BigDecimal grossProfit(@Param("status") Sale.Status status,
                           @Param("start") LocalDateTime start,
                           @Param("end") LocalDateTime end);
}
