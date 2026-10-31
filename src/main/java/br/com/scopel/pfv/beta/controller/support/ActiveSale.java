package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.model.Sale;

public final class ActiveSale {

    private final UserSession userSession;

    private Sale sale;

    public ActiveSale(UserSession userSession) {
        this.userSession = userSession;
    }

    public Sale current() {
        return sale;
    }

    public boolean exists() {
        return sale != null;
    }

    public void set(Sale newSale) {
        this.sale = newSale;
        userSession.setVendaAtual(newSale);
    }
}
