param([Parameter(Mandatory=$true)][string]$Sdk, [string]$Output = (Join-Path $PSScriptRoot 'dist'))
$ErrorActionPreference='Stop'
$sdkPath=(Resolve-Path -LiteralPath $Sdk).Path
$work=Join-Path $env:LOCALAPPDATA ('Temp\opencode\loan-window-example-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $work | Out-Null
$sources=@(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src') -Recurse -Filter '*.java' | ForEach-Object FullName)
& javac --release 17 -encoding UTF-8 -cp $sdkPath -d $work @sources
if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar exemplo SDK-only' }
New-Item -ItemType Directory -Path $Output -Force | Out-Null
$jar=Join-Path $Output 'loan-window-example.jar'
if (Test-Path -LiteralPath $jar) { throw 'Escolha destino novo' }
& jar --create --file $jar -C $work . -C $PSScriptRoot vbrasfoot.mod.properties
if ($LASTEXITCODE -ne 0) { throw 'Falha ao empacotar exemplo' }
