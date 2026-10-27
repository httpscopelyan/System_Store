# =============================================================
# build-installer.ps1 — Gera o instalador COMPLETO (app + PostgreSQL + banco vazio + usuário inicial)
# =============================================================
# Uso (JDK 24+ no JAVA_HOME; Inno Setup 6 instalado):
#   winget download PostgreSQL.PostgreSQL.16 --download-directory C:\caminho\_pg
#   .\scripts\build-installer.ps1 -Version 1.0.0 -PgInstaller "C:\caminho\_pg\<instalador>.exe"
#
# O .exe gerado leva o instalador do PostgreSQL: é PRIVADO, não publique (GitHub/nuvem pública).
# A Release pública do GitHub continua só com o JAR de atualização.
# =============================================================

param(
    [Parameter(Mandatory=$true)] [string]$Version,
    [Parameter(Mandatory=$true)] [string]$PgInstaller,
    [string]$Jar = "target\beta-$Version.jar",
    [string]$OutDir = "dist"
)

$ErrorActionPreference = "Stop"

$iscc = @("$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe",
          "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe",
          "$env:ProgramFiles\Inno Setup 6\ISCC.exe") | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $iscc) { throw "Inno Setup 6 não encontrado (winget install JRSoftware.InnoSetup)." }
if (-not (Test-Path $PgInstaller)) { throw "Instalador do PostgreSQL não encontrado: $PgInstaller" }

$image = Join-Path $OutDir "image"
& "$PSScriptRoot\package.ps1" -Version $Version -Jar $Jar -Type app-image -OutDir $image
if ($LASTEXITCODE -ne 0 -and $null -ne $LASTEXITCODE) { throw "package.ps1 falhou" }

$appDir = Join-Path (Resolve-Path $image) "Scopel Store"
& $iscc "/DAppVersion=$Version" "/DAppSourceDir=$appDir" "/DPgInstaller=$((Resolve-Path $PgInstaller).Path)" `
        "/O$((Resolve-Path $OutDir).Path)" "$PSScriptRoot\..\installer\ScopelStore.iss"
if ($LASTEXITCODE -ne 0) { throw "ISCC falhou (código $LASTEXITCODE)" }
Write-Host "Instalador em: $OutDir (PRIVADO: não publicar)" -ForegroundColor Green
