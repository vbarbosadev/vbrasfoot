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
if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar mod contra SDK publico' }
$testSources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'test') -Recurse -Filter '*.java' | ForEach-Object FullName)
& javac --release 17 -encoding UTF-8 -cp "$sdkPath;$classes" -d $tests @testSources
if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar testes do mod' }
& java '-Djava.awt.headless=true' -cp "$sdkPath;$classes;$tests" br.exemplo.consulta.ReadOnlyModTest
if ($LASTEXITCODE -ne 0) { throw 'Testes do template falharam' }
$dist = Join-Path $PSScriptRoot 'dist'
[IO.Directory]::CreateDirectory($dist) | Out-Null
$jarPath = Join-Path $dist 'meu-mod.jar'
& jar --create --file $jarPath -C $classes . -C $PSScriptRoot vbrasfoot.mod.properties -C $PSScriptRoot LICENSE
if ($LASTEXITCODE -ne 0) { throw 'Falha ao empacotar mod' }
Write-Host "JAR criado (somente codigo do mod): $jarPath"
