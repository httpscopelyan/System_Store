# =============================================================
# package.ps1 — Gera o app nativo do Scopel Store PDV com jpackage
# =============================================================
# Uso (JDK 24+ no JAVA_HOME; para -Type exe/msi o WiX Toolset precisa estar instalado):
#   .\scripts\package.ps1 -Version 1.0.0 -Jar target\beta-1.0.0.jar                # instalador .exe
#   .\scripts\package.ps1 -Version 1.0.0 -Jar target\beta-1.0.0.jar -Type app-image # pasta pronta, sem WiX
#
# O instalador embute o runtime Java (a máquina do cliente NÃO precisa ter JDK) e instala
# por usuário (sem admin) em %LOCALAPPDATA%\Scopel Store, para o atualizador poder trocar o JAR.
# O nome do JAR dentro do app é sempre scopel-store.jar: a atualização remota o substitui.
# =============================================================

param(
    [Parameter(Mandatory=$true)] [string]$Version,
    [Parameter(Mandatory=$true)] [string]$Jar,
    [ValidateSet("exe", "msi", "app-image")] [string]$Type = "exe",
    [string]$OutDir = "dist"
)

$ErrorActionPreference = "Stop"

if ($Version -notmatch '^\d+\.\d+\.\d+$') { throw "Versão deve ser X.Y.Z" }
if (-not (Test-Path $Jar)) { throw "JAR não encontrado: $Jar" }

$stage = Join-Path $OutDir "stage"
Remove-Item $stage -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $stage | Out-Null
Copy-Item $Jar (Join-Path $stage "scopel-store.jar")
Remove-Item (Join-Path $OutDir "Scopel Store*") -Recurse -Force -ErrorAction SilentlyContinue

# Módulos do runtime embutido. O JAR do Spring Boot é "fat", então o jdeps não enxerga o que ele usa:
# java.se (java.desktop p/ impressão, java.sql...), jdk.unsupported, jdk.crypto.ec (TLS),
# jdk.localedata (formatação pt-BR), jdk.management/net/naming.dns/zipfs/charsets (Spring/Hikari/Postgres).
$modules = "java.se,jdk.unsupported,jdk.crypto.ec,jdk.localedata,jdk.management,jdk.net,jdk.naming.dns,jdk.zipfs,jdk.charsets"

$args = @(
    "--type", $Type,
    "--name", "Scopel Store",
    "--app-version", $Version,
    "--vendor", "Scopel",
    "--input", $stage,
    "--main-jar", "scopel-store.jar",
    "--main-class", "org.springframework.boot.loader.launch.JarLauncher",
    "--dest", $OutDir,
    "--add-modules", $modules,
    # O padrão do jpackage inclui --strip-native-commands, que remove runtime\bin\java.exe/javaw.exe.
    # O atualizador precisa deles para rodar em processo separado: por isso a lista é explícita, sem essa flag.
    "--jlink-options", "--strip-debug --no-man-pages --no-header-files",
    "--java-options", "-Dfile.encoding=UTF-8",
    "--java-options", "--enable-native-access=ALL-UNNAMED",
    # $APPDIR é expandido pelo launcher (aspas simples: o PowerShell não pode expandir). O .env fica
    # na pasta de instalação, ao lado do .exe — independente do diretório atual de quem abriu o app.
    "--java-options", '-Dspring.config.import=optional:file:$APPDIR/../.env[.properties]',
    "--java-options", '-Dpdv.bootstrap.env-file=$APPDIR/../.env'
)
if ($Type -ne "app-image") {
    $args += @("--win-per-user-install", "--win-menu", "--win-shortcut",
               "--win-upgrade-uuid", "6f1d3c52-7a4e-4b8a-9c2d-5e0b8f3a1d47")
}

& jpackage @args
if ($LASTEXITCODE -ne 0) { throw "jpackage falhou (código $LASTEXITCODE)" }
Remove-Item $stage -Recurse -Force
Write-Host "Pronto em: $OutDir" -ForegroundColor Green
