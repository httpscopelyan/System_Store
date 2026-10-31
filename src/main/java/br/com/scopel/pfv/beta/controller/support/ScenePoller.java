package br.com.scopel.pfv.beta.controller.support;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.util.Duration;

public final class ScenePoller {

    private final Timeline timeline;

    private ScenePoller(Duration interval, Runnable action) {
        timeline = new Timeline(new KeyFrame(interval, event -> action.run()));
        timeline.setCycleCount(Animation.INDEFINITE);
    }

    public static ScenePoller start(Node pageNode, Duration interval, Runnable action) {
        ScenePoller poller = new ScenePoller(interval, action);
        pageNode.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene == null) {
                poller.timeline.pause();
            } else {
                poller.timeline.play();
            }
        });
        poller.timeline.play();
        return poller;
    }

    public void stop() {
        timeline.stop();
    }
}
