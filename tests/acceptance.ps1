$ErrorActionPreference = 'Continue'
$Root  = (Resolve-Path "$PSScriptRoot\..").Path

$Passed = 0; $Failed = 0; $Skipped = 0
function Section($n) { Write-Host ""; Write-Host "==> $n" -ForegroundColor Cyan }
function Pass()      { $script:Passed++; Write-Host "  PASS" -ForegroundColor Green }
function FailMsg($m) { $script:Failed++; Write-Host "  FAIL: $m" -ForegroundColor Red }
function SkipMsg($m) { $script:Skipped++; Write-Host "  SKIP: $m" -ForegroundColor Yellow }

# ---------------------------------------------------------------------------
# Compile (reuse out-smoke if it already exists from a previous smoke run)
# ---------------------------------------------------------------------------
$Out = Join-Path $Root 'out-smoke'
if (-not (Test-Path $Out)) {
    Write-Host "Compiling (out-smoke missing — running smoke-template.ps1)..." -ForegroundColor Gray
    & "$PSScriptRoot\smoke-template.ps1" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  ERROR: compilation failed — cannot continue" -ForegroundColor Red
        exit 1
    }
}
$LibCp = (Join-Path $Root 'mcp-server\lib\*')
$Cp    = "$Out;$LibCp"

# ---------------------------------------------------------------------------
# AC5: Mock mode still works (60-second demo unchanged)
# ---------------------------------------------------------------------------
Section "AC5: Mock mode still works (60-second demo unchanged)"
$out5 = & java -cp $Cp com.example.salesai.SalesAiCli 2>&1 | Out-String
if ($LASTEXITCODE -eq 0) { Pass } else { FailMsg "exit code $LASTEXITCODE" }

# ---------------------------------------------------------------------------
# AC7: Unit tests pass
# ---------------------------------------------------------------------------
Section "AC7: Unit tests pass"
& "$PSScriptRoot\run-tests.ps1" | Out-Null
if ($LASTEXITCODE -eq 0) { Pass } else { FailMsg "test runner exit $LASTEXITCODE" }

# ---------------------------------------------------------------------------
# AC6: All 4 READMEs document new flags + perimeter caveat
# ---------------------------------------------------------------------------
Section "AC6: All 4 READMEs document new flags + perimeter caveat"
$readmes = @('README.md', 'README.zh-TW.md', 'README.ja.md', 'README.ko.md')
$missing = @()
foreach ($r in $readmes) {
    $path = Join-Path $Root $r
    if (-not (Test-Path $path)) { $missing += "$r (file missing)"; continue }
    $body = Get-Content $path -Raw
    if ($body -notmatch '--llm') { $missing += "$r (--llm flag undocumented)" }
    if ($body -notmatch 'openai-compatible') { $missing += "$r (perimeter caveat / openai-compatible missing)" }
    if ($body -notmatch '--email-mcp') { $missing += "$r (--email-mcp flag undocumented)" }
}
if ($missing.Count -eq 0) { Pass } else { FailMsg ($missing -join '; ') }

# ---------------------------------------------------------------------------
# AC2: High-risk mock email → drafts blocked, NO LLM call
#
# The default mock customer (Wei-Ming Chen / Lumora) triggers
# REQUIRES_MANAGER_APPROVAL.  Run with --llm anthropic but a fake key —
# the risk gate must short-circuit BEFORE any API call.  If the gate works,
# output contains DRAFTS_BLOCKED_BY_RISK_GATE and does NOT contain LLM_CALL
# or a 401 error leaking out.
# ---------------------------------------------------------------------------
Section "AC2: High-risk mock email -> drafts blocked, NO LLM call"
$env:ANTHROPIC_API_KEY = 'sk-ant-fake-for-testing-only'
$out2 = & java -cp $Cp com.example.salesai.SalesAiCli --llm anthropic 2>&1 | Out-String
$ok2  = ($out2 -match 'DRAFTS_BLOCKED_BY_RISK_GATE') -and `
        ($out2 -notmatch 'LLM_CALL') -and `
        ($out2 -notmatch 'anthropic 401')
Remove-Item Env:\ANTHROPIC_API_KEY -ErrorAction SilentlyContinue
if ($ok2) { Pass } else {
    FailMsg "either no DRAFTS_BLOCKED_BY_RISK_GATE, or LLM_CALL appeared, or 401 leaked"
}

# ---------------------------------------------------------------------------
# AC1: Full mode end-to-end (real Gmail + real Anthropic + JDBC SQLite)
# Requires: ANTHROPIC_API_KEY, OAuth-configured Gmail MCP, non-blocked email
# ---------------------------------------------------------------------------
Section "AC1: Full mode end-to-end (real Gmail + real Anthropic + JDBC)"
if (-not $env:ANTHROPIC_API_KEY) {
    SkipMsg "ANTHROPIC_API_KEY not set; manual verification required (needs OAuth-configured Gmail MCP + customer DB + non-blocked email)"
} else {
    SkipMsg "manual verification required (needs OAuth-configured Gmail MCP + customer DB + non-blocked email)"
}

# ---------------------------------------------------------------------------
# AC3: Local LLM via openai-compatible endpoint
# Requires: Ollama or compatible server running on localhost:11434
# Command: java -cp <cp> com.example.salesai.SalesAiCli \
#            --llm openai-compatible --llm-endpoint http://localhost:11434/v1
# ---------------------------------------------------------------------------
Section "AC3: Local LLM via openai-compatible endpoint"
SkipMsg "manual verification required (needs Ollama or compatible server running on localhost:11434)"

# ---------------------------------------------------------------------------
# AC4: Audit log includes one LlmCallAuditEntry per LLM call with all 13 fields
# Covered by AnthropicLlmClient + LlmReplyDraftAdapter unit tests (AC7).
# Full end-to-end audit field verification needs a real LLM call (AC1 / AC3).
# ---------------------------------------------------------------------------
Section "AC4: Audit entry has all 13 fields populated"
SkipMsg "covered by AnthropicLlmClient + LlmReplyDraftAdapter unit tests; manual end-to-end verification needs real LLM call (run AC1 or AC3 and inspect audit log)"

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
Write-Host ""
Write-Host "Total: $($Passed+$Failed+$Skipped)  --  Passed: $Passed  --  Failed: $Failed  --  Skipped: $Skipped"
if ($Failed -gt 0) { exit 1 }
