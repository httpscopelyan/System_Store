package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.model.FiscalDocument;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.repository.FiscalDocumentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * NFC-e (RF49–RF53). ATENÇÃO: este serviço faz apenas o REGISTRO/armazenamento
 * local do documento fiscal. A EMISSÃO real — montar o XML, assinar com o
 * certificado digital (A1/A3), transmitir à SEFAZ, tratar autorização/rejeição,
 * contingência e cancelamento — NÃO está implementada e deve ser integrada na
 * Fase 3 com uma biblioteca fiscal dedicada. Os métodos de emissão lançam
 * UnsupportedOperationException de propósito, para nunca dar a falsa impressão
 * de que um cupom foi emitido legalmente.
 */
@Service
public class FiscalService {

    private final FiscalDocumentRepository repository;

    public FiscalService(FiscalDocumentRepository repository) {
        this.repository = repository;
    }

    /** Cria o registro PENDENTE da NFC-e de uma venda (ainda não emitida). */
    public FiscalDocument registerPending(Sale sale, String consumerCpf) {
        FiscalDocument doc = new FiscalDocument();
        doc.setSale(sale);
        doc.setStatus(FiscalDocument.Status.PENDENTE);
        doc.setConsumerCpf(consumerCpf);
        doc.setCreatedAt(LocalDateTime.now());
        return repository.save(doc);
    }

    public List<FiscalDocument> findBySale(Long saleId) {
        return repository.findBySale_Id(saleId);
    }

    public List<FiscalDocument> findPending() {
        return repository.findByStatus(FiscalDocument.Status.PENDENTE);
    }

    /**
     * Emite a NFC-e na SEFAZ. NÃO IMPLEMENTADO: requer certificado digital e
     * integração com a SEFAZ (Fase 3).
     */
    public FiscalDocument emit(Long fiscalDocumentId) {
        throw new UnsupportedOperationException(
                "Emissão de NFC-e exige certificado digital + integração SEFAZ (Fase 3). "
                        + "Integrar uma biblioteca fiscal antes de habilitar.");
    }

    /**
     * Cancela a NFC-e junto à SEFAZ. NÃO IMPLEMENTADO (Fase 3).
     */
    public FiscalDocument cancel(Long fiscalDocumentId, String reason) {
        throw new UnsupportedOperationException(
                "Cancelamento de NFC-e exige integração SEFAZ (Fase 3).");
    }
}
