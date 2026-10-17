param(
    [string]$GameDir = (Join-Path $PSScriptRoot '..\Brasfoot22-23'),
    [string]$Destination = (Join-Path $env:LOCALAPPDATA ('Temp\opencode\vbrasfoot-validation-' + [guid]::NewGuid().ToString('N'))),
    [switch]$Preview
)
$ErrorActionPreference = 'Stop'
function Read-Hash([string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    $sha = [Security.Cryptography.SHA256]::Create()
    try { [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose(); $stream.Dispose() }
}
$game = (Resolve-Path -LiteralPath $GameDir).Path
$destinationPath = [IO.Path]::GetFullPath($Destination)
if (Test-Path -LiteralPath $destinationPath) { throw 'O destino deve ser novo; nenhuma copia existente sera sobrescrita.' }
if ($destinationPath.StartsWith($game.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'A copia de validacao deve ficar fora da pasta original.'
}
$launcher = Join-Path $PSScriptRoot 'dist\vbrasfoot.jar'
if (-not (Test-Path -LiteralPath $launcher)) { throw 'Execute build.ps1 antes de preparar a validacao.' }
$expected = '2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c'
if ((Read-Hash (Join-Path $game 'bf22-23.exe')) -ne $expected) {
    throw 'Executavel desconhecido; copia recusada.'
}
# Reject reparse points before recursive traversal, including directory junctions.
function Get-SafeEntries([string]$Directory) {
    $item = Get-Item -LiteralPath $Directory -Force
    if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Link/junction recusado: $Directory" }
    foreach ($entry in Get-ChildItem -LiteralPath $Directory -Force) {
        if ($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Link/junction recusado: $($entry.FullName)" }
        $entry
        if ($entry.PSIsContainer) { Get-SafeEntries $entry.FullName }
    }
}
$entries = @(Get-SafeEntries $game)
$files = @($entries | Where-Object { -not $_.PSIsContainer })
$bytes = ($files | Measure-Object -Property Length -Sum).Sum
Write-Host "Origem (somente leitura): $game"
Write-Host "Destino novo: $destinationPath"
Write-Host "Arquivos: $($files.Count); bytes do jogo: $bytes"
Write-Host 'A preparacao nao abre o jogo, nao restaura saves e nao aprova o P0.'
if ($Preview) { return }
if ($destinationPath -notmatch '^[A-Za-z]:\\') { throw 'Use destino local com letra de unidade para validar espaco livre.' }
$drive = [IO.DriveInfo]::new([IO.Path]::GetPathRoot($destinationPath))
if ($drive.AvailableFreeSpace -lt ($bytes + 256MB)) { throw 'Espaco livre insuficiente para copia e margem inicial de diagnostico.' }
# Existing ancestors must not redirect writes through junctions/symlinks.
$ancestor = Split-Path $destinationPath -Parent
while ($ancestor) {
    if (Test-Path -LiteralPath $ancestor) {
        if ((Get-Item -LiteralPath $ancestor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) {
            throw "Link/junction no destino: $ancestor"
        }
    }
    $ancestor = Split-Path $ancestor -Parent
}
if (Get-CimInstance Win32_Process | Where-Object {
    $_.Name -eq 'bf22-23.exe' -or ($_.Name -in @('java.exe', 'javaw.exe') -and $_.CommandLine -like '*vbrasfoot.jar*')
}) { throw 'Feche as sessoes do jogo/loader antes da copia. Nenhum processo sera encerrado.' }
New-Item -ItemType Directory -Path $destinationPath | Out-Null
$copy = Join-Path $destinationPath 'Brasfoot22-23'
$loaderHome = Join-Path $destinationPath 'loader'
New-Item -ItemType Directory -Path $loaderHome | Out-Null
if (-not ('Vbrasfoot.Validation.VerifiedGameCopy' -as [type])) {
    Add-Type -Path (Join-Path $PSScriptRoot 'tools\VerifiedGameCopy.cs')
}
Write-Host 'Copiando e verificando hashes com concorrencia limitada (4 arquivos)...'
$baseline = [Vbrasfoot.Validation.VerifiedGameCopy]::Copy($game, $copy)
Write-Host "Copiados e verificados: $($baseline.Count)"
Copy-Item -LiteralPath $launcher -Destination (Join-Path $loaderHome 'vbrasfoot.jar')
New-Item -ItemType Directory -Path (Join-Path $loaderHome 'mods') | Out-Null
if (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'mods\inspector.jar')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'mods\inspector.jar') -Destination (Join-Path $loaderHome 'mods\inspector.jar')
}
# Independent validation launchers; the development checkout is not needed after preparation.
$command = '@echo off' + "`r`n" + 'cd /d "%~dp0Brasfoot22-23"' + "`r`n" +
    'java --add-exports=java.desktop/sun.java2d=ALL-UNNAMED -Xmx1536m -jar "%~dp0loader\vbrasfoot.jar" --home "%~dp0loader" --game-dir "%~dp0Brasfoot22-23"'
($command + ' %*' + "`r`n" + 'pause') | Set-Content -LiteralPath (Join-Path $destinationPath 'normal.cmd') -Encoding ASCII
($command + ' --safe-mode %*' + "`r`n" + 'pause') | Set-Content -LiteralPath (Join-Path $destinationPath 'safe.cmd') -Encoding ASCII
('@echo off' + "`r`n" + 'cd /d "%~dp0Brasfoot22-23"' + "`r`n" + 'start "" "bf22-23.exe"') |
    Set-Content -LiteralPath (Join-Path $destinationPath 'original.cmd') -Encoding ASCII
# Completion manifest is published only after all files and launchers exist.
$manifestTemporary = Join-Path $destinationPath 'baseline.json.tmp'
[pscustomobject]@{ Source = $game; Created = (Get-Date).ToString('o'); Files = $baseline } |
    ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $manifestTemporary -Encoding UTF8
Move-Item -LiteralPath $manifestTemporary -Destination (Join-Path $destinationPath 'baseline.json')
Write-Host "Copia completa verificada: $destinationPath"
Write-Host 'Use o roteiro em roadmap/P0-VALIDACAO.md. normal.cmd usa apenas o inspetor; safe.cmd ignora mods.'
