package br.com.scopel.pfv.beta.controller.support;

import br.com.scopel.pfv.beta.model.CardType;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ManualCardApproval {

    private final Predicate<String> operatorConfirms;

    public ManualCardApproval() {
        this(ManualCardApproval::askOperator);
    }

    public ManualCardApproval(Predicate<String> operatorConfirms) {
        this.operatorConfirms = operatorConfirms;
    }

    public void request(String formattedTotal, CardType type, CardChargePoller pendingCharge,
                        Consumer<String> status, Runnable onConfirmed) {
        if (!operatorConfirms.test(messageFor(formattedTotal, type))) {
            return;
        }
        if (pendingCharge == null) {
            onConfirmed.run();
            return;
        }
        if (pendingCharge.chargeId() == null) {
            pendingCharge.stop();
            onConfirmed.run();
            return;
        }
        status.accept("Cancelando a cobrança na maquininha...");
        pendingCharge.cancel(onConfirmed, status);
    }

    static String messageFor(String formattedTotal, CardType type) {
        return "Confirme somente se o cliente JÁ PAGOU R$ " + formattedTotal + " no "
                + (type == CardType.DEBITO ? "débito" : "crédito")
                + " (aprovação vista na maquininha ou o valor no app do Mercado Pago).\n\n"
                + "A cobrança pendente na maquininha será cancelada, a venda será finalizada "
                + "e esta confirmação fica registrada na auditoria.";
    }

    private static boolean askOperator(String message) {
        ButtonType back = new ButtonType("Não, voltar", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType confirm = new ButtonType("Sim, o cliente pagou", ButtonBar.ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, confirm, back);
        alert.setHeaderText("Finalizar a venda sem a maquininha?");
        ((Button) alert.getDialogPane().lookupButton(back)).setDefaultButton(true);
        ((Button) alert.getDialogPane().lookupButton(confirm)).setDefaultButton(false);
        return alert.showAndWait().filter(confirm::equals).isPresent();
    }
}
