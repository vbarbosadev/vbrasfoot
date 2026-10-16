$ErrorActionPreference = 'Stop'
$launcher = Join-Path $PSScriptRoot 'dist\vbrasfoot.jar'
if (-not (Test-Path $launcher)) { throw 'Execute .\build.ps1 antes de iniciar.' }
$game = (Resolve-Path (Join-Path $PSScriptRoot '..\Brasfoot22-23')).Path
# O diretorio de trabalho real tambem precisa ser o do jogo; user.dir sozinho nao basta.
Push-Location $game
try {
    & java '--add-exports=java.desktop/sun.java2d=ALL-UNNAMED' -Xmx1536m -jar $launcher --home $PSScriptRoot --game-dir $game @args
    $code = $LASTEXITCODE
} finally { Pop-Location }
exit $code
