param([string]$Sdk)
$ErrorActionPreference = 'Stop'
if (-not $Sdk) { $Sdk = Join-Path $PSScriptRoot 'lib\vbrasfoot-api.jar' }
$sdkPath = (Resolve-Path -LiteralPath $Sdk).Path
$output = Join-Path $PSScriptRoot ('build\' + [guid]::NewGuid().ToString('N'))
$classes = Join-Path $output 'classes'
$tests = Join-Path $output 'tests'
[IO.Directory]::CreateDirectory($classes) | Out-Null
[IO.Directory]::CreateDirectory($tests) | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src') -Recurse -Filter '*.java' | ForEach-Object FullName)
& javac --release 17 -encoding UTF-8 -cp $sdkPath -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar observador contra SDK publico' }
$testSources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'test') -Recurse -Filter '*.java' | ForEach-Object FullName)
& javac --release 17 -encoding UTF-8 -cp "$sdkPath;$classes" -d $tests @testSources
if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar testes do observador' }
& java '-Djava.awt.headless=true' -cp "$sdkPath;$classes;$tests" br.vbrasfoot.observer.CareerObserverModTest
if ($LASTEXITCODE -ne 0) { throw 'Testes do observador falharam' }
$dist = Join-Path $PSScriptRoot 'dist'
[IO.Directory]::CreateDirectory($dist) | Out-Null
& jar --create --file (Join-Path $dist 'career-observer.jar') -C $classes . -C $PSScriptRoot vbrasfoot.mod.properties -C $PSScriptRoot LICENSE
if ($LASTEXITCODE -ne 0) { throw 'Falha ao empacotar observador' }
Write-Host "JAR criado (somente codigo do mod): $dist\career-observer.jar"
