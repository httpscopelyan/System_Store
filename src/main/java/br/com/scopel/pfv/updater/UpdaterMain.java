package br.com.scopel.pfv.updater;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Atualizador do PDV. Roda em um processo SEPARADO do app (o app extrai esta
 * classe para uma pasta temporária e a executa com `java -cp`), então pode
 * trocar o JAR do app depois que ele fecha.
 *
 * Regras de design:
 *  - Só JDK, sem Spring/JavaFX, e uma única classe (sem classes aninhadas), pois
 *    o app copia apenas UpdaterMain.class para fora do JAR.
 *  - Não toca em nada além do JAR do app: .env, pasta de estado e banco ficam intactos.
 *  - Trava de arquivo (updater.lock) impede dois atualizadores ao mesmo tempo.
 *  - Instalação por cópia para arquivo vizinho + ATOMIC_MOVE: nunca há JAR pela metade.
 *  - Depois de instalar, só considera sucesso se o novo processo gravar o marcador
 *    started.ok com o próprio pid e a versão esperada; senão restaura o backup.
 *
 * Entrada: caminho de um .properties (UTF-8) com a tarefa. Saída: exit code
 * 0 = atualizado; 1 = falhou (versão anterior preservada/restaurada); 2 = não executou.
 * O resultado também é gravado em stateDir/update-result.properties.
 */
public final class UpdaterMain {

    public static final String LOCK_FILE = "updater.lock";
    public static final String MARKER_FILE = "started.ok";
    public static final String RESULT_FILE = "update-result.properties";
    public static final String LOG_FILE = "updater.log";

    private static volatile Path logFile;

