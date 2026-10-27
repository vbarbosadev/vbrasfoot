param(
    [Parameter(Mandatory=$true)][string]$Sdk,
    [string]$Output = (Join-Path $PSScriptRoot 'dist')
)
$ErrorActionPreference = 'Stop'
$sdkPath = (Resolve-Path -LiteralPath $Sdk).Path
$work = Join-Path $PSScriptRoot ('build\' + [guid]::NewGuid().ToString('N'))
$classes = Join-Path $work 'classes'
$tests = Join-Path $work 'tests'
[IO.Directory]::CreateDirectory($classes) | Out-Null
[IO.Directory]::CreateDirectory($tests) | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src') -Recurse -Filter '*.java' | ForEach-Object FullName)
& javac --release 17 -encoding UTF-8 -cp $sdkPath -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar mod de patrocinio contra SDK publico' }
$testSources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'test') -Recurse -Filter '*.java' | ForEach-Object FullName)
& javac --release 17 -encoding UTF-8 -cp "$sdkPath;$classes" -d $tests @testSources
if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar testes do mod de patrocinio' }
& java '-Djava.awt.headless=true' -cp "$sdkPath;$classes;$tests" br.vbrasfoot.sponsormanager.SponsorStoreTest
if ($LASTEXITCODE -ne 0) { throw 'Testes do modelo/codec/logo falharam' }
& java '-Djava.awt.headless=true' -cp "$sdkPath;$classes;$tests" br.vbrasfoot.sponsormanager.SponsorPanelLayoutTest
if ($LASTEXITCODE -ne 0) { throw 'Teste de layout do painel falhou' }
& java '-Djava.awt.headless=true' -cp "$sdkPath;$classes;$tests" br.vbrasfoot.sponsormanager.SponsorClubLookupTest
if ($LASTEXITCODE -ne 0) { throw 'Teste de paginação e identificação do clube falhou' }
& java '-Djava.awt.headless=true' -cp "$sdkPath;$classes;$tests" br.vbrasfoot.sponsormanager.SponsorActivationNoticeTest
if ($LASTEXITCODE -ne 0) { throw 'Teste de mensagem de credito falhou' }
& java '-Djava.awt.headless=true' -cp "$sdkPath;$classes;$tests" br.vbrasfoot.sponsormanager.SponsorOptionalMessageTest
if ($LASTEXITCODE -ne 0) { throw 'Teste de logo opcional e texto editavel falhou' }
$dist = [IO.Path]::GetFullPath($Output)
[IO.Directory]::CreateDirectory($dist) | Out-Null
$jarPath = Join-Path $dist 'sponsor-manager.jar'
if (Test-Path -LiteralPath $jarPath) { throw "JAR ja existe; revise-o antes de sobrescrever: $jarPath" }
& jar --create --file $jarPath -C $classes . -C $PSScriptRoot vbrasfoot.mod.properties -C $PSScriptRoot LICENSE
if ($LASTEXITCODE -ne 0) { throw 'Falha ao empacotar mod de patrocinio' }
Write-Host "Mod criado: $jarPath"
