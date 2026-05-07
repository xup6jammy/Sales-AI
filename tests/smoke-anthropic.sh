#!/usr/bin/env sh
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [ -z "$ANTHROPIC_API_KEY" ]; then
  echo "SKIP: ANTHROPIC_API_KEY not set"
  exit 0
fi

# Reuse compiled classes from the test runner (or compile if needed).
OUT="$ROOT/out-smoke"
if [ ! -d "$OUT" ]; then
  echo "Run tests/smoke-template.sh first to compile" >&2
  exit 1
fi

to_native() {
  if command -v cygpath > /dev/null 2>&1; then cygpath -w "$1"; else echo "$1"; fi
}
SEP=":"
command -v cygpath > /dev/null 2>&1 && SEP=";"
OUT_NATIVE="$(to_native "$OUT")"
LIBCP="$(to_native "$ROOT/mcp-server/lib/*")"

echo "=== mock + Anthropic (low risk → real LLM call) ==="
java -cp "$OUT_NATIVE${SEP}$LIBCP" com.example.salesai.SalesAiCli --llm anthropic

echo
echo "=== mock + Anthropic with --approve flag ==="
java -cp "$OUT_NATIVE${SEP}$LIBCP" com.example.salesai.SalesAiCli --llm anthropic --approve

echo
echo "Verify above outputs include LLM_CALL audit lines (if reaching the LLM)"
echo "or DRAFTS_BLOCKED_BY_RISK_GATE (if mock email triggers HIGH risk)."