    private UpdaterMain() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("uso: UpdaterMain <job.properties>");
            System.exit(2);
        }
        System.exit(run(Path.of(args[0])));
    }

    public static int run(Path jobFile) {
        Properties job = new Properties();
        try (Reader in = Files.newBufferedReader(jobFile, StandardCharsets.UTF_8)) {
            job.load(in);
        } catch (IOException e) {
            System.err.println("Tarefa ilegível: " + e);
            return 2;
        }

        Path stateDir = Path.of(req(job, "stateDir"));
        try {
            Files.createDirectories(stateDir);
        } catch (IOException e) {
            System.err.println("Pasta de estado inacessível: " + e);
            return 2;
        }
        logFile = stateDir.resolve(LOG_FILE);

        try (FileChannel channel = FileChannel.open(stateDir.resolve(LOCK_FILE),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock lock = tryLock(channel);
            if (lock == null) {
                log("Outro atualizador já está em execução; abortando sem alterar nada.");
                return 2;
            }
            try (lock) {
                return execute(job, stateDir);
            }
        } catch (IOException e) {
            log("Falha ao obter a trava do atualizador: " + e);
            return 2;
        } finally {
            cleanupWorkDir(jobFile);
        }
    }

    /** null = trava ocupada (por outro processo ou por esta mesma JVM). */
    private static FileLock tryLock(FileChannel channel) throws IOException {
        try {
            return channel.tryLock();
        } catch (OverlappingFileLockException e) {
            return null;
        }
    }

    // -----------------------------------------------------------------
    //  Fluxo principal
    // -----------------------------------------------------------------

    private static int execute(Properties job, Path stateDir) {
        Path appJar = Path.of(req(job, "appJar"));
        Path newJar = Path.of(req(job, "newJar"));
        Path backupDir = Path.of(req(job, "backupDir"));
        Path workDir = Path.of(req(job, "workDir"));
        String java = req(job, "java");
        String expectedSha = req(job, "sha256");
        String oldVersion = job.getProperty("oldVersion", "desconhecida");
        String newVersion = req(job, "newVersion");
        long appPid = Long.parseLong(req(job, "appPid"));
        long waitExit = Long.parseLong(job.getProperty("waitExitSeconds", "90"));
        long startTimeout = Long.parseLong(job.getProperty("startTimeoutSeconds", "180"));
        long rollbackAlive = Long.parseLong(job.getProperty("rollbackAliveSeconds", "5"));
        int keepBackups = Integer.parseInt(job.getProperty("keepBackups", "3"));
        Path staged = appJar.resolveSibling(appJar.getFileName() + ".new");

        log("=== Atualização " + oldVersion + " -> " + newVersion + " ===");
        try {
            Files.deleteIfExists(staged);

            // 1) Valida o download ANTES de mexer em qualquer coisa.
            requireIntegrity(newJar, expectedSha);
            log("Download verificado (SHA-256 ok).");

            // 2) Espera o PDV encerrar (shutdown gracioso feito pelo app).
            if (!waitForExit(appPid, waitExit)) {
                log("O PDV não encerrou em " + waitExit + "s; nada foi alterado.");
                writeResult(stateDir, "FAILED", newVersion, oldVersion,
                        "O PDV não encerrou a tempo; a atualização foi cancelada sem alterações.");
                return 1;
            }
            log("PDV encerrado.");
        } catch (Exception e) {
            log("Falha antes da instalação: " + e);
            writeResult(stateDir, "FAILED", newVersion, oldVersion, "Falha antes da instalação: " + e.getMessage());
            // Se o app já fechou, reabre a versão atual para o operador não ficar sem sistema.
            if (!isAlive(appPid)) {
                relaunch(java, appJar, workDir, stateDir);
            }
            return 1;
        }

        Path backup = null;
        String backupSha = null;
        boolean installed = false;
        Process started = null;
        String failure;
        try {
            // 3) Backup da versão atual.
            backup = backup(appJar, backupDir, oldVersion);
            backupSha = sha256(backup);
            log("Backup criado: " + backup);

            // 4) Instalação atômica.
            Files.copy(newJar, staged, StandardCopyOption.REPLACE_EXISTING);
            requireIntegrity(staged, expectedSha);
            moveWithRetry(staged, appJar);
            installed = true;
            log("Nova versão instalada.");

            // 5) Reinicia e verifica.
            Files.deleteIfExists(stateDir.resolve(MARKER_FILE));
            started = launch(java, appJar, workDir, stateDir);
            log("Novo processo iniciado (pid " + started.pid() + "); aguardando confirmação...");
            failure = awaitStart(started, stateDir.resolve(MARKER_FILE), newVersion, startTimeout);
            if (failure == null) {
                writeResult(stateDir, "SUCCESS", newVersion, oldVersion, "Atualizado com sucesso.");
                Files.deleteIfExists(newJar);
                prune(backupDir, keepBackups);
                log("Atualização concluída com sucesso.");
                return 0;
            }
        } catch (Exception e) {
            failure = "Erro durante a instalação: " + e;
        }

        // 6) Falhou: restaura a versão anterior.
        log("FALHA: " + failure + " — iniciando restauração.");
        killTree(started);
        try {
            Files.deleteIfExists(staged);
            if (installed || backup != null) {
                restore(backup, backupSha, appJar);
                log("Versão anterior restaurada.");
            }
        } catch (Exception e) {
            log("Não foi possível restaurar automaticamente: " + e);
            writeResult(stateDir, "FAILED", newVersion, oldVersion, failure
                    + " | Restauração automática falhou: " + e.getMessage()
                    + (backup != null ? " | Backup em: " + backup : ""));
            return 1;
        }
        // Resultado gravado ANTES de reabrir, para o app restaurado já encontrá-lo.
        writeResult(stateDir, "ROLLED_BACK", newVersion, oldVersion, failure);
        Process old = relaunch(java, appJar, workDir, stateDir);
        if (old == null || !stayAlive(old, rollbackAlive)) {
            writeResult(stateDir, "FAILED", newVersion, oldVersion, failure
                    + " | A versão anterior foi restaurada, mas não iniciou. Abra o PDV manualmente.");
        }
        return 1;
    }

    // -----------------------------------------------------------------
    //  Etapas
    // -----------------------------------------------------------------

    private static void requireIntegrity(Path jar, String expectedSha) throws IOException {
        if (!Files.isRegularFile(jar) || Files.size(jar) == 0) {
            throw new IOException("Arquivo de atualização ausente ou vazio: " + jar);
        }
        String actual = sha256(jar);
        if (!actual.equalsIgnoreCase(expectedSha)) {
            throw new IOException("SHA-256 do arquivo de atualização não confere.");
        }
        try (JarFile jf = new JarFile(jar.toFile())) {
            if (jf.getManifest() == null || jf.getManifest().getMainAttributes().getValue("Main-Class") == null) {
                throw new IOException("Arquivo de atualização não é um JAR executável.");
            }
        }
    }

    private static boolean waitForExit(long pid, long seconds) throws InterruptedException {
        Optional<ProcessHandle> handle = ProcessHandle.of(pid);
        if (handle.isEmpty() || !handle.get().isAlive()) {
            return true;
        }
        try {
            handle.get().onExit().get(seconds, TimeUnit.SECONDS);
            return true;
        } catch (java.util.concurrent.TimeoutException e) {
            return false;
        } catch (java.util.concurrent.ExecutionException e) {
            return !handle.get().isAlive();
        }
    }

    private static boolean isAlive(long pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    private static Path backup(Path appJar, Path backupDir, String oldVersion) throws IOException {
        Files.createDirectories(backupDir);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        Path target = backupDir.resolve("scopel-store-" + oldVersion + "-" + stamp + ".jar");
        Files.copy(appJar, target, StandardCopyOption.REPLACE_EXISTING);
        if (Files.size(target) != Files.size(appJar)) {
            throw new IOException("Backup incompleto.");
        }
        return target;
    }

    private static void prune(Path backupDir, int keep) {
        try (Stream<Path> files = Files.list(backupDir)) {
            List<Path> old = files
                    .filter(p -> p.getFileName().toString().startsWith("scopel-store-")
                            && p.getFileName().toString().endsWith(".jar"))
                    .sorted(Comparator.comparingLong(UpdaterMain::lastModified).reversed())
                    .skip(Math.max(keep, 1))
                    .toList();
            for (Path p : old) {
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            log("Não foi possível limpar backups antigos: " + e);
        }
    }

    private static long lastModified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    /** No Windows o JAR ainda pode ficar bloqueado por instantes após o processo sair. */
    private static void moveWithRetry(Path from, Path to) throws IOException, InterruptedException {
        IOException last = null;
        for (int i = 0; i < 30; i++) {
            try {
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return;
            } catch (IOException e) {
                last = e;
                Thread.sleep(1000);
            }
        }
        Files.deleteIfExists(from);
        throw new IOException("Não foi possível substituir o JAR (arquivo em uso?): " + last);
    }

    private static void restore(Path backup, String backupSha, Path appJar) throws IOException, InterruptedException {
        if (backup == null) {
            return;
        }
        Path tmp = appJar.resolveSibling(appJar.getFileName() + ".restore");
        Files.copy(backup, tmp, StandardCopyOption.REPLACE_EXISTING);
        if (!sha256(tmp).equals(backupSha)) {
            Files.deleteIfExists(tmp);
            throw new IOException("Backup corrompido (SHA-256 divergente).");
        }
        moveWithRetry(tmp, appJar);
    }

    private static Process launch(String java, Path appJar, Path workDir, Path stateDir) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(java, "-jar", appJar.toString())
                .directory(workDir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(stateDir.resolve("app.log").toFile()));
        return pb.start();
    }

    private static Process relaunch(String java, Path appJar, Path workDir, Path stateDir) {
        try {
            Process p = launch(java, appJar, workDir, stateDir);
            log("PDV reaberto (pid " + p.pid() + ").");
            return p;
        } catch (IOException e) {
            log("Não foi possível reabrir o PDV: " + e);
            return null;
        }
    }

    /** Retorna null se o novo app confirmou a partida; senão, o motivo da falha. */
    private static String awaitStart(Process p, Path marker, String expectedVersion, long timeoutSeconds)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            Properties m = readMarker(marker);
            if (m != null) {
                long markerPid = Long.parseLong(m.getProperty("pid", "-1"));
                boolean ours = markerPid == p.pid()
                        || p.descendants().anyMatch(h -> h.pid() == markerPid);
                if (ours) {
                    String version = m.getProperty("version", "");
                    if (!version.equals(expectedVersion)) {
                        return "O app iniciado reporta a versão '" + version
                                + "', mas era esperada '" + expectedVersion + "'.";
                    }
                    return null;
                }
            }
            if (!p.isAlive()) {
                return "O novo app encerrou antes de iniciar (código " + p.exitValue() + ").";
            }
            Thread.sleep(500);
        }
        return "O novo app não confirmou a partida em " + timeoutSeconds + "s.";
    }

    private static Properties readMarker(Path marker) {
        if (!Files.isRegularFile(marker)) {
            return null;
        }
        try (Reader in = Files.newBufferedReader(marker, StandardCharsets.UTF_8)) {
            Properties p = new Properties();
            p.load(in);
            return p.getProperty("pid") == null ? null : p;
        } catch (IOException | RuntimeException e) {
            return null; // arquivo ainda sendo escrito; tenta de novo
        }
    }

    private static boolean stayAlive(Process p, long seconds) {
        try {
            return !p.waitFor(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return p.isAlive();
        }
    }

    private static void killTree(Process p) {
        if (p == null) {
            return;
        }
        p.descendants().forEach(ProcessHandle::destroyForcibly);
        p.destroyForcibly();
        try {
            p.waitFor(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // -----------------------------------------------------------------
    //  Utilitários
    // -----------------------------------------------------------------

    private static void writeResult(Path stateDir, String status, String target, String previous, String message) {
        Properties p = new Properties();
        p.setProperty("status", status);
        p.setProperty("targetVersion", target);
        p.setProperty("previousVersion", previous);
        p.setProperty("message", message == null ? "" : message);
        p.setProperty("timestamp", LocalDateTime.now().toString());
        Path tmp = stateDir.resolve(RESULT_FILE + ".tmp");
        try {
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                p.store(w, "Resultado da ultima atualizacao");
            }
            Files.move(tmp, stateDir.resolve(RESULT_FILE),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log("Não foi possível gravar o resultado: " + e);
        }
    }

    private static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                md.update(buf, 0, n);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String req(Properties job, String key) {
        String v = job.getProperty(key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Campo ausente na tarefa: " + key);
        }
        return v;
    }

    private static void log(String message) {
        String line = LocalDateTime.now() + " [updater] " + message;
        System.out.println(line);
        Path file = logFile;
        if (file != null) {
            try (OutputStream out = Files.newOutputStream(file,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                out.write((line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // log é melhor-esforço
            }
        }
    }

    /** Remove a pasta temporária de extração (só se for a criada pelo app). */
    private static void cleanupWorkDir(Path jobFile) {
        Path dir = jobFile.toAbsolutePath().getParent();
        if (dir == null || !dir.getFileName().toString().startsWith("scopel-updater-")) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // melhor-esforço
        }
    }
}
