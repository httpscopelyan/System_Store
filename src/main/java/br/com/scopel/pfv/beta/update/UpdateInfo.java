package br.com.scopel.pfv.beta.update;

import br.com.scopel.pfv.beta.config.UpdateConfig;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Uma atualização publicada no version.json. Só existe se o manifesto for
 * íntegro: versão SemVer válida, URL https de host permitido e SHA-256 presente.
 */
public record UpdateInfo(
        String version,
        URI downloadUrl,
        String sha256,
        long size,
        String changelog,
        boolean mandatory,
        String releaseDate,
        int minJavaVersion
) {

    private static final Pattern SHA256 = Pattern.compile("^[0-9a-fA-F]{64}$");

    /** Lê e valida o manifesto; lança IllegalArgumentException se for inválido. */
    public static UpdateInfo fromJson(JsonNode root, UpdateConfig config) {
        String version = SemVer.parse(text(root, "version")).toString();

        URI uri = parseUri(text(root, "downloadUrl"));
        validateUrl(uri, config);

        String sha = text(root, "sha256").trim().toLowerCase(Locale.ROOT);
        if (!SHA256.matcher(sha).matches()) {
            throw new IllegalArgumentException("sha256 inválido no manifesto.");
        }

        long size = root.path("size").asLong(-1);
        if (root.has("size") && size <= 0) {
            throw new IllegalArgumentException("size inválido no manifesto.");
        }

        int minJava = root.path("minJavaVersion").asInt(0);
        return new UpdateInfo(version, uri, sha, size,
                root.path("changelog").asText(""),
                root.path("mandatory").asBoolean(false),
                root.path("releaseDate").asText(""),
                minJava);
    }

    private static String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || node.asText().isBlank()) {
            throw new IllegalArgumentException("Campo obrigatório ausente no manifesto: " + field);
        }
        return node.asText();
    }

    private static URI parseUri(String text) {
        try {
            return URI.create(text.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("downloadUrl inválida no manifesto.");
        }
    }

    private static void validateUrl(URI uri, UpdateConfig config) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (config.isRequireHttps() ? !scheme.equals("https") : !(scheme.equals("https") || scheme.equals("http"))) {
            throw new IllegalArgumentException("downloadUrl precisa usar https.");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean allowed = config.getAllowedHosts().stream()
                .map(h -> h.toLowerCase(Locale.ROOT))
                .anyMatch(h -> host.equals(h) || host.endsWith("." + h));
        if (!allowed) {
            throw new IllegalArgumentException("Host de download não permitido: " + host);
        }
    }
}
