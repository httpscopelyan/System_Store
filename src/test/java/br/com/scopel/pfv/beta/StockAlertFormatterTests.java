package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.model.Product;
import br.com.scopel.pfv.beta.service.alert.AlertRecipients;
import br.com.scopel.pfv.beta.service.alert.StockAlert;
import br.com.scopel.pfv.beta.service.alert.StockAlertFormatter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockAlertFormatterTests {

    private final StockAlertFormatter formatter = new StockAlertFormatter();

    private StockAlert alert(String description, String quantity, String minStock, String suggested,
                             StockAlert.Severity severity) {
        return new StockAlert("c-" + description, description, Product.Unidade.UN, new BigDecimal(quantity),
                new BigDecimal(minStock), new BigDecimal(suggested), severity);
    }

    @Test
    void emptyListSaysThereIsNothingToRestock_RN22() {
        String body = formatter.body("Mercado", List.of());
        assertEquals("Mercado — Lista de itens que precisam de ser repostos:\n\n"
                + "Nenhum item abaixo do estoque mínimo.\n", body);
    }

    @Test
    void singleItemUsesTheRequestedLayout_RN22() {
        String body = formatter.body("Mercado", List.of(
                alert("energetico", "15.000", "10.000", "0.000", StockAlert.Severity.ATENCAO)));
        assertEquals("Mercado — Lista de itens que precisam de ser repostos:\n\n"
                + "energetico: estoque atual 15: estoque mínimo 10 (repor 0)\n\n"
                + "1 item(ns) nesta situação.\n", body);
    }

    @Test
    void manyItemsAreListedInGivenOrder_RN22() {
        String body = formatter.body("Mercado", List.of(
                alert("pao frances", "4", "10", "11", StockAlert.Severity.CRITICO),
                alert("energetico", "15", "10", "0", StockAlert.Severity.ATENCAO),
                alert("carne", "2.500", "3.000", "5.500", StockAlert.Severity.CRITICO)));
        assertEquals("Mercado — Lista de itens que precisam de ser repostos:\n\n"
                + "pao frances: estoque atual 4: estoque mínimo 10 (repor 11)\n"
                + "energetico: estoque atual 15: estoque mínimo 10 (repor 0)\n"
                + "carne: estoque atual 2.5: estoque mínimo 3 (repor 5.5)\n\n"
                + "3 item(ns) nesta situação.\n", body);
    }

    @Test
    void subjectCarriesTheItemCount_RN22() {
        assertEquals("Mercado — 3 item(ns) para repor no estoque", formatter.subject("Mercado", 3));
    }

    @Test
    void recipientsAcceptCommasSemicolonsAndSpacesWithoutDuplicates_RF56() {
        List<String> recipients = AlertRecipients.parse("a@x.com, b@y.com; a@x.com  c@z.com.br");
        assertEquals(List.of("a@x.com", "b@y.com", "c@z.com.br"), recipients);
    }

    @Test
    void blankRecipientsMeanNoRecipients_RF56() {
        assertTrue(AlertRecipients.parse("   ").isEmpty());
        assertTrue(AlertRecipients.parse(null).isEmpty());
    }

    @Test
    void invalidRecipientIsRefused_RF56() {
        assertThrows(IllegalArgumentException.class, () -> AlertRecipients.parse("sem-arroba"));
        assertThrows(IllegalArgumentException.class, () -> AlertRecipients.parse("ok@x.com, ruim@"));
    }
}
