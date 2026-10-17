param([Parameter(Mandatory=$true)][string]$Package, [string]$GameDir = (Join-Path $PSScriptRoot '..\Brasfoot22-23'))
$ErrorActionPreference = 'Stop'
$packageRoot = (Resolve-Path -LiteralPath $Package).Path
$fixture = Join-Path $env:LOCALAPPDATA ('Temp\opencode\vbrasfoot-install-' + [guid]::NewGuid().ToString('N'))
$game = Join-Path $fixture ('Jogo com espacos e acento ' + [char]0x00e7)
[IO.Directory]::CreateDirectory($game) | Out-Null
Copy-Item -LiteralPath (Join-Path $GameDir 'bf22-23.exe') -Destination $game
& (Join-Path $packageRoot 'install.ps1') -GameDir $game -Preview
if (Test-Path -LiteralPath (Join-Path $game 'vbrasfoot')) { throw 'Preview escreveu na fixture' }
& (Join-Path $packageRoot 'install.ps1') -GameDir $game
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $game 'vbrasfoot\app\launch.ps1') --validate-only
if ($LASTEXITCODE -ne 0) { throw 'Validacao instalada falhou' }
[IO.Directory]::CreateDirectory((Join-Path $game 'vbrasfoot\data')) | Out-Null
[IO.Directory]::CreateDirectory((Join-Path $game 'vbrasfoot\mods')) | Out-Null
Set-Content -LiteralPath (Join-Path $game 'vbrasfoot\data\preservar.txt') -Value 'preservar'
Set-Content -LiteralPath (Join-Path $game 'vbrasfoot\mods\broken.jar') -Value 'invalid'
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $game 'vbrasfoot\app\launch.ps1') --safe-mode --validate-only
if ($LASTEXITCODE -ne 0) { throw 'Modo seguro instalado falhou' }
& (Join-Path $game 'vbrasfoot-safe.cmd') --validate-only
if ($LASTEXITCODE -ne 0) { throw 'Launcher CMD seguro falhou' }
& (Join-Path $packageRoot 'install.ps1') -GameDir $game
# Do not overwrite user-edited managed files.
$managedFile = Join-Path $game 'vbrasfoot.cmd'
$originalBytes = [IO.File]::ReadAllBytes($managedFile)
[IO.File]::AppendAllText($managedFile, "`r`nrem user edit")
$rejected = $false
try { & (Join-Path $packageRoot 'install.ps1') -GameDir $game -Preview } catch { $rejected = $true }
if (-not $rejected) { throw 'Arquivo modificado foi aceito' }
[IO.File]::WriteAllBytes($managedFile, $originalBytes)
# A running loader lock must prevent updates even without a matching process name.
$lock = [IO.File]::Open((Join-Path $game 'vbrasfoot\.runtime.lock'), 'Open', 'ReadWrite', 'None')
$rejected = $false
try { try { & (Join-Path $packageRoot 'install.ps1') -GameDir $game } catch { $rejected = $true } }
finally { $lock.Dispose() }
if (-not $rejected) { throw 'Atualizacao aceitou lock ativo' }
$rejected = $false
try { & (Join-Path $packageRoot 'install.ps1') -GameDir $game -ReserveBytes ([long]::MaxValue) } catch { $rejected = $true }
if (-not $rejected -or (Test-Path -LiteralPath (Join-Path $game 'vbrasfoot\.update-pending'))) { throw 'Preflight de espaco nao recusou antes de staging' }
# Real NTFS write denial, only on the private fixture; restore ACL in finally.
$app = Join-Path $game 'vbrasfoot\app'
$savedAcl = Get-Acl -LiteralPath $app
$savedSddl = $savedAcl.Sddl
$denyWrite = [Security.AccessControl.FileSystemAccessRule]::new(
    [Security.Principal.WindowsIdentity]::GetCurrent().User,
    [Security.AccessControl.FileSystemRights]::Write,
    [Security.AccessControl.InheritanceFlags]'ContainerInherit,ObjectInherit',
    [Security.AccessControl.PropagationFlags]::None,
    [Security.AccessControl.AccessControlType]::Deny)
