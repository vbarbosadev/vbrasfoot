$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$owned=Join-Path $env:LOCALAPPDATA ('Temp\opencode\skill-installer-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $owned | Out-Null
$script=Join-Path $repo 'ai\install-skill.ps1'
& powershell -NoProfile -ExecutionPolicy Bypass -File $script -ProjectRoot $owned
if($LASTEXITCODE -ne 0) { throw 'Project install failed' }
$dest=Join-Path $owned '.agents\skills\vbrasfoot-mod-author\SKILL.md'
$source=Join-Path $repo 'ai\skills\vbrasfoot-mod-author\SKILL.md'
if((Get-FileHash -LiteralPath $dest).Hash -ne (Get-FileHash -LiteralPath $source).Hash) { throw 'Installed hash mismatch' }
[IO.File]::AppendAllText($dest,"`nLOCAL CHANGE")
$before=(Get-FileHash -LiteralPath $dest).Hash
$child=Start-Process powershell -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"'+$script+'"'),'-ProjectRoot',('"'+$owned+'"')) -Wait -PassThru -WindowStyle Hidden -RedirectStandardError (Join-Path $owned 'expected-error.txt') -RedirectStandardOutput (Join-Path $owned 'expected-output.txt')
if($child.ExitCode -eq 0 -or (Get-FileHash -LiteralPath $dest).Hash -ne $before) { throw 'Existing skill overwritten without Replace' }
& powershell -NoProfile -ExecutionPolicy Bypass -File $script -ProjectRoot $owned -Replace
if($LASTEXITCODE -ne 0) { throw 'Replace failed' }
$backup=@(Get-ChildItem -LiteralPath (Join-Path $owned '.agents\skill-backups') -Recurse -Filter SKILL.md)
if($backup.Count -ne 1 -or (Get-FileHash -LiteralPath $backup[0].FullName).Hash -ne $before) { throw 'Previous skill backup not preserved' }
if((Get-FileHash -LiteralPath $dest).Hash -ne (Get-FileHash -LiteralPath $source).Hash) { throw 'Replacement mismatch' }
Write-Output "PASS: project install, duplicate refusal, backup and replacement hashes. Only disposable project touched: $owned"
