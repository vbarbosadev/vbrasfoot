param([string]$Output)
$ErrorActionPreference = 'Stop'
if (-not $Output) { $Output = Join-Path $PSScriptRoot 'dist\sdk-author' }
$root = [IO.Path]::GetFullPath($Output)
$archive = $root + '.zip'
$sidecar = $archive + '.sha256'
function Assert-PlainPath([string]$Path) {
    for ($p = [IO.Path]::GetFullPath($Path); $p; $p = Split-Path $p -Parent) {
        $item = Get-Item -LiteralPath $p -Force -ErrorAction SilentlyContinue
        if ($item -and ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw "Link/junction recusado: $p" }
    }
}
foreach ($path in @($root,$archive,$sidecar)) { Assert-PlainPath $path; if (Test-Path -LiteralPath $path) { throw "Escolha destino novo; nao sera sobrescrito: $path" } }
$sdkSource = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'src\br\vbrasfoot\api\Sdk.java') -Raw
if ($sdkSource -notmatch 'String VERSION = "(\d+\.\d+\.\d+)"') { throw 'Versao do SDK nao encontrada' }
$version = $Matches[1]
$build = Join-Path $PSScriptRoot ('build\sdk-author-' + [guid]::NewGuid().ToString('N'))
$apiClasses = Join-Path $build 'api'
$toolClasses = Join-Path $build 'tool'
$emptySources = Join-Path $build 'empty-sourcepath'
foreach ($directory in @($root,$apiClasses,$toolClasses,$emptySources,(Join-Path $root 'api'),(Join-Path $root 'tools'))) { Assert-PlainPath $directory; [IO.Directory]::CreateDirectory($directory) | Out-Null }
function Copy-Source([string]$Source, [string]$Relative) {
    Assert-PlainPath $Source
    $target = Join-Path $root $Relative
    [IO.Directory]::CreateDirectory((Split-Path $target -Parent)) | Out-Null
    [IO.File]::Copy($Source, $target, $false)
}
$apiJar = Join-Path $root "api\vbrasfoot-api-$version.jar"
$apiSources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src\br\vbrasfoot\api') -Filter '*.java' | ForEach-Object { Assert-PlainPath $_.FullName; $_.FullName })
& javac --release 17 -encoding UTF-8 -sourcepath $emptySources -d $apiClasses @apiSources
if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar API isolada' }
& jar --create --file $apiJar -C $apiClasses . -C $PSScriptRoot LICENSE
if ($LASTEXITCODE -ne 0) { throw 'Falha ao empacotar API isolada' }
$toolSources = @('SdkValidator.java','ModManifest.java','ModValidator.java') | ForEach-Object { Join-Path $PSScriptRoot "src\br\vbrasfoot\$_" }
foreach ($path in $toolSources) { Assert-PlainPath $path }
& javac --release 17 -encoding UTF-8 -sourcepath $emptySources -cp $apiJar -d $toolClasses @toolSources
if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar validador sem loader/jogo' }
& jar --create --file (Join-Path $root "tools\vbrasfoot-validator-$version.jar") --main-class br.vbrasfoot.SdkValidator -C $apiClasses . -C $toolClasses . -C $PSScriptRoot LICENSE
if ($LASTEXITCODE -ne 0) { throw 'Falha ao empacotar validador isolado' }
Copy-Source (Join-Path $PSScriptRoot 'LICENSE') 'LICENSE'
Copy-Source (Join-Path $PSScriptRoot 'sdk-tools\verify-sdk.ps1') 'verify.ps1'
foreach ($name in @('SDK-QUICKSTART.md','SDK-COMPATIBILITY.md','SDK-DISTRIBUTION.md','SDK-CONTRACT.md','SCOPED-QUERIES.md','CAREER-OBSERVATIONS.md','MOD-MANAGEMENT.md','DIAGNOSTIC-EXPORT.md')) { Copy-Source (Join-Path $PSScriptRoot "roadmap\$name") "docs\$name" }
foreach ($relative in @('README.md','LICENSE','build.ps1','install.ps1','vbrasfoot.mod.properties','.gitignore','src\br\exemplo\consulta\ReadOnlyMod.java','test\br\exemplo\consulta\ReadOnlyModTest.java')) { Copy-Source (Join-Path $PSScriptRoot "templates\read-only-mod\$relative") "template-read-only\$relative" }
foreach ($relative in @('README.md','LICENSE','build.ps1','vbrasfoot.mod.properties','.gitignore','src\br\vbrasfoot\observer\CareerObserverMod.java','test\br\vbrasfoot\observer\CareerObserverModTest.java')) { Copy-Source (Join-Path $PSScriptRoot "examples\career-observer\$relative") "examples\career-observer\$relative" }
& javadoc --release 17 -encoding UTF-8 -quiet '-Xdoclint:all,-missing' -d (Join-Path $root 'javadoc') -sourcepath (Join-Path $PSScriptRoot 'src') br.vbrasfoot.api
if ($LASTEXITCODE -ne 0) { throw 'Falha ao gerar Javadocs do SDK' }
$readme = @"
# vbrasfoot SDK $version - experimental

