package br.com.scopel.pfv.beta.web;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LoginThrottle {

    private record Attempts(int failures, Instant windowStart, Instant blockedUntil) {
    }

    private final int maxFailures;
    private final Duration window;
    private final Duration blockDuration;
    private final Map<String, Attempts> byClient = new ConcurrentHashMap<>();

    public LoginThrottle(int maxFailures, Duration window, Duration blockDuration) {
        this.maxFailures = maxFailures;
        this.window = window;
        this.blockDuration = blockDuration;
    }

    public boolean isBlocked(String client) {
        Attempts attempts = byClient.get(client);
        return attempts != null && attempts.blockedUntil() != null && attempts.blockedUntil().isAfter(Instant.now());
    }

    public void recordFailure(String client) {
        Instant now = Instant.now();
        byClient.compute(client, (key, current) -> {
            if (current == null || current.windowStart().plus(window).isBefore(now)) {
                return new Attempts(1, now, null);
            }
            int failures = current.failures() + 1;
            return new Attempts(failures, current.windowStart(), failures >= maxFailures ? now.plus(blockDuration) : null);
        });
    }

    public void recordSuccess(String client) {
        byClient.remove(client);
    }
}
