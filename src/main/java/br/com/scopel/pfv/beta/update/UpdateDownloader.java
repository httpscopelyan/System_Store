package br.com.scopel.pfv.beta.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Baixa o JAR de uma atualização para um arquivo temporário (.part), valida
 * status HTTP, tamanho, SHA-256 e estrutura de JAR e só então o publica com o
 * nome final. Um download incompleto ou adulterado nunca fica com nome final.
 */
public class UpdateDownloader {

    private final HttpClient http;
    private final Duration overallTimeout;

    public UpdateDownloader(HttpClient http, Duration overallTimeout) {
        this.http = http;
        this.overallTimeout = overallTimeout;
    }

    public Path download(UpdateInfo info, Path dir) throws IOException, InterruptedException {
        Files.createDirectories(dir);
        Path part = dir.resolve("scopel-store-" + info.version() + ".jar.part");
        Path target = dir.resolve("scopel-store-" + info.version() + ".jar");
        Files.deleteIfExists(part);
        try {
            fetch(info.downloadUrl(), info, part);
            requireValidJar(part);
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return target;
        } catch (IOException | RuntimeException | InterruptedException e) {
            Files.deleteIfExists(part);
            throw e;
        }
    }

    private void fetch(URI uri, UpdateInfo info, Path part) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .GET().build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("Download falhou: HTTP " + response.statusCode());
            }
            long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (info.size() > 0 && declared >= 0 && declared != info.size()) {
                throw new IOException("Tamanho anunciado (" + declared
                        + ") difere do manifesto (" + info.size() + ").");
            }

            // Watchdog: um download travado não pode prender a atualização para sempre.
            Thread watchdog = new Thread(() -> {
                try {
                    Thread.sleep(overallTimeout.toMillis());
                    body.close();
                } catch (InterruptedException | IOException ignored) {
                    // download terminou antes do prazo
                }
            }, "update-download-watchdog");
            watchdog.setDaemon(true);
            watchdog.start();

            MessageDigest digest = sha256();
            long copied = 0;
            try (OutputStream out = Files.newOutputStream(part,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[64 * 1024];
                int n;
                while ((n = body.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                    digest.update(buffer, 0, n);
                    copied += n;
                }
            } finally {
                watchdog.interrupt();
            }

            if (copied == 0) {
                throw new IOException("Download vazio.");
            }
            if (declared >= 0 && copied != declared) {
                throw new IOException("Download incompleto: " + copied + " de " + declared + " bytes.");
            }
            if (info.size() > 0 && copied != info.size()) {
                throw new IOException("Tamanho baixado (" + copied + ") difere do manifesto (" + info.size() + ").");
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            if (!actual.equalsIgnoreCase(info.sha256())) {
                throw new IOException("SHA-256 não confere (esperado " + info.sha256() + ", obtido " + actual + ").");
            }
        }
    }

    static void requireValidJar(Path file) throws IOException {
        try (JarFile jar = new JarFile(file.toFile())) {
            Manifest manifest = jar.getManifest();
            if (manifest == null || manifest.getMainAttributes().getValue("Main-Class") == null) {
                throw new IOException("O arquivo baixado não é um JAR executável.");
            }
        } catch (java.util.zip.ZipException e) {
            throw new IOException("O arquivo baixado não é um JAR válido: " + e.getMessage(), e);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
