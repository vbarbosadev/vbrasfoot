param([Parameter(Mandatory=$true)][string]$Package, [string]$GameDir = (Join-Path $PSScriptRoot '..\Brasfoot22-23'))
$ErrorActionPreference = 'Stop'
$packageRoot = (Resolve-Path -LiteralPath $Package).Path
$fixture = Join-Path $env:LOCALAPPDATA ('Temp\opencode\vbrasfoot-mod-management-' + [guid]::NewGuid().ToString('N'))
$game = Join-Path $fixture 'game'
[IO.Directory]::CreateDirectory($game) | Out-Null
$exe = Join-Path $GameDir 'bf22-23.exe'
$before = (Get-FileHash -LiteralPath $exe).Hash
Copy-Item -LiteralPath $exe -Destination $game
& (Join-Path $packageRoot 'install.ps1') -GameDir $game
$loaderRoot = Join-Path $game 'vbrasfoot'
$mods = Join-Path $loaderRoot 'mods'
[IO.Directory]::CreateDirectory($mods) | Out-Null
$broken = Join-Path $mods 'broken.jar'
[IO.File]::WriteAllBytes($broken, [byte[]]@(1,2,3))
function Run-Check([string]$Label, [string[]]$Arguments, [bool]$Success) {
    $log = Join-Path $fixture ($Label + '.log')
    $err = Join-Path $fixture ($Label + '.err')
    $jar = Join-Path $loaderRoot 'app\vbrasfoot.jar'
    $javaArgs = @('-jar', ('"' + $jar + '"'), '--home', ('"' + $loaderRoot + '"'), '--game-dir', ('"' + $game + '"')) + $Arguments
    $info = [Diagnostics.ProcessStartInfo]::new((Get-Command java).Source, ($javaArgs -join ' '))
    $info.UseShellExecute = $false
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $info
    $null = $process.Start()
    $output = $process.StandardOutput.ReadToEndAsync()
    $errors = $process.StandardError.ReadToEndAsync()
    try {
        if (-not $process.WaitForExit(30000)) { $process.Kill(); throw "Owned validation timeout: $Label" }
        $process.WaitForExit()
        [IO.File]::WriteAllText($log, $output.GetAwaiter().GetResult())
        [IO.File]::WriteAllText($err, $errors.GetAwaiter().GetResult())
        if (($process.ExitCode -eq 0) -ne $Success) { Get-Content -LiteralPath $err; throw "Unexpected exit: $Label / $($process.ExitCode)" }
    } finally { $process.Dispose() }
}
Run-Check 'broken-active' @('--validate-only') $false
Run-Check 'disable' @('--disable-mod','broken.jar') $true
$config = Join-Path $loaderRoot 'mods-selection.properties'
$disabledHash = (Get-FileHash -LiteralPath $config).Hash
Run-Check 'list' @('--list-mods') $true
if (-not (Select-String -LiteralPath (Join-Path $fixture 'list.log') -SimpleMatch 'DESABILITADO broken.jar')) { throw 'Missing disabled inventory' }
Run-Check 'disabled-validate' @('--validate-only') $true
Run-Check 'enable-broken-refused' @('--enable-mod','broken.jar') $false
if ((Get-FileHash -LiteralPath $config).Hash -ne $disabledHash) { throw 'Refused enable changed config' }
$lock = [IO.File]::Open((Join-Path $loaderRoot '.runtime.lock'), 'Open', 'ReadWrite', 'None')
try { Run-Check 'session-lock-refused' @('--disable-mod','broken.jar') $false }
finally { $lock.Dispose() }
Set-Content -LiteralPath $config -Value 'format=99' -Encoding UTF8
Run-Check 'corrupt-refused' @('--validate-only') $false
Run-Check 'safe-ignores-corrupt' @('--safe-mode','--validate-only') $true
if ((Get-FileHash -LiteralPath $broken).Hash -ne '039058C6F2C0CB492C533B0A4D14EF77CC0F78ABCCCED5287D84A1A2011CFB81') { throw 'Broken JAR modified' }
if ((Get-FileHash -LiteralPath $exe).Hash -ne $before -or (Get-FileHash -LiteralPath (Join-Path $game 'bf22-23.exe')).Hash -ne $before) { throw 'EXE changed' }
Write-Host "PASS: next-start CLI, invalid disabled JAR, atomic config preservation, session lock, corrupt config and safe mode; no game/mod initialization. Fixture: $fixture"
