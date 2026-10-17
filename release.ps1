param([string]$Output = (Join-Path $PSScriptRoot 'dist\portable'))
$ErrorActionPreference = 'Stop'
if (Test-Path -LiteralPath $Output) { throw 'Escolha um destino novo para o pacote.' }
& "$PSScriptRoot\build.ps1"
if ($LASTEXITCODE -ne 0) { throw 'Build falhou' }
[IO.Directory]::CreateDirectory((Join-Path $Output 'app')) | Out-Null
Copy-Item -LiteralPath "$PSScriptRoot\dist\vbrasfoot.jar" -Destination (Join-Path $Output 'app\vbrasfoot.jar')
foreach ($name in @('install.ps1', 'uninstall.ps1', 'launch.ps1', 'vbrasfoot.cmd', 'vbrasfoot-safe.cmd')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot "packaging\$name") -Destination $Output
}
Copy-Item -LiteralPath "$PSScriptRoot\LICENSE" -Destination (Join-Path $Output 'app\LICENSE')
[IO.Directory]::CreateDirectory((Join-Path $Output 'sdk')) | Out-Null
Copy-Item -LiteralPath "$PSScriptRoot\dist\vbrasfoot-api.jar" -Destination (Join-Path $Output 'sdk\vbrasfoot-api-0.2.0.jar')
Copy-Item -LiteralPath "$PSScriptRoot\roadmap\SDK-CONTRACT.md" -Destination (Join-Path $Output 'sdk\SDK-CONTRACT.md')
$hashes = @{}
Get-ChildItem -LiteralPath $Output -File -Recurse | ForEach-Object {
    $hashes[$_.FullName.Substring(([IO.Path]::GetFullPath($Output)).TrimEnd('\').Length + 1)] = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
}
$hashes | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $Output 'package-hashes.json') -Encoding UTF8
Write-Host "Pacote experimental sem runtime Java ou arquivos do jogo: $Output"
