$ErrorActionPreference = 'Stop'
$Root    = (Resolve-Path "$PSScriptRoot\..").Path
$SrcMain = Join-Path $Root 'src\main\java'
$SrcTest = Join-Path $Root 'src\test\java'
$Out     = Join-Path $Root 'out-test'

if (Test-Path $Out) { Remove-Item -Recurse -Force $Out }
New-Item -ItemType Directory -Path $Out | Out-Null

$Sources = Get-ChildItem -Recurse -Filter *.java -Path $SrcMain,$SrcTest |
           ForEach-Object { $_.FullName }
$SourceList = Join-Path $Out 'sources.txt'
$Sources | Out-File -Encoding ascii $SourceList

$LibCp = (Join-Path $Root 'mcp-server\lib\*')
& javac -d $Out -cp $LibCp "@$SourceList"
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

$Tests = Get-ChildItem -Recurse -Filter '*Test.java' -Path $SrcTest |
         ForEach-Object {
           $rel = $_.FullName.Substring(($SrcTest.Length + 1))
           $rel -replace '\\','.' -replace '\.java$',''
         }

$Passed = 0; $Failed = 0
foreach ($t in $Tests) {
  Write-Host "==> $t"
  & java -ea -cp "$Out;$LibCp" $t
  if ($LASTEXITCODE -eq 0) { $Passed++ } else { $Failed++ }
}
Write-Host ""
Write-Host "Total: $($Passed+$Failed) — Passed: $Passed — Failed: $Failed"
if ($Failed -gt 0) { exit 1 }
