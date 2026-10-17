$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $classes = Join-Path $PSScriptRoot 'build\classes'
    $tests = Join-Path $PSScriptRoot 'build\tests'
    $example = Join-Path $PSScriptRoot 'build\inspector'
    foreach ($dir in @($classes, $tests, $example, 'dist', 'mods')) {
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
    }
    $sources = @(Get-ChildItem 'src' -Recurse -Filter '*.java' | ForEach-Object FullName)
    & javac --release 17 -encoding UTF-8 -d $classes @sources
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar vbrasfoot' }
    $hookSources = @(Get-ChildItem 'hook-src' -Recurse -Filter '*.java' | ForEach-Object FullName)
    & javac -source 17 -target 17 -Xlint:-options '--add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED' -encoding UTF-8 -cp $classes -d $classes @hookSources
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar sonda interna' }
    & jar --create --file 'dist\vbrasfoot.jar' --main-class br.vbrasfoot.Main -C $classes .
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao empacotar vbrasfoot' }
    & jar --create --file 'dist\vbrasfoot-api.jar' -C $classes br/vbrasfoot/api
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao empacotar API' }
    $exampleSources = @(Get-ChildItem 'examples\inspector\src' -Recurse -Filter '*.java' | ForEach-Object FullName)
    & javac --release 17 -encoding UTF-8 -cp 'dist\vbrasfoot-api.jar' -d $example @exampleSources
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar mod de exemplo' }
    & jar --create --file 'mods\inspector.jar' -C $example . -C 'examples\inspector' vbrasfoot.mod.properties
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao empacotar mod de exemplo' }
    $testSources = @(Get-ChildItem 'test' -Recurse -Filter '*.java' | ForEach-Object FullName)
    & javac --release 17 -encoding UTF-8 -cp $classes -d $tests @testSources
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar testes' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.SelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.HookProbeSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes da sonda falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.ProbeDispatcherSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes da fila da sonda falharam' }
    & java '-Djava.awt.headless=true' '--add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED' -cp "$classes;$tests" br.vbrasfoot.hooks.HookTransformerSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de bytecode sintetico falharam' }
    Write-Host 'Build e testes concluidos. Execute .\run.ps1 --validate-only'
} finally { Pop-Location }
