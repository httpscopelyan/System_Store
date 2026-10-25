# =============================================================
# release.ps1 — Script de release do Scopel Store PDV
# =============================================================
# Uso:
#   .\scripts\release.ps1 -Version 1.2.0
#   .\scripts\release.ps1 -Version 1.2.0 -Message "Correção de pagamento PIX"
#
# O que faz:
#   1. Atualiza a versão no pom.xml
#   2. Commita e cria a tag git (v1.2.0)
#   3. Faz push → GitHub Actions testa, builda (Windows/JDK 25), cria a Release
#      com o JAR + SHA-256 e só então publica o version.json na main
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

# 4. Atualiza versão no pom.xml (fonte única da versão: o app lê dela via filtro Maven)
Write-Host "[1/3] Atualizando pom.xml..." -ForegroundColor Green
$pomContent = Get-Content "pom.xml" -Raw
$pomContent = $pomContent -replace '<version>\d+\.\d+\.\d+(-SNAPSHOT)?</version>(\s*<name)', "<version>$Version</version>`$2"
$pomContent | Out-File -FilePath "pom.xml" -Encoding utf8NoBOM -NoNewline

# 5. Commit + Tag
# O version.json NÃO é editado aqui: o GitHub Actions o publica na main DEPOIS de
# criar a Release, com o SHA-256 e o tamanho do JAR exato (senão os PDVs veriam a
# versão antes de o arquivo existir).
Write-Host "[2/3] Commitando e criando tag v$Version..." -ForegroundColor Green
git add pom.xml
git commit -m "release: v$Version - $Message"
git tag -a "v$Version" -m "$Message"

# 8. Push
Write-Host "[3/3] Fazendo push..." -ForegroundColor Green
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
Write-Host "  3. Publicar o version.json (com SHA-256) na main."
Write-Host "  4. Os PDVs detectarão a atualização e oferecerão 'Atualizar e reiniciar'."
Write-Host ""
