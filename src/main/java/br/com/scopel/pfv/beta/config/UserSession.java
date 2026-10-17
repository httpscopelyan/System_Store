package br.com.scopel.pfv.beta.config;

import br.com.scopel.pfv.beta.model.CashRegister;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.User;
import org.springframework.stereotype.Component;

@Component
public class UserSession {

    private User loggedUser;
    private CashRegister caixaAtual;
    private Sale vendaAtual;

    public CashRegister getCaixaAtual() { return caixaAtual; }
    public void setCaixaAtual(CashRegister caixaAtual) { this.caixaAtual = caixaAtual; }

    public Sale getVendaAtual() { return vendaAtual; }
    public void setVendaAtual(Sale vendaAtual) { this.vendaAtual = vendaAtual; }

    public User getLoggedUser() {
        return loggedUser;
    }

    public void setLoggedUser(User loggedUser) {
        this.loggedUser = loggedUser;
    }
}