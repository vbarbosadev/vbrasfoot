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
if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar mod contra API publica 0.5.0' }
$testSources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'test') -Recurse -Filter '*.java' | ForEach-Object FullName)
& javac --release 17 -encoding UTF-8 -cp "$sdkPath;$classes" -d $tests @testSources
if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar testes' }
& java '-Djava.awt.headless=true' -cp "$sdkPath;$classes;$tests" br.vbrasfoot.careerdashboard.DashboardModelTest
if ($LASTEXITCODE -ne 0) { throw 'Testes falharam' }
$dist = Join-Path $PSScriptRoot 'dist'
[IO.Directory]::CreateDirectory($dist) | Out-Null
$jarPath = Join-Path $dist 'career-dashboard.jar'
& jar --create --file $jarPath -C $classes . -C $PSScriptRoot vbrasfoot.mod.properties -C $PSScriptRoot LICENSE
if ($LASTEXITCODE -ne 0) { throw 'Falha ao empacotar mod' }
$entries = @(& jar --list --file $jarPath)
if ($LASTEXITCODE -ne 0) { throw 'Falha ao inspecionar JAR' }
if ($entries | Where-Object { $_ -match '^br/vbrasfoot/api/|Test.*\.class$' }) { throw 'API/testes indevidos no JAR' }
Write-Host "JAR standalone criado: $jarPath"
