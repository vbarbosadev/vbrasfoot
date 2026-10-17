$ErrorActionPreference = 'Stop'
$installRoot = Split-Path $PSScriptRoot -Parent
$gameRoot = Split-Path $installRoot -Parent
if (Test-Path -LiteralPath (Join-Path $installRoot '.update-pending')) { throw 'Atualizacao incompleta: use recover-update.ps1 antes de iniciar o loader. O EXE original permanece disponivel.' }
$java = Get-Command java -ErrorAction Stop
Push-Location $gameRoot
try {
    & $java.Source '--add-exports=java.desktop/sun.java2d=ALL-UNNAMED' '-Xmx1536m' -jar (Join-Path $PSScriptRoot 'vbrasfoot.jar') --home $installRoot --game-dir $gameRoot @args
    exit $LASTEXITCODE
} finally { Pop-Location }
