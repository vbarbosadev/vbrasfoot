param([string]$RootPath)
$ErrorActionPreference = 'Stop'
if (-not $RootPath) { $RootPath = $PSScriptRoot }
$root = (Resolve-Path -LiteralPath $RootPath).Path
for ($p = $root; $p; $p = Split-Path $p -Parent) {
    if ((Get-Item -LiteralPath $p -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Link/junction recusado: $p" }
}
$files = [Collections.Generic.List[string]]::new()
$pending = [Collections.Generic.Stack[string]]::new()
$pending.Push($root)
while ($pending.Count) {
    foreach ($item in Get-ChildItem -LiteralPath $pending.Pop() -Force) {
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Link/junction recusado: $($item.FullName)" }
        if ($item.PSIsContainer) { $pending.Push($item.FullName) }
        else { $files.Add($item.FullName) }
        if ($files.Count -gt 4096 -or $pending.Count -gt 4096) { throw 'SDK excede limites de arquivos/diretorios' }
    }
}
$metadata = Get-Content -LiteralPath (Join-Path $root 'sdk-package.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if ($metadata.format -ne 1 -or $metadata.kind -ne 'sdk-only' -or $metadata.apiMajor -ne 1 -or $metadata.javaRelease -ne 17 -or
    $metadata.gameProfile -ne 'brasfoot22-23' -or $metadata.experimental -ne $true -or $metadata.version -notmatch '^\d+\.\d+\.\d+$') { throw 'Metadados de SDK incompativeis' }
$version = $metadata.version
$fixed = @('README.md','LICENSE','verify.ps1','sdk-package.json',"api/vbrasfoot-api-$version.jar", "tools/vbrasfoot-validator-$version.jar")
foreach ($doc in @('SDK-QUICKSTART.md','SDK-COMPATIBILITY.md','SDK-DISTRIBUTION.md','SDK-CONTRACT.md','AI-MOD-AUTHORING.md','QUERY-PERFORMANCE.md','SCOPED-QUERIES.md','CAREER-OBSERVATIONS.md','MOD-MANAGEMENT.md','DIAGNOSTIC-EXPORT.md')) { $fixed += "docs/$doc" }
foreach ($relative in @('README.md','LICENSE','build.ps1','install.ps1','vbrasfoot.mod.properties','.gitignore','src/br/exemplo/consulta/ReadOnlyMod.java','test/br/exemplo/consulta/ReadOnlyModTest.java')) { $fixed += "template-read-only/$relative" }
foreach ($relative in @('README.md','LICENSE','build.ps1','vbrasfoot.mod.properties','.gitignore','src/br/vbrasfoot/observer/CareerObserverMod.java','test/br/vbrasfoot/observer/CareerObserverModTest.java')) { $fixed += "examples/career-observer/$relative" }
foreach ($relative in @('README.md','LICENSE','build.ps1','install.ps1','vbrasfoot.mod.properties','.gitignore','src/br/vbrasfoot/playercomparator/ComparisonModel.java','src/br/vbrasfoot/playercomparator/PlayerComparatorMod.java','test/br/vbrasfoot/playercomparator/ComparisonModelTest.java')) { $fixed += "examples/player-comparator/$relative" }
$checksums = Get-Content -LiteralPath (Join-Path $root 'checksums.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($entry in $checksums.PSObject.Properties) {
    $relative = $entry.Name
    if ($relative -match '[\\:\x00-\x1f<>|*?]' -or $relative.StartsWith('/') -or
        ($relative.Split('/') | Where-Object { $_ -in @('', '.', '..') -or $_ -match '[. ]$' -or $_ -match '^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\.|$)' })) { throw "Caminho de checksum invalido: $relative" }
    if (-not $seen.Add($relative) -or $entry.Value -notmatch '^[a-fA-F0-9]{64}$') { throw "Checksum invalido/duplicado: $relative" }
    $docAsset = $relative -match '^javadoc/(?:br/vbrasfoot/api/(?:class-use/)?)?[A-Za-z0-9._-]+\.html$' -or
        $relative -match '^javadoc/(?:[A-Za-z0-9._-]+\.(?:js|css|svg|png)|(?:script-dir|resources)/(?:[A-Za-z0-9._-]+/)*[A-Za-z0-9._-]+\.(?:js|css|svg|png|gif|woff2?|ttf|eot)|legal/(?:LICENSE|COPYRIGHT|ASSEMBLY_EXCEPTION|ADDITIONAL_LICENSE_INFO|[A-Za-z0-9._-]+\.md)|element-list|package-list)$'
    if ($relative -notin $fixed -and -not $docAsset) { throw "Arquivo fora da allowlist do SDK: $relative" }
    $path = Join-Path $root ($relative.Replace('/', [IO.Path]::DirectorySeparatorChar))
    if (-not (Test-Path -LiteralPath $path -PathType Leaf) -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $entry.Value) { throw "Arquivo ausente/alterado: $relative" }
}
foreach ($relative in $fixed + @('javadoc/index.html','javadoc/br/vbrasfoot/api/Mod.html')) { if (-not $seen.Contains($relative)) { throw "SDK incompleto: $relative" } }
foreach ($file in $files) {
    $relative = $file.Substring($root.TrimEnd('\').Length + 1).Replace('\', '/')
    if ($relative -ne 'checksums.json' -and -not $seen.Contains($relative)) { throw "Arquivo extra nao verificado: $relative" }
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach ($kind in @('api','validator')) {
    $relative = if ($kind -eq 'api') { "api/vbrasfoot-api-$version.jar" } else { "tools/vbrasfoot-validator-$version.jar" }
    $jar = [IO.Compression.ZipFile]::OpenRead((Join-Path $root $relative))
    try {
        if (-not $jar.GetEntry('LICENSE') -or -not $jar.GetEntry('META-INF/MANIFEST.MF')) { throw "Licenca/manifesto ausente no JAR $kind" }
        $classNames = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach ($entry in $jar.Entries) {
            $name = $entry.FullName
            if ($name.EndsWith('/')) { continue }
            if ($name -in @('META-INF/MANIFEST.MF','LICENSE')) { continue }
            $isApi = $name -match '^br/vbrasfoot/api/[A-Za-z0-9_$]+\.class$'
            $isTool = $kind -eq 'validator' -and $name -in @('br/vbrasfoot/SdkValidator.class','br/vbrasfoot/ModManifest.class','br/vbrasfoot/ModValidator.class')
            if (-not $isApi -and -not $isTool) { throw "Classe/recurso nao permitido no JAR $kind : $name" }
            if (-not $classNames.Add($name)) { throw "Classe duplicada no SDK: $name" }
        }
        foreach ($required in @('br/vbrasfoot/api/Sdk.class','br/vbrasfoot/api/Mod.class','br/vbrasfoot/api/GameAccess.class')) { if (-not $classNames.Contains($required)) { throw "API incompleta: $required" } }
        if ($kind -eq 'validator' -and -not $classNames.Contains('br/vbrasfoot/SdkValidator.class')) { throw 'Validador ausente' }
    } finally { $jar.Dispose() }
}
Write-Host "PASS: SDK $version, hashes, allowlist e namespaces; nenhuma classe inicializada. Checksums nao sao assinatura/autenticacao."
