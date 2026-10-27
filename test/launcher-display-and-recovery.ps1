$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$owned = Join-Path $env:LOCALAPPDATA ('Temp\opencode\launcher-display-' + [guid]::NewGuid().ToString('N'))
$app = Join-Path $owned 'game\vbrasfoot\app'
$fake = Join-Path $owned 'fakebin'
New-Item -ItemType Directory -Path $app,$fake -Force | Out-Null
Copy-Item (Join-Path $project 'packaging\launch.ps1') (Join-Path $app 'launch.ps1')
Copy-Item (Join-Path $project 'packaging\vbrasfoot.cmd') (Join-Path $owned 'game\vbrasfoot.cmd')
Copy-Item (Join-Path $project 'packaging\vbrasfoot-completo.cmd') (Join-Path $owned 'game\vbrasfoot-completo.cmd')
$capture = Join-Path $owned 'java-args.txt'
[IO.File]::WriteAllText((Join-Path $fake 'java.cmd'), "@echo off`r`necho %* > `"%VBRASFOOT_TEST_CAPTURE%`"`r`nexit /b 0`r`n")
$previousPath = $env:PATH
$previousCapture = $env:VBRASFOOT_TEST_CAPTURE
try {
    $env:PATH = $fake + ';' + $previousPath
    $env:VBRASFOOT_TEST_CAPTURE = $capture
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $app 'launch.ps1') --probe-career-hooks --pixel-perfect --validate-only
    if ($LASTEXITCODE -ne 0) { throw 'Pixel-perfect launcher failed' }
    $line = Get-Content $capture -Raw
    if ($line -notmatch '-Dsun.java2d.uiScale=1' -or $line -match '--pixel-perfect' -or $line -notmatch '--validate-only' -or $line -notmatch 'jdk.internal.org.objectweb.asm') { throw 'Incorrect display/probe/parser arguments' }
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $app 'launch.ps1') --validate-only
    if ($LASTEXITCODE -ne 0) { throw 'Normal launcher failed' }
    if ((Get-Content $capture -Raw) -match 'sun.java2d.uiScale') { throw 'Normal launcher must preserve system scaling' }
    & (Join-Path $owned 'game\vbrasfoot-completo.cmd') --validate-only
    if ($LASTEXITCODE -ne 0) { throw 'Combined launcher failed' }
    $line = Get-Content $capture -Raw
    if ($line -notmatch '--combined' -or $line -notmatch '--probe-career-hooks' -or $line -notmatch '-Dsun.java2d.uiScale=1' -or $line -match '--pixel-perfect') { throw 'Combined launcher must enable profile, probes and 1:1 scaling' }
} finally { $env:PATH = $previousPath; $env:VBRASFOOT_TEST_CAPTURE = $previousCapture }

$paths = @('vbrasfoot\app\vbrasfoot.jar','vbrasfoot\app\LICENSE','vbrasfoot\app\launch.ps1','vbrasfoot\app\uninstall.ps1','vbrasfoot\app\recover-update.ps1','vbrasfoot.cmd','vbrasfoot-safe.cmd','vbrasfoot-nitido.cmd')
foreach ($count in @(7,8)) {
    $game = Join-Path $owned "recovery-$count"
    $pending = Join-Path $game 'vbrasfoot\.update-pending'
    New-Item -ItemType Directory -Path $pending -Force | Out-Null
    $records = @()
    for ($i = 0; $i -lt $count; $i++) {
        $target = Join-Path $game $paths[$i]
        New-Item -ItemType Directory -Path (Split-Path $target -Parent) -Force | Out-Null
        [IO.File]::WriteAllText($target, 'owned-fixture')
        $records += [pscustomobject]@{Path=$paths[$i];Index=$i;Before=$null;After=(Get-FileHash $target).Hash}
    }
    $manifest = Join-Path $game 'vbrasfoot\install-manifest.json'
    [IO.File]::WriteAllText($manifest, '{}')
    $journal = [pscustomobject]@{Format=1;Files=$records;ManifestBefore=$null;ManifestAfter=(Get-FileHash $manifest).Hash}
    $journal | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $pending 'journal.json')
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $project 'packaging\recover-update.ps1') -GameDir $game -Complete
    if ($LASTEXITCODE -ne 0) { throw "Recovery of $count-file generation failed" }
    if (-not (Test-Path $pending)) { throw 'Preview changed the transaction' }
    $journal.Files[0].Index = 7
    $journal.Files[1].Index = 7
    $journal | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $pending 'journal.json')
    # Suppress expected child stderr without treating it as a parent PowerShell failure.
    $child = Start-Process powershell -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"' + (Join-Path $project 'packaging\recover-update.ps1') + '"'),'-GameDir',('"' + $game + '"'),'-Complete') -Wait -PassThru -WindowStyle Hidden -RedirectStandardError (Join-Path $owned "rejected-$count.log") -RedirectStandardOutput (Join-Path $owned "rejected-$count.out")
    if ($child.ExitCode -eq 0) { throw 'Invalid duplicate/out-of-range indices accepted' }
}
Write-Output "PASS: launcher scale/probe arguments and legacy7/new8 recovery previews; owned fixtures only: $owned"
