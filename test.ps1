param([switch]$IncludeUi, [string]$Save)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    & "$PSScriptRoot\build.ps1"
    & "$PSScriptRoot\run.ps1" --validate-only
    if ($LASTEXITCODE -ne 0) { throw 'Validacao falhou' }
    if ($IncludeUi) {
        $classpath = "$PSScriptRoot\build\classes;$PSScriptRoot\build\tests"
        $game = (Resolve-Path '..\Brasfoot22-23').Path
        & java -cp $classpath br.vbrasfoot.UiSmokeTest $game $PSScriptRoot
        if ($LASTEXITCODE -ne 0) { throw 'Teste Swing falhou' }
    }
    if ($Save) {
        $savePath = (Resolve-Path $Save).Path
        & "$PSScriptRoot\run.ps1" --check-save $savePath
        if ($LASTEXITCODE -ne 0) { throw 'Verificacao do save falhou' }
    }
} finally { Pop-Location }
