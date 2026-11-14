package br.com.scopel.pfv.beta;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {"admin.web.enabled=true", "admin.web.port=18443", "admin.web.bind=127.0.0.1"})
class AdminWebServerTests {

    private static final String BASE = "http://127.0.0.1:18443";

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE + path));
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) {
            builder.header("Content-Type", "application/x-www-form-urlencoded");
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void pagesWithoutASessionRedirectToLogin() throws Exception {
        for (String path : new String[]{"/", "/history", "/permissions", "/accounts", "/cash-groups"}) {
            HttpResponse<String> response = send("GET", path, null);
            assertEquals(303, response.statusCode(), path);
            assertEquals("/login", response.headers().firstValue("Location").orElse(""), path);
        }
    }

    @Test
    void actionsWithoutASessionAreNotExecuted() throws Exception {
        HttpResponse<String> response = send("POST", "/accounts/delete", "userId=1&confirm=yes");
        assertEquals(303, response.statusCode());
        assertEquals("/login", response.headers().firstValue("Location").orElse(""));
    }

    @Test
    void loginPageCarriesTheSecurityHeadersAndStylesheetIsServed() throws Exception {
        HttpResponse<String> login = send("GET", "/login", null);
        assertEquals(200, login.statusCode());
        assertTrue(login.body().contains("name=\"password\""));
        assertEquals("DENY", login.headers().firstValue("X-Frame-Options").orElse(""));
        assertEquals("nosniff", login.headers().firstValue("X-Content-Type-Options").orElse(""));
        assertEquals("no-store", login.headers().firstValue("Cache-Control").orElse(""));
        assertTrue(login.headers().firstValue("Content-Security-Policy").orElse("").contains("default-src 'none'"));
        HttpResponse<String> css = send("GET", "/admin.css", null);
        assertEquals(200, css.statusCode());
        assertTrue(css.headers().firstValue("Content-Type").orElse("").startsWith("text/css"));
    }

    @Test
    void methodsOtherThanGetAndPostAreRefused() throws Exception {
        assertEquals(405, send("PUT", "/history", "x=1").statusCode());
        assertEquals(405, send("DELETE", "/accounts", null).statusCode());
    }
}
