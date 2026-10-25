package br.com.scopel.pfv.beta.update;

import br.com.scopel.pfv.beta.config.UpdateConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Comparação SemVer e validação do manifesto version.json (puro, sem Spring). */
class SemVerAndManifestTests {

    private static final String SHA = "a".repeat(64);
    private final ObjectMapper mapper = new ObjectMapper();

    private static boolean newer(String a, String b) {
        return SemVer.parse(a).isNewerThan(SemVer.parse(b));
    }

    @Test
    void comparesNumericallyNotLexically() {
        assertTrue(newer("1.10.0", "1.9.9"));
        assertTrue(newer("2.0.0", "1.99.99"));
        assertTrue(newer("1.0.1", "1.0.0"));
        assertFalse(newer("1.0.0", "1.0.0"));
        assertFalse(newer("1.0.0", "1.0.1"));
    }

    @Test
    void preReleaseIsOlderThanFinalAndOrdered() {
        assertTrue(newer("1.2.0", "1.2.0-beta"));
        assertFalse(newer("1.2.0-beta", "1.2.0"));
        assertTrue(newer("1.2.0-beta.2", "1.2.0-beta.1"));
        assertTrue(newer("1.2.0-beta.10", "1.2.0-beta.2"));
        assertTrue(newer("1.2.0-rc", "1.2.0-beta"));
        assertTrue(newer("1.2.0-beta.1", "1.2.0-beta"));
    }

    @Test
    void ignoresBuildMetadataAndPrefixV() {
        assertFalse(newer("1.0.0+build.5", "1.0.0"));
        assertFalse(newer("v1.0.0", "1.0.0"));
        assertTrue(newer("v1.0.1", "1.0.0"));
    }

    @Test
    void rejectsInvalidVersions() {
        for (String bad : new String[]{"", "1", "1.2", "a.b.c", "1.2.3.4", "1.2.x", "1.0.0-", null}) {
            assertTrue(SemVer.tryParse(bad).isEmpty(), "deveria rejeitar: " + bad);
        }
    }

    private UpdateConfig config() {
        return new UpdateConfig();
    }

    private UpdateInfo parse(String json, UpdateConfig cfg) throws Exception {
        return UpdateInfo.fromJson(mapper.readTree(json), cfg);
    }

    private String manifest(String url, String sha) {
        return """
                {"version":"1.1.0","downloadUrl":"%s","sha256":%s,"size":10,
                 "changelog":"x","mandatory":false,"releaseDate":"2026-10-25","minJavaVersion":24}
                """.formatted(url, sha == null ? "null" : "\"" + sha + "\"");
    }

    @Test
    void acceptsValidManifest() throws Exception {
        UpdateInfo info = parse(manifest("https://github.com/o/r/releases/download/v1.1.0/scopel-store.jar", SHA), config());
        assertEquals("1.1.0", info.version());
        assertEquals(SHA, info.sha256());
        assertEquals(10, info.size());
        assertEquals(24, info.minJavaVersion());
    }

    @Test
    void rejectsManifestWithoutChecksum() {
        assertThrows(IllegalArgumentException.class,
                () -> parse(manifest("https://github.com/x.jar", null), config()));
        assertThrows(IllegalArgumentException.class,
                () -> parse(manifest("https://github.com/x.jar", "123"), config()));
    }

    @Test
    void rejectsPlainHttpAndForeignHosts() {
        assertThrows(IllegalArgumentException.class,
                () -> parse(manifest("http://github.com/x.jar", SHA), config()));
        assertThrows(IllegalArgumentException.class,
                () -> parse(manifest("https://evil.example.com/x.jar", SHA), config()));
        assertThrows(IllegalArgumentException.class,
                () -> parse(manifest("https://notgithub.com/x.jar", SHA), config()));
    }

    @Test
    void acceptsGithubReleaseCdnHost() throws Exception {
        UpdateInfo info = parse(manifest("https://objects.githubusercontent.com/x.jar", SHA), config());
        assertEquals("1.1.0", info.version());
    }

    @Test
    void allowsPlainHttpOnlyWhenExplicitlyConfigured() throws Exception {
        UpdateConfig cfg = config();
        cfg.setRequireHttps(false);
        cfg.setAllowedHosts(List.of("localhost"));
        assertEquals("1.1.0", parse(manifest("http://localhost:8080/x.jar", SHA), cfg).version());
    }

    @Test
    void rejectsInvalidVersionField() {
        assertThrows(IllegalArgumentException.class, () -> parse("""
                {"version":"abc","downloadUrl":"https://github.com/x.jar","sha256":"%s"}
                """.formatted(SHA), config()));
    }
}
