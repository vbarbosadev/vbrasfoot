param([Parameter(Mandatory=$true)][string]$GameDir, [switch]$Preview)
$ErrorActionPreference = 'Stop'
$game = (Resolve-Path -LiteralPath $GameDir).Path
$root = Join-Path $game 'vbrasfoot'
$mods = Join-Path $root 'mods'
$jar = Join-Path $PSScriptRoot 'dist\career-dashboard.jar'
$target = Join-Path $mods 'br.vbrasfoot.career-dashboard.jar'
foreach ($path in @($game,$root,$mods,$target,$jar)) {
    for ($p = $path; $p; $p = Split-Path $p -Parent) {
        if ((Test-Path -LiteralPath $p) -and ((Get-Item -LiteralPath $p -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw "Link/junction recusado: $p" }
    }
}
if (-not (Test-Path -LiteralPath (Join-Path $root 'install-manifest.json'))) { throw 'Instale primeiro o loader0.5.0+.' }
if (Test-Path -LiteralPath (Join-Path $root '.update-pending')) { throw 'Recupere a atualizacao do loader antes de instalar.' }
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw 'Execute build.ps1 do painel antes.' }
if (Test-Path -LiteralPath $target) { throw 'JAR ja existe; revise atualizacao manualmente, sem overwrite.' }
Write-Host "Instalar para proximo inicio: $target"
if ($Preview) { return }
if (Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'bf22-23.exe' -or ($_.Name -in @('java.exe','javaw.exe') -and $_.CommandLine -like '*vbrasfoot.jar*') }) { throw 'Feche jogo/loader; nenhum processo sera encerrado.' }
& java -jar (Join-Path $root 'app\vbrasfoot.jar') --validate-mod $jar
if ($LASTEXITCODE -ne 0) { throw 'Mod recusado; nenhuma instalacao feita.' }
[IO.Directory]::CreateDirectory($mods) | Out-Null
$lock = [IO.File]::Open((Join-Path $root '.runtime.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
try {
    $temporary = Join-Path $mods ([guid]::NewGuid().ToString('N') + '.tmp')
    try {
        [IO.File]::Copy($jar, $temporary, $false)
        if ((Get-FileHash -LiteralPath $temporary).Hash -ne (Get-FileHash -LiteralPath $jar).Hash) { throw 'JAR mudou durante copia.' }
        [IO.File]::Move($temporary, $target)
    } finally { if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary } }
} finally { $lock.Dispose() }
Write-Host 'Instalado; sem hot reload, configuracao/saves inalterados.'
