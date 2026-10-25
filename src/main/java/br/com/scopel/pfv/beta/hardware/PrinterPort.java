package br.com.scopel.pfv.beta.hardware;

/**
 * Porta de saída da impressora (cupom/etiqueta). A lógica de formatação fica
 * nos services; o adaptador físico (ex.: escpos-coffee para a impressora térmica
 * VT, abertura de gaveta Elgin) implementa esta interface na Fase 2 sem tocar na
 * regra de negócio (RNF13).
 */
public interface PrinterPort {

    /** Imprime o conteúdo já formatado. */
    void print(String content);

    /** Abre a gaveta de dinheiro (RN11 — só nas situações autorizadas). */
    void openDrawer();
}
