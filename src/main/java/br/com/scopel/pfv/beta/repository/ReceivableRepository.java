package br.com.scopel.pfv.beta.repository;

import br.com.scopel.pfv.beta.model.Receivable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ReceivableRepository extends JpaRepository<Receivable, Long> {

    List<Receivable> findByCustomer_IdAndStatus(Long customerId, Receivable.Status status);

    List<Receivable> findByCustomer_IdAndStatusAndDueDateLessThan(
            Long customerId, Receivable.Status status, LocalDate date);
}
