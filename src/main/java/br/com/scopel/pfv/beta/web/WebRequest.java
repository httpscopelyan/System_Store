package br.com.scopel.pfv.beta.web;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WebRequest {

    private static final int MAX_BODY_BYTES = 64 * 1024;

    private final String method;
    private final String path;
    private final Map<String, List<String>> parameters;
    private final String cookieHeader;

    private WebRequest(String method, String path, Map<String, List<String>> parameters, String cookieHeader) {
        this.method = method;
        this.path = path;
        this.parameters = parameters;
        this.cookieHeader = cookieHeader;
    }

    public static WebRequest parse(String method, URI uri, InputStream body, String cookieHeader) throws IOException {
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        parse(uri.getRawQuery(), parameters);
        if ("POST".equals(method)) {
            parse(readBody(body), parameters);
        }
        return new WebRequest(method, uri.getPath(), parameters, cookieHeader);
    }

    public String method() {
        return method;
    }

    public String path() {
        return path;
    }

    public String param(String name) {
        List<String> values = parameters.get(name);
        return values == null || values.isEmpty() ? "" : values.get(0);
    }

    public List<String> params(String name) {
        return parameters.getOrDefault(name, List.of());
    }

    public Long longParam(String name) {
        try {
            return Long.parseLong(param(name).trim());
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    public String cookie(String name) {
        if (cookieHeader == null) {
            return null;
        }
        for (String pair : cookieHeader.split(";")) {
            String[] parts = pair.trim().split("=", 2);
            if (parts.length == 2 && parts[0].equals(name)) {
                return parts[1];
            }
        }
        return null;
    }

    private static String readBody(InputStream body) throws IOException {
        byte[] bytes = body.readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) {
            throw new IOException("Corpo da requisição grande demais");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void parse(String encoded, Map<String, List<String>> into) {
        if (encoded == null || encoded.isEmpty()) {
            return;
        }
        for (String pair : encoded.split("&")) {
            String[] parts = pair.split("=", 2);
            String name = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            into.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
        }
    }
}
