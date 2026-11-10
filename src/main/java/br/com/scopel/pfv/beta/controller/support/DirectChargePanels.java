package br.com.scopel.pfv.beta.controller.support;

import javafx.scene.Node;

public final class DirectChargePanels {

    public enum State { INPUT, CHOOSE, CHARGING }

    private final Node inputPanel;
    private final Node choosePanel;
    private final Node chargingPanel;

    private State state = State.INPUT;

    public DirectChargePanels(Node inputPanel, Node choosePanel, Node chargingPanel) {
        this.inputPanel = inputPanel;
        this.choosePanel = choosePanel;
        this.chargingPanel = chargingPanel;
    }

    public State state() {
        return state;
    }

    public boolean is(State expectedState) {
        return state == expectedState;
    }

    public void show(State newState) {
        state = newState;
        setVisible(inputPanel, newState == State.INPUT);
        setVisible(choosePanel, newState == State.CHOOSE);
        setVisible(chargingPanel, newState == State.CHARGING);
    }

    private void setVisible(Node panel, boolean visible) {
        panel.setVisible(visible);
        panel.setManaged(visible);
    }
}