$savedAcl.AddAccessRule($denyWrite)
$rejected = $false
try {
    Set-Acl -LiteralPath $app -AclObject $savedAcl
    try { & (Join-Path $packageRoot 'install.ps1') -GameDir $game } catch { $rejected = $true }
} finally {
    $restoredAcl = [Security.AccessControl.DirectorySecurity]::new()
    $restoredAcl.SetSecurityDescriptorSddlForm($savedSddl)
    Set-Acl -LiteralPath $app -AclObject $restoredAcl
}
if (-not $rejected) { throw 'Instalacao aceitou pasta sem permissao de escrita' }
& (Join-Path $packageRoot 'recover-update.ps1') -GameDir $game -Apply
# Force a genuine replacement failure after earlier files have been published.
# Only fixture-managed files are touched; never saves or the user's installation.
$updatedPackage = Join-Path $fixture 'updated-package'
Copy-Item -LiteralPath $packageRoot -Destination $updatedPackage -Recurse
[IO.File]::AppendAllText((Join-Path $updatedPackage 'app\LICENSE'), "`r`nfixture-update")
$hashesPath = Join-Path $updatedPackage 'package-hashes.json'
$hashes = Get-Content -LiteralPath $hashesPath -Raw | ConvertFrom-Json
$hashes.'app\LICENSE' = (Get-FileHash -LiteralPath (Join-Path $updatedPackage 'app\LICENSE')).Hash
$hashes | ConvertTo-Json | Set-Content -LiteralPath $hashesPath -Encoding UTF8
$licensePath = Join-Path $game 'vbrasfoot\app\LICENSE'
$licenseBefore = (Get-FileHash -LiteralPath $licensePath).Hash
$fileLock = [IO.File]::Open((Join-Path $game 'vbrasfoot\app\launch.ps1'), 'Open', 'Read', 'Read')
$rejected = $false
try { try { & (Join-Path $updatedPackage 'install.ps1') -GameDir $game } catch { $rejected = $true } }
finally { $fileLock.Dispose() }
if (-not $rejected -or -not (Test-Path -LiteralPath (Join-Path $game 'vbrasfoot\.update-pending\journal.json'))) { throw 'Falha real nao preservou journal' }
if ((Get-FileHash -LiteralPath $licensePath).Hash -eq $licenseBefore) { throw 'Fixture nao alcancou publicacao parcial' }
$rejected = $false
try { & (Join-Path $packageRoot 'recover-update.ps1') -GameDir $game -Complete } catch { $rejected = $true }
if (-not $rejected) { throw 'Conclusao aceitou geracao parcial' }
$previousLicense = Join-Path $game 'vbrasfoot\.update-pending\1.before'
$previousBytes = [IO.File]::ReadAllBytes($previousLicense)
[IO.File]::AppendAllText($previousLicense, 'corrupt')
$rejected = $false
try { & (Join-Path $packageRoot 'recover-update.ps1') -GameDir $game -Apply } catch { $rejected = $true }
if (-not $rejected -or (Get-FileHash -LiteralPath $licensePath).Hash -eq $licenseBefore) { throw 'Recuperacao aceitou backup corrompido ou alterou alvo antes de validar' }
[IO.File]::WriteAllBytes($previousLicense, $previousBytes)
$journalPath = Join-Path $game 'vbrasfoot\.update-pending\journal.json'
$journalBytes = [IO.File]::ReadAllBytes($journalPath)
$badJournal = Get-Content -LiteralPath $journalPath -Raw | ConvertFrom-Json
$badJournal.Files[0].Path = 'sav\Flamengo.s22'
$badJournal | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $journalPath -Encoding UTF8
$rejected = $false
try { & (Join-Path $packageRoot 'recover-update.ps1') -GameDir $game -Apply } catch { $rejected = $true }
if (-not $rejected -or (Get-FileHash -LiteralPath $licensePath).Hash -eq $licenseBefore) { throw 'Journal fora da allowlist aceito ou alvo alterado' }
[IO.File]::WriteAllBytes($journalPath, $journalBytes)
$previousPreference = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue' # Expected child stderr must not terminate PS5 when output is redirected.
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $game 'vbrasfoot\app\launch.ps1') --safe-mode --validate-only
    $blockedExit = $LASTEXITCODE
} finally { $ErrorActionPreference = $previousPreference }
if ($blockedExit -eq 0) { throw 'Launcher aceitou atualizacao incompleta' }
& (Join-Path $packageRoot 'recover-update.ps1') -GameDir $game
if (-not (Test-Path -LiteralPath (Join-Path $game 'vbrasfoot\.update-pending'))) { throw 'Preview removeu journal' }
& (Join-Path $packageRoot 'recover-update.ps1') -GameDir $game -Apply
if ((Get-FileHash -LiteralPath $licensePath).Hash -ne $licenseBefore) { throw 'Rollback gerenciado nao restaurou hash anterior' }
if (Test-Path -LiteralPath (Join-Path $game 'vbrasfoot\.update-pending')) { throw 'Recuperacao deixou transacao pendente' }
& (Join-Path $packageRoot 'install.ps1') -GameDir $game
# Model the exact post-publication state with cleanup interrupted (synthetic fixture).
$transaction = Join-Path $game 'vbrasfoot\.update-pending'
[IO.Directory]::CreateDirectory($transaction) | Out-Null
$manifestPath = Join-Path $game 'vbrasfoot\install-manifest.json'
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$records = @(); $index = 0
foreach ($file in $manifest.Files) {
    $records += [pscustomobject]@{ Path = $file.Path; Before = $file.SHA256; After = $file.SHA256; Index = $index }
    $index++
}
$manifestHash = (Get-FileHash -LiteralPath $manifestPath).Hash
[pscustomobject]@{ Format = 1; Files = $records; ManifestBefore = $manifestHash; ManifestAfter = $manifestHash } |
    ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $transaction 'journal.json') -Encoding UTF8
