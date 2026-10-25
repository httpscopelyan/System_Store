package br.com.scopel.pfv.beta.update;

import br.com.scopel.pfv.beta.config.UpdateConfig;
import br.com.scopel.pfv.beta.service.UpdateService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ciclo completo pelo UpdateService com um "GitHub" local (HttpServer): detectar versão,
 * baixar e validar, fechar o app antigo, instalar, reiniciar na nova versão e recuperar de
 * falhas (download, checksum, partida). O atualizador roda como processo separado de verdade.
 */
@Timeout(180)
class UpdateServiceLifecycleTests {

    @TempDir
    Path root;

    private HttpServer server;
    private String base;
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private final Map<String, Integer> statusOverride = new ConcurrentHashMap<>();
    private final Map<String, Boolean> truncate = new ConcurrentHashMap<>();

    private UpdateConfig config;
    private UpdateService service;
    private Path appJar;
    private Path stateDir;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            Integer forced = statusOverride.get(path);
            byte[] body = files.get(path);
            try (exchange) {
                if (forced != null || body == null) {
                    exchange.sendResponseHeaders(forced != null ? forced : 404, -1);
                    return;
                }
                if (truncate.containsKey(path)) {
                    exchange.sendResponseHeaders(200, body.length); // promete tudo...
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(body, 0, body.length / 2);          // ...entrega metade
                    } catch (IOException ignored) {
                        // o servidor reclama de bytes faltando; é o que queremos simular
                    }
                    return;
                }
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();

        appJar = FakeAppFactory.createJar(root.resolve("scopel-store.jar"), "1.0.0", FakeAppFactory.OK);
        stateDir = root.resolve("updates");
        Files.writeString(root.resolve(".env"), "DB_PASSWORD=segredo");

        config = new UpdateConfig();
        config.setVersionUrl(base + "/version.json");
        config.setCurrentVersion("1.0.0");
        config.setWorkDir(root.toString());
        config.setAppJar(appJar.toString());
        config.setRequireHttps(false);
        config.setAllowedHosts(List.of("127.0.0.1"));
        config.setWaitExitSeconds(15);
        config.setStartTimeoutSeconds(5);
        service = new UpdateService(config);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.stop(0);
        List<ProcessHandle> leftovers = new java.util.ArrayList<>(ProcessHandle.current().descendants().toList());
        // O PDV reaberto pelo atualizador fica órfão (o atualizador já saiu): acha-se pelo marcador.
        Properties marker = FakeAppFactory.readMarker(stateDir);
        if (marker != null) {
            ProcessHandle.of(Long.parseLong(marker.getProperty("pid"))).ifPresent(leftovers::add);
        }
        leftovers.forEach(ProcessHandle::destroyForcibly);
        for (ProcessHandle h : leftovers) {
            try {
                h.onExit().get(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // melhor-esforço
            }
        }
    }

    // ---------- helpers ----------

    /** Publica um release: asset + version.json. Retorna o JAR publicado. */
    private Path publish(String version, String mode) throws IOException {
        Path jar = FakeAppFactory.createJar(root.resolve("published-" + version + ".jar"), version, mode);
        publishAsset(version, jar, FakeAppFactory.sha256(jar), Files.size(jar));
        return jar;
    }

    private void publishAsset(String version, Path jar, String sha, long size) throws IOException {
        files.put("/releases/v" + version + "/scopel-store.jar", Files.readAllBytes(jar));
        files.put("/version.json", """
                {"version":"%s","releaseDate":"2026-10-25","minJavaVersion":%d,"changelog":"novidades",
                 "mandatory":false,"sha256":"%s","size":%d,
                 "downloadUrl":"%s/releases/v%s/scopel-store.jar"}
                """.formatted(version, Runtime.version().feature(), sha, size, base, version)
                .getBytes(StandardCharsets.UTF_8));
    }

    private void assertNoDownloadLeftovers() throws IOException {
        if (!Files.isDirectory(stateDir)) return;
        try (Stream<Path> s = Files.list(stateDir)) {
            assertEquals(List.of(), s.map(p -> p.getFileName().toString())
                    .filter(n -> n.startsWith("scopel-store-")).toList(),
                    "nenhum arquivo de download (parcial ou final) deve sobrar");
        }
    }

    private UpdateService.UpdateResult awaitResult() throws Exception {
        for (int i = 0; i < 240; i++) {
            Optional<UpdateService.UpdateResult> r = service.consumeLastResult();
            if (r.isPresent()) return r.get();
            Thread.sleep(250);
        }
        return fail("o atualizador não gravou o resultado a tempo");
    }

