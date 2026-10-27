$ErrorActionPreference = 'Stop'
$launcher = Join-Path $PSScriptRoot 'dist\vbrasfoot.jar'
if (-not (Test-Path $launcher)) { throw 'Execute .\build.ps1 antes de iniciar.' }
$game = (Resolve-Path (Join-Path $PSScriptRoot '..\Brasfoot22-23')).Path
# O diretorio de trabalho real tambem precisa ser o do jogo; user.dir sozinho nao basta.
Push-Location $game
try {
    $probeArgs = @()
    $gameArgs = @($args | Where-Object { $_ -ne '--pixel-perfect' })
    $displayArgs = @()
    if ($args -contains '--pixel-perfect') { $displayArgs = @('-Dsun.java2d.uiScale=1') }
    if ($args -contains '--probe-career-hooks') { $probeArgs = @('--add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED') }
    & java '--add-exports=java.desktop/sun.java2d=ALL-UNNAMED' @probeArgs @displayArgs -Xmx1536m -jar $launcher --home $PSScriptRoot --game-dir $game @gameArgs
    $code = $LASTEXITCODE
} finally { Pop-Location }
exit $code
