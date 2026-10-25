package br.com.scopel.pfv.beta.update;

import br.com.scopel.pfv.updater.UpdaterMain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ciclo do atualizador (UpdaterMain) com processos reais: instalar, reiniciar, confirmar a
 * partida e restaurar em falhas. Cada cenário afirma o que acontece com o JAR, os dados do
 * usuário, o backup e o resultado gravado.
 */
@Timeout(120)
class UpdaterLifecycleTests {

    @TempDir
    Path root;

    private Path appJar;
    private Path stateDir;
    private Path backupDir;
    private final List<Process> spawned = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        appJar = FakeAppFactory.createJar(root.resolve("scopel-store.jar"), "1.0.0", FakeAppFactory.OK);
        stateDir = root.resolve("updates");
        backupDir = root.resolve("backup");
        Files.writeString(root.resolve(".env"), "DB_PASSWORD=segredo");
        Files.writeString(root.resolve("config-local.txt"), "preferencias-do-operador");
    }

    @AfterEach
    void killEverything() throws Exception {
        // Tudo que o teste iniciou (inclusive o PDV reaberto pelo atualizador) é descendente desta JVM.
        List<ProcessHandle> leftovers = ProcessHandle.current().descendants().toList();
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

    private long deadPid() throws Exception {
        Process p = new ProcessBuilder(FakeAppFactory.javaExecutable(), "-version")
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        p.waitFor();
        return p.pid();
    }

    private Path job(long appPid, Path newJar, String sha, String newVersion) throws IOException {
        Properties p = new Properties();
        p.setProperty("appPid", Long.toString(appPid));
        p.setProperty("appJar", appJar.toString());
        p.setProperty("newJar", newJar.toString());
        p.setProperty("sha256", sha);
        p.setProperty("newVersion", newVersion);
        p.setProperty("oldVersion", "1.0.0");
        p.setProperty("stateDir", stateDir.toString());
        p.setProperty("backupDir", backupDir.toString());
        p.setProperty("workDir", root.toString());
        p.setProperty("java", FakeAppFactory.javaExecutable());
        p.setProperty("waitExitSeconds", "5");
        p.setProperty("startTimeoutSeconds", "4");
        p.setProperty("rollbackAliveSeconds", "1");
        Path file = root.resolve("job-" + System.nanoTime() + ".properties");
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            p.store(w, null);
        }
        return file;
    }

    private Path newJar(String version, String mode) throws IOException {
        Path dir = Files.createDirectories(stateDir);
        return FakeAppFactory.createJar(dir.resolve("scopel-store-" + version + ".jar"), version, mode);
    }

    private Properties result() throws IOException {
        Properties p = new Properties();
        try (var in = Files.newBufferedReader(stateDir.resolve("update-result.properties"), StandardCharsets.UTF_8)) {
            p.load(in);
        }
        return p;
    }

    private void assertUserDataPreserved() throws IOException {
        assertEquals("DB_PASSWORD=segredo", Files.readString(root.resolve(".env")));
        assertEquals("preferencias-do-operador", Files.readString(root.resolve("config-local.txt")));
    }

    private long backupCount() throws IOException {
        if (!Files.isDirectory(backupDir)) return 0;
        try (Stream<Path> s = Files.list(backupDir)) {
            return s.count();
        }
    }

    /** O app (re)aberto grava o marcador logo após subir; espera até 15 s por ele. */
    private void assertRunningMarker(String expectedVersion) throws InterruptedException {
        Properties m = null;
        for (int i = 0; i < 60; i++) {
            m = FakeAppFactory.readMarker(stateDir);
            if (m != null && expectedVersion.equals(m.getProperty("version"))) {
                break;
            }
            Thread.sleep(250);
        }
        assertNotNull(m, "o app deveria ter gravado o marcador de partida");
        assertEquals(expectedVersion, m.getProperty("version"));
        assertTrue(ProcessHandle.of(Long.parseLong(m.getProperty("pid"))).map(ProcessHandle::isAlive).orElse(false),
                "o app deveria estar rodando");
    }

    // ---------- cenários ----------

    @Test
    void installsNewVersionRestartsAndPreservesUserData() throws Exception {
        String oldSha = FakeAppFactory.sha256(appJar);
        Path update = newJar("1.1.0", FakeAppFactory.OK);
        String newSha = FakeAppFactory.sha256(update);

        int rc = UpdaterMain.run(job(deadPid(), update, newSha, "1.1.0"));

        assertEquals(0, rc);
        assertEquals(newSha, FakeAppFactory.sha256(appJar), "o JAR do app deve ser o novo");
        assertEquals(1, backupCount(), "deve existir backup da versão anterior");
        try (Stream<Path> s = Files.list(backupDir)) {
            assertEquals(oldSha, FakeAppFactory.sha256(s.findFirst().orElseThrow()));
        }
        assertEquals("SUCCESS", result().getProperty("status"));
        assertRunningMarker("1.1.0");
        assertUserDataPreserved();
        assertFalse(Files.exists(appJar.resolveSibling("scopel-store.jar.new")), "sem sobras de instalação parcial");
        assertFalse(Files.exists(update), "download consumido após o sucesso");
    }

    @Test
    void rollsBackWhenNewVersionCrashesOnStartup() throws Exception {
        String oldSha = FakeAppFactory.sha256(appJar);
        Path update = newJar("1.1.0", FakeAppFactory.CRASH);

        int rc = UpdaterMain.run(job(deadPid(), update, FakeAppFactory.sha256(update), "1.1.0"));

        assertEquals(1, rc);
        assertEquals(oldSha, FakeAppFactory.sha256(appJar), "o JAR anterior deve ser restaurado");
        assertEquals("ROLLED_BACK", result().getProperty("status"));
        assertEquals("1.1.0", result().getProperty("targetVersion"));
        assertRunningMarker("1.0.0"); // a versão anterior foi reaberta
        assertUserDataPreserved();
    }

    @Test
    void rollsBackWhenNewVersionNeverConfirmsStartup() throws Exception {
        String oldSha = FakeAppFactory.sha256(appJar);
        Path update = newJar("1.1.0", FakeAppFactory.HANG);

        int rc = UpdaterMain.run(job(deadPid(), update, FakeAppFactory.sha256(update), "1.1.0"));

        assertEquals(1, rc);
        assertEquals(oldSha, FakeAppFactory.sha256(appJar));
        assertEquals("ROLLED_BACK", result().getProperty("status"));
        assertTrue(result().getProperty("message").contains("não confirmou"));
        assertRunningMarker("1.0.0");
    }

    @Test
    void rollsBackWhenStartedAppReportsUnexpectedVersion() throws Exception {
        String oldSha = FakeAppFactory.sha256(appJar);
        // O manifesto promete 1.1.0, mas o asset publicado na verdade é a 9.9.9.
        Path update = newJar("9.9.9", FakeAppFactory.OK);

        int rc = UpdaterMain.run(job(deadPid(), update, FakeAppFactory.sha256(update), "1.1.0"));

        assertEquals(1, rc);
        assertEquals(oldSha, FakeAppFactory.sha256(appJar));
        assertEquals("ROLLED_BACK", result().getProperty("status"));
        assertRunningMarker("1.0.0");
    }

    @Test
    void refusesTamperedDownloadWithoutTouchingTheApp() throws Exception {
        String oldSha = FakeAppFactory.sha256(appJar);
        Path update = newJar("1.1.0", FakeAppFactory.OK);

        int rc = UpdaterMain.run(job(deadPid(), update, "f".repeat(64), "1.1.0"));

        assertEquals(1, rc);
        assertEquals(oldSha, FakeAppFactory.sha256(appJar), "JAR intacto");
        assertEquals(0, backupCount(), "nada foi instalado, nem backup");
        assertEquals("FAILED", result().getProperty("status"));
        assertUserDataPreserved();
    }

    @Test
    void refusesTruncatedDownload() throws Exception {
        String oldSha = FakeAppFactory.sha256(appJar);
        Path update = newJar("1.1.0", FakeAppFactory.OK);
        String sha = FakeAppFactory.sha256(update);
        byte[] bytes = Files.readAllBytes(update);
        Files.write(update, java.util.Arrays.copyOf(bytes, bytes.length / 2));

        int rc = UpdaterMain.run(job(deadPid(), update, sha, "1.1.0"));

        assertEquals(1, rc);
        assertEquals(oldSha, FakeAppFactory.sha256(appJar));
        assertEquals("FAILED", result().getProperty("status"));
    }

    @Test
    void givesUpWithoutChangesIfAppDoesNotClose() throws Exception {
        String oldSha = FakeAppFactory.sha256(appJar);
        Path update = newJar("1.1.0", FakeAppFactory.OK);
        Process stillRunning = FakeAppFactory.start(appJar, root);
        spawned.add(stillRunning);

        int rc = UpdaterMain.run(job(stillRunning.pid(), update, FakeAppFactory.sha256(update), "1.1.0"));

        assertEquals(1, rc);
        assertTrue(stillRunning.isAlive(), "o app em execução não pode ser morto pelo atualizador");
        assertEquals(oldSha, FakeAppFactory.sha256(appJar));
        assertEquals(0, backupCount());
        assertEquals("FAILED", result().getProperty("status"));
    }

    @Test
    void waitsForTheRunningAppToExitBeforeInstalling() throws Exception {
        Path update = newJar("1.1.0", FakeAppFactory.OK);
        String newSha = FakeAppFactory.sha256(update);
        Process running = FakeAppFactory.start(appJar, root);
        spawned.add(running);
        Thread.sleep(500);
        // O PDV "fecha" 1,5 s depois de o atualizador começar a esperar.
        Thread closer = new Thread(() -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
                return;
            }
            running.destroy();
        });
        closer.start();

        int rc = UpdaterMain.run(job(running.pid(), update, newSha, "1.1.0"));

        assertEquals(0, rc);
        assertFalse(running.isAlive());
        assertEquals(newSha, FakeAppFactory.sha256(appJar));
        assertRunningMarker("1.1.0");
    }

    @Test
    void secondUpdaterIsRefusedWhileOneIsRunning() throws Exception {
        String oldSha = FakeAppFactory.sha256(appJar);
        Path update = newJar("1.1.0", FakeAppFactory.OK);
        Files.createDirectories(stateDir);
        try (FileChannel ch = FileChannel.open(stateDir.resolve("updater.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock held = ch.tryLock()) {
            assertNotNull(held);

            int rc = UpdaterMain.run(job(deadPid(), update, FakeAppFactory.sha256(update), "1.1.0"));

            assertEquals(2, rc);
        }
        assertEquals(oldSha, FakeAppFactory.sha256(appJar));
        assertEquals(0, backupCount());
    }

    @Test
    void keepsOnlyTheConfiguredNumberOfBackups() throws Exception {
        Files.createDirectories(backupDir);
        for (int i = 0; i < 5; i++) {
            Path old = backupDir.resolve("scopel-store-0.0." + i + "-2026010100000" + i + ".jar");
            Files.writeString(old, "x");
            Files.setLastModifiedTime(old, java.nio.file.attribute.FileTime.fromMillis(1_000_000L + i));
        }
        Path update = newJar("1.1.0", FakeAppFactory.OK);

        assertEquals(0, UpdaterMain.run(job(deadPid(), update, FakeAppFactory.sha256(update), "1.1.0")));

        assertEquals(3, backupCount(), "mantém só os 3 backups mais recentes");
    }
}
