# Sistema de Atualização Remota — Scopel Store PDV

Atualização **completa**: detecta, baixa, valida, fecha o PDV, troca o JAR, reabre,
confirma que a versão nova subiu e, se não subir, restaura a anterior.

## Como o PDV é distribuído

JAR executável do Spring Boot (`java -jar scopel-store.jar`), **não** `jpackage`.
Por isso o updater é um processo separado que troca o arquivo `.jar` depois que o PDV fecha.
Requer JDK 24+ (JavaFX 26). O updater usa o **mesmo `java`** do PDV em execução.

Estrutura numa máquina de cliente (diretório de trabalho = onde o PDV é iniciado):

```
scopel-store.jar      ← único arquivo substituído pela atualização
.env                  ← preservado
updates/              ← estado do updater (log, marcador, resultado) — preservado
backup/               ← últimos 3 JARs anteriores
```

Dados do negócio ficam no PostgreSQL (fora da pasta); configuração do operador fica no
banco (`app_setting`) e no `.env`. Nada disso é tocado pela atualização.

## Fluxo

```
PDV (15s após abrir, depois a cada 60 min)
  └─ GET version.json (GitHub Raw) ── compara SemVer ── mais novo?
        └─ botão "⬆ Atualizar e reiniciar" na barra de status (operador confirma)
              └─ baixa p/ .part → valida HTTP 200, tamanho, SHA-256, é JAR executável
              └─ extrai UpdaterMain para pasta temporária e o executa (processo separado)
              └─ PDV encerra (Platform.exit; System.exit após 15 s se travar)
                    UPDATER:
                    1. trava updater.lock (impede 2 updaters)
                    2. revalida SHA-256 do download
                    3. espera o PID do PDV sair (90 s; senão cancela sem alterar nada)
                    4. backup do JAR atual → backup/
                    5. copia p/ scopel-store.jar.new → valida → ATOMIC_MOVE sobre o JAR
                    6. reabre `java -jar scopel-store.jar` e espera o marcador updates/started.ok
                       (pid do novo processo + versão esperada, até 180 s)
                    7a. OK  → grava SUCCESS
                    7b. falha → mata o novo, restaura backup, grava ROLLED_BACK, reabre o anterior
```

- O JAR em execução **nunca** é sobrescrito pelo próprio PDV.
- O marcador `started.ok` é gravado por `BetaApplication.start()` após `stage.show()`
  (`UpdateService.markStarted`). Se a versão do marcador ≠ a do manifesto (asset errado), é rollback.
- `ROLLED_BACK` bloqueia aquela versão em `updates/blocked-version.txt`: não é oferecida de
  novo até sair uma versão **mais nova** (sem loop de atualização). O operador vê um aviso.
- A atualização só é oferecida se não houver venda em andamento (itens lançados ou em pagamento).

## Integridade do release

`version.json` (publicado pelo GitHub Actions **depois** de a Release existir):

```json
{
  "version": "1.1.0",
  "releaseDate": "2026-10-25",
  "minJavaVersion": 24,
  "changelog": "texto da mensagem da tag",
  "mandatory": false,
  "sha256": "<64 hex do scopel-store.jar anexado>",
  "size": 58422324,
  "downloadUrl": "https://github.com/<repo>/releases/download/v1.1.0/scopel-store.jar"
}
```

- URL **versionada** (nunca `latest/download`), `https` obrigatório, host `github.com`/`*.githubusercontent.com`.
- `sha256` obrigatório; sem ele a versão não é oferecida. Nenhum JAR incompleto ou com hash
  diferente é instalado nem executado (checado no download **e** de novo no updater).
- Nenhuma credencial do GitHub vai no app (repositório público; só leitura anônima).
- **Limite conhecido:** o hash vem do mesmo repositório que o JAR; ele protege contra download
  corrompido/truncado e asset trocado, mas não contra quem controla o repositório. Assinatura
  de código seria o próximo passo.

## Como publicar uma versão

```powershell
.\scripts\release.ps1 -Version 1.1.0 -Message "Correção no pagamento PIX"
```

Atualiza o `pom.xml` (única fonte da versão; o app a lê via filtro Maven em
`app.update.current-version=@project.version@`), cria a tag `v1.1.0` e dá push. O workflow
(`.github/workflows/release.yml`, **Windows + JDK 25**) roda os testes do updater, builda,
anexa `scopel-store.jar` + `.sha256` e só então publica o `version.json` na `main`.

## Configuração (`app.update.*`)

| Propriedade | Padrão | Função |
|---|---|---|
| `enabled` | `true` | liga/desliga |
| `version-url` | GitHub Raw `main/version.json` | manifesto |
| `check-interval-minutes` | `60` | intervalo |
| `download-dir` | `updates` | estado do updater |
| `backup-dir` | `backup` | backups |
| `backups-to-keep` | `3` | retenção |
| `work-dir` | (vazio = cwd) | base de `updates/`, `backup/` e do reinício |
| `wait-exit-seconds` / `start-timeout-seconds` | `90` / `180` | prazos do updater |

## Limitações

- Instalações na **1.0.0** (sem updater) precisam ser atualizadas manualmente uma vez.
- O PDV precisa ser iniciado por `java -jar` (não pela IDE nem `mvn javafx:run`: nesses modos
  o botão informa "modo desenvolvimento" e não aplica nada).
- O reinício usa `java -jar scopel-store.jar` no mesmo diretório; opções extras de JVM do
  atalho original não são repassadas.
- Hoje o relatório de falha é um aviso na tela do operador + `updates/updater.log`.
