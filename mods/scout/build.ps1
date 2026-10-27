param([string]$Sdk = (Join-Path $PSScriptRoot 'lib\vbrasfoot-api.jar'))
$ErrorActionPreference = 'Stop'
$sdkPath = (Resolve-Path -LiteralPath $Sdk).Path
$output = Join-Path $PSScriptRoot ('build\' + [guid]::NewGuid().ToString('N'))
$classes = Join-Path $output 'classes'
$tests = Join-Path $output 'tests'
[IO.Directory]::CreateDirectory($classes) | Out-Null
[IO.Directory]::CreateDirectory($tests) | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src') -Recurse -Filter '*.java' | ForEach-Object FullName)
& javac --release 17 -encoding UTF-8 -cp $sdkPath -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar scout contra SDK publico' }
$sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'test') -Recurse -Filter '*.java' | ForEach-Object FullName)
& javac --release 17 -encoding UTF-8 -cp "$sdkPath;$classes" -d $tests @sources
if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar testes do scout' }
& java '-Djava.awt.headless=true' -cp "$sdkPath;$classes;$tests" br.vbrasfoot.playerscout.ScoutModelTest
if ($LASTEXITCODE -ne 0) { throw 'Falha nos testes do scout' }
$dist = Join-Path $PSScriptRoot 'dist'
[IO.Directory]::CreateDirectory($dist) | Out-Null
& jar --create --file (Join-Path $dist 'player-scout.jar') -C $classes . -C $PSScriptRoot vbrasfoot.mod.properties -C $PSScriptRoot LICENSE
if ($LASTEXITCODE -ne 0) { throw 'Falha ao empacotar scout' }
