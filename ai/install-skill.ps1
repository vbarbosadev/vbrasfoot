param([string]$ProjectRoot, [switch]$Replace)
$ErrorActionPreference='Stop'
function Assert-Plain([string]$Path) {
    for($p=[IO.Path]::GetFullPath($Path); $p; $p=Split-Path $p -Parent) {
        $item=Get-Item -LiteralPath $p -Force -ErrorAction SilentlyContinue
        if($item -and ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw "Link/junction recusado: $p" }
    }
}
$source=Join-Path $PSScriptRoot 'skills\vbrasfoot-mod-author\SKILL.md'
Assert-Plain $source
if(-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw 'SKILL.md da distribuicao ausente' }
if($ProjectRoot) {
    if(-not (Test-Path -LiteralPath $ProjectRoot -PathType Container)) { throw 'ProjectRoot deve ser uma pasta existente' }
    $base=(Resolve-Path -LiteralPath $ProjectRoot).Path
} else { $base=[Environment]::GetFolderPath('UserProfile') }
if(-not $base) { throw 'Pasta do usuario indisponivel' }
$target=Join-Path $base '.agents\skills\vbrasfoot-mod-author'
Assert-Plain $target
if(Test-Path -LiteralPath $target) {
    if(-not $Replace) { throw "Skill ja instalada: $target. Use -Replace para guardar backup e substituir." }
    if(-not (Test-Path -LiteralPath (Join-Path $target 'SKILL.md') -PathType Leaf)) { throw 'Destino desconhecido: revise manualmente, nao sobrescrito' }
    foreach($item in Get-ChildItem -LiteralPath $target -Force -Recurse) { Assert-Plain $item.FullName }
    $backup=Join-Path $base ('.agents\skill-backups\vbrasfoot-mod-author-'+[guid]::NewGuid().ToString('N'))
    Assert-Plain $backup
    New-Item -ItemType Directory -Path (Split-Path $backup -Parent) -Force | Out-Null
    Copy-Item -LiteralPath $target -Destination $backup -Recurse
    Write-Output "Backup preservado: $backup"
}
New-Item -ItemType Directory -Path $target -Force | Out-Null
$dest=Join-Path $target 'SKILL.md'
Copy-Item -LiteralPath $source -Destination $dest -Force
if((Get-FileHash -LiteralPath $source).Hash -ne (Get-FileHash -LiteralPath $dest).Hash) { throw 'Falha de verificacao da copia' }
Write-Output "Skill instalada: $dest"
Write-Output 'Reabra a sessao do agente. Se nao descobrir a skill, solicite a leitura direta desse SKILL.md.'
