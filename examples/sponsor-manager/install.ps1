param([Parameter(Mandatory=$true)][string]$GameDir, [switch]$Preview, [switch]$Update)
$ErrorActionPreference = 'Stop'
$game = (Resolve-Path -LiteralPath $GameDir).Path
$root = Join-Path $game 'vbrasfoot'
$mods = Join-Path $root 'mods'
$jar = Join-Path $PSScriptRoot 'dist\sponsor-manager.jar'
$target = Join-Path $mods 'br.vbrasfoot.sponsor-manager.jar'
foreach ($path in @($game,$root,$mods,$target,$jar)) {
    for ($p = $path; $p; $p = Split-Path $p -Parent) {
        if ((Test-Path -LiteralPath $p) -and ((Get-Item -LiteralPath $p -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw "Link/junction recusado: $p" }
    }
}
if (-not (Test-Path -LiteralPath (Join-Path $root 'install-manifest.json'))) { throw 'Instale primeiro o loader compativel.' }
if (Test-Path -LiteralPath (Join-Path $root '.update-pending')) { throw 'Recupere a atualizacao do loader antes de instalar.' }
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw 'Execute build.ps1 e valide o JAR antes.' }
if ((Test-Path -LiteralPath $target) -and -not $Update) { throw 'JAR ja existe; use -Update para criar backup e substituir.' }
if (-not (Test-Path -LiteralPath $target) -and $Update) { throw 'Nao ha instalacao anterior para atualizar; execute sem -Update.' }
Write-Host "Instalar para o proximo inicio: $target"
if ($Update) { Write-Host 'Atualizacao: o JAR anterior sera copiado para backups antes da troca.' }
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
        if ($Update) {
            $backupDir = Join-Path $root ('backups\mod-update-sponsor-manager-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N'))
            foreach ($path in @($backupDir,(Join-Path $backupDir 'sponsor-manager.previous.jar'))) {
                for ($p = $path; $p; $p = Split-Path $p -Parent) {
                    if ((Test-Path -LiteralPath $p) -and ((Get-Item -LiteralPath $p -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw "Link/junction recusado: $p" }
                }
            }
            [IO.Directory]::CreateDirectory($backupDir) | Out-Null
            $backup = Join-Path $backupDir 'sponsor-manager.previous.jar'
            if (Test-Path -LiteralPath $backup) { throw 'Destino de backup ja existe; atualizacao cancelada.' }
            $previousHash = (Get-FileHash -LiteralPath $target).Hash
            [IO.File]::Replace($temporary, $target, $backup)
            if ((Get-FileHash -LiteralPath $backup).Hash -ne $previousHash) { throw 'Backup do mod anterior divergiu; revise antes de iniciar o jogo.' }
            Write-Host "Backup anterior: $backup"
        } else { [IO.File]::Move($temporary, $target) }
        if ((Get-FileHash -LiteralPath $target).Hash -ne (Get-FileHash -LiteralPath $jar).Hash) { throw 'JAR instalado divergiu do artefato validado.' }
    } finally { if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary } }
} finally { $lock.Dispose() }
Write-Host 'Instalado para o proximo inicio. O prototipo altera apenas dados proprios do mod.'
