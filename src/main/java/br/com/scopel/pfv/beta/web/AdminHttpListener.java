package br.com.scopel.pfv.beta.web;

import br.com.scopel.pfv.beta.model.User;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;

@Slf4j
class AdminHttpListener {

        private static final String SESSION_COOKIE = "adm_sid";
    private static final long FAILED_LOGIN_DELAY_MILLIS = 700;

    private final AdminWebProperties properties;
    private final AdminWebRouter router;
    private final AdminWebAuthService authService;
    private final WebSessionStore sessions = new WebSessionStore(Duration.ofMinutes(30));
    private final LoginThrottle throttle = new LoginThrottle(5, Duration.ofMinutes(15), Duration.ofMinutes(15));

    private HttpServer server;

    AdminHttpListener(AdminWebProperties properties, AdminWebRouter router, AdminWebAuthService authService) {
        this.properties = properties;
        this.router = router;
        this.authService = authService;
    }

    void start() {
        try {
            InetSocketAddress address = new InetSocketAddress(InetAddress.getByName(properties.bind()), properties.port());
            server = properties.httpsConfigured() ? httpsServer(address) : HttpServer.create(address, 0);
            server.createContext("/", this::handle);
            server.setExecutor(Executors.newFixedThreadPool(4));
            server.start();
            log.info("[AdminWeb] Painel de administração em {}://{}:{}", properties.httpsConfigured() ? "https" : "http",
                    properties.bind(), properties.port());
            if (!properties.httpsConfigured() && !isLoopback(properties.bind()) && !properties.secureCookie()) {
                log.warn("[AdminWeb] Exposto sem HTTPS. Use ADMIN_WEB_KEYSTORE ou um túnel HTTPS antes de abrir para a internet.");
            }
        } catch (Exception failure) {
            server = null;
            log.error("[AdminWeb] Não foi possível iniciar o painel web: {}", failure.getMessage());
        }
    }

    void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    boolean running() {
        return server != null;
    }

    private HttpServer httpsServer(InetSocketAddress address) throws Exception {
        char[] password = properties.keystorePassword().toCharArray();
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream stream = Files.newInputStream(Path.of(properties.keystorePath()))) {
            keyStore.load(stream, password);
        }
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keyStore, password);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers.getKeyManagers(), null, null);
        HttpsServer https = HttpsServer.create(address, 0);
        https.setHttpsConfigurator(new HttpsConfigurator(context));
        return https;
    }

    private void handle(HttpExchange exchange) throws IOException {
        WebResponse response;
        try {
            response = dispatch(exchange);
        } catch (Exception failure) {
            log.warn("[AdminWeb] Falha ao atender requisição: {}", failure.toString());
            response = WebResponse.text(500, "Erro interno");
        }
        send(exchange, response);
    }

    private WebResponse dispatch(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if (!"GET".equals(method) && !"POST".equals(method)) {
            return WebResponse.text(405, "Método não permitido");
        }
        WebRequest request = WebRequest.parse(method, exchange.getRequestURI(), exchange.getRequestBody(),
                exchange.getRequestHeaders().getFirst("Cookie"));
        if ("/admin.css".equals(request.path())) {
            return WebResponse.css(AdminPageLayout.STYLESHEET);
        }
        String client = clientOf(exchange);
        if ("/login".equals(request.path())) {
            return "POST".equals(method) ? login(request, client) : loginPage(null);
        }
        WebSessionStore.WebSession session = sessions.find(request.cookie(SESSION_COOKIE));
        Optional<User> actor = session == null ? Optional.empty() : authService.activeDev(session.userId());
        if (actor.isEmpty()) {
            if (session != null) {
                sessions.close(request.cookie(SESSION_COOKIE));
            }
            return WebResponse.redirect("/login");
        }
        if ("POST".equals(method) && !validCsrf(session, request.param("_csrf"))) {
            return WebResponse.text(403, "Requisição recusada");
        }
        if ("/logout".equals(request.path()) && "POST".equals(method)) {
            sessions.close(request.cookie(SESSION_COOKIE));
            return WebResponse.redirect("/login").withCookie(cookie("", 0));
        }
        return router.route(request, session, actor.get());
    }

    private WebResponse login(WebRequest request, String client) {
        if (throttle.isBlocked(client)) {
            return loginPage("Muitas tentativas. Aguarde alguns minutos.");
        }
        Optional<User> user = authService.login(request.param("login"), request.param("password"), client);
        if (user.isEmpty()) {
            throttle.recordFailure(client);
            pause();
            return loginPage("Usuário ou senha inválidos.");
        }
        throttle.recordSuccess(client);
        String sessionId = sessions.open(user.get().getId());
        return WebResponse.redirect("/history").withCookie(cookie(sessionId, -1));
    }

    private WebResponse loginPage(String error) {
        return WebResponse.html(AdminPageLayout.page("Entrar", null, null, error, true, AdminManagementPages.login()));
    }

    private boolean validCsrf(WebSessionStore.WebSession session, String token) {
        return MessageDigest.isEqual(session.csrfToken().getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }

    private String cookie(String value, int maxAgeSeconds) {
        return SESSION_COOKIE + "=" + value + "; Path=/; HttpOnly; SameSite=Strict"
                + (maxAgeSeconds >= 0 ? "; Max-Age=" + maxAgeSeconds : "")
                + (properties.secureCookie() ? "; Secure" : "");
    }

    private String clientOf(HttpExchange exchange) {
        if (properties.trustForwardedFor()) {
            String forwarded = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String first = forwarded.split(",")[0].trim();
                return first.length() > 45 ? first.substring(0, 45) : first;
            }
        }
        return exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    private void pause() {
        try {
            Thread.sleep(FAILED_LOGIN_DELAY_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isLoopback(String bind) {
        try {
            return InetAddress.getByName(bind).isLoopbackAddress();
        } catch (IOException unknown) {
            return false;
        }
    }

    private void send(HttpExchange exchange, WebResponse response) throws IOException {
        byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
        var headers = exchange.getResponseHeaders();
        headers.set("Content-Type", response.contentType());
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Content-Security-Policy",
                "default-src 'none'; style-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'");
        if (properties.secureCookie()) {
            headers.set("Strict-Transport-Security", "max-age=31536000");
        }
        if (response.location() != null) {
            headers.set("Location", response.location());
        }
        response.cookies().forEach(cookie -> headers.add("Set-Cookie", cookie));
        exchange.sendResponseHeaders(response.status(), body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }
}
