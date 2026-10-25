# =============================================================
# release.ps1 — Script de release do Scopel Store PDV
# =============================================================
# Uso:
#   .\scripts\release.ps1 -Version 1.2.0
#   .\scripts\release.ps1 -Version 1.2.0 -Message "Correção de pagamento PIX"
#
# O que faz:
#   1. Atualiza a versão no pom.xml
#   2. Atualiza o version.json
#   3. Commita as mudanças
#   4. Cria a tag git (v1.2.0)
#   5. Faz push da tag → GitHub Actions faz o build e cria a Release
# =============================================================

param(
    [Parameter(Mandatory=$true)]
    [string]$Version,

    [string]$Message = "Release v$Version"
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  SCOPEL STORE — Release v$Version"       -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 1. Valida formato da versão
if ($Version -notmatch '^\d+\.\d+\.\d+$') {
    Write-Host "ERRO: Versão deve estar no formato X.Y.Z (ex: 1.2.0)" -ForegroundColor Red
    exit 1
}

# 2. Verifica se está na branch main
$branch = git branch --show-current
if ($branch -ne "main") {
    Write-Host "ERRO: Você precisa estar na branch 'main' para fazer release." -ForegroundColor Red
    Write-Host "Branch atual: $branch" -ForegroundColor Yellow
    exit 1
}

# 3. Verifica se working tree está limpa
$status = git status --porcelain
if ($status) {
    Write-Host "AVISO: Existem alterações não commitadas:" -ForegroundColor Yellow
    git status --short
    Write-Host ""
    $confirm = Read-Host "Deseja continuar mesmo assim? (s/N)"
    if ($confirm -ne "s") {
        Write-Host "Release cancelada." -ForegroundColor Yellow
        exit 0
    }
}

# 4. Atualiza version.json
Write-Host "[1/5] Atualizando version.json..." -ForegroundColor Green
$releaseDate = Get-Date -Format "yyyy-MM-dd"
$downloadUrl = "https://github.com/httpscopelyan/System_Store/releases/download/v$Version/scopel-store.jar"

$versionJson = @{
    version        = $Version
    releaseDate    = $releaseDate
    minJavaVersion = "21"
    changelog      = $Message
    mandatory      = $false
    downloadUrl    = $downloadUrl
} | ConvertTo-Json -Depth 2

$versionJson | Out-File -FilePath "version.json" -Encoding utf8NoBOM

# 5. Atualiza versão no pom.xml
Write-Host "[2/5] Atualizando pom.xml..." -ForegroundColor Green
$pomContent = Get-Content "pom.xml" -Raw
$pomContent = $pomContent -replace '<version>\d+\.\d+\.\d+(-SNAPSHOT)?</version>([\r\n\s]*<name)', "<version>$Version</version>`$2"
$pomContent | Out-File -FilePath "pom.xml" -Encoding utf8NoBOM -NoNewline

# 6. Atualiza versão no UpdateConfig.java
Write-Host "[3/5] Atualizando currentVersion no UpdateConfig..." -ForegroundColor Green
$configPath = "src\main\java\br\com\scopel\pfv\beta\config\UpdateConfig.java"
if (Test-Path $configPath) {
    $configContent = Get-Content $configPath -Raw
    $configContent = $configContent -replace 'private String currentVersion = "[^"]*"', "private String currentVersion = `"$Version`""
    $configContent | Out-File -FilePath $configPath -Encoding utf8NoBOM -NoNewline
}

# 7. Commit + Tag
Write-Host "[4/5] Commitando e criando tag v$Version..." -ForegroundColor Green
git add version.json pom.xml $configPath
git commit -m "release: v$Version - $Message"
git tag -a "v$Version" -m "$Message"

# 8. Push
Write-Host "[5/5] Fazendo push..." -ForegroundColor Green
git push origin main
git push origin "v$Version"

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  ✅ Release v$Version publicada!"       -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "O GitHub Actions vai:"
Write-Host "  1. Buildar o JAR"
Write-Host "  2. Criar a Release em: https://github.com/httpscopelyan/System_Store/releases"
Write-Host "  3. Os PDVs detectarão a atualização automaticamente."
Write-Host ""
