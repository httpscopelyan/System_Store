package br.com.scopel.pfv.beta.web;

import java.util.ArrayList;
import java.util.List;

public record WebResponse(int status, String contentType, String body, String location, List<String> cookies) {

    public static WebResponse html(String body) {
        return new WebResponse(200, "text/html; charset=utf-8", body, null, List.of());
    }

    public static WebResponse css(String body) {
        return new WebResponse(200, "text/css; charset=utf-8", body, null, List.of());
    }

    public static WebResponse redirect(String location) {
        return new WebResponse(303, "text/plain; charset=utf-8", "", location, List.of());
    }

    public static WebResponse text(int status, String body) {
        return new WebResponse(status, "text/plain; charset=utf-8", body, null, List.of());
    }

    public WebResponse withCookie(String cookie) {
        List<String> all = new ArrayList<>(cookies);
        all.add(cookie);
        return new WebResponse(status, contentType, body, location, all);
    }
}
