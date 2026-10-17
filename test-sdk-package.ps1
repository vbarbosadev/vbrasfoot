param([Parameter(Mandatory=$true)][string]$Archive)
$ErrorActionPreference = 'Stop'
$archivePath = (Resolve-Path -LiteralPath $Archive).Path
$sidecar = (Get-Content -LiteralPath ($archivePath + '.sha256') -Raw -Encoding UTF8).Trim()
if ($sidecar -notmatch '^([a-fA-F0-9]{64})  ([^/\\\r\n]+\.zip)$') { throw 'Sidecar SHA-256 invalido' }
if ($Matches[2] -cne [IO.Path]::GetFileName($archivePath) -or (Get-FileHash -LiteralPath $archivePath).Hash -ne $Matches[1]) { throw 'ZIP diverge do SHA-256 publicado localmente' }
$tempBase = Join-Path $env:LOCALAPPDATA 'Temp\opencode'
[IO.Directory]::CreateDirectory($tempBase) | Out-Null
$fixture = Join-Path $tempBase ('vbrasfoot-sdk-author-' + [guid]::NewGuid().ToString('N'))
$sdk = Join-Path $fixture 'sdk'
[IO.Directory]::CreateDirectory($fixture) | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($archivePath)
try {
    $names = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    [long]$total = 0
    if ($zip.Entries.Count -gt 4096) { throw 'ZIP com muitos arquivos' }
    foreach ($entry in $zip.Entries) {
        $name = $entry.FullName
        $components = $name.TrimEnd('/').Split('/')
        if (-not $name -or $name.StartsWith('/') -or $name -match '[\\:\x00-\x1f<>|*?]' -or
            ($components | Where-Object { $_ -in @('', '.', '..') -or $_ -match '[. ]$' -or $_ -match '^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\.|$)' }) -or -not $names.Add($name)) { throw "Caminho ZIP invalido/duplicado: $name" }
        if ((($entry.ExternalAttributes -shr 16) -band 0xF000) -eq 0xA000 -or ($entry.ExternalAttributes -band 0x400)) { throw 'Links nao permitidos no SDK' }
        if ($entry.Length -gt 8MB) { throw 'Entrada ZIP excede 8 MiB' }
        $total += $entry.Length
        if ($total -gt 64MB) { throw 'SDK excede 64 MiB descompactados' }
    }
} finally { $zip.Dispose() }
# Only run this test for a trusted, locally generated/reviewed SDK. Checksums are not signatures.
[IO.Compression.ZipFile]::ExtractToDirectory($archivePath, $sdk)
& (Join-Path $PSScriptRoot 'sdk-tools\verify-sdk.ps1') -RootPath $sdk
$metadata = Get-Content -LiteralPath (Join-Path $sdk 'sdk-package.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$version = $metadata.version
$api = Join-Path $sdk "api\vbrasfoot-api-$version.jar"
$validator = Join-Path $sdk "tools\vbrasfoot-validator-$version.jar"
function Run-Validator([string]$Label, [string[]]$Arguments, [int]$Expected = 0) {
    $info = [Diagnostics.ProcessStartInfo]::new((Get-Command java).Source)
    $info.UseShellExecute = $false; $info.RedirectStandardOutput = $true; $info.RedirectStandardError = $true
    $info.WorkingDirectory = $fixture
    $info.Arguments = (@('-jar', $validator) + $Arguments | ForEach-Object { '"' + $_ + '"' }) -join ' '
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $info; $null = $process.Start()
    $output = $process.StandardOutput.ReadToEndAsync(); $errors = $process.StandardError.ReadToEndAsync()
    try {
        if (-not $process.WaitForExit(15000)) { $process.Kill(); throw "Owned SDK validator timeout: $Label" }
        $text = $output.GetAwaiter().GetResult() + $errors.GetAwaiter().GetResult()
        [IO.File]::WriteAllText((Join-Path $fixture "$Label.log"), $text)
        if ($process.ExitCode -ne $Expected) { throw "SDK validator exit inesperado: $Label / $($process.ExitCode)" }
        return $text
    } finally { $process.Dispose() }
}
$identity = Run-Validator 'version' @('--version')
if (-not $identity.Contains("$version | API 1 | brasfoot22-23")) { throw 'Identidade compilada diverge dos metadados do pacote' }
$project = Join-Path $fixture 'meu-mod'
Copy-Item -LiteralPath (Join-Path $sdk 'template-read-only') -Destination $project -Recurse
if (-not (Test-Path -LiteralPath (Join-Path $project '.gitignore'))) { throw 'ZIP perdeu .gitignore' }
$manifest = Join-Path $project 'vbrasfoot.mod.properties'
$text = [IO.File]::ReadAllText($manifest).Replace('id=br.exemplo.consulta', 'id=br.sdk.fixture')
$quotedName = 'Autor "IA" / ' + [char]0x00e7
$text = [regex]::Replace($text, '(?m)^name=[^\r\n]*', ('name=' + $quotedName))
[IO.File]::WriteAllText($manifest, $text, [Text.UTF8Encoding]::new($false))
& (Join-Path $project 'build.ps1') -Sdk $api
if ($LASTEXITCODE -ne 0) { throw 'Template SDK-only fora do checkout falhou' }
$mod = Join-Path $project 'dist\meu-mod.jar'
$result = Run-Validator 'template-validation' @($mod)
if (-not $result.Contains('br.sdk.fixture')) { throw 'Mod personalizado nao reconhecido' }
$description = Run-Validator 'sdk-description' @('--describe-sdk') | ConvertFrom-Json
if ($description.format -ne 1 -or $description.kind -ne 'sdk-description' -or $description.sandbox -ne $false -or $description.sdkVersion -ne $version) { throw 'Contrato machine-readable divergente' }
$jsonMod = Run-Validator 'template-json' @('--json',$mod) | ConvertFrom-Json
if ($jsonMod.valid -ne $true -or $jsonMod.mod.id -ne 'br.sdk.fixture' -or $jsonMod.codeInitialized -ne $false -or $jsonMod.installedSetValidated -ne $false) { throw 'Validacao JSON do mod incorreta' }
if ($jsonMod.mod.name -cne $quotedName) { throw 'JSON perdeu quotes/acentos do manifesto UTF8' }
$observerProject = Join-Path $fixture 'observador'
Copy-Item -LiteralPath (Join-Path $sdk 'examples\career-observer') -Destination $observerProject -Recurse
& (Join-Path $observerProject 'build.ps1') -Sdk $api
if ($LASTEXITCODE -ne 0) { throw 'Observador SDK-only falhou' }
$observer = Join-Path $observerProject 'dist\career-observer.jar'
$null = Run-Validator 'observer-validation' @($observer)
foreach ($jarPath in @($mod,$observer)) {
    $jar = [IO.Compression.ZipFile]::OpenRead($jarPath)
    try {
        foreach ($entry in $jar.Entries) {
            if ($entry.FullName -match '^br/vbrasfoot/api/|Test.*\.class$|^best/|^(a|c)/') { throw 'Plugin embutiu testes/API/classes de jogo' }
        }
    } finally { $jar.Dispose() }
}
# A class whose initializer/constructor/callback would fail must only be linked, never initialized.
$source = Join-Path $fixture 'UninitializedMod.java'
[IO.File]::WriteAllText($source, @'
package fixture;
import br.vbrasfoot.api.*;
public final class UninitializedMod implements Mod {
    static { if (System.nanoTime() != 0) throw new AssertionError("MUST NOT INITIALIZE"); }
    public UninitializedMod() { throw new AssertionError("MUST NOT CONSTRUCT"); }
    public void initialize(ModContext context) { throw new AssertionError("MUST NOT CALL"); }
}
'@)
$classes = Join-Path $fixture 'fixture-classes'
[IO.Directory]::CreateDirectory($classes) | Out-Null
& javac --release 17 -encoding UTF-8 -cp $api -d $classes $source
if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar fixture publica' }
$resources = Join-Path $fixture 'fixture-resources'
[IO.Directory]::CreateDirectory($resources) | Out-Null
[IO.File]::WriteAllText((Join-Path $resources 'vbrasfoot.mod.properties'), "id=fixture`nname=Fixture`nversion=1`napiVersion=1`ngameProfile=brasfoot22-23`nmainClass=fixture.UninitializedMod`n")
$fixtureJar = Join-Path $fixture 'uninitialized.jar'
& jar --create --file $fixtureJar -C $classes . -C $resources vbrasfoot.mod.properties
if ($LASTEXITCODE -ne 0) { throw 'Falha ao empacotar fixture publica' }
$null = Run-Validator 'no-initialization' @($fixtureJar)
$noInit = Run-Validator 'no-initialization-json' @('--json',$fixtureJar) | ConvertFrom-Json
if ($noInit.valid -ne $true -or $noInit.codeInitialized -ne $false) { throw 'JSON inicializou/rejeitou fixture no-init' }
$broken = Join-Path $fixture 'broken.jar'; [IO.File]::WriteAllBytes($broken, [byte[]]@(1,2,3))
$null = Run-Validator 'invalid-refused' @($broken) 1
$invalidJson = Run-Validator 'invalid-json' @('--json',$broken) 1 | ConvertFrom-Json
if ($invalidJson.valid -ne $false -or $invalidJson.error.code -ne 'MANIFEST_REJECTED') { throw 'Erro JSON sem codigo esperado' }
$badArgsJson = Run-Validator 'bad-arguments-json' @('--json') 1 | ConvertFrom-Json
if ($badArgsJson.error.code -ne 'INVALID_ARGUMENTS') { throw 'Erro JSON de argumentos incorreto' }
$null = Run-Validator 'bad-arguments' @('--not-supported') 1
if (Test-Path -LiteralPath (Join-Path $fixture 'vbrasfoot')) { throw 'Validador criou home/cache/logs do loader' }
# Hash-only verification again: builds happened only in copied projects, not inside the SDK.
& (Join-Path $sdk 'verify.ps1')
$apiBefore = (Get-FileHash -LiteralPath $api).Hash
$rejected = $false
try { & (Join-Path $PSScriptRoot 'release-sdk.ps1') -Output $sdk } catch { $rejected = $true }
if (-not $rejected -or (Get-FileHash -LiteralPath $api).Hash -ne $apiBefore) { throw 'Builder aceitou/sobrescreveu destino existente' }
$tampered = Join-Path $fixture 'tampered-sdk'
Copy-Item -LiteralPath $sdk -Destination $tampered -Recurse
[IO.File]::AppendAllText((Join-Path $tampered 'README.md'), 'tamper')
$rejected = $false
try { & (Join-Path $PSScriptRoot 'sdk-tools\verify-sdk.ps1') -RootPath $tampered } catch { $rejected = $true }
if (-not $rejected) { throw 'Hash adulterado aceito' }
# Negative fixtures contain only owned synthetic bytes, never a user's save.
$extra = Join-Path $fixture 'extra-sdk'
Copy-Item -LiteralPath $sdk -Destination $extra -Recurse
[IO.File]::WriteAllText((Join-Path $extra 'private.s22'), 'OWNED_SENTINEL_NOT_GAME_DATA')
$rejected = $false
try { & (Join-Path $PSScriptRoot 'sdk-tools\verify-sdk.ps1') -RootPath $extra } catch { $rejected = $true }
if (-not $rejected) { throw 'Arquivo extra privado aceito' }
$unsafe = Join-Path $fixture 'unsafe-index'
Copy-Item -LiteralPath $sdk -Destination $unsafe -Recurse
[IO.File]::WriteAllText((Join-Path $unsafe 'checksums.json'), ('{"../outside.txt":"' + ('0' * 64) + '"}'))
$rejected = $false
try { & (Join-Path $PSScriptRoot 'sdk-tools\verify-sdk.ps1') -RootPath $unsafe } catch { $rejected = $true }
if (-not $rejected) { throw 'Indice de checksum com traversal aceito' }
$badNamespace = Join-Path $fixture 'bad-namespace'
Copy-Item -LiteralPath $sdk -Destination $badNamespace -Recurse
$unsafeApi = Join-Path $badNamespace "api\vbrasfoot-api-$version.jar"
$jar = [IO.Compression.ZipFile]::Open($unsafeApi, [IO.Compression.ZipArchiveMode]::Update)
try {
    $entry = $jar.CreateEntry('best/ForbiddenFixture.class')
    $stream = $entry.Open(); try { $bytes = [byte[]]@(1,2,3); $stream.Write($bytes,0,$bytes.Length) } finally { $stream.Dispose() }
} finally { $jar.Dispose() }
$indexPath = Join-Path $badNamespace 'checksums.json'
$index = [IO.File]::ReadAllText($indexPath) | ConvertFrom-Json
$index.PSObject.Properties["api/vbrasfoot-api-$version.jar"].Value = (Get-FileHash -LiteralPath $unsafeApi).Hash
[IO.File]::WriteAllText($indexPath, ($index | ConvertTo-Json -Depth 4))
$rejected = $false
try { & (Join-Path $PSScriptRoot 'sdk-tools\verify-sdk.ps1') -RootPath $badNamespace } catch { $rejected = $true }
if (-not $rejected) { throw 'Namespace proibido aceito apesar de checksum consistente' }
Write-Host "PASS: SDK ZIP fora do checkout, hashes/allowlist, template e observador SDK-only, validador sem inicializacao e corrupcao recusada. Sem jogo/gameplay. Fixture: $fixture"