API major 1; Java 17/21. SDK para compilar/testar mods sem jogo ou checkout do loader.
Leia [o guia](docs/SDK-QUICKSTART.md) e [a compatibilidade](docs/SDK-COMPATIBILITY.md).
Javadocs: [javadoc/index.html](javadoc/index.html).

1. Execute verify.ps1 para integridade/allowlist (checksums nao autenticam a origem).
2. Copie template-read-only para projeto novo fora desta pasta.
3. Compile com api/vbrasfoot-api-$version.jar; veja o guia para comandos completos.
4. Valide seu JAR com tools/vbrasfoot-validator-$version.jar, sem inicializar mod/jogo.

Nao inclui loader completo, jogo, saves, bibliotecas proprietarias ou JRE/JDK.
Para jogar/instalar e necessario loader portable compativel e copia local conhecida
do Brasfoot22-23; nao ha bypass de licenca. Nao embuta a API/validador no seu mod.
Sem identidade persistente/eventos completos/gameplay; observador e opt-in experimental.
MIT aplica-se ao codigo original; Javadocs preservam avisos legais do gerador/JDK.
Nao e release estavel nem aceite de todos os gates P0-P7. Nao publica nada em rede.
"@
[IO.File]::WriteAllText((Join-Path $root 'README.md'), $readme, [Text.UTF8Encoding]::new($false))
$metadata = [ordered]@{ format=1; kind='sdk-only'; version=$version; apiMajor=1; gameProfile='brasfoot22-23'; javaRelease=17; experimental=$true }
[IO.File]::WriteAllText((Join-Path $root 'sdk-package.json'), ($metadata | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
$hashes = [ordered]@{}
Get-ChildItem -LiteralPath $root -Recurse -Force -File | Sort-Object FullName | ForEach-Object {
    $relative = $_.FullName.Substring($root.TrimEnd('\').Length + 1).Replace('\','/')
    $hashes[$relative] = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
}
[IO.File]::WriteAllText((Join-Path $root 'checksums.json'), ($hashes | ConvertTo-Json -Depth 4), [Text.UTF8Encoding]::new($false))
& (Join-Path $PSScriptRoot 'sdk-tools\verify-sdk.ps1') -RootPath $root
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archiveStream = [IO.File]::Open($archive, 'CreateNew', 'Write', 'None')
try {
    $zip = [IO.Compression.ZipArchive]::new($archiveStream, [IO.Compression.ZipArchiveMode]::Create, $true)
    try {
        # .NET Framework CreateFromDirectory emits backslashes; use canonical ZIP slashes explicitly.
        foreach ($file in Get-ChildItem -LiteralPath $root -Recurse -Force -File | Sort-Object FullName) {
            Assert-PlainPath $file.FullName
            $relative = $file.FullName.Substring($root.TrimEnd('\').Length + 1).Replace('\','/')
            $entry = $zip.CreateEntry($relative, [IO.Compression.CompressionLevel]::Optimal)
            $input = [IO.File]::OpenRead($file.FullName)
            try { $outputStream = $entry.Open(); try { $input.CopyTo($outputStream) } finally { $outputStream.Dispose() } }
            finally { $input.Dispose() }
        }
    } finally { $zip.Dispose() }
} finally { $archiveStream.Dispose() }
$hash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
$stream = [IO.File]::Open($sidecar, 'CreateNew', 'Write', 'None')
try { $bytes = [Text.Encoding]::UTF8.GetBytes($hash + '  ' + [IO.Path]::GetFileName($archive) + "`n"); $stream.Write($bytes,0,$bytes.Length) } finally { $stream.Dispose() }
Write-Host "SDK experimental criado, sem publicar: $archive"
