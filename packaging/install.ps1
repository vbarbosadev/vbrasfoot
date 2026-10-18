param(
    [Parameter(Mandatory=$true)][string]$GameDir,
    [switch]$Preview,
    [ValidateRange(268435456, 9223372036854775807)][long]$ReserveBytes = 268435456
)
$ErrorActionPreference = 'Stop'
$game = (Resolve-Path -LiteralPath $GameDir).Path
function Assert-PlainPath([string]$Path) {
    for ($p = $Path; $p; $p = Split-Path $p -Parent) {
        if ((Test-Path -LiteralPath $p) -and ((Get-Item -LiteralPath $p -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw "Link/junction recusado: $p" }
    }
}
Assert-PlainPath $game
$root = Join-Path $game 'vbrasfoot'
Assert-PlainPath $root
if ((Get-FileHash -LiteralPath (Join-Path $game 'bf22-23.exe') -Algorithm SHA256).Hash -ne '2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c') { throw 'Versao do jogo desconhecida' }
$mapping = [ordered]@{
    'app\vbrasfoot.jar' = 'vbrasfoot\app\vbrasfoot.jar'
    'app\LICENSE' = 'vbrasfoot\app\LICENSE'
    'launch.ps1' = 'vbrasfoot\app\launch.ps1'
    'uninstall.ps1' = 'vbrasfoot\app\uninstall.ps1'
    'recover-update.ps1' = 'vbrasfoot\app\recover-update.ps1'
    'vbrasfoot.cmd' = 'vbrasfoot.cmd'
    'vbrasfoot-safe.cmd' = 'vbrasfoot-safe.cmd'
}
$packageHashes = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'package-hashes.json') -Raw | ConvertFrom-Json
$manifestPath = Join-Path $root 'install-manifest.json'
$transaction = Join-Path $root '.update-pending'
Assert-PlainPath $transaction
if (Test-Path -LiteralPath $transaction) { throw 'Atualizacao incompleta preservada. Execute recover-update.ps1 -GameDir <pasta> (preview), depois -Apply; nao apague o journal.' }
Assert-PlainPath $manifestPath
$old = if (Test-Path -LiteralPath $manifestPath) { Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json } else { $null }
foreach ($source in $mapping.Keys) {
    $target = Join-Path $game $mapping[$source]
    Assert-PlainPath $target
    if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot $source) -PathType Leaf)) { throw "Pacote incompleto: $source" }
    if ((Get-FileHash -LiteralPath (Join-Path $PSScriptRoot $source) -Algorithm SHA256).Hash -ne $packageHashes.$source) { throw "Hash do pacote divergente: $source" }
    if (Test-Path -LiteralPath $target) {
        $managed = @($old.Files | Where-Object { $_.Path -eq $mapping[$source] })
        if ($managed.Count -ne 1 -or (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $managed[0].SHA256) { throw "Arquivo nao gerenciado ou modificado: $target" }
    }
    Write-Host "Instalar/atualizar: $target"
}
if ($Preview) { return }
$start = [Diagnostics.ProcessStartInfo]::new((Get-Command java -ErrorAction Stop).Source, '-version')
$start.UseShellExecute = $false
$start.RedirectStandardError = $true
$probe = [Diagnostics.Process]::Start($start)
try { $javaVersion = $probe.StandardError.ReadToEnd(); $probe.WaitForExit(); if ($probe.ExitCode -ne 0) { throw 'Falha ao consultar Java' } }
finally { $probe.Dispose() }
if ($javaVersion -notmatch 'version "(\d+)\.' -or [int]$Matches[1] -lt 17) { throw 'Instale um runtime Java 17+; JDK nao e exigido pelo launcher.' }
if (Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'bf22-23.exe' -or ($_.Name -in @('java.exe','javaw.exe') -and $_.CommandLine -like '*vbrasfoot.jar*') }) { throw 'Feche o jogo/loader antes de instalar. Nenhum processo sera encerrado.' }
if ($game -notmatch '^[A-Za-z]:\\') { throw 'Instalacao exige unidade local.' }
$managedBytes = ($mapping.Keys | ForEach-Object { (Get-Item -LiteralPath (Join-Path $PSScriptRoot $_)).Length } | Measure-Object -Sum).Sum
if ([IO.DriveInfo]::new([IO.Path]::GetPathRoot($game)).AvailableFreeSpace -lt ([decimal]$ReserveBytes + 3 * $managedBytes)) { throw 'Espaco livre insuficiente para staging, geracao anterior e reserva.' }
[IO.Directory]::CreateDirectory($root) | Out-Null
$lock = [IO.File]::Open((Join-Path $root '.runtime.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
try {
    # Stage all sources and preserve the previous managed generation before applying any file.
    [IO.Directory]::CreateDirectory($transaction) | Out-Null
    $records = @()
    $index = 0
    foreach ($source in $mapping.Keys) {
        $target = Join-Path $game $mapping[$source]
        $before = $null
        if (Test-Path -LiteralPath $target) {
            [IO.File]::Copy($target, (Join-Path $transaction "$index.before"), $false)
            $before = (Get-FileHash -LiteralPath $target).Hash
            if ((Get-FileHash -LiteralPath (Join-Path $transaction "$index.before")).Hash -ne $before) { throw 'Arquivo mudou durante staging' }
        }
        [IO.File]::Copy((Join-Path $PSScriptRoot $source), (Join-Path $transaction "$index.after"), $false)
        $after = (Get-FileHash -LiteralPath (Join-Path $transaction "$index.after")).Hash
        if ($after -ne $packageHashes.$source) { throw 'Pacote mudou durante staging' }
        $records += [pscustomobject]@{ Path = $mapping[$source]; Before = $before; After = $after; Index = $index }
        $index++
    }
    $manifestBefore = $null
    if (Test-Path -LiteralPath $manifestPath) {
        [IO.File]::Copy($manifestPath, (Join-Path $transaction 'manifest.before'), $false)
        $manifestBefore = (Get-FileHash -LiteralPath $manifestPath).Hash
    }
    $nextManifest = [pscustomobject]@{ Version = '0.9.0'; Experimental = $true; Files = @($records | ForEach-Object { [pscustomobject]@{ Path = $_.Path; SHA256 = $_.After } }) }
    $nextManifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $transaction 'manifest.after') -Encoding UTF8
    [pscustomobject]@{ Format = 1; Files = $records; ManifestBefore = $manifestBefore; ManifestAfter = (Get-FileHash -LiteralPath (Join-Path $transaction 'manifest.after')).Hash } |
        ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $transaction 'journal.json.tmp') -Encoding UTF8
    [IO.File]::Move((Join-Path $transaction 'journal.json.tmp'), (Join-Path $transaction 'journal.json'))
    $files = @()
    $index = 0
    foreach ($source in $mapping.Keys) {
        $target = Join-Path $game $mapping[$source]
        [IO.Directory]::CreateDirectory((Split-Path $target -Parent)) | Out-Null
        $temporary = $target + '.' + [guid]::NewGuid().ToString('N') + '.tmp'
        try {
            [IO.File]::Copy((Join-Path $transaction "$index.after"), $temporary, $false)
            if ((Get-FileHash -LiteralPath $temporary).Hash -ne $records[$index].After) { throw 'Hash divergente durante instalacao' }
            if (Test-Path -LiteralPath $target) {
                $previous = $temporary + '.previous'
                [IO.File]::Replace($temporary, $target, $previous)
                Remove-Item -LiteralPath $previous
            } else { [IO.File]::Move($temporary, $target) }
        } finally { if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary } }
        $files += [pscustomobject]@{ Path = $mapping[$source]; SHA256 = (Get-FileHash -LiteralPath $target).Hash }
        $index++
    }
    $tempManifest = $manifestPath + '.tmp'
    [IO.File]::Copy((Join-Path $transaction 'manifest.after'), $tempManifest, $false)
    if (Test-Path -LiteralPath $manifestPath) {
        $previous = $tempManifest + '.' + [guid]::NewGuid().ToString('N') + '.previous'
        [IO.File]::Replace($tempManifest, $manifestPath, $previous)
        Remove-Item -LiteralPath $previous
    } else { [IO.File]::Move($tempManifest, $manifestPath) }
    # Exact transaction-owned paths only; never recursively delete a user directory.
    foreach ($record in $records) {
        foreach ($suffix in @('before', 'after')) {
            $owned = Join-Path $transaction "$($record.Index).$suffix"
            if (Test-Path -LiteralPath $owned) { Remove-Item -LiteralPath $owned }
        }
    }
    foreach ($name in @('manifest.before','manifest.after','journal.json')) {
        $owned = Join-Path $transaction $name
        if (Test-Path -LiteralPath $owned) { Remove-Item -LiteralPath $owned }
    }
    [IO.Directory]::Delete($transaction, $false)
} catch {
    Write-Warning 'Falha de instalacao: arquivos e journal preservados. Nao abra o loader antes da recuperacao explicita.'
    throw
} finally { $lock.Dispose() }
Write-Host 'Instalado. Dados e mods existentes preservados. Abra vbrasfoot.cmd; P0 continua pendente.'