& (Join-Path $packageRoot 'recover-update.ps1') -GameDir $game -Complete
if (-not (Test-Path -LiteralPath $transaction)) { throw 'Preview completou cleanup' }
& (Join-Path $packageRoot 'recover-update.ps1') -GameDir $game -Complete -Apply
if (Test-Path -LiteralPath $transaction) { throw 'Cleanup de geracao completa falhou' }
[IO.Directory]::CreateDirectory($transaction) | Out-Null
Set-Content -LiteralPath (Join-Path $transaction '0.after') -Value 'fixture staged before journal'
& (Join-Path $packageRoot 'recover-update.ps1') -GameDir $game -Apply
if (Test-Path -LiteralPath $transaction) { throw 'Staging interrompido nao recuperado' }
$moved = Join-Path $fixture 'Jogo movido'
Move-Item -LiteralPath $game -Destination $moved
$game = $moved
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $game 'vbrasfoot\app\launch.ps1') --safe-mode --validate-only
if ($LASTEXITCODE -ne 0) { throw 'Validacao apos mover falhou' }
& (Join-Path $game 'vbrasfoot\app\uninstall.ps1')
if (-not (Test-Path -LiteralPath (Join-Path $game 'vbrasfoot.cmd'))) { throw 'Preview removeu launcher' }
& (Join-Path $game 'vbrasfoot\app\uninstall.ps1') -Apply
if (-not (Test-Path -LiteralPath (Join-Path $game 'vbrasfoot\data\preservar.txt'))) { throw 'Dado removido' }
if (Test-Path -LiteralPath (Join-Path $game 'vbrasfoot.cmd')) { throw 'Launcher nao removido' }
if ((Get-FileHash -LiteralPath (Join-Path $game 'bf22-23.exe')).Hash -ne (Get-FileHash -LiteralPath (Join-Path $GameDir 'bf22-23.exe')).Hash) { throw 'EXE alterado' }
$linkGame = Join-Path $fixture 'junction-game'
$protected = Join-Path $fixture 'protected-original-resources'
[IO.Directory]::CreateDirectory($linkGame) | Out-Null
[IO.Directory]::CreateDirectory($protected) | Out-Null
Copy-Item -LiteralPath (Join-Path $GameDir 'bf22-23.exe') -Destination $linkGame
New-Item -ItemType Junction -Path (Join-Path $linkGame 'vbrasfoot') -Target $protected | Out-Null
$rejected = $false
try { & (Join-Path $packageRoot 'install.ps1') -GameDir $linkGame -Preview } catch { $rejected = $true }
if (-not $rejected) { throw 'Instalador aceitou junction' }
try {
    $ErrorActionPreference = 'Continue'
    & java -jar (Join-Path $packageRoot 'app\vbrasfoot.jar') --game-dir $linkGame --home (Join-Path $linkGame 'vbrasfoot') --validate-only
    $blockedExit = $LASTEXITCODE
} finally { $ErrorActionPreference = $previousPreference }
if ($blockedExit -eq 0) { throw 'Loader aceitou junction' }
if (@(Get-ChildItem -LiteralPath $protected -Force).Count -ne 0) { throw 'Escrita atraves de junction' }
$global:LASTEXITCODE = 0
Write-Host "PASS: fixture portable (sem gameplay nem saves). Evidencias: $fixture"
