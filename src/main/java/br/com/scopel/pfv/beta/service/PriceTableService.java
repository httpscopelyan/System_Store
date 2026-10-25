package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.exception.PriceTableNotFoundException;
import br.com.scopel.pfv.beta.model.PriceTable;
import br.com.scopel.pfv.beta.model.PriceTableItem;
import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.repository.PriceTableItemRepository;
import br.com.scopel.pfv.beta.repository.PriceTableRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Tabelas de preço (PDV Prático: TabelasPreco / AlterarTabelaPreco).
 * getPrice cai no preço base do produto quando não há preço específico na tabela.
 */
@Service
public class PriceTableService {

    private final PriceTableRepository tableRepository;
    private final PriceTableItemRepository itemRepository;

    public PriceTableService(PriceTableRepository tableRepository,
                             PriceTableItemRepository itemRepository) {
        this.tableRepository = tableRepository;
        this.itemRepository = itemRepository;
    }

    public PriceTable createTable(String name) {
        PriceTable table = new PriceTable();
        table.setName(name);
        table.setActive(true);
        return tableRepository.save(table);
    }

    public PriceTable findTable(Long id) {
        return tableRepository.findById(id).orElseThrow(() -> new PriceTableNotFoundException(id));
    }

    public List<PriceTable> listActiveTables() {
        return tableRepository.findByActiveTrue();
    }

    public List<PriceTableItem> items(Long tableId) {
        return itemRepository.findByPriceTable_Id(tableId);
    }

    /** Define (cria ou atualiza) o preço de um produto numa tabela. */
    public PriceTableItem setPrice(PriceTable table, Product product, BigDecimal price) {
        PriceTableItem item = itemRepository
                .findByPriceTable_IdAndProduct_Id(table.getId(), product.getId())
                .orElseGet(() -> {
                    PriceTableItem i = new PriceTableItem();
                    i.setPriceTable(table);
                    i.setProduct(product);
                    return i;
                });
        item.setPrice(price.setScale(2, RoundingMode.HALF_UP));
        return itemRepository.save(item);
    }

    /** Preço do produto na tabela; se não houver, usa o preço base do produto. */
    public BigDecimal getPrice(PriceTable table, Product product) {
        return itemRepository.findByPriceTable_IdAndProduct_Id(table.getId(), product.getId())
                .map(PriceTableItem::getPrice)
                .orElse(product.getPrice());
    }
}