    private void awaitMarker(String version) throws Exception {
        for (int i = 0; i < 60; i++) {
            Properties m = FakeAppFactory.readMarker(stateDir);
            if (m != null && version.equals(m.getProperty("version"))
                    && ProcessHandle.of(Long.parseLong(m.getProperty("pid"))).map(ProcessHandle::isAlive).orElse(false)) {
                return;
            }
            Thread.sleep(250);
        }
        fail("o app " + version + " não está rodando");
    }

    // ---------- detecção ----------

    @Test
    void noUpdateWhenVersionsMatch() throws Exception {
        publish("1.0.0", FakeAppFactory.OK);
        assertTrue(service.checkNow().isEmpty());
        assertFalse(service.isUpdateAvailable());
    }

    @Test
    void noUpdateWhenRemoteIsOlder() throws Exception {
        config.setCurrentVersion("1.2.0");
        publish("1.1.0", FakeAppFactory.OK);
        assertTrue(service.checkNow().isEmpty());
    }

    @Test
    void oldReleaseWithoutChecksumIsNotAnError() throws Exception {
        files.put("/version.json", """
                {"version":"1.0.0","downloadUrl":"https://github.com/x.jar"}""".getBytes(StandardCharsets.UTF_8));
        assertTrue(service.checkNow().isEmpty());
    }

    @Test
    void detectsNewerVersionAndNotifiesListeners() throws Exception {
        publish("1.1.0", FakeAppFactory.OK);
        List<String> notified = new CopyOnWriteArrayList<>();
        service.addListener(info -> notified.add(info.version()));

        Optional<UpdateInfo> info = service.checkNow();

        assertTrue(info.isPresent());
        assertEquals("1.1.0", info.get().version());
        assertTrue(service.isUpdateAvailable());
        assertEquals(List.of("1.1.0"), notified);
        service.checkNow();
        assertEquals(List.of("1.1.0"), notified, "mesma versão não notifica de novo");
    }

    @Test
    void manifestFailuresAreNotUpdates() throws Exception {
        statusOverride.put("/version.json", 500);
        assertThrows(IOException.class, () -> service.checkNow());
        assertFalse(service.isUpdateAvailable());

        statusOverride.clear();
        publish("1.1.0", FakeAppFactory.OK);
        files.put("/version.json", """
                {"version":"1.1.0","downloadUrl":"%s/x.jar"}""".formatted(base).getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> service.checkNow());
        assertFalse(service.isUpdateAvailable());
    }

    @Test
    void doesNotOfferVersionRequiringNewerJava() throws Exception {
        publish("1.1.0", FakeAppFactory.OK);
        String json = new String(files.get("/version.json"), StandardCharsets.UTF_8)
                .replace("\"minJavaVersion\":" + Runtime.version().feature(),
                        "\"minJavaVersion\":" + (Runtime.version().feature() + 5));
        files.put("/version.json", json.getBytes(StandardCharsets.UTF_8));
        assertTrue(service.checkNow().isEmpty());
    }

    // ---------- falhas de download: nada é instalado ----------

    private void assertApplyFailsSafely(Class<? extends Exception> type) throws Exception {
        String before = FakeAppFactory.sha256(appJar);
        AtomicBoolean shutdownCalled = new AtomicBoolean();
        service.checkNow();

        assertThrows(type, () -> service.applyUpdateAndRestart(ProcessHandle.current().pid(),
                () -> shutdownCalled.set(true)));

        assertFalse(shutdownCalled.get(), "o app não pode fechar se a atualização falhou");
        assertFalse(service.isUpdateInProgress(), "deve permitir nova tentativa");
        assertEquals(before, FakeAppFactory.sha256(appJar), "JAR do app intacto");
        assertNoDownloadLeftovers();
        assertEquals("DB_PASSWORD=segredo", Files.readString(root.resolve(".env")));
    }

    @Test
    void downloadHttpErrorIsHandled() throws Exception {
        publish("1.1.0", FakeAppFactory.OK);
        statusOverride.put("/releases/v1.1.0/scopel-store.jar", 404);
        assertApplyFailsSafely(IOException.class);
    }

    @Test
    void truncatedDownloadIsRejected() throws Exception {
        publish("1.1.0", FakeAppFactory.OK);
        truncate.put("/releases/v1.1.0/scopel-store.jar", true);
        assertApplyFailsSafely(IOException.class);
    }

    @Test
    void checksumMismatchIsRejected() throws Exception {
        Path jar = FakeAppFactory.createJar(root.resolve("p.jar"), "1.1.0", FakeAppFactory.OK);
        publishAsset("1.1.0", jar, "0".repeat(64), Files.size(jar));
        assertApplyFailsSafely(IOException.class);
    }

