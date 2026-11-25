package br.com.scopel.pfv.beta.controller.support;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class BackgroundPrinting {

    private static final String BUSY_MESSAGE = "Já existe uma impressão em andamento. Aguarde terminar.";

    private static final ExecutorService PRINT_LANE = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "print-lane");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean PRINTING = new AtomicBoolean();
    private static final ReadOnlyBooleanWrapper PRINTING_PROPERTY = new ReadOnlyBooleanWrapper(false);
    private static final AtomicReference<Thread> DRAWER_PULSE = new AtomicReference<>();

    private BackgroundPrinting() {
    }

    public static ReadOnlyBooleanProperty printingProperty() {
        return PRINTING_PROPERTY.getReadOnlyProperty();
    }

    public static boolean isPrinting() {
        return PRINTING.get();
    }

    public static boolean run(Runnable job, Consumer<String> onFailure) {
        if (!PRINTING.compareAndSet(false, true)) {
            onFx(() -> onFailure.accept(BUSY_MESSAGE));
            return false;
        }
        onFx(() -> PRINTING_PROPERTY.set(true));
        PRINT_LANE.execute(() -> {
            try {
                job.run();
            } catch (RuntimeException failure) {
                onFx(() -> onFailure.accept(failure.getMessage()));
            } finally {
                PRINTING.set(false);
                onFx(() -> PRINTING_PROPERTY.set(PRINTING.get()));
            }
        });
        return true;
    }

    public static void runDrawer(Runnable pulse, Consumer<String> onFailure) {
        Thread previous = DRAWER_PULSE.get();
        Thread current = new Thread(() -> {
            try {
                pulse.run();
            } catch (RuntimeException failure) {
                onFx(() -> onFailure.accept(failure.getMessage()));
            } finally {
                DRAWER_PULSE.compareAndSet(Thread.currentThread(), null);
            }
        }, "drawer-pulse");
        current.setDaemon(true);
        DRAWER_PULSE.set(current);
        if (previous != null) {
            previous.interrupt();
        }
        current.start();
    }

    private static void onFx(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        try {
            Platform.runLater(action);
        } catch (IllegalStateException toolkitNotStarted) {
            action.run();
        }
    }
}
