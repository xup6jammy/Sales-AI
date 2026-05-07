#!/usr/bin/env sh
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

OUT="$ROOT/out-smoke"
rm -rf "$OUT"
mkdir -p "$OUT"

# Compile main only (no tests). On Git Bash use cygpath if available.
to_native() {
  if command -v cygpath > /dev/null 2>&1; then cygpath -w "$1"; else echo "$1"; fi
}
SEP=":"
command -v cygpath > /dev/null 2>&1 && SEP=";"
OUT_NATIVE="$(to_native "$OUT")"
LIBCP="$(to_native "$ROOT/mcp-server/lib/*")"
find "$ROOT/src/main/java" -name '*.java' > "$OUT/sources.txt"
javac -d "$OUT_NATIVE" -cp "$LIBCP" "@$(to_native "$OUT/sources.txt")" || {
  echo "javac failed" >&2; exit 1;
}

echo "=== mock + template (must work, zero env) ==="
java -cp "$OUT_NATIVE${SEP}$LIBCP" com.example.salesai.SalesAiCli
