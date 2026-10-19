param(
    [string]$GameDir = (Join-Path $PSScriptRoot '..\Brasfoot22-23'),
    [string]$Destination = (Join-Path $env:LOCALAPPDATA ('Temp\opencode\vbrasfoot-load-validation-' + [guid]::NewGuid().ToString('N'))),
    [switch]$Preview
)
$ErrorActionPreference = 'Stop'
if ($Preview) {
    & "$PSScriptRoot\prepare-validation.ps1" -GameDir $GameDir -Destination $Destination -Preview
    Write-Host 'Apos copia verificada: cria launcher de sonda e home NOVO sem mods; nao inicia jogo ou carga.'
    return
}
& "$PSScriptRoot\prepare-validation.ps1" -GameDir $GameDir -Destination $Destination
$target = (Resolve-Path -LiteralPath $Destination).Path
if (-not (Test-Path -LiteralPath (Join-Path $target 'baseline.json'))) { throw 'Baseline da copia ausente; preparacao incompleta preservada.' }
$probe = Join-Path $target 'load-probe'
[IO.Directory]::CreateDirectory($probe) | Out-Null
Copy-Item -LiteralPath (Join-Path $target 'loader\vbrasfoot.jar') -Destination (Join-Path $probe 'vbrasfoot.jar')
$launcher = @'
@echo off
setlocal DisableDelayedExpansion
cd /d "%~dp0Brasfoot22-23"
echo COPIA ISOLADA. Carregue uma carreira EXISTENTE pelo menu.
echo Nao avance, nao jogue, nao salve e nao confirme metadados neste roteiro.
echo Se surgir qualquer dialogo inesperado, registre e cancele; nao aceite por tentativa.
java --add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports=java.desktop/sun.java2d=ALL-UNNAMED -Xmx1536m -jar "%~dp0load-probe\vbrasfoot.jar" --home "%~dp0load-probe" --game-dir "%~dp0Brasfoot22-23" --probe-career-hooks
pause
'@
$inspector = @'
@echo off
setlocal DisableDelayedExpansion
if "%~1"=="" (
 echo Uso: inspect-load.cmd "caminho explicito de load-probe\logs\session-ID encerrada"
 exit /b 2
)
java -jar "%~dp0load-probe\vbrasfoot.jar" --inspect-load-validation "%~1"
pause
'@
Set-Content -LiteralPath (Join-Path $target 'probe-load.cmd') -Value $launcher -Encoding ASCII
Set-Content -LiteralPath (Join-Path $target 'inspect-load.cmd') -Value $inspector -Encoding ASCII
$instructions = @'
VALIDACAO DELIMITADA DE CARGA EXISTENTE - NAO ACEITE DE GAMEPLAY
1. Feche outras instancias normalmente. Esta pasta e uma copia, nao a instalacao pessoal.
2. Abra probe-load.cmd. Carregue um save EXISTENTE pelo menu original. Nao crie carreira.
3. Nao avance calendario, jogue, salve, clique em simulacao ou confirme metadados.
4. No elenco, abra vbrasfoot > Carreira e diarios. Apenas Verificar; UUIDs podem ficar vazios.
5. Registre se a origem/geracao conferem, screenshots/erros e o que realmente foi observado.
6. Feche normalmente SEM salvar. Se o original tentar autosave/progressao/dialogos, registre;
   nao mude configuracoes nem confirme por tentativa. O roteiro pode ficar nao qualificado.
7. Escolha EXPLICITAMENTE a pasta encerrada load-probe/logs/session-ID indicada no console.
8. Execute inspect-load.cmd "caminho completo dessa sessao". Nao escolher latest por heuristica.
9. QUALIFIED_LOAD_OBSERVED e somente coerencia de markers; nao autoriza escrita nem prova
   consistencia global, ausencia de progressao, compatibilidade total ou aceite humano.
10. Preserve baseline.json e logs privados; nao redistribua jogo/saves ou paths pessoais.
A preparacao nao abriu o jogo. A origem nao foi alterada. Nada sera instalado automaticamente.
'@
Set-Content -LiteralPath (Join-Path $target 'INSTRUCOES-CARGA.txt') -Value $instructions -Encoding ASCII
$jarHash = (Get-FileHash -LiteralPath (Join-Path $probe 'vbrasfoot.jar')).Hash.ToLowerInvariant()
[pscustomobject]@{ Format = 1; Prepared = (Get-Date).ToString('o'); LoaderSHA256 = $jarHash; AutomaticLaunch = $false; WriteAuthority = $false } |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $target 'load-validation.json') -Encoding UTF8
Write-Host "Copia de carga preparada, NAO executada: $target"
Write-Host 'Leia INSTRUCOES-CARGA.txt; probe-load.cmd inicia somente por acao explicita.'
