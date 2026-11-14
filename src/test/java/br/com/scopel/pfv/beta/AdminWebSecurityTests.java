package br.com.scopel.pfv.beta;

import br.com.scopel.pfv.beta.web.HtmlEscape;
import br.com.scopel.pfv.beta.web.LoginThrottle;
import br.com.scopel.pfv.beta.web.WebSessionStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminWebSecurityTests {

    @Test
    void clientIsBlockedAfterTooManyFailuresAndClearedByASuccess() {
        LoginThrottle throttle = new LoginThrottle(3, Duration.ofMinutes(15), Duration.ofMinutes(15));
        throttle.recordFailure("10.0.0.1");
        throttle.recordFailure("10.0.0.1");
        assertFalse(throttle.isBlocked("10.0.0.1"));
        throttle.recordFailure("10.0.0.1");
        assertTrue(throttle.isBlocked("10.0.0.1"));
        assertFalse(throttle.isBlocked("10.0.0.2"));
        throttle.recordSuccess("10.0.0.1");
        assertFalse(throttle.isBlocked("10.0.0.1"));
    }

    @Test
    void sessionsGetUnguessableDistinctIdsAndCsrfTokens() {
        WebSessionStore store = new WebSessionStore(Duration.ofMinutes(30));
        String first = store.open(1L);
        String second = store.open(1L);
        assertNotEquals(first, second);
        assertTrue(first.length() >= 40);
        assertNotNull(store.find(first));
        assertNotEquals(store.find(first).csrfToken(), store.find(second).csrfToken());
        assertEquals(1L, store.find(first).userId());
    }

    @Test
    void closedAndExpiredSessionsAreNotFound() throws InterruptedException {
        WebSessionStore store = new WebSessionStore(Duration.ofMinutes(30));
        String id = store.open(7L);
        store.close(id);
        assertNull(store.find(id));
        assertNull(store.find(null));
        assertNull(store.find("inexistente"));
        WebSessionStore instantExpiry = new WebSessionStore(Duration.ofMillis(5));
        String expired = instantExpiry.open(7L);
        Thread.sleep(50);
        assertNull(instantExpiry.find(expired));
    }

    @Test
    void htmlEscapeNeutralizesMarkupAndQuotes() {
        assertEquals("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; &quot;q&quot;",
                HtmlEscape.of("<script>alert('x')</script> & \"q\""));
        assertEquals("", HtmlEscape.of(null));
    }
}
