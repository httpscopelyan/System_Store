package br.com.scopel.pfv.beta.web;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WebSessionStore {

    public static final class WebSession {
        private final Long userId;
        private final String csrfToken;
        private volatile Instant lastAccess;
        private volatile String flash;

        WebSession(Long userId, String csrfToken, Instant now) {
            this.userId = userId;
            this.csrfToken = csrfToken;
            this.lastAccess = now;
        }

        public Long userId() {
            return userId;
        }

        public String csrfToken() {
            return csrfToken;
        }

        public void flash(String message) {
            this.flash = message;
        }

        public String takeFlash() {
            String message = flash;
            flash = null;
            return message;
        }
    }

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, WebSession> sessions = new ConcurrentHashMap<>();
    private final Duration idleTimeout;

    public WebSessionStore(Duration idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public String open(Long userId) {
        purgeExpired();
        String sessionId = newToken();
        sessions.put(sessionId, new WebSession(userId, newToken(), Instant.now()));
        return sessionId;
    }

    public WebSession find(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        WebSession session = sessions.get(sessionId);
        if (session == null) {
            return null;
        }
        Instant now = Instant.now();
        if (session.lastAccess.plus(idleTimeout).isBefore(now)) {
            sessions.remove(sessionId);
            return null;
        }
        session.lastAccess = now;
        return session;
    }

    public void close(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    private void purgeExpired() {
        Instant limit = Instant.now().minus(idleTimeout);
        sessions.values().removeIf(session -> session.lastAccess.isBefore(limit));
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
