package br.com.scopel.pfv.beta.update;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Gera "PDVs de mentira": JARs executáveis reais (compilados na hora) que gravam o
 * marcador started.ok como o app de verdade faz. Permite testar o ciclo de atualização
 * com processos reais, sem Spring/JavaFX/banco.
 */
final class FakeAppFactory {

    /** Modos de comportamento do app falso. */
    static final String OK = "ok";        // grava o marcador e fica rodando
    static final String CRASH = "crash";  // encerra na hora, sem marcador
    static final String HANG = "hang";    // roda, mas nunca grava o marcador

    private static final String SOURCE = """
            import java.io.*;
            import java.nio.file.*;
            import java.util.Properties;
            public class FakeApp {
                public static void main(String[] a) throws Exception {
                    Properties cfg = new Properties();
                    try (InputStream in = FakeApp.class.getResourceAsStream("/fake.properties")) { cfg.load(in); }
                    String mode = cfg.getProperty("mode");
                    if (mode.equals("crash")) System.exit(3);
                    if (mode.equals("ok")) {
                        Path dir = Path.of("updates");
                        Files.createDirectories(dir);
                        Properties m = new Properties();
                        m.setProperty("pid", Long.toString(ProcessHandle.current().pid()));
                        m.setProperty("version", cfg.getProperty("version"));
                        try (Writer w = Files.newBufferedWriter(dir.resolve("started.ok"))) { m.store(w, null); }
                    }
                    Thread.sleep(120_000);
                }
            }
            """;

    private static byte[] compiledClass;

    private FakeAppFactory() {
    }

    /** Cria um JAR do app falso com a versão e o modo informados. */
    static Path createJar(Path file, String version, String mode) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "FakeApp");
        Properties cfg = new Properties();
        cfg.setProperty("version", version);
        cfg.setProperty("mode", mode);
        try (OutputStream os = Files.newOutputStream(file);
             JarOutputStream jar = new JarOutputStream(os, manifest)) {
            jar.putNextEntry(new JarEntry("FakeApp.class"));
            jar.write(fakeAppClass());
            jar.closeEntry();
            jar.putNextEntry(new JarEntry("fake.properties"));
            cfg.store(jar, null);
            jar.closeEntry();
        }
        return file;
    }

    private static synchronized byte[] fakeAppClass() throws IOException {
        if (compiledClass == null) {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                throw new IllegalStateException("Testes precisam rodar em um JDK (compilador ausente).");
            }
            Path dir = Files.createTempDirectory("fakeapp-src");
            Path src = dir.resolve("FakeApp.java");
            Files.writeString(src, SOURCE);
            int rc = compiler.run(null, null, null, "-d", dir.toString(), src.toString());
            if (rc != 0) {
                throw new IllegalStateException("Falha ao compilar o app falso.");
            }
            compiledClass = Files.readAllBytes(dir.resolve("FakeApp.class"));
        }
        return compiledClass;
    }

    static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = in.readNBytes(Integer.MAX_VALUE - 8);
            return HexFormat.of().formatHex(md.digest(buf));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String javaExecutable() {
        return ProcessHandle.current().info().command()
                .orElseGet(() -> Path.of(System.getProperty("java.home"), "bin", "java").toString());
    }

    /** Inicia o JAR como o PDV de verdade seria iniciado: java -jar, com o diretório de trabalho dado. */
    static Process start(Path jar, Path workDir) throws IOException {
        return new ProcessBuilder(javaExecutable(), "-jar", jar.toString())
                .directory(workDir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    /** Lê o marcador started.ok (ou null). */
    static Properties readMarker(Path stateDir) {
        Path marker = stateDir.resolve("started.ok");
        if (!Files.isRegularFile(marker)) {
            return null;
        }
        try (Reader in = Files.newBufferedReader(marker, StandardCharsets.UTF_8)) {
            Properties p = new Properties();
            p.load(in);
            return p;
        } catch (IOException e) {
            return null;
        }
    }
}
