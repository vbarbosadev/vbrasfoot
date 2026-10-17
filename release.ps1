param([string]$Output = (Join-Path $PSScriptRoot 'dist\portable'))
$ErrorActionPreference = 'Stop'
if (Test-Path -LiteralPath $Output) { throw 'Escolha um destino novo para o pacote.' }
& "$PSScriptRoot\build.ps1"
if ($LASTEXITCODE -ne 0) { throw 'Build falhou' }
[IO.Directory]::CreateDirectory((Join-Path $Output 'app')) | Out-Null
Copy-Item -LiteralPath "$PSScriptRoot\dist\vbrasfoot.jar" -Destination (Join-Path $Output 'app\vbrasfoot.jar')
foreach ($name in @('install.ps1', 'uninstall.ps1', 'recover-update.ps1', 'launch.ps1', 'vbrasfoot.cmd', 'vbrasfoot-safe.cmd')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot "packaging\$name") -Destination $Output
}
Copy-Item -LiteralPath "$PSScriptRoot\LICENSE" -Destination (Join-Path $Output 'app\LICENSE')
[IO.Directory]::CreateDirectory((Join-Path $Output 'sdk')) | Out-Null
Copy-Item -LiteralPath "$PSScriptRoot\dist\vbrasfoot-api.jar" -Destination (Join-Path $Output 'sdk\vbrasfoot-api-0.4.0.jar')
Copy-Item -LiteralPath "$PSScriptRoot\roadmap\SDK-CONTRACT.md" -Destination (Join-Path $Output 'sdk\SDK-CONTRACT.md')
Copy-Item -LiteralPath "$PSScriptRoot\roadmap\SCOPED-QUERIES.md" -Destination (Join-Path $Output 'sdk\SCOPED-QUERIES.md')
Copy-Item -LiteralPath "$PSScriptRoot\roadmap\CAREER-OBSERVATIONS.md" -Destination (Join-Path $Output 'sdk\CAREER-OBSERVATIONS.md')
[IO.Directory]::CreateDirectory((Join-Path $Output 'sdk\examples\career-observer')) | Out-Null
Copy-Item -LiteralPath "$PSScriptRoot\dist\career-observer.jar" -Destination (Join-Path $Output 'sdk\examples\career-observer.jar')
foreach ($relative in @('README.md','vbrasfoot.mod.properties','src\br\vbrasfoot\observer\CareerObserverMod.java')) {
    $target = Join-Path $Output "sdk\examples\career-observer\$relative"
    [IO.Directory]::CreateDirectory((Split-Path $target -Parent)) | Out-Null
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot "examples\career-observer\$relative") -Destination $target
}
Copy-Item -LiteralPath "$PSScriptRoot\LICENSE" -Destination (Join-Path $Output 'sdk\examples\career-observer\LICENSE')
& javadoc --release 17 -encoding UTF-8 -quiet '-Xdoclint:all,-missing' -d (Join-Path $Output 'sdk\javadoc') -sourcepath (Join-Path $PSScriptRoot 'src') br.vbrasfoot.api
if ($LASTEXITCODE -ne 0) { throw 'Falha ao gerar Javadocs publicos' }
# Fixed template sources only: never copy its build/dist/lib/private outputs.
$template = Join-Path $Output 'sdk\template-read-only'
foreach ($relative in @('README.md','LICENSE','build.ps1','install.ps1','vbrasfoot.mod.properties','.gitignore',
        'src\br\exemplo\consulta\ReadOnlyMod.java','test\br\exemplo\consulta\ReadOnlyModTest.java')) {
    $target = Join-Path $template $relative
    [IO.Directory]::CreateDirectory((Split-Path $target -Parent)) | Out-Null
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot "templates\read-only-mod\$relative") -Destination $target
}
$hashes = @{}
Get-ChildItem -LiteralPath $Output -File -Recurse | ForEach-Object {
    $hashes[$_.FullName.Substring(([IO.Path]::GetFullPath($Output)).TrimEnd('\').Length + 1)] = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
}
$hashes | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $Output 'package-hashes.json') -Encoding UTF8
Write-Host "Pacote experimental sem runtime Java ou arquivos do jogo: $Output"
