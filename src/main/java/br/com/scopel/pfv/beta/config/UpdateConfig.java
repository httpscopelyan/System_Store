package br.com.scopel.pfv.beta.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configurações do sistema de atualização remota (prefixo "app.update.*").
 *
 * O PDV consulta o version.json no GitHub; se houver versão mais nova, o
 * operador pode "Atualizar e reiniciar": o app baixa e valida o JAR, abre um
 * atualizador separado, fecha, o atualizador troca o JAR e reabre o PDV.
 */
@Configuration
@ConfigurationProperties(prefix = "app.update")
@Getter
@Setter
public class UpdateConfig {

    /** Habilita ou desabilita a verificação automática de atualizações. */
    private boolean enabled = true;

    /** URL do version.json (GitHub Raw). */
    private String versionUrl =
            "https://raw.githubusercontent.com/httpscopelyan/System_Store/main/version.json";

    /** Intervalo entre verificações automáticas, em minutos. */
    private int checkIntervalMinutes = 60;

    /**
     * Pasta de estado do atualizador (download, marcador de partida, resultado,
     * log). Relativa ao diretório de trabalho do app. Fica FORA do JAR.
     */
    private String downloadDir = "updates";

    /**
     * Diretório de trabalho do app (onde ficam .env, updates/ e backup/ e onde o
     * app é reaberto). Vazio = diretório atual do processo.
     */
    private String workDir = "";

    /** Pasta dos backups do JAR anterior (relativa ao diretório de trabalho). */
    private String backupDir = "backup";

    /** Quantos backups manter. */
    private int backupsToKeep = 3;

    /**
     * Versão em execução. Vem do pom.xml via filtro Maven (@project.version@) —
     * única fonte da verdade, para o app nunca "achar" que está desatualizado
     * depois de atualizar. Valor inválido (ex.: IDE sem filtro) desliga a atualização.
     */
    private String currentVersion = "0.0.0";

    /** Caminho do JAR em execução; vazio = detectar pelo class path (java -jar). */
    private String appJar = "";

    /** Exige https no download (só desligar em testes). */
    private boolean requireHttps = true;

    /** Hosts (ou domínios) aceitos para o download. */
    private List<String> allowedHosts = List.of("github.com", "githubusercontent.com");

    /** Quanto o atualizador espera o PDV fechar antes de desistir (segundos). */
    private int waitExitSeconds = 90;

    /** Quanto o atualizador espera o novo PDV confirmar a partida (segundos). */
    private int startTimeoutSeconds = 180;

    /** Prazo total do download (minutos). */
    private int downloadTimeoutMinutes = 15;
}
