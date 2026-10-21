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
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.CareerSidecarStoreSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes do nucleo de sidecar falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.CareerSaveAssociationsSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de associacao explicita de saves falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.CareerRecoverySelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de recuperacao do diario e exclusao de save falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.DecodedSaveProvenanceSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de vinculo entre stream decodificado e roots falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.CareerAssociationCoordinatorSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes do coordenador de associacao explicita falharam' }
    & java '-Djava.awt.headless=true' '--add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED' -cp "$classes;$tests" br.vbrasfoot.CareerIoInterlockSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de exclusao nas entradas originais de I/O falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.BindingJournalInventorySelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de inventario dos diarios do binding falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.LoadValidationInspectorSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes do inspetor offline de carga falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.NoEffectReconciliationSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de reconciliacao limitada sem efeitos falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.CareerOperationalContextSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de contexto operacional interno falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.CareerCommandGateSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes da reserva de comandos falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.CareerCommandJournalSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes do diario de comandos falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.CareerCommandDispatcherSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes do despacho de comandos falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.FinanceDeltaSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de delta financeiro falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.FinanceRequestSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de pedidos financeiros persistentes falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.FinanceRequestStoreSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes do armazenamento de pedidos financeiros falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.WrittenSaveProvenanceSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes da evidencia do stream de save falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.FinanceSaveAssessmentSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes da avaliacao por operacao no save falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.FinanceExecutionWitnessSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes da evidencia duravel de execucao financeira falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.ProvenNoEffectReconciliationSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes da reconciliacao com prova duravel NO_EFFECT falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.FinanceMutationTrackerSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes do tracker de mutacao financeira falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.FinanceSerializationTrackerSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes da captura de operacoes no serializer falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.FinanceFieldEmissionSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de emissao numerica de campos falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.FinanceSerializationEvidenceSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de observacoes duraveis do serializer falharam' }
    & java '-Djava.awt.headless=true' '--add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED' -cp "$classes;$tests" br.vbrasfoot.hooks.FinanceMutationTransformerSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes do transformer de mutacao financeira falharam' }
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
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.EmbeddedPanelsSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de UI integrada falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.HookProbeSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes da sonda falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.CreationContentTrackerSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de metadados de criacao falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.AsyncCreationTrackerSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes do tracker async falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.CalendarSnapshotsSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de calendario/saldo falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.PlayerSnapshotsSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de detalhes de jogadores falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.ProbeDispatcherSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes da fila da sonda falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.CareerLifecycleSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de lifecycle interno falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.CareerObservationsSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de assinaturas de carreira falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.CareerIoEventsSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes do contrato de eventos de I/O falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.QueryScopesSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de escopo de consulta falharam' }
    & java '-Djava.awt.headless=true' '--add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED' -cp "$classes;$tests" br.vbrasfoot.hooks.HookTransformerSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de bytecode sintetico falharam' }
    & java '-Djava.awt.headless=true' '--add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED' -cp "$classes;$tests" br.vbrasfoot.hooks.OutputCloseTransformerSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes do fechamento de stream falharam' }
    & java '-Djava.awt.headless=true' '--add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED' -cp "$classes;$tests" br.vbrasfoot.hooks.CreationPathTransformerSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes da sonda de candidatos de criacao falharam' }
    & java '-Djava.awt.headless=true' '--add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED' -cp "$classes;$tests" br.vbrasfoot.hooks.AsyncCreationTransformerSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes do bytecode async falharam' }
    & java '-Djava.awt.headless=true' '--add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED' -cp "$classes;$tests" br.vbrasfoot.hooks.CreationProgressTransformerSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes do bytecode de progressao falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.CreationDialogTrackerSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes do tracker de dialogo falharam' }
    & java '-Djava.awt.headless=true' '--add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED' -cp "$classes;$tests" br.vbrasfoot.hooks.CreationDialogTransformerSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes do bytecode de dialogo falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.CreationEntryTrackerSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes do tracker de origem falharam' }
    & java '-Djava.awt.headless=true' '--add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED' -cp "$classes;$tests" br.vbrasfoot.hooks.CreationEntryTransformerSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes do bytecode de origem falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.CreationPathQualificationSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de qualificacao estrita falharam' }
    & java '-Djava.awt.headless=true' -cp "$classes;$tests" br.vbrasfoot.CreationQualificationIntegrationSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Testes de integracao da qualificacao falharam' }
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
    & "$PSScriptRoot\examples\career-dashboard\build.ps1" -Sdk "$PSScriptRoot\dist\vbrasfoot-api.jar"
    if ($LASTEXITCODE -ne 0) { throw 'Build independente do painel de carreira falhou' }
    & java -jar "$PSScriptRoot\dist\vbrasfoot-validator.jar" --json "$PSScriptRoot\examples\career-dashboard\dist\career-dashboard.jar"
    if ($LASTEXITCODE -ne 0) { throw 'Validador recusou painel de carreira' }
    Write-Host 'Build e testes concluidos. Execute .\run.ps1 --validate-only'
    & "$PSScriptRoot\examples\player-scout\build.ps1" -Sdk "$PSScriptRoot\dist\vbrasfoot-api.jar"
    if ($LASTEXITCODE -ne 0) { throw 'Build independente do scout falhou' }
    & java -jar "$PSScriptRoot\dist\vbrasfoot-validator.jar" --json "$PSScriptRoot\examples\player-scout\dist\player-scout.jar"
    if ($LASTEXITCODE -ne 0) { throw 'Validador recusou scout' }
} finally { Pop-Location }
