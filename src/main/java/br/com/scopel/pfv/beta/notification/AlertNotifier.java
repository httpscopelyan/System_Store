package br.com.scopel.pfv.beta.notification;

import java.util.List;

public interface AlertNotifier {

    void send(String subject, String body, List<String> recipients);

    boolean canDeliver();
}
