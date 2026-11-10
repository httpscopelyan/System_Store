package br.com.scopel.pfv.beta.controller.support;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Label;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ShellClock {

    private static Timeline runningClock;

    private ShellClock() {
    }

    public static void restart(Label dateTimeLabel, DateTimeFormatter format) {
        if (runningClock != null) {
            runningClock.stop();
        }
        runningClock = new Timeline(new KeyFrame(Duration.ZERO,
                tick -> dateTimeLabel.setText(LocalDateTime.now().format(format))),
                new KeyFrame(Duration.seconds(1)));
        runningClock.setCycleCount(Animation.INDEFINITE);
        runningClock.play();
    }
}
