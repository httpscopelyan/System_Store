package br.com.scopel.pfv.beta.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configurações do sistema de atualização remota.
 *
 * O PDV consulta o GitHub para verificar se existe uma versão mais recente.
 * Se houver, baixa o novo JAR e avisa o operador.
 *
 * Todas as propriedades estão sob o prefixo "app.update.*" no
 * application.properties.
 */
@Configuration
@ConfigurationProperties(prefix = "app.update")
@Getter
@Setter
public class UpdateConfig {

    /** Habilita ou desabilita a verificação automática de atualizações. */
    private boolean enabled = true;

    /**
     * URL do version.json no GitHub (raw).
     * O app faz GET nessa URL para saber qual é a versão mais recente.
     */
    private String versionUrl =
            "https://raw.githubusercontent.com/httpscopelyan/System_Store/main/version.json";

    /**
     * Intervalo em minutos entre cada verificação automática.
     * Padrão: 60 (1 hora).
     */
    private int checkIntervalMinutes = 60;

    /**
     * Diretório local onde o JAR atualizado é salvo antes de aplicar.
     * Relativo ao diretório de trabalho do app.
     */
    private String downloadDir = "updates";

    /** Versão atual do app — injetada via filtro Maven no build. */
    private String currentVersion = "1.0.0";
}
