param(
    [string]$GameDir = (Join-Path $PSScriptRoot '..\Brasfoot22-23'),
    [string]$Save = (Join-Path $PSScriptRoot '..\Brasfoot22-23\sav\Flamengo.s22')
)
$ErrorActionPreference = 'Stop'
$game = (Resolve-Path -LiteralPath $GameDir).Path
$savePath = (Resolve-Path -LiteralPath $Save).Path
if (Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'bf22-23.exe' -or ($_.Name -in @('java.exe','javaw.exe') -and $_.CommandLine -like '*vbrasfoot.jar*') }) { throw 'Feche o jogo antes de criar a fixture; nenhum processo sera encerrado.' }
$before = (Get-FileHash -LiteralPath $savePath).Hash
$exeHash = (Get-FileHash -LiteralPath (Join-Path $game 'bf22-23.exe')).Hash
if ($exeHash -ne '2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c') { throw 'Perfil desconhecido' }
& "$PSScriptRoot\build.ps1"
if ($LASTEXITCODE -ne 0) { throw 'Build falhou' }
$fixture = Join-Path $env:LOCALAPPDATA ('Temp\opencode\vbrasfoot-hook-io-' + [guid]::NewGuid().ToString('N'))
$copy = Join-Path $fixture 'game'
$loaderData = Join-Path $fixture 'loader'
[IO.Directory]::CreateDirectory((Join-Path $copy 'sav')) | Out-Null
[IO.Directory]::CreateDirectory($loaderData) | Out-Null
Copy-Item -LiteralPath (Join-Path $game 'bf22-23.exe') -Destination $copy
Copy-Item -LiteralPath $savePath -Destination (Join-Path $copy 'sav\input.s22')
Set-Content -LiteralPath (Join-Path $copy '.hook-test-fixture') -Value 'Private fixture: newly named saves only'
if ((Get-FileHash -LiteralPath (Join-Path $copy 'sav\input.s22')).Hash -ne $before) { throw 'Save mudou durante copia' }
$classpath = "$PSScriptRoot\build\classes;$PSScriptRoot\build\tests"
Push-Location $copy
try {
    $ErrorActionPreference = 'Continue' # Legacy failure cases intentionally print stack traces.
    & java '--add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED' '--add-exports=java.desktop/sun.java2d=ALL-UNNAMED' -Xmx1536m -cp $classpath br.vbrasfoot.CareerHookRuntimeTest $copy $loaderData "$PSScriptRoot\dist\career-observer.jar" *> (Join-Path $fixture 'runtime-test.log')
    $code = $LASTEXITCODE
} finally { $ErrorActionPreference = 'Stop'; Pop-Location }
Get-Content -LiteralPath (Join-Path $fixture 'runtime-test.log') -Tail 25
if ((Get-FileHash -LiteralPath $savePath).Hash -ne $before -or (Get-FileHash -LiteralPath (Join-Path $game 'bf22-23.exe')).Hash -ne $exeHash) { throw 'Origem mudou; resultado invalido' }
Write-Host "Fixture privada preservada: $fixture"
if ($code -ne 0) { throw "Teste de hooks falhou: $code" }
