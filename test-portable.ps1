param([Parameter(Mandatory=$true)][string]$Package, [string]$GameDir = (Join-Path $PSScriptRoot '..\Brasfoot22-23'))
$ErrorActionPreference = 'Stop'
$packageRoot = (Resolve-Path -LiteralPath $Package).Path
$fixture = Join-Path $env:LOCALAPPDATA ('Temp\opencode\vbrasfoot-install-' + [guid]::NewGuid().ToString('N'))
$game = Join-Path $fixture ('Jogo com espacos e acento ' + [char]0x00e7)
[IO.Directory]::CreateDirectory($game) | Out-Null
Copy-Item -LiteralPath (Join-Path $GameDir 'bf22-23.exe') -Destination $game
& (Join-Path $packageRoot 'install.ps1') -GameDir $game -Preview
if (Test-Path -LiteralPath (Join-Path $game 'vbrasfoot')) { throw 'Preview escreveu na fixture' }
& (Join-Path $packageRoot 'install.ps1') -GameDir $game
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $game 'vbrasfoot\app\launch.ps1') --validate-only
if ($LASTEXITCODE -ne 0) { throw 'Validacao instalada falhou' }
[IO.Directory]::CreateDirectory((Join-Path $game 'vbrasfoot\data')) | Out-Null
[IO.Directory]::CreateDirectory((Join-Path $game 'vbrasfoot\mods')) | Out-Null
Set-Content -LiteralPath (Join-Path $game 'vbrasfoot\data\preservar.txt') -Value 'preservar'
Set-Content -LiteralPath (Join-Path $game 'vbrasfoot\mods\broken.jar') -Value 'invalid'
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $game 'vbrasfoot\app\launch.ps1') --safe-mode --validate-only
if ($LASTEXITCODE -ne 0) { throw 'Modo seguro instalado falhou' }
& (Join-Path $game 'vbrasfoot-safe.cmd') --validate-only
if ($LASTEXITCODE -ne 0) { throw 'Launcher CMD seguro falhou' }
& (Join-Path $packageRoot 'install.ps1') -GameDir $game
# Do not overwrite user-edited managed files.
$managedFile = Join-Path $game 'vbrasfoot.cmd'
$originalBytes = [IO.File]::ReadAllBytes($managedFile)
[IO.File]::AppendAllText($managedFile, "`r`nrem user edit")
$rejected = $false
try { & (Join-Path $packageRoot 'install.ps1') -GameDir $game -Preview } catch { $rejected = $true }
if (-not $rejected) { throw 'Arquivo modificado foi aceito' }
[IO.File]::WriteAllBytes($managedFile, $originalBytes)
# A running loader lock must prevent updates even without a matching process name.
$lock = [IO.File]::Open((Join-Path $game 'vbrasfoot\.runtime.lock'), 'Open', 'ReadWrite', 'None')
$rejected = $false
try { try { & (Join-Path $packageRoot 'install.ps1') -GameDir $game } catch { $rejected = $true } }
finally { $lock.Dispose() }
if (-not $rejected) { throw 'Atualizacao aceitou lock ativo' }
$moved = Join-Path $fixture 'Jogo movido'
Move-Item -LiteralPath $game -Destination $moved
$game = $moved
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $game 'vbrasfoot\app\launch.ps1') --safe-mode --validate-only
if ($LASTEXITCODE -ne 0) { throw 'Validacao apos mover falhou' }
& (Join-Path $game 'vbrasfoot\app\uninstall.ps1')
if (-not (Test-Path -LiteralPath (Join-Path $game 'vbrasfoot.cmd'))) { throw 'Preview removeu launcher' }
& (Join-Path $game 'vbrasfoot\app\uninstall.ps1') -Apply
if (-not (Test-Path -LiteralPath (Join-Path $game 'vbrasfoot\data\preservar.txt'))) { throw 'Dado removido' }
if (Test-Path -LiteralPath (Join-Path $game 'vbrasfoot.cmd')) { throw 'Launcher nao removido' }
if ((Get-FileHash -LiteralPath (Join-Path $game 'bf22-23.exe')).Hash -ne (Get-FileHash -LiteralPath (Join-Path $GameDir 'bf22-23.exe')).Hash) { throw 'EXE alterado' }
$linkGame = Join-Path $fixture 'junction-game'
$protected = Join-Path $fixture 'protected-original-resources'
[IO.Directory]::CreateDirectory($linkGame) | Out-Null
[IO.Directory]::CreateDirectory($protected) | Out-Null
Copy-Item -LiteralPath (Join-Path $GameDir 'bf22-23.exe') -Destination $linkGame
New-Item -ItemType Junction -Path (Join-Path $linkGame 'vbrasfoot') -Target $protected | Out-Null
$rejected = $false
try { & (Join-Path $packageRoot 'install.ps1') -GameDir $linkGame -Preview } catch { $rejected = $true }
if (-not $rejected) { throw 'Instalador aceitou junction' }
& java -jar (Join-Path $packageRoot 'app\vbrasfoot.jar') --game-dir $linkGame --home (Join-Path $linkGame 'vbrasfoot') --validate-only
if ($LASTEXITCODE -eq 0) { throw 'Loader aceitou junction' }
if (@(Get-ChildItem -LiteralPath $protected -Force).Count -ne 0) { throw 'Escrita atraves de junction' }
$global:LASTEXITCODE = 0
Write-Host "PASS: fixture portable (sem gameplay nem saves). Evidencias: $fixture"
