package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.hardware.PrinterPort;
import br.com.scopel.pfv.beta.model.Product;
import org.springframework.stereotype.Service;

/**
 * Etiqueta de código de barras do produto (RF44): descrição, preço e o código
 * (valor do código de barras). A renderização do código em si (EAN-13) fica a
 * cargo do adaptador/impressora de etiquetas.
 */
@Service
public class LabelService {

    private final PrinterPort printer;

    public LabelService(PrinterPort printer) {
        this.printer = printer;
    }

    public String buildLabel(Product product) {
        StringBuilder sb = new StringBuilder();
        sb.append(product.getDescription()).append('\n');
        sb.append("R$ ").append(product.getPrice().toPlainString().replace('.', ',')).append('\n');
        sb.append("*").append(product.getCode()).append("*");   // marca de código de barras
        return sb.toString();
    }

    public void printLabels(Product product, int copies) {
        String label = buildLabel(product);
        for (int i = 0; i < copies; i++) {
            printer.print(label);
        }
    }
}
