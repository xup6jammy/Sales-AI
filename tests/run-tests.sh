#!/usr/bin/env sh
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC_MAIN="$ROOT/src/main/java"
SRC_TEST="$ROOT/src/test/java"
OUT="$ROOT/out-test"

rm -rf "$OUT"
mkdir -p "$OUT"

# On Windows (Git Bash) javac needs Windows-style paths; convert if cygpath is available
to_native() {
  if command -v cygpath > /dev/null 2>&1; then
    cygpath -w "$1"
  else
    echo "$1"
  fi
}

OUT_NATIVE="$(to_native "$OUT")"
SOURCES_FILE="$OUT/sources.txt"

# Classpath separator: ';' on Windows (cygpath present), ':' on POSIX
SEP=":"
command -v cygpath > /dev/null 2>&1 && SEP=";"

# Compile main + test together
find "$SRC_MAIN" "$SRC_TEST" -name '*.java' | while IFS= read -r f; do
  to_native "$f"
done > "$SOURCES_FILE"

LIBCP="$(to_native "$ROOT/mcp-server/lib/*")"
javac -d "$OUT_NATIVE" -cp "$LIBCP" "@$(to_native "$SOURCES_FILE")"

# Discover and run every *Test class
TESTS=$(find "$SRC_TEST" -name '*Test.java' \
        -exec sh -c 'echo "${0#*src/test/java/}" | sed -e "s|/|.|g" -e "s|\.java$||"' {} \;)

PASSED=0
FAILED=0
for t in $TESTS; do
  echo "==> $t"
  if java -ea -cp "$OUT_NATIVE${SEP}$LIBCP" "$t"; then
    PASSED=$((PASSED+1))
  else
    FAILED=$((FAILED+1))
  fi
done
echo
echo "Total: $((PASSED+FAILED)) - Passed: $PASSED - Failed: $FAILED"
[ "$FAILED" -eq 0 ]
