package br.com.scopel.pfv.beta.config;

import br.com.scopel.pfv.beta.model.CardType;
import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.PaymentMethod;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.User;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class UserSession {

    private User loggedUser;
    private CashRegister caixaAtual;
    private Sale vendaAtual;

    private CardType cardTypeSelecionado;

    private Sale ultimaVenda;
    private PaymentMethod ultimaForma;
    private BigDecimal ultimoTroco;

    private Long produtoEmEdicaoId;

    public CashRegister getCaixaAtual() { return caixaAtual; }
    public void setCaixaAtual(CashRegister caixaAtual) { this.caixaAtual = caixaAtual; }

    public Sale getVendaAtual() { return vendaAtual; }
    public void setVendaAtual(Sale vendaAtual) { this.vendaAtual = vendaAtual; }

    public CardType getCardTypeSelecionado() { return cardTypeSelecionado; }
    public void setCardTypeSelecionado(CardType cardTypeSelecionado) { this.cardTypeSelecionado = cardTypeSelecionado; }

    public Sale getUltimaVenda() { return ultimaVenda; }
    public void setUltimaVenda(Sale ultimaVenda) { this.ultimaVenda = ultimaVenda; }

    public PaymentMethod getUltimaForma() { return ultimaForma; }
    public void setUltimaForma(PaymentMethod ultimaForma) { this.ultimaForma = ultimaForma; }

    public BigDecimal getUltimoTroco() { return ultimoTroco; }
    public void setUltimoTroco(BigDecimal ultimoTroco) { this.ultimoTroco = ultimoTroco; }

    private String stockEntryPrefillCode;

    public String consumeStockEntryPrefillCode() {
        String code = stockEntryPrefillCode;
        stockEntryPrefillCode = null;
        return code;
    }

    public void setStockEntryPrefillCode(String stockEntryPrefillCode) {
        this.stockEntryPrefillCode = stockEntryPrefillCode;
    }

    public Long getProdutoEmEdicaoId() { return produtoEmEdicaoId; }
    public void setProdutoEmEdicaoId(Long produtoEmEdicaoId) { this.produtoEmEdicaoId = produtoEmEdicaoId; }

    public User getLoggedUser() {
        return loggedUser;
    }

    public void setLoggedUser(User loggedUser) {
        this.loggedUser = loggedUser;
        br.com.scopel.pfv.beta.service.audit.AuditActorContext.set(loggedUser);
    }
}