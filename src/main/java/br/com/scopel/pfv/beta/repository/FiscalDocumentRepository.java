package br.com.scopel.pfv.beta.repository;

import br.com.scopel.pfv.beta.model.FiscalDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FiscalDocumentRepository extends JpaRepository<FiscalDocument, Long> {

    List<FiscalDocument> findBySale_Id(Long saleId);

    List<FiscalDocument> findByStatus(FiscalDocument.Status status);
}
