package br.com.scopel.pfv.beta.service;

import br.com.scopel.pfv.beta.config.UpdateConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;

/**
 * Serviço de atualização remota do PDV Scopel Store.
 *
 * Fluxo:
 *   1. Na inicialização, agenda verificação periódica.
 *   2. Consulta version.json hospedado no GitHub (raw).
 *   3. Compara a versão remota com a versão local.
 *   4. Se houver atualização, baixa o novo JAR para o diretório "updates/".
 *   5. Notifica o controller/UI para exibir aviso ao operador.
 *
 * O GitHub serve como "servidor de atualização" — sem custo, sem infra extra.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateService {

    private final UpdateConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Resultado da última verificação de atualização. */
    private volatile UpdateInfo latestUpdate = null;

    /** Timer para verificação periódica. */
    private Timer scheduler;

    // ---------------------------------------------------------------
    //  Inicialização
    // ---------------------------------------------------------------

    @PostConstruct
    public void init() {
        if (!config.isEnabled()) {
            log.info("[Update] Verificação de atualizações desabilitada.");
            return;
        }

        log.info("[Update] Verificação de atualizações habilitada.");
        log.info("[Update] Versão atual: {}", config.getCurrentVersion());
        log.info("[Update] URL de manifesto: {}", config.getVersionUrl());
        log.info("[Update] Intervalo de verificação: {} min", config.getCheckIntervalMinutes());

        // Cria diretório de download se não existir.
        try {
            Files.createDirectories(Path.of(config.getDownloadDir()));
        } catch (IOException e) {
            log.warn("[Update] Não foi possível criar diretório de download: {}", e.getMessage());
        }

        // Agenda verificação periódica.
        scheduler = new Timer("update-checker", true);
        long intervalMs = (long) config.getCheckIntervalMinutes() * 60 * 1000;

        scheduler.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkForUpdatesQuietly();
            }
        }, 15_000, intervalMs);  // Primeira verificação 15s após o boot.
    }

    // ---------------------------------------------------------------
    //  API pública
    // ---------------------------------------------------------------

    /**
     * Verifica se existe atualização disponível (async).
     * Retorna o UpdateInfo se houver versão nova, ou null se estiver atualizado.
     */
    public CompletableFuture<UpdateInfo> checkForUpdates() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return doCheck();
            } catch (Exception e) {
                log.error("[Update] Falha ao verificar atualizações: {}", e.getMessage());
                return null;
            }
        });
    }

    /**
     * Baixa o JAR da versão mais recente para o diretório de updates.
     * Retorna o Path do arquivo baixado.
     */
    public CompletableFuture<Path> downloadUpdate() {
        return CompletableFuture.supplyAsync(() -> {
            if (latestUpdate == null) {
                throw new IllegalStateException("Nenhuma atualização disponível para download.");
            }
            try {
                return doDownload(latestUpdate);
            } catch (Exception e) {
                log.error("[Update] Falha no download: {}", e.getMessage());
                throw new RuntimeException("Erro ao baixar atualização", e);
            }
        });
    }

    /** Retorna a informação da última atualização encontrada, ou null. */
    public UpdateInfo getLatestUpdate() {
        return latestUpdate;
    }

    /** Retorna true se há atualização disponível já verificada. */
    public boolean isUpdateAvailable() {
        return latestUpdate != null;
    }

    /** Retorna a versão atual do app. */
    public String getCurrentVersion() {
        return config.getCurrentVersion();
    }

    // ---------------------------------------------------------------
    //  Lógica interna
    // ---------------------------------------------------------------

    /** Verificação silenciosa — chamada pelo timer. */
    private void checkForUpdatesQuietly() {
        try {
            UpdateInfo info = doCheck();
            if (info != null) {
                log.info("[Update] ✅ Nova versão disponível: {} (atual: {})",
                        info.version, config.getCurrentVersion());
            } else {
                log.debug("[Update] App está na versão mais recente.");
            }
        } catch (Exception e) {
            log.warn("[Update] Erro na verificação automática: {}", e.getMessage());
        }
    }

    /**
     * Faz GET no version.json do GitHub e compara versões.
     */
    private UpdateInfo doCheck() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getVersionUrl()))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.warn("[Update] GitHub retornou HTTP {}", response.statusCode());
            return null;
        }

        JsonNode root = objectMapper.readTree(response.body());

        String remoteVersion = root.get("version").asText();
        String downloadUrl   = root.get("downloadUrl").asText();
        String changelog      = root.has("changelog") ? root.get("changelog").asText() : "";
        boolean mandatory     = root.has("mandatory") && root.get("mandatory").asBoolean();
        String releaseDate    = root.has("releaseDate") ? root.get("releaseDate").asText() : "";

        if (isNewerVersion(remoteVersion, config.getCurrentVersion())) {
            latestUpdate = new UpdateInfo(
                    remoteVersion, downloadUrl, changelog, mandatory, releaseDate
            );
            return latestUpdate;
        }

        latestUpdate = null;
        return null;
    }

    /**
     * Baixa o JAR da URL informada no UpdateInfo.
     */
    private Path doDownload(UpdateInfo info) throws IOException, InterruptedException {
        log.info("[Update] Baixando versão {} de: {}", info.version, info.downloadUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(info.downloadUrl))
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IOException("Download falhou com HTTP " + response.statusCode());
        }

        Path downloadDir = Path.of(config.getDownloadDir());
        Files.createDirectories(downloadDir);

        Path targetFile = downloadDir.resolve("scopel-store-" + info.version + ".jar");

        try (InputStream in = response.body()) {
            Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }

        log.info("[Update] ✅ Download concluído: {}", targetFile);
        return targetFile;
    }

    /**
     * Compara duas versões semânticas (ex: "1.2.3" > "1.2.0").
     * Retorna true se 'remote' for maior que 'current'.
     */
    static boolean isNewerVersion(String remote, String current) {
        if (remote == null || current == null) return false;

        // Remove prefixo 'v' se houver.
        remote  = remote.replace("v", "").trim();
        current = current.replace("v", "").trim();

        String[] rParts = remote.split("\\.");
        String[] cParts = current.split("\\.");

        int length = Math.max(rParts.length, cParts.length);
        for (int i = 0; i < length; i++) {
            int r = i < rParts.length ? parseSegment(rParts[i]) : 0;
            int c = i < cParts.length ? parseSegment(cParts[i]) : 0;
            if (r > c) return true;
            if (r < c) return false;
        }
        return false;
    }

    private static int parseSegment(String segment) {
        // Remove sufixos como "-SNAPSHOT", "-beta", etc.
        String numeric = segment.replaceAll("[^0-9]", "");
        return numeric.isEmpty() ? 0 : Integer.parseInt(numeric);
    }

    // ---------------------------------------------------------------
    //  DTO de informação de atualização
    // ---------------------------------------------------------------

    /**
     * Dados sobre uma atualização disponível.
     */
    public record UpdateInfo(
            String version,
            String downloadUrl,
            String changelog,
            boolean mandatory,
            String releaseDate
    ) {}
}
