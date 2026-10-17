param([string]$GameDir, [string]$Save, [ValidateRange(20,1000)][int]$Samples = 100)
$ErrorActionPreference = 'Stop'
if (-not $GameDir) { $GameDir = Join-Path $PSScriptRoot '..\Brasfoot22-23' }
if (-not $Save) { $Save = Join-Path $GameDir 'sav\Flamengo.s22' }
$game = (Resolve-Path -LiteralPath $GameDir).Path
$inputSave = (Resolve-Path -LiteralPath $Save).Path
if (Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'bf22-23.exe' -or ($_.Name -in @('java.exe','javaw.exe') -and $_.CommandLine -like '*vbrasfoot.jar*') }) { throw 'Feche o jogo/loader; nenhum processo do usuario sera encerrado.' }
$before = (Get-FileHash -LiteralPath $inputSave).Hash
$exe = Join-Path $game 'bf22-23.exe'
$exeHash = (Get-FileHash -LiteralPath $exe).Hash
if ($exeHash -ne '2fa65476a92611419ce28c0cfd158a0c7f741af235aa988b0bed00c44443bb1c') { throw 'Perfil desconhecido' }
& (Join-Path $PSScriptRoot 'build.ps1')
if ($LASTEXITCODE -ne 0) { throw 'Build falhou' }
$fixture = Join-Path $env:LOCALAPPDATA ('Temp\opencode\vbrasfoot-query-benchmark-' + [guid]::NewGuid().ToString('N'))
$copy = Join-Path $fixture 'game'
$loaderData = Join-Path $fixture 'loader'
[IO.Directory]::CreateDirectory((Join-Path $copy 'sav')) | Out-Null
[IO.Directory]::CreateDirectory($loaderData) | Out-Null
[IO.File]::Copy($exe, (Join-Path $copy 'bf22-23.exe'), $false)
[IO.File]::Copy($inputSave, (Join-Path $copy 'sav\input.s22'), $false)
[IO.File]::WriteAllText((Join-Path $copy '.query-benchmark-fixture'), 'Owned private read-only query fixture')
if ((Get-FileHash -LiteralPath (Join-Path $copy 'sav\input.s22')).Hash -ne $before) { throw 'Input mudou durante copia' }
$info = [Diagnostics.ProcessStartInfo]::new((Get-Command java).Source)
$info.UseShellExecute = $false; $info.RedirectStandardOutput = $true; $info.RedirectStandardError = $true; $info.WorkingDirectory = $copy
$classpath = "$PSScriptRoot\build\classes;$PSScriptRoot\build\tests"
$info.Arguments = (@('--add-exports=java.desktop/sun.java2d=ALL-UNNAMED','-Xmx1536m','-cp',$classpath,'br.vbrasfoot.CareerQueryBenchmark',$copy,$loaderData,$Samples.ToString()) | ForEach-Object { '"' + $_ + '"' }) -join ' '
$process = [Diagnostics.Process]::new(); $process.StartInfo = $info
$null = $process.Start()
$output = $process.StandardOutput.ReadToEndAsync(); $errors = $process.StandardError.ReadToEndAsync()
try {
    if (-not $process.WaitForExit(180000)) { $process.Kill(); $process.WaitForExit(); throw 'Owned benchmark timed out; fixture preserved, no release conclusion' }
    [IO.File]::WriteAllText((Join-Path $fixture 'benchmark.log'), $output.GetAwaiter().GetResult() + $errors.GetAwaiter().GetResult())
    Get-Content -LiteralPath (Join-Path $fixture 'benchmark.log') -Tail 8
    if ($process.ExitCode -ne 0) { throw "Benchmark refused: $($process.ExitCode)" }
} finally {
    $process.Dispose()
    if ((Get-FileHash -LiteralPath $inputSave).Hash -ne $before -or (Get-FileHash -LiteralPath $exe).Hash -ne $exeHash) { throw 'Origem mudou; medicao invalida' }
}
Write-Host "Fixture privada/report preservados: $fixture"
