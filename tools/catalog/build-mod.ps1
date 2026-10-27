param([Parameter(Mandatory=$true)][string]$Mod)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
if ($Mod -notmatch '^[a-z0-9][a-z0-9-]*$') { throw 'Nome de pasta invalido' }
$catalog=Get-Content -LiteralPath (Join-Path $root 'catalog.json') -Raw | ConvertFrom-Json
if ($Mod -notin @($catalog.mods.folder)) { throw 'Mod fora do catalogo' }
$project=Join-Path $root "mods\$Mod"
$manifest=Get-Content -LiteralPath (Join-Path $project 'vbrasfoot.mod.properties') -Raw
$entry=@($catalog.mods | Where-Object folder -eq $Mod)[0]
if ($manifest -notmatch '(?m)^id=([^\r\n]+)' -or $Matches[1].Trim() -ne $entry.id) { throw 'ID diferente do catalogo' }
if (-not (Test-Path -LiteralPath (Join-Path $project 'LICENSE'))) { throw 'Licenca ausente' }
foreach($file in Get-ChildItem -LiteralPath $project -Recurse -File) {
    $rel=$file.FullName.Substring($project.Length+1).Replace('\','/')
    if ($rel -match '^(build|dist)/') { continue }
    if ($file.Extension -in @('.exe','.jar','.class','.s22','.sbck')) { throw "Artefato proibido na fonte do mod: $rel" }
}
$work=Join-Path $root ('build\'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path "$work\api","$work\classes","$work\tests" | Out-Null
$api=@(Get-ChildItem (Join-Path $root 'sdk\src') -Recurse -Filter '*.java' | ForEach-Object FullName)
& javac --release 17 -encoding UTF-8 -d "$work\api" @api
if ($LASTEXITCODE -ne 0) { throw 'API isolada nao compilou' }
$sources=@(Get-ChildItem (Join-Path $project 'src') -Recurse -Filter '*.java' | ForEach-Object FullName)
& javac --release 17 -encoding UTF-8 -cp "$work\api" -d "$work\classes" @sources
if ($LASTEXITCODE -ne 0) { throw 'Mod nao compilou contra API publica' }
$tests=@()
if (Test-Path (Join-Path $project 'test')) { $tests=@(Get-ChildItem (Join-Path $project 'test') -Recurse -Filter '*.java') }
if ($tests.Count) {
    $paths=@($tests | ForEach-Object FullName)
    & javac --release 17 -encoding UTF-8 -cp "$work\api;$work\classes" -d "$work\tests" @paths
    if ($LASTEXITCODE -ne 0) { throw 'Testes nao compilaram' }
    foreach($test in $tests) {
        $text=Get-Content -LiteralPath $test.FullName -Raw
        if ($text -match 'public\s+static\s+void\s+main\s*\(') {
            if ($text -notmatch 'package\s+([\w.]+)\s*;') { throw 'Teste sem package' }
            $name=$Matches[1]+'.'+$test.BaseName
            & java '-Djava.awt.headless=true' -cp "$work\api;$work\classes;$work\tests" $name
            if ($LASTEXITCODE -ne 0) { throw "Teste falhou: $name" }
        }
    }
}
$dest=Join-Path $root "dist\$Mod"
New-Item -ItemType Directory -Path $dest -Force | Out-Null
$jar=Join-Path $dest "$Mod.jar"
& jar --create --file $jar -C "$work\classes" . -C $project vbrasfoot.mod.properties
if ($LASTEXITCODE -ne 0) { throw 'Falha no JAR' }
Copy-Item -LiteralPath (Join-Path $project 'README.md') -Destination $dest -Force
if (Test-Path (Join-Path $project 'LICENSE')) { Copy-Item -LiteralPath (Join-Path $project 'LICENSE') -Destination $dest -Force }
(Get-FileHash -LiteralPath $jar).Hash | Set-Content -LiteralPath "$jar.sha256" -Encoding ASCII
Write-Output "Compilado/testado: $jar. Nao e auditoria de seguranca nem teste no jogo."
