package br.com.scopel.pfv.beta;

import javafx.application.Platform;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class FxTestSupport {

    private FxTestSupport() {
    }

    static void startToolkit() {
        try {
            Platform.startup(() -> {
            });
        } catch (IllegalStateException toolkitAlreadyStarted) {
        }
        Platform.setImplicitExit(false);
    }

    static void runOnFx(Runnable action) throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                action.run();
                done.complete(null);
            } catch (Throwable failure) {
                done.completeExceptionally(failure);
            }
        });
        try {
            done.get(20, TimeUnit.SECONDS);
        } catch (TimeoutException blocked) {
            Thread.getAllStackTraces().forEach((thread, stack) -> {
                if (thread.getName().contains("JavaFX Application Thread")) {
                    System.err.println("FX THREAD BLOCKED AT:");
                    for (StackTraceElement frame : stack) {
                        System.err.println("    at " + frame);
                    }
                }
            });
            throw blocked;
        }
    }
}