    @Test
    void sizeMismatchIsRejected() throws Exception {
        Path jar = FakeAppFactory.createJar(root.resolve("p.jar"), "1.1.0", FakeAppFactory.OK);
        publishAsset("1.1.0", jar, FakeAppFactory.sha256(jar), Files.size(jar) + 7);
        assertApplyFailsSafely(IOException.class);
    }

    @Test
    void nonJarPayloadWithMatchingChecksumIsRejected() throws Exception {
        Path junk = root.resolve("junk.jar");
        Files.writeString(junk, "isto nao e um jar");
        publishAsset("1.1.0", junk, FakeAppFactory.sha256(junk), Files.size(junk));
        assertApplyFailsSafely(IOException.class);
    }

    @Test
    void refusesToApplyWhenNotRunningFromAJar() throws Exception {
        publish("1.1.0", FakeAppFactory.OK);
        config.setAppJar(root.resolve("nao-existe.jar").toString());
        assertApplyFailsSafely(IllegalStateException.class);
    }

    @Test
    void refusesToApplyWhenAnotherUpdaterHoldsTheLock() throws Exception {
        publish("1.1.0", FakeAppFactory.OK);
        Files.createDirectories(stateDir);
        try (var ch = java.nio.channels.FileChannel.open(stateDir.resolve("updater.lock"),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE);
             var held = ch.tryLock()) {
            assertNotNull(held);
            assertApplyFailsSafely(IllegalStateException.class);
        }
    }

    // ---------- ciclo completo ----------

    @Test
    void fullLifecycleDetectDownloadCloseInstallRestart() throws Exception {
        Path published = publish("1.1.0", FakeAppFactory.OK);
        String newSha = FakeAppFactory.sha256(published);
        String oldSha = FakeAppFactory.sha256(appJar);

        Process oldApp = FakeAppFactory.start(appJar, root);
        awaitMarker("1.0.0");

        assertTrue(service.checkNow().isPresent());
        AtomicBoolean shutdownCalled = new AtomicBoolean();
        service.applyUpdateAndRestart(oldApp.pid(), () -> {
            shutdownCalled.set(true);
            oldApp.destroy(); // o app "se encerra"; o atualizador está esperando por isso
        });

        assertTrue(shutdownCalled.get());
        assertThrows(IllegalStateException.class,
                () -> service.applyUpdateAndRestart(oldApp.pid(), () -> { }),
                "não pode haver uma segunda atualização em paralelo");

        UpdateService.UpdateResult result = awaitResult();
        assertEquals("SUCCESS", result.status(), result.message());
        assertEquals("1.1.0", result.targetVersion());
        assertEquals(newSha, FakeAppFactory.sha256(appJar), "JAR do app é o da versão nova");
        try (Stream<Path> backups = Files.list(root.resolve("backup"))) {
            assertEquals(List.of(oldSha), backups.map(p -> {
                try {
                    return FakeAppFactory.sha256(p);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }).toList(), "backup é exatamente a versão anterior");
        }
        awaitMarker("1.1.0");
        assertFalse(oldApp.isAlive());
        assertEquals("DB_PASSWORD=segredo", Files.readString(root.resolve(".env")), "configuração preservada");

        // Após atualizar, o app nova roda como 1.1.0: sem atualização pendente (sem loop).
        config.setCurrentVersion("1.1.0");
        assertTrue(service.checkNow().isEmpty());
    }

    @Test
    void failedStartupRollsBackAndTheBadVersionIsNotOfferedAgain() throws Exception {
        publish("1.1.0", FakeAppFactory.CRASH);
        String oldSha = FakeAppFactory.sha256(appJar);
        Process oldApp = FakeAppFactory.start(appJar, root);
        awaitMarker("1.0.0");

        service.checkNow();
        service.applyUpdateAndRestart(oldApp.pid(), oldApp::destroy);

        UpdateService.UpdateResult result = awaitResult();
        assertEquals("ROLLED_BACK", result.status());
        assertEquals("1.1.0", result.targetVersion());
        assertEquals(oldSha, FakeAppFactory.sha256(appJar), "versão anterior restaurada");
        awaitMarker("1.0.0"); // e reaberta

        // Sem loop: a versão que falhou não é oferecida de novo...
        assertTrue(service.checkNow().isEmpty());
        // ...mas uma correção mais nova é.
        publish("1.1.1", FakeAppFactory.OK);
        assertEquals("1.1.1", service.checkNow().orElseThrow().version());
    }
}
