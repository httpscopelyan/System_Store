package br.com.scopel.pfv.beta.repository;

import br.com.scopel.pfv.beta.model.Payable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PayableRepository extends JpaRepository<Payable, Long> {

    List<Payable> findByStatus(Payable.Status status);

    List<Payable> findByStatusAndDueDateLessThan(Payable.Status status, LocalDate date);
}
