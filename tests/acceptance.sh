#!/usr/bin/env sh
# acceptance.sh — validates all 7 acceptance criteria from the design spec.
# ACs that need external resources (API keys, Gmail OAuth, Ollama) are skipped
# with clear messages; AC2, AC5, AC6, AC7 run without any external deps.

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PASSED=0; FAILED=0; SKIPPED=0

section() { printf '\n==> %s\n' "$1"; }
pass()     { PASSED=$((PASSED+1));   printf '  PASS\n'; }
fail_msg() { FAILED=$((FAILED+1));   printf '  FAIL: %s\n' "$1"; }
skip_msg() { SKIPPED=$((SKIPPED+1)); printf '  SKIP: %s\n' "$1"; }

# ---------------------------------------------------------------------------
# Helpers: path conversion for Git Bash on Windows
# ---------------------------------------------------------------------------
to_native() {
  if command -v cygpath > /dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi
}
SEP=":"
command -v cygpath > /dev/null 2>&1 && SEP=";"

# ---------------------------------------------------------------------------
# Compile (reuse out-smoke if present)
# ---------------------------------------------------------------------------
OUT="$ROOT/out-smoke"
OUT_NATIVE="$(to_native "$OUT")"
LIBCP="$(to_native "$ROOT/mcp-server/lib/*")"
CP="${OUT_NATIVE}${SEP}${LIBCP}"

if [ ! -d "$OUT" ]; then
  printf 'Compiling (out-smoke missing — running smoke-template.sh)...\n'
  sh "$ROOT/tests/smoke-template.sh"
  if [ $? -ne 0 ]; then
    printf '  ERROR: compilation failed — cannot continue\n'
    exit 1
  fi
fi

# ---------------------------------------------------------------------------
# AC5: Mock mode still works (60-second demo unchanged)
# ---------------------------------------------------------------------------
section "AC5: Mock mode still works (60-second demo unchanged)"
if java -cp "$CP" com.example.salesai.SalesAiCli > /dev/null 2>&1; then
  pass
else
  fail_msg "non-zero exit from SalesAiCli (mock/template mode)"
fi

# ---------------------------------------------------------------------------
# AC7: Unit tests pass
# ---------------------------------------------------------------------------
section "AC7: Unit tests pass"
if sh "$ROOT/tests/run-tests.sh" > /dev/null 2>&1; then
  pass
else
  fail_msg "run-tests.sh returned non-zero"
fi

# ---------------------------------------------------------------------------
# AC6: All 4 READMEs document new flags + perimeter caveat
# ---------------------------------------------------------------------------
section "AC6: All 4 READMEs document new flags + perimeter caveat"
missing=""
for r in README.md README.zh-TW.md README.ja.md README.ko.md; do
  path="$ROOT/$r"
  if [ ! -f "$path" ]; then
    missing="${missing}${r} (file missing); "
    continue
  fi
  if ! grep -q -- '--llm' "$path"; then
    missing="${missing}${r} (--llm flag undocumented); "
  fi
  if ! grep -q 'openai-compatible' "$path"; then
    missing="${missing}${r} (perimeter caveat / openai-compatible missing); "
  fi
  if ! grep -q -- '--email-mcp' "$path"; then
    missing="${missing}${r} (--email-mcp flag undocumented); "
  fi
done
if [ -z "$missing" ]; then
  pass
else
  fail_msg "$missing"
fi

# ---------------------------------------------------------------------------
# AC2: High-risk mock email → drafts blocked, NO LLM call
#
# The default mock customer (Wei-Ming Chen / Lumora) triggers
# REQUIRES_MANAGER_APPROVAL.  Run with --llm anthropic but a fake key —
# the risk gate must short-circuit BEFORE any API call.  If the gate works,
# output contains DRAFTS_BLOCKED_BY_RISK_GATE and does NOT contain LLM_CALL
# or a 401 error leaking out.
# ---------------------------------------------------------------------------
section "AC2: High-risk mock email -> drafts blocked, NO LLM call"
ANTHROPIC_API_KEY='sk-ant-fake-for-testing-only'
export ANTHROPIC_API_KEY
out2="$(java -cp "$CP" com.example.salesai.SalesAiCli --llm anthropic 2>&1)"
unset ANTHROPIC_API_KEY

has_blocked=0; has_llm_call=0; has_401=0
printf '%s' "$out2" | grep -q 'DRAFTS_BLOCKED_BY_RISK_GATE' && has_blocked=1
printf '%s' "$out2" | grep -q 'LLM_CALL'                    && has_llm_call=1
printf '%s' "$out2" | grep -q 'anthropic 401'               && has_401=1

if [ "$has_blocked" -eq 1 ] && [ "$has_llm_call" -eq 0 ] && [ "$has_401" -eq 0 ]; then
  pass
else
  fail_msg "either no DRAFTS_BLOCKED_BY_RISK_GATE, or LLM_CALL appeared, or 401 leaked"
fi

# ---------------------------------------------------------------------------
# AC1: Full mode end-to-end (real Gmail + real Anthropic + JDBC SQLite)
# Requires: ANTHROPIC_API_KEY, OAuth-configured Gmail MCP, non-blocked email
# ---------------------------------------------------------------------------
section "AC1: Full mode end-to-end (real Gmail + real Anthropic + JDBC)"
if [ -z "$ANTHROPIC_API_KEY" ]; then
  skip_msg "ANTHROPIC_API_KEY not set; manual verification required (needs OAuth-configured Gmail MCP + customer DB + non-blocked email)"
else
  skip_msg "manual verification required (needs OAuth-configured Gmail MCP + customer DB + non-blocked email)"
fi

# ---------------------------------------------------------------------------
# AC3: Local LLM via openai-compatible endpoint
# Requires: Ollama or compatible server running on localhost:11434
# Command: java -cp <cp> com.example.salesai.SalesAiCli \
#            --llm openai-compatible --llm-endpoint http://localhost:11434/v1
# ---------------------------------------------------------------------------
section "AC3: Local LLM via openai-compatible endpoint"
skip_msg "manual verification required (needs Ollama or compatible server running on localhost:11434)"

# ---------------------------------------------------------------------------
# AC4: Audit log includes one LlmCallAuditEntry per LLM call with all 13 fields
# Covered by AnthropicLlmClient + LlmReplyDraftAdapter unit tests (AC7).
# Full end-to-end audit field verification needs a real LLM call (AC1 / AC3).
# ---------------------------------------------------------------------------
section "AC4: Audit entry has all 13 fields populated"
skip_msg "covered by AnthropicLlmClient + LlmReplyDraftAdapter unit tests; manual end-to-end verification needs real LLM call (run AC1 or AC3 and inspect audit log)"

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
printf '\nTotal: %d  --  Passed: %d  --  Failed: %d  --  Skipped: %d\n' \
  "$((PASSED+FAILED+SKIPPED))" "$PASSED" "$FAILED" "$SKIPPED"
[ "$FAILED" -eq 0 ]
