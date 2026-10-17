param([Parameter(Mandatory=$true)][string]$Package, [string]$GameDir = (Join-Path $PSScriptRoot '..\Brasfoot22-23'))
$ErrorActionPreference = 'Stop'
$packageRoot = (Resolve-Path -LiteralPath $Package).Path
$fixture = Join-Path $env:LOCALAPPDATA ('Temp\opencode\vbrasfoot-author-template-' + [guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($fixture) | Out-Null
$project = Join-Path $fixture 'my-new-mod'
Copy-Item -LiteralPath (Join-Path $packageRoot 'sdk\template-read-only') -Destination $project -Recurse
$sdk = Join-Path $packageRoot 'sdk\vbrasfoot-api-0.4.6.jar'
# Build from a copied project; it has no paths back to loader source/classes.
& (Join-Path $project 'build.ps1') -Sdk $sdk
if ($LASTEXITCODE -ne 0) { throw 'Template independente falhou' }
& java -jar (Join-Path $packageRoot 'app\vbrasfoot.jar') --validate-mod (Join-Path $project 'dist\meu-mod.jar')
if ($LASTEXITCODE -ne 0) { throw 'Validador standalone recusou template' }
$game = Join-Path $fixture 'game'
[IO.Directory]::CreateDirectory($game) | Out-Null
Copy-Item -LiteralPath (Join-Path $GameDir 'bf22-23.exe') -Destination $game
& (Join-Path $packageRoot 'install.ps1') -GameDir $game
& (Join-Path $project 'install.ps1') -GameDir $game -Preview
if (Test-Path -LiteralPath (Join-Path $game 'vbrasfoot\mods\br.exemplo.consulta.jar')) { throw 'Preview instalou mod' }
$lock = [IO.File]::Open((Join-Path $game 'vbrasfoot\.runtime.lock'), 'Open', 'ReadWrite', 'None')
$rejected = $false
try { try { & (Join-Path $project 'install.ps1') -GameDir $game } catch { $rejected = $true } }
finally { $lock.Dispose() }
if (-not $rejected -or (Test-Path -LiteralPath (Join-Path $game 'vbrasfoot\mods\br.exemplo.consulta.jar'))) { throw 'Sessao ativa nao bloqueou instalacao do mod' }
& (Join-Path $project 'install.ps1') -GameDir $game
$sourceHash = (Get-FileHash -LiteralPath (Join-Path $project 'dist\meu-mod.jar')).Hash
if ((Get-FileHash -LiteralPath (Join-Path $game 'vbrasfoot\mods\br.exemplo.consulta.jar')).Hash -ne $sourceHash) { throw 'JAR instalado divergiu' }
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $game 'vbrasfoot\app\launch.ps1') --validate-only
if ($LASTEXITCODE -ne 0) { throw 'Conjunto instalado invalido' }
$rejected = $false
try { & (Join-Path $project 'install.ps1') -GameDir $game } catch { $rejected = $true }
if (-not $rejected) { throw 'Instalador sobrescreveu mod existente' }
if ((Get-FileHash -LiteralPath (Join-Path $game 'bf22-23.exe')).Hash -ne (Get-FileHash -LiteralPath (Join-Path $GameDir 'bf22-23.exe')).Hash) { throw 'EXE alterado' }
Write-Host "PASS: copiar template, compilar somente SDK, testar, empacotar, validar e instalar sem checkout. Sem gameplay. Fixture: $fixture"
