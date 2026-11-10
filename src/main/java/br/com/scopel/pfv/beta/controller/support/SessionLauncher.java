package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.BetaApplication;
import br.com.scopel.pfv.beta.config.Navigator;
import br.com.scopel.pfv.beta.config.Page;
import br.com.scopel.pfv.beta.config.ThemeManager;
import br.com.scopel.pfv.beta.config.UserSession;
import br.com.scopel.pfv.beta.model.Sale;
import br.com.scopel.pfv.beta.model.User;
import br.com.scopel.pfv.beta.service.CashRegisterService;
import br.com.scopel.pfv.beta.service.SplitPaymentService;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

@Component
public class SessionLauncher {

    private static final String RESUMED_SPLIT_SALE_NOTICE =
            "Venda com pagamento parcial retomada. Conclua o valor restante.";

    private final UserSession userSession;
    private final CashRegisterService cashRegisterService;
    private final SplitPaymentService splitPaymentService;
    private final Navigator navigator;
    private final ThemeManager themeManager;

    public SessionLauncher(UserSession userSession, CashRegisterService cashRegisterService,
                           SplitPaymentService splitPaymentService, Navigator navigator, ThemeManager themeManager) {
        this.userSession = userSession;
        this.cashRegisterService = cashRegisterService;
        this.splitPaymentService = splitPaymentService;
        this.navigator = navigator;
        this.themeManager = themeManager;
    }

    public void start(User operator, Scene scene) throws IOException {
        userSession.setLoggedUser(operator);
        userSession.setCaixaAtual(cashRegisterService.findOpenByOperator(operator).orElse(null));
        userSession.setVendaAtual(null);
        userSession.setUltimaVenda(null);
        userSession.setUltimaForma(null);
        userSession.setUltimoTroco(null);
        userSession.setCardTypeSelecionado(null);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main_layout.fxml"));
        loader.setControllerFactory(BetaApplication.springContext::getBean);
        Parent shellRoot = loader.load();
        themeManager.apply(shellRoot);
        scene.setRoot(shellRoot);

        Optional<Sale> saleWithPendingParts = splitPaymentService.findSaleWithPendingParts(operator);
        if (saleWithPendingParts.isPresent()) {
            userSession.setVendaAtual(saleWithPendingParts.get());
            navigator.navigate(Page.PAGAMENTO_DIVIDIDO);
            navigator.notice(RESUMED_SPLIT_SALE_NOTICE);
            return;
        }
        navigator.navigate(Page.INICIO);
    }
}
