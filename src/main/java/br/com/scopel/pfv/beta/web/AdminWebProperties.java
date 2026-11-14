package br.com.scopel.pfv.beta.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AdminWebProperties {

    private final boolean enabled;
    private final String bind;
    private final int port;
    private final String keystorePath;
    private final String keystorePassword;
    private final boolean secureCookie;
    private final boolean trustForwardedFor;

    public AdminWebProperties(@Value("${admin.web.enabled:false}") boolean enabled,
                              @Value("${admin.web.bind:127.0.0.1}") String bind,
                              @Value("${admin.web.port:8443}") int port,
                              @Value("${admin.web.keystore:}") String keystorePath,
                              @Value("${admin.web.keystore-password:}") String keystorePassword,
                              @Value("${admin.web.secure-cookie:false}") boolean secureCookie,
                              @Value("${admin.web.trust-forwarded-for:false}") boolean trustForwardedFor) {
        this.enabled = enabled;
        this.bind = bind;
        this.port = port;
        this.keystorePath = keystorePath;
        this.keystorePassword = keystorePassword;
        this.secureCookie = secureCookie;
        this.trustForwardedFor = trustForwardedFor;
    }

    public boolean enabled() {
        return enabled;
    }

    public String bind() {
        return bind;
    }

    public int port() {
        return port;
    }

    public boolean httpsConfigured() {
        return keystorePath != null && !keystorePath.isBlank();
    }

    public String keystorePath() {
        return keystorePath;
    }

    public String keystorePassword() {
        return keystorePassword;
    }

    public boolean secureCookie() {
        return secureCookie || httpsConfigured();
    }

    public boolean trustForwardedFor() {
        return trustForwardedFor;
    }
}
