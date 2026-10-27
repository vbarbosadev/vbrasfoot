param([string]$Branch='mods', [switch]$UpdateExisting)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
& git -C $root check-ref-format --branch $Branch | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Branch invalida' }
& git -C $root show-ref --verify --quiet "refs/heads/$Branch"
 $exists=$LASTEXITCODE -eq 0
if ($exists -and -not $UpdateExisting) { throw 'Branch existente: preserve e atualize por PR, nao recrie' }
$parent=(& git -C $root rev-parse HEAD).Trim()
$expected='0000000000000000000000000000000000000000'
if ($exists) { $parent=(& git -C $root rev-parse "refs/heads/$Branch").Trim(); $expected=$parent }
$catalog=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'catalog.json') -Raw | ConvertFrom-Json
$index=Join-Path $env:LOCALAPPDATA ('Temp\opencode\catalog-index-'+[guid]::NewGuid().ToString('N'))
$previous=$env:GIT_INDEX_FILE
function Add-Blob([string]$Source,[string]$Target) {
    $hash=(& git -C $root hash-object -w -- $Source).Trim()
    if ($LASTEXITCODE -ne 0) { throw "Falha no blob: $Source" }
    & git -C $root update-index --add --cacheinfo "100644,$hash,$Target"
    if ($LASTEXITCODE -ne 0) { throw "Falha no indice: $Target" }
}
try {
    $env:GIT_INDEX_FILE=$index
    & git -C $root read-tree --empty
    Add-Blob (Join-Path $PSScriptRoot 'CATALOG-README.md') 'README.md'
    Add-Blob (Join-Path $root 'docs\CRIAR-E-PUBLICAR-MODS.md') 'CONTRIBUTING.md'
    Add-Blob (Join-Path $PSScriptRoot 'catalog.json') 'catalog.json'
    Add-Blob (Join-Path $PSScriptRoot 'build-mod.ps1') 'tools/build-mod.ps1'
    Add-Blob (Join-Path $PSScriptRoot 'catalog.yml') '.github/workflows/catalog.yml'
    Add-Blob (Join-Path $PSScriptRoot 'PR-TEMPLATE.md') '.github/PULL_REQUEST_TEMPLATE.md'
    Add-Blob (Join-Path $root '.gitignore') '.gitignore'
    Add-Blob (Join-Path $root 'LICENSE') 'LICENSE'
    Add-Blob (Join-Path $root 'roadmap\SDK-BETA-016.md') 'docs/SDK-BETA-016.md'
    foreach($f in Get-ChildItem -LiteralPath (Join-Path $root 'ai') -Recurse -File) {
        $rel=$f.FullName.Substring($root.Length+1).Replace('\','/')
        Add-Blob $f.FullName $rel
    }
    foreach($f in Get-ChildItem (Join-Path $root 'src\br\vbrasfoot\api') -Filter '*.java') {
        Add-Blob $f.FullName ('sdk/src/br/vbrasfoot/api/'+$f.Name)
    }
    foreach($mod in $catalog.mods) {
        $source=Join-Path $root $mod.source
        foreach($f in Get-ChildItem -LiteralPath $source -Recurse -File) {
            $rel=$f.FullName.Substring($source.Length+1).Replace('\','/')
            if ($rel -match '^(src|test)/.*\.java$' -or $rel -in @('README.md','LICENSE','build.ps1','install.ps1','vbrasfoot.mod.properties','.gitignore')) {
                Add-Blob $f.FullName ("mods/"+$mod.folder+'/'+$rel)
            }
        }
        if (-not (Test-Path (Join-Path $source 'LICENSE'))) { Add-Blob (Join-Path $root 'LICENSE') ("mods/"+$mod.folder+'/LICENSE') }
    }
    $tree=(& git -C $root write-tree).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Falha tree' }
    $commit=(& git -C $root -c core.hooksPath=NUL commit-tree $tree -p $parent -m 'feat(catalog): initialize independent mods catalog and PR workflow').Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Configure identidade Git para commit de catalogo' }
    & git -C $root update-ref "refs/heads/$Branch" $commit $expected
    if ($LASTEXITCODE -ne 0) { throw 'Branch mudou durante preparacao; nao sobrescrita' }
    Write-Output "Branch LOCAL criada: $Branch ($commit). Checkout/indice atual intactos; nenhum push."
} finally {
    $env:GIT_INDEX_FILE=$previous
    if (Test-Path -LiteralPath $index) { Remove-Item -LiteralPath $index }
}
