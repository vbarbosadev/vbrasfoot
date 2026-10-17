param([switch]$Apply)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$game = Split-Path $root -Parent
# Fixed allowlist: never trust deletion paths supplied by an editable manifest.
$allowed = @('vbrasfoot\app\vbrasfoot.jar','vbrasfoot\app\LICENSE','vbrasfoot\app\launch.ps1','vbrasfoot\app\uninstall.ps1','vbrasfoot.cmd','vbrasfoot-safe.cmd')
$manifestPath = Join-Path $root 'install-manifest.json'
foreach ($item in @($game, $root, $PSScriptRoot, $manifestPath)) {
    for ($p = $item; $p; $p = Split-Path $p -Parent) {
        if ((Get-Item -LiteralPath $p -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Link/junction recusado' }
    }
}
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$targets = @()
foreach ($entry in $manifest.Files) {
    if ($entry.Path -notin $allowed) { throw 'Manifesto contem caminho nao gerenciado' }
    $target = Join-Path $game $entry.Path
    if (-not (Test-Path -LiteralPath $target)) { continue }
    if ((Get-Item -LiteralPath $target -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Link recusado' }
    if ((Get-FileHash -LiteralPath $target).Hash -ne $entry.SHA256) { throw "Arquivo modificado; preserve e revise: $target" }
    Write-Host "Remover arquivo gerenciado: $target"
    $targets += $target
}
Write-Host 'Mods, configuracoes, dados, cache, logs, backups e saves serao preservados. Use -Apply para confirmar.'
if (-not $Apply) { return }
if (Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'bf22-23.exe' -or ($_.Name -in @('java.exe','javaw.exe') -and $_.CommandLine -like '*vbrasfoot.jar*') }) { throw 'Feche o jogo/loader antes de desinstalar.' }
$lock = [IO.File]::Open((Join-Path $root '.runtime.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
try {
    foreach ($target in $targets) { Remove-Item -LiteralPath $target }
    Remove-Item -LiteralPath $manifestPath
} finally { $lock.Dispose() }
