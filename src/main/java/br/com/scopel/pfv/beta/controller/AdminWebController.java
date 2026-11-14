package br.com.scopel.pfv.beta.controller;

import br.com.scopel.pfv.beta.web.AdminWebServer;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Optional;

@Component
public class AdminWebController {

    private final AdminWebServer webServer;

    @FXML private Label statusLabel;
    @FXML private Label addressLabel;
    @FXML private Label guideLabel;

    public AdminWebController(AdminWebServer webServer) {
        this.webServer = webServer;
    }

    public void refresh() {
        AdminWebServer.Status status = webServer.status();
        String scheme = status.https() ? "https" : "http";
        if (!status.enabled()) {
            statusLabel.setText("Painel web DESLIGADO. Para ligar, coloque ADMIN_WEB_ENABLED=true no arquivo .env e reinicie o sistema.");
            addressLabel.setText("");
        } else if (!status.running()) {
            statusLabel.setText("Painel web LIGADO na configuração, mas não conseguiu iniciar. Veja o log (porta em uso?).");
            addressLabel.setText("");
        } else {
            statusLabel.setText("Painel web ATIVO. Entre com o usuário e a senha da conta DEV.");
            addressLabel.setText("Neste computador: " + scheme + "://localhost:" + status.port()
                    + lanAddress().map(ip -> "\nNa rede da loja: " + scheme + "://" + ip + ":" + status.port()
                    + ("127.0.0.1".equals(status.bind()) ? "  (só funciona com ADMIN_WEB_BIND=0.0.0.0)" : "")).orElse(""));
        }
        guideLabel.setText("Para acessar de fora da loja use um túnel com HTTPS (Cloudflare Tunnel ou Tailscale Funnel) "
                + "apontando para esta porta, ou informe um certificado em ADMIN_WEB_KEYSTORE. "
                + "Nunca exponha a porta em HTTP puro: a senha da conta DEV trafegaria sem proteção. "
                + "Detalhes no GUIA-DE-USO.md.");
    }

    private Optional<String> lanAddress() {
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp() || network.isLoopback() || network.isVirtual()) {
                    continue;
                }
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        return Optional.of(address.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }
}
