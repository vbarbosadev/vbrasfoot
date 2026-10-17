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
    & jar --create --file 'dist\vbrasfoot-validator.jar' --main-class br.vbrasfoot.SdkValidator -C $classes br/vbrasfoot/api -C $classes br/vbrasfoot/SdkValidator.class -C $classes br/vbrasfoot/ModManifest.class -C $classes br/vbrasfoot/ModValidator.class -C $PSScriptRoot LICENSE
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao empacotar validador target-free' }
    $exampleSources = @(Get-ChildItem 'examples\inspector\src' -Recurse -Filter '*.java' | ForEach-Object FullName)
    & javac --release 17 -encoding UTF-8 -cp 'dist\vbrasfoot-api.jar' -d $example @exampleSources
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar mod de exemplo' }
    & jar --create --file 'mods\inspector.jar' -C $example . -C 'examples\inspector' vbrasfoot.mod.properties
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao empacotar mod de exemplo' }
    $observer = Join-Path $PSScriptRoot 'build\career-observer'
    New-Item -ItemType Directory -Force -Path $observer | Out-Null
    $observerSources = @(Get-ChildItem 'examples\career-observer\src' -Recurse -Filter '*.java' | ForEach-Object FullName)
    & javac --release 17 -encoding UTF-8 -cp 'dist\vbrasfoot-api.jar' -d $observer @observerSources
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar observador contra SDK publico' }
    & jar --create --file 'dist\career-observer.jar' -C $observer . -C 'examples\career-observer' vbrasfoot.mod.properties -C $PSScriptRoot LICENSE
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao empacotar observador experimental' }
    $testSources = @(Get-ChildItem 'test' -Recurse -Filter '*.java' | ForEach-Object FullName)
    & javac --release 17 -encoding UTF-8 -cp $classes -d $tests @testSources
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar testes' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.SelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.ModSelectionSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de selecao de mods falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.ModFailuresSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de historico de falhas falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.ModContextLifecycleSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de lifecycle do contexto falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.ModJarIsolationSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de isolamento de JARs de mods falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.StorageInventorySelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de inventario somente leitura falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.SdkValidatorJsonSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes JSON do validador SDK falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.DiagnosticExportSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de exportacao diagnostica falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.QueryMeasurementsSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de estatisticas de consulta falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.GetterCacheSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de cache de getters falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.WindowRecoverySelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de recuperacao de janela falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.HookProbeSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes da sonda falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.ProbeDispatcherSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes da fila da sonda falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.CareerLifecycleSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de lifecycle interno falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.CareerObservationsSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de assinaturas de carreira falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.QueryScopesSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de escopo de consulta falharam' }
    & java '-Djava.awt.headless=true' '--add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED' -cp "$classes;$tests" br.vbrasfoot.hooks.HookTransformerSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de bytecode sintetico falharam' }
    & java '-Djava.awt.headless=true' '--add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED' -cp "$classes;$tests" br.vbrasfoot.hooks.CreationPathTransformerSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes da sonda de candidatos de criacao falharam' }
    & "$PSScriptRoot\templates\read-only-mod\build.ps1" -Sdk "$PSScriptRoot\dist\vbrasfoot-api.jar"
    if ($LASTEXITCODE -ne 0) { throw 'Build independente do template falhou' }
    & java -jar "$PSScriptRoot\dist\vbrasfoot.jar" --validate-mod "$PSScriptRoot\templates\read-only-mod\dist\meu-mod.jar"
    if ($LASTEXITCODE -ne 0) { throw 'Validacao individual do template falhou' }
    & "$PSScriptRoot\examples\career-observer\build.ps1" -Sdk "$PSScriptRoot\dist\vbrasfoot-api.jar"
    if ($LASTEXITCODE -ne 0) { throw 'Build independente do observador falhou' }
    & "$PSScriptRoot\examples\player-comparator\build.ps1" -Sdk "$PSScriptRoot\dist\vbrasfoot-api.jar"
    if ($LASTEXITCODE -ne 0) { throw 'Build independente do comparador falhou' }
    & java -jar "$PSScriptRoot\dist\vbrasfoot-validator.jar" --json "$PSScriptRoot\examples\player-comparator\dist\player-comparator.jar"
    if ($LASTEXITCODE -ne 0) { throw 'Validador recusou comparador' }
    Write-Host 'Build e testes concluidos. Execute .\run.ps1 --validate-only'
} finally { Pop-Location }
