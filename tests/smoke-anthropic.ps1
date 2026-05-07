$ErrorActionPreference = 'Stop'
$Root  = (Resolve-Path "$PSScriptRoot\..").Path
$Out   = Join-Path $Root 'out-smoke'

if (-not $env:ANTHROPIC_API_KEY) {
  Write-Host "SKIP: ANTHROPIC_API_KEY not set"
  exit 0
}

if (-not (Test-Path $Out)) {
  Write-Host "Run tests\smoke-template.ps1 first to compile" -ErrorAction Continue
  exit 1
}

$LibCp = (Join-Path $Root 'mcp-server\lib\*')

Write-Host "=== mock + Anthropic (low risk -> real LLM call) ==="
& java -cp "$Out;$LibCp" com.example.salesai.SalesAiCli --llm anthropic

Write-Host ""
Write-Host "=== mock + Anthropic with --approve flag ==="
& java -cp "$Out;$LibCp" com.example.salesai.SalesAiCli --llm anthropic --approve

Write-Host ""
Write-Host "Verify above outputs include LLM_CALL audit lines (if reaching the LLM)"
Write-Host "or DRAFTS_BLOCKED_BY_RISK_GATE (if mock email triggers HIGH risk)."
