param(
    [Parameter(Mandatory=$true)][string]$Original,
    [Parameter(Mandatory=$true)][string]$RoundTrip,
    [long]$Focus = 8911369
)
$ErrorActionPreference = 'Stop'
$inputPath = (Resolve-Path -LiteralPath $Original).Path
$roundtripPath = (Resolve-Path -LiteralPath $RoundTrip).Path
$before = (Get-FileHash -LiteralPath $inputPath).Hash
$roundtripBefore = (Get-FileHash -LiteralPath $roundtripPath).Hash
# Reuse extraction from the verified local game; never include these JARs in the SDK.
& "$PSScriptRoot\run.ps1" --safe-mode --validate-only
if ($LASTEXITCODE -ne 0) { throw 'Perfil/runtime local nao validado' }
$cache = Join-Path $PSScriptRoot 'cache\2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c'
$classpath = ((Get-ChildItem -LiteralPath $cache -Filter '*.jar' | ForEach-Object FullName) -join ';')
$output = Join-Path $PSScriptRoot ('build\save-analysis-' + [guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($output) | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tools\save-analysis') -Filter '*.java' | ForEach-Object FullName)
& javac --release 17 -encoding UTF-8 -cp $classpath -d $output @sources
if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar sondas proprias' }
& java -Xmx1536m -cp "$output;$classpath" CompareSaveGraphs --self-test
if ($LASTEXITCODE -ne 0) { throw 'Autoteste do comparador falhou' }
& java -Xmx1536m -cp "$output;$classpath" CompareSaveGraphs $inputPath $roundtripPath > (Join-Path $output 'graph-comparison.txt')
$comparisonExit = $LASTEXITCODE
& java -Xmx1536m -cp "$output;$classpath" TraceSaveFields $inputPath (Join-Path $output 'probe.s22') $Focus > (Join-Path $output 'field-trace.txt')
if ($LASTEXITCODE -ne 0) { throw 'Sonda de campo falhou' }
$probeHash = (Get-FileHash -LiteralPath (Join-Path $output 'probe.s22')).Hash
if ((Get-FileHash -LiteralPath $inputPath).Hash -ne $before -or (Get-FileHash -LiteralPath $roundtripPath).Hash -ne $roundtripBefore) { throw 'Input mudou durante analise; relatorio invalido' }
Get-Content -LiteralPath (Join-Path $output 'graph-comparison.txt')
Get-Content -LiteralPath (Join-Path $output 'field-trace.txt')
Write-Host "Input preservado. Hash da sonda: $probeHash; igual ao round-trip: $($probeHash -eq $roundtripBefore)"
Write-Host "Relatorios privados: $output"
if ($comparisonExit -ne 0) { throw "Comparacao incompleta ou divergente (exit $comparisonExit); nao afirmar equivalencia" }
if ($probeHash -ne $roundtripBefore) { throw 'Sonda nao reproduziu o round-trip; instrumentacao nao corroborada' }
