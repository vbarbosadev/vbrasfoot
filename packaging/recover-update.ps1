param([string]$GameDir = (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent), [switch]$Apply, [switch]$Complete)
$ErrorActionPreference = 'Stop'
$game = (Resolve-Path -LiteralPath $GameDir).Path
$root = Join-Path $game 'vbrasfoot'
$transaction = Join-Path $root '.update-pending'
$manifestPath = Join-Path $root 'install-manifest.json'
$allowed = @('vbrasfoot\app\vbrasfoot.jar','vbrasfoot\app\LICENSE','vbrasfoot\app\launch.ps1','vbrasfoot\app\uninstall.ps1','vbrasfoot\app\recover-update.ps1','vbrasfoot.cmd','vbrasfoot-safe.cmd','vbrasfoot-nitido.cmd')
function Assert-PlainPath([string]$Path) {
    for ($p = $Path; $p; $p = Split-Path $p -Parent) {
        if ((Test-Path -LiteralPath $p) -and ((Get-Item -LiteralPath $p -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw "Link/junction recusado: $p" }
    }
}
function Read-Hash([string]$Path) { if (Test-Path -LiteralPath $Path) { (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash } else { $null } }
Assert-PlainPath $transaction
if (-not (Test-Path -LiteralPath $transaction -PathType Container)) { throw 'Nenhuma atualizacao pendente.' }
$journalPath = Join-Path $transaction 'journal.json'
Assert-PlainPath $journalPath
$journal = if (Test-Path -LiteralPath $journalPath) { Get-Content -LiteralPath $journalPath -Raw | ConvertFrom-Json } else { $null }
$ownedNames = @('manifest.before','manifest.after','journal.json','journal.json.tmp')
for ($i = 0; $i -lt $allowed.Count; $i++) { $ownedNames += "$i.before"; $ownedNames += "$i.after" }
foreach ($entry in Get-ChildItem -LiteralPath $transaction -Force) {
    if ($entry.PSIsContainer -or $entry.Name -notin $ownedNames) { throw 'Arquivo estranho no journal; preservado para revisao manual.' }
    Assert-PlainPath $entry.FullName
}
if ($journal) {
    $recordCount = @($journal.Files).Count
    if ($journal.Format -ne 1 -or $recordCount -notin @(7,8)) { throw 'Formato de journal desconhecido.' }
    $generationPaths = if ($recordCount -eq 7) { @($allowed | Where-Object { $_ -ne 'vbrasfoot-nitido.cmd' }) } else { $allowed }
    $seenPaths = @(); $seenIndexes = @()
    foreach ($record in $journal.Files) {
        if ($record.Path -notin $generationPaths -or $record.Path -in $seenPaths -or
            "$($record.Index)" -notmatch '^[0-7]$' -or [int]$record.Index -ge $recordCount -or $record.Index -in $seenIndexes -or
            $record.After -notmatch '^[a-fA-F0-9]{64}$' -or ($record.Before -and $record.Before -notmatch '^[a-fA-F0-9]{64}$')) { throw 'Journal invalido; recuperacao recusada.' }
        $seenPaths += $record.Path; $seenIndexes += $record.Index
        $target = Join-Path $game $record.Path
        Assert-PlainPath $target
        $actual = Read-Hash $target
        if ($Complete) {
            if ($actual -ne $record.After) { throw "Geracao nova incompleta: $target" }
        } else {
            if ($actual -ne $record.Before -and $actual -ne $record.After) { throw "Arquivo modificado fora da atualizacao: $target" }
            if ($record.Before -and (Read-Hash (Join-Path $transaction "$($record.Index).before")) -ne $record.Before) { throw 'Backup gerenciado ausente/corrompido.' }
        }
        Write-Host "$(if ($Complete) { 'Manter geracao nova' } else { 'Retornar geracao anterior' }): $target"
    }
    if ($journal.ManifestAfter -notmatch '^[a-fA-F0-9]{64}$' -or ($journal.ManifestBefore -and $journal.ManifestBefore -notmatch '^[a-fA-F0-9]{64}$')) { throw 'Identidade de manifesto invalida' }
    Assert-PlainPath $manifestPath
    $actualManifest = Read-Hash $manifestPath
    if ($Complete) {
        if ($actualManifest -ne $journal.ManifestAfter) { throw 'Manifesto novo nao publicado; use rollback.' }
    } else {
        if ($actualManifest -ne $journal.ManifestBefore -and $actualManifest -ne $journal.ManifestAfter) { throw 'Manifesto alterado fora da atualizacao.' }
        if ($journal.ManifestBefore -and (Read-Hash (Join-Path $transaction 'manifest.before')) -ne $journal.ManifestBefore) { throw 'Backup do manifesto corrompido.' }
    }
} else {
    # Installation applies no managed files until journal.json is published.
    Write-Host 'Staging interrompido antes de publicar o journal: somente arquivos temporarios serao removidos.'
}
Write-Host 'Saves, mods e dados do usuario nao serao tocados. Preview; use -Apply para confirmar.'
if (-not $Apply) { return }
if (Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'bf22-23.exe' -or ($_.Name -in @('java.exe','javaw.exe') -and $_.CommandLine -like '*vbrasfoot.jar*') }) { throw 'Feche o jogo/loader antes da recuperacao.' }
$lock = [IO.File]::Open((Join-Path $root '.runtime.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
try {
    if ($journal -and -not $Complete) {
        $restore = @($journal.Files | ForEach-Object { [pscustomobject]@{ Target = (Join-Path $game $_.Path); Blob = (Join-Path $transaction "$($_.Index).before"); Hash = $_.Before } })
        $restore += [pscustomobject]@{ Target = $manifestPath; Blob = (Join-Path $transaction 'manifest.before'); Hash = $journal.ManifestBefore }
        foreach ($item in $restore) {
            if ((Read-Hash $item.Target) -eq $item.Hash) { continue }
            if (-not $item.Hash) { Remove-Item -LiteralPath $item.Target; continue }
            $temporary = $item.Target + '.' + [guid]::NewGuid().ToString('N') + '.recovery'
            try {
                [IO.File]::Copy($item.Blob, $temporary, $false)
                if (Test-Path -LiteralPath $item.Target) {
                    $backup = $temporary + '.previous'
                    [IO.File]::Replace($temporary, $item.Target, $backup)
                    Remove-Item -LiteralPath $backup
                } else { [IO.File]::Move($temporary, $item.Target) }
                if ((Read-Hash $item.Target) -ne $item.Hash) { throw 'Hash incorreto apos recuperacao' }
            } finally { if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary } }
        }
    }
    foreach ($name in $ownedNames | Where-Object { $_ -ne 'journal.json' }) {
        $file = Join-Path $transaction $name
        if (Test-Path -LiteralPath $file) { Remove-Item -LiteralPath $file }
    }
    if (Test-Path -LiteralPath $journalPath) { Remove-Item -LiteralPath $journalPath }
    [IO.Directory]::Delete($transaction, $false)
} finally { $lock.Dispose() }
Write-Host 'Recuperacao de arquivos gerenciados concluida; dados e saves preservados.'
