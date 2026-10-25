package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.config.UpdateConfig;
import br.com.scopel.pfv.beta.update.SemVer;
import br.com.scopel.pfv.beta.update.UpdateDownloader;
import br.com.scopel.pfv.beta.update.UpdateInfo;
import br.com.scopel.pfv.updater.UpdaterMain;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Atualização remota do PDV (GitHub Raw + Releases).
 *
 * Ciclo completo:
 *  1. 15 s após o boot e a cada N min, lê o version.json e compara por SemVer.
 *  2. Havendo versão nova, avisa os ouvintes (UI) — o operador escolhe "Atualizar e reiniciar".
 *  3. {@link #applyUpdateAndRestart}: baixa para .part, valida status/tamanho/SHA-256/JAR,
 *     extrai o atualizador para uma pasta temporária, o executa em processo separado e
 *     pede o desligamento do PDV. O JAR em execução nunca é sobrescrito por este processo.
 *  4. O atualizador (UpdaterMain) espera o PDV sair, faz backup, instala, reabre e confirma
 *     a partida pelo marcador {@code started.ok}; se falhar, restaura o backup.
 *  5. Na próxima partida, {@link #ingestUpdaterResult} lê o resultado; uma versão que sofreu
 *     rollback fica bloqueada até sair uma versão ainda mais nova (sem loop de atualização).
 */
@Service
@Slf4j
public class UpdateService {

    private static final String BLOCKED_FILE = "blocked-version.txt";
    private static final String UPDATER_CLASS = "br/com/scopel/pfv/updater/UpdaterMain.class";

    /** Resultado da última tentativa de atualização, lido do atualizador. */
    public record UpdateResult(String status, String targetVersion, String previousVersion, String message) {
        public boolean isSuccess() {
            return "SUCCESS".equals(status);
        }
    }

    private final UpdateConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final List<Consumer<UpdateInfo>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean updateInProgress = new AtomicBoolean(false);

    private volatile UpdateInfo latestUpdate;
    private volatile UpdateResult lastResult;
    private ScheduledExecutorService scheduler;

    public UpdateService(UpdateConfig config) {
        this.config = config;
    }

    // ---------------------------------------------------------------
    //  Ciclo de vida / verificação periódica
    // ---------------------------------------------------------------

    @PostConstruct
    public void init() {
        if (!config.isEnabled()) {
            log.info("[Update] Verificação de atualizações desabilitada.");
            return;
        }
        log.info("[Update] Versão atual: {} | manifesto: {} | intervalo: {} min",
                config.getCurrentVersion(), config.getVersionUrl(), config.getCheckIntervalMinutes());

        try {
            ingestUpdaterResult();
        } catch (RuntimeException e) {
            log.warn("[Update] Não foi possível ler o resultado da última atualização: {}", e.getMessage());
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "update-checker");
            t.setDaemon(true);
            return t;
        });
        // Primeira verificação 15 s após o boot; depois a cada intervalo.
        scheduler.scheduleWithFixedDelay(this::checkQuietly, 15,
                Math.max(1, config.getCheckIntervalMinutes()) * 60L, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void checkQuietly() {
        try {
            checkNow().ifPresentOrElse(
                    info -> log.info("[Update] Nova versão disponível: {} (atual: {})",
                            info.version(), config.getCurrentVersion()),
                    () -> log.debug("[Update] App está na versão mais recente."));
        } catch (Exception e) {
            log.warn("[Update] Falha na verificação automática: {}", e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    //  Verificação
    // ---------------------------------------------------------------

    /** Consulta o manifesto agora. Vazio = nenhuma versão mais nova (ou manifesto não utilizável). */
    public Optional<UpdateInfo> checkNow() throws IOException, InterruptedException {
        ingestUpdaterResult();

        Optional<SemVer> current = SemVer.tryParse(config.getCurrentVersion());
        if (current.isEmpty()) {
            log.warn("[Update] Versão atual inválida ('{}'); atualização desativada.", config.getCurrentVersion());
            latestUpdate = null;
            return Optional.empty();
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(config.getVersionUrl()))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache")
                .GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Manifesto indisponível: HTTP " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        // Compara a versão ANTES de validar o resto: manifesto sem sha256 de uma
        // release antiga não deve gerar erro quando não há nada a atualizar.
        SemVer remote = SemVer.parse(root.path("version").asText(null));
        if (!remote.isNewerThan(current.get())) {
            latestUpdate = null;
            return Optional.empty();
        }

        UpdateInfo info = UpdateInfo.fromJson(root, config);

        if (info.minJavaVersion() > Runtime.version().feature()) {
            log.warn("[Update] Versão {} exige Java {}+ (em uso: {}); não oferecida.",
                    info.version(), info.minJavaVersion(), Runtime.version().feature());
            latestUpdate = null;
            return Optional.empty();
        }
        Optional<SemVer> blocked = blockedVersion();
        if (blocked.isPresent() && !remote.isNewerThan(blocked.get())) {
            log.info("[Update] Versão {} bloqueada (falhou em teste anterior com rollback).", info.version());
            latestUpdate = null;
            return Optional.empty();
        }

        boolean isNew = latestUpdate == null || !latestUpdate.version().equals(info.version());
        latestUpdate = info;
        if (isNew) {
            listeners.forEach(l -> l.accept(info));
        }
        return Optional.of(info);
    }

    // ---------------------------------------------------------------
    //  Aplicação: baixar, validar, delegar ao atualizador e fechar
    // ---------------------------------------------------------------

    /**
     * Baixa, valida e entrega a instalação ao atualizador externo; ao final chama
     * {@code shutdownAction}, que DEVE encerrar o PDV (o atualizador espera o processo sair).
     * Bloqueante — chame fora da thread de UI. Lança exceção (sem alterar nada) se algo falhar antes.
     */
    public void applyUpdateAndRestart(Runnable shutdownAction) throws IOException, InterruptedException {
        applyUpdateAndRestart(ProcessHandle.current().pid(), shutdownAction);
    }

    /** Variante com pid explícito (o atualizador espera ESTE processo encerrar). */
    public void applyUpdateAndRestart(long appPid, Runnable shutdownAction)
            throws IOException, InterruptedException {
        if (!updateInProgress.compareAndSet(false, true)) {
            throw new IllegalStateException("Já existe uma atualização em andamento.");
        }
        boolean handedOver = false;
        try {
            UpdateInfo info = latestUpdate;
            if (info == null) {
                throw new IllegalStateException("Nenhuma atualização disponível.");
            }
            Path appJar = resolveAppJar().orElseThrow(() -> new IllegalStateException(
                    "Atualização automática indisponível: o PDV não está rodando a partir de um JAR "
                            + "(modo desenvolvimento)."));
            if (isUpdaterRunning()) {
                throw new IllegalStateException("Já existe um atualizador em execução.");
            }

            Path stateDir = stateDir();
            Path downloaded = new UpdateDownloader(httpClient,
                    Duration.ofMinutes(config.getDownloadTimeoutMinutes())).download(info, stateDir);
            log.info("[Update] Download verificado: {}", downloaded);

            startUpdater(appPid, appJar, downloaded, info, stateDir);
            handedOver = true;
        } finally {
            if (!handedOver) {
                updateInProgress.set(false);
            }
        }
        shutdownAction.run();
    }

    private void startUpdater(long appPid, Path appJar, Path newJar, UpdateInfo info, Path stateDir)
            throws IOException {
        Path work = Files.createTempDirectory("scopel-updater-");
        try {
            Path classFile = work.resolve(UPDATER_CLASS);
            Files.createDirectories(classFile.getParent());
            try (InputStream in = UpdaterMain.class.getClassLoader().getResourceAsStream(UPDATER_CLASS)) {
                if (in == null) {
                    throw new IOException("Classe do atualizador não encontrada no aplicativo.");
                }
                Files.copy(in, classFile);
            }

            Properties job = new Properties();
            job.setProperty("appPid", Long.toString(appPid));
            job.setProperty("appJar", appJar.toString());
            job.setProperty("newJar", newJar.toString());
            job.setProperty("sha256", info.sha256());
            job.setProperty("newVersion", info.version());
            job.setProperty("oldVersion", config.getCurrentVersion());
            job.setProperty("stateDir", stateDir.toString());
            job.setProperty("backupDir", absolute(config.getBackupDir()).toString());
            job.setProperty("workDir", baseDir().toString());
            job.setProperty("java", javaExecutable());
            job.setProperty("waitExitSeconds", Integer.toString(config.getWaitExitSeconds()));
            job.setProperty("startTimeoutSeconds", Integer.toString(config.getStartTimeoutSeconds()));
            job.setProperty("keepBackups", Integer.toString(config.getBackupsToKeep()));
            Path jobFile = work.resolve("job.properties");
            try (Writer w = Files.newBufferedWriter(jobFile, StandardCharsets.UTF_8)) {
                job.store(w, null);
            }

            Files.createDirectories(stateDir);
            Process p = new ProcessBuilder(javaExecutable(), "-cp", work.toString(),
                    UpdaterMain.class.getName(), jobFile.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(stateDir.resolve("updater-stdout.log").toFile()))
                    .start();
            log.info("[Update] Atualizador iniciado (pid {}) para a versão {}.", p.pid(), info.version());
        } catch (IOException | RuntimeException e) {
            deleteQuietly(work);
            throw e;
        }
    }

    // ---------------------------------------------------------------
    //  Estado em disco (marcador, resultado, versão bloqueada)
    // ---------------------------------------------------------------

    /** Chamado quando a UI subiu: confirma ao atualizador que esta versão iniciou de verdade. */
    public void markStarted() {
        Properties p = new Properties();
        p.setProperty("pid", Long.toString(ProcessHandle.current().pid()));
        p.setProperty("version", config.getCurrentVersion());
        p.setProperty("startedAt", java.time.LocalDateTime.now().toString());
        try {
            Path dir = stateDir();
            Files.createDirectories(dir);
            Path tmp = dir.resolve(UpdaterMain.MARKER_FILE + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                p.store(w, null);
            }
            Files.move(tmp, dir.resolve(UpdaterMain.MARKER_FILE),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("[Update] Não foi possível gravar o marcador de partida: {}", e.getMessage());
        }
    }

    /** Lê (e consome do disco) o resultado deixado pelo atualizador. */
    synchronized void ingestUpdaterResult() {
        Path file = stateDir().resolve(UpdaterMain.RESULT_FILE);
        if (!Files.isRegularFile(file)) {
            return;
        }
        Properties p = new Properties();
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(in);
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("[Update] Resultado de atualização ilegível: {}", e.getMessage());
            return;
        }
        UpdateResult result = new UpdateResult(p.getProperty("status", "FAILED"),
                p.getProperty("targetVersion", ""), p.getProperty("previousVersion", ""),
                p.getProperty("message", ""));
        lastResult = result;
        log.info("[Update] Resultado da última atualização: {} -> {} ({}) {}", result.previousVersion(),
                result.targetVersion(), result.status(), result.message());
        if ("ROLLED_BACK".equals(result.status()) && !result.targetVersion().isBlank()) {
            writeBlockedVersion(result.targetVersion());
        }
    }

    /** Resultado ainda não mostrado ao operador (some depois de lido). */
    public synchronized Optional<UpdateResult> consumeLastResult() {
        ingestUpdaterResult();
        UpdateResult r = lastResult;
        lastResult = null;
        return Optional.ofNullable(r);
    }

    private void writeBlockedVersion(String version) {
        try {
            Files.createDirectories(stateDir());
            Files.writeString(stateDir().resolve(BLOCKED_FILE), version, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("[Update] Não foi possível registrar a versão bloqueada: {}", e.getMessage());
        }
    }

    private Optional<SemVer> blockedVersion() {
        try {
            Path f = stateDir().resolve(BLOCKED_FILE);
            return Files.isRegularFile(f) ? SemVer.tryParse(Files.readString(f, StandardCharsets.UTF_8))
                    : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    // ---------------------------------------------------------------
    //  Auxiliares
    // ---------------------------------------------------------------

    public void addListener(Consumer<UpdateInfo> listener) {
        listeners.add(listener);
    }

    public UpdateInfo getLatestUpdate() {
        return latestUpdate;
    }

    public boolean isUpdateAvailable() {
        return latestUpdate != null;
    }

    public boolean isUpdateInProgress() {
        return updateInProgress.get();
    }

    public String getCurrentVersion() {
        return config.getCurrentVersion();
    }

    /** JAR em execução: configurado ou detectado pelo class path de `java -jar app.jar`. */
    Optional<Path> resolveAppJar() {
        String configured = config.getAppJar();
        String candidate = configured != null && !configured.isBlank()
                ? configured : System.getProperty("java.class.path", "");
        if (candidate.isBlank() || candidate.contains(java.io.File.pathSeparator) && configured.isBlank()) {
            return Optional.empty();
        }
        Path path = absolute(candidate);
        return candidate.toLowerCase().endsWith(".jar") && Files.isRegularFile(path)
                ? Optional.of(path) : Optional.empty();
    }

    /** True se outro atualizador segura a trava (neste ou em outro processo). */
    private boolean isUpdaterRunning() {
        Path lockFile = stateDir().resolve(UpdaterMain.LOCK_FILE);
        if (!Files.exists(lockFile)) {
            return false;
        }
        try (FileChannel ch = FileChannel.open(lockFile, StandardOpenOption.WRITE);
             FileLock lock = ch.tryLock()) {
            return lock == null;
        } catch (OverlappingFileLockException e) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private Path stateDir() {
        return absolute(config.getDownloadDir());
    }

    private Path baseDir() {
        String w = config.getWorkDir();
        return Path.of(w == null || w.isBlank() ? "" : w).toAbsolutePath().normalize();
    }

    private Path absolute(String path) {
        return baseDir().resolve(path).normalize();
    }

    private static String javaExecutable() {
        return ProcessHandle.current().info().command()
                .orElseGet(() -> Path.of(System.getProperty("java.home"), "bin", "java").toString());
    }

    private static void deleteQuietly(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // melhor-esforço
        }
    }
}
