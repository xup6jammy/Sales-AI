$ErrorActionPreference = 'Stop'
$Root  = (Resolve-Path "$PSScriptRoot\..").Path
$Out   = Join-Path $Root 'out-smoke'

if (Test-Path $Out) { Remove-Item -Recurse -Force $Out }
New-Item -ItemType Directory -Path $Out | Out-Null

$Sources = Get-ChildItem -Recurse -Filter *.java -Path (Join-Path $Root 'src\main\java') |
           ForEach-Object { $_.FullName }
$SourceList = Join-Path $Out 'sources.txt'
$Sources | Out-File -Encoding ascii $SourceList

$LibCp = (Join-Path $Root 'mcp-server\lib\*')
& javac -d $Out -cp $LibCp "@$SourceList"
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Host "=== mock + template (must work, zero env) ==="
& java -cp "$Out;$LibCp" com.example.salesai.SalesAiCli
