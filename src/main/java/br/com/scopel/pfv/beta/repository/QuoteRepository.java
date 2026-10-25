package br.com.scopel.pfv.beta.repository;

import br.com.scopel.pfv.beta.model.Quote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuoteRepository extends JpaRepository<Quote, Long> {

    List<Quote> findByStatus(Quote.Status status);

    List<Quote> findByCustomer_Id(Long customerId);
}
