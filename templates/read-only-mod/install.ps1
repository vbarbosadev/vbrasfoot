param([Parameter(Mandatory=$true)][string]$GameDir, [switch]$Preview)
$ErrorActionPreference = 'Stop'
$game = (Resolve-Path -LiteralPath $GameDir).Path
$jar = Join-Path $PSScriptRoot 'dist\meu-mod.jar'
$root = Join-Path $game 'vbrasfoot'
$mods = Join-Path $root 'mods'
$target = Join-Path $mods 'br.exemplo.consulta.jar'
foreach ($path in @($game, $root, $mods, $target, $jar)) {
    for ($p = $path; $p; $p = Split-Path $p -Parent) {
        if ((Test-Path -LiteralPath $p) -and ((Get-Item -LiteralPath $p -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw "Link/junction recusado: $p" }
    }
}
if (-not (Test-Path -LiteralPath (Join-Path $root 'install-manifest.json'))) { throw 'Instale primeiro o loader portable.' }
if (Test-Path -LiteralPath (Join-Path $root '.update-pending')) { throw 'Recupere a instalacao do loader antes de instalar o mod.' }
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw 'Execute build.ps1 do template antes.' }
if (Test-Path -LiteralPath $target) { throw 'Mod ja existe: revise a atualizacao manualmente; nao sera sobrescrito.' }
Write-Host "Copiar JAR do mod para: $target"
if ($Preview) { return }
$probe = [Diagnostics.ProcessStartInfo]::new((Get-Command java -ErrorAction Stop).Source)
$probe.Arguments = '-jar "' + (Join-Path $root 'app\vbrasfoot.jar') + '" --validate-mod "' + $jar + '"'
$probe.UseShellExecute = $false
$validation = [Diagnostics.Process]::Start($probe)
try { $validation.WaitForExit(); if ($validation.ExitCode -ne 0) { throw 'JAR recusado pelo validador; nenhuma instalacao feita.' } }
finally { $validation.Dispose() }
[IO.Directory]::CreateDirectory($mods) | Out-Null
$lock = [IO.File]::Open((Join-Path $root '.runtime.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
try {
    $temporary = Join-Path $mods ([guid]::NewGuid().ToString('N') + '.tmp')
    try {
        [IO.File]::Copy($jar, $temporary, $false)
        if ((Get-FileHash -LiteralPath $temporary).Hash -ne (Get-FileHash -LiteralPath $jar).Hash) { throw 'JAR mudou durante copia' }
        [IO.File]::Move($temporary, $target)
    } finally { if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary } }
} finally { $lock.Dispose() }
Write-Host 'Instalado para proxima inicializacao. Nao existe hot reload.'
