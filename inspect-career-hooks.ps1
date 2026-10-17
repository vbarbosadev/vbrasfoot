param([string]$Executable = (Join-Path $PSScriptRoot '..\Brasfoot22-23\bf22-23.exe'))
$ErrorActionPreference = 'Stop'
$executablePath = (Resolve-Path -LiteralPath $Executable).Path
$output = Join-Path $PSScriptRoot ('build\hook-analysis-' + [guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($output) | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tools\hook-analysis') -Filter '*.java' | ForEach-Object FullName)
& javac --release 17 -encoding UTF-8 -d $output @sources
if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar inventario proprio' }
# Reads classfile/Code only after verifying the full EXE hash; never loads a game class.
& java -cp $output InspectCareerHooks $executablePath (Join-Path $output 'fingerprints.properties')
if ($LASTEXITCODE -ne 0) { throw 'Artefato/candidatos de hook recusados' }
& java -cp $output FindCareerReferences $executablePath (Join-Path $output 'career-references.tsv')
if ($LASTEXITCODE -ne 0) { throw 'Inventario de callers incompleto/falhou' }
& javap -classpath $executablePath -c -p -s c.a | Set-Content -LiteralPath (Join-Path $output 'career-io-bytecode.txt') -Encoding UTF8
if ($LASTEXITCODE -ne 0) { throw 'Inspecao javap falhou' }
$invalid = Join-Path $output 'unknown-game.exe'
[IO.File]::WriteAllBytes($invalid, [byte[]]@(1,2,3))
$oldPreference = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue'
    & java -cp $output InspectCareerHooks $invalid (Join-Path $output 'must-not-exist.properties') 2> (Join-Path $output 'unknown-profile-error.txt')
    $rejectedExit = $LASTEXITCODE
} finally { $ErrorActionPreference = $oldPreference }
if ($rejectedExit -eq 0 -or (Test-Path -LiteralPath (Join-Path $output 'must-not-exist.properties'))) { throw 'Inventario nao recusou perfil desconhecido' }
$global:LASTEXITCODE = 0
Write-Host "Inspecao estatica aprovada (nao comprova hooks em runtime). Relatorios privados: $output"
