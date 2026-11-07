package br.com.scopel.pfv.beta.controller.support;

import javafx.application.Platform;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class BackgroundPrinting {

    private static final ExecutorService PRINT_QUEUE = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "print-queue");
        thread.setDaemon(true);
        return thread;
    });

    private BackgroundPrinting() {
    }

    public static void run(Runnable job, Consumer<String> onFailure) {
        PRINT_QUEUE.execute(() -> {
            try {
                job.run();
            } catch (RuntimeException failure) {
                Platform.runLater(() -> onFailure.accept(failure.getMessage()));
            }
        });
    }
}
