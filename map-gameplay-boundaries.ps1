param(
    [string]$GameDir = (Join-Path $PSScriptRoot '..\Brasfoot22-23'),
    [string]$Output
)
$ErrorActionPreference = 'Stop'
$executable = Join-Path (Resolve-Path -LiteralPath $GameDir).Path 'bf22-23.exe'
if (-not $Output) { $Output = Join-Path $PSScriptRoot ('build\gameplay-boundaries-' + [guid]::NewGuid().ToString('N') + '.tsv') }
$outputPath = [IO.Path]::GetFullPath($Output)
if (Test-Path -LiteralPath $outputPath) { throw 'Escolha saida nova; nenhuma sobrescrita.' }
foreach ($path in @($executable, $outputPath)) {
    for ($p = $path; $p; $p = Split-Path $p -Parent) {
        if ((Test-Path -LiteralPath $p) -and ((Get-Item -LiteralPath $p -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Link/junction recusado.' }
    }
}
if ((Get-FileHash -LiteralPath $executable).Hash -ne '2FA65476A92611419CE28C0CFD158A0C7F741AF235AA988B0BED00C44443BB1C') { throw 'Perfil desconhecido; inspecao recusada.' }
$classes = Join-Path $PSScriptRoot ('build\gameplay-tool-' + [guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($classes) | Out-Null
& javac --release 17 -encoding UTF-8 -d $classes (Join-Path $PSScriptRoot 'tools\hook-analysis\FindCareerReferences.java') (Join-Path $PSScriptRoot 'tools\hook-analysis\MapGameplayBoundaries.java') (Join-Path $PSScriptRoot 'tools\hook-analysis\MapGameplayBoundariesSelfTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar mapa target-free.' }
& java -cp $classes MapGameplayBoundariesSelfTest
if ($LASTEXITCODE -ne 0) { throw 'Testes do mapa gameplay falharam.' }
& java -cp $classes MapGameplayBoundaries $executable $outputPath
if ($LASTEXITCODE -ne 0) { throw 'Mapa gameplay recusado.' }
Write-Host "Mapa privado somente leitura: $outputPath"
Write-Host 'Nao executa jogo, nao altera saldo/mercado e nao comprova eventos ou comandos. Nao publique TSV/bytecode privados.'
