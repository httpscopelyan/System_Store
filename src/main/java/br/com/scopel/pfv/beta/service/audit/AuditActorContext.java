package br.com.scopel.pfv.beta.service.audit;

import br.com.scopel.pfv.beta.model.User;

public final class AuditActorContext {

    private static volatile User currentActor;

    private AuditActorContext() {
    }

    public static void set(User actor) {
        currentActor = actor;
    }

    public static User get() {
        return currentActor;
    }
}
