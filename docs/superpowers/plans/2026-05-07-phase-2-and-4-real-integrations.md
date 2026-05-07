# Phase 2 + Phase 4 — Real Email & LLM Integrations: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `MockEmailThreadAdapter` with a real MCP-client-driven adapter (Gmail + Outlook), and replace `TemplateReplyDraftAdapter` with a real LLM-backed adapter (Anthropic + OpenAI + Gemini + local LLM via OpenAI-compatible endpoint), while hardening the risk gate as an architectural invariant.

**Architecture:** The engine becomes an MCP client — it spawns external MCP servers as child processes and speaks JSON-RPC 2.0 over stdio to them. LLM calls go directly via `java.net.http` to provider HTTPS endpoints (zero Java deps). The `AdvisorWorkflow` enforces the risk gate before invoking the reply-draft port, so the LLM has no path to high-risk customer data.

**Tech Stack:** Java 21 (records, sealed interfaces, switch expressions, text blocks); JDK `java.net.http.HttpClient`; JDK `ProcessBuilder`; existing `MiniJson`. No new runtime dependencies.

**Reference spec:** [`docs/superpowers/specs/2026-05-07-phase-2-and-4-real-integrations-design.md`](../specs/2026-05-07-phase-2-and-4-real-integrations-design.md)

---

## How to use this plan

- Tasks are numbered by phase: `0.x` = foundation (must run first), `2.x` = Phase 2 (email), `4.x` = Phase 4 (LLM), `F.x` = final polish.
- Tasks within a phase MUST run in order. Phase 4 depends on Phase 0 (audit refactor); Phase 2 is independent.
- Each task ends with a commit. Run the test command after every implementation step to fail fast.
- Tests are pure JDK (`-ea` enables `assert`) — no JUnit. Test class has a `main()` method and runs assertions.
- Test files live in `src/test/java/...` mirroring `src/main/java/...`. The test runner script (Task 0.1) compiles + runs all `*Test.java` mains.
- Where Windows PowerShell vs POSIX shell commands differ, both are shown.

---

## Phase 0 — Foundation (3 tasks)

These are prerequisites. Phase 4 cannot start without 0.2 and 0.3. Phase 2 only needs 0.1.

---

### Task 0.1: Test runner infrastructure

**Files:**
- Create: `tests/run-tests.sh`
- Create: `tests/run-tests.ps1`
- Create: `src/test/java/com/example/salesai/SmokeTest.java`
- Create: `.gitignore` additions for `out-test/`

- [ ] **Step 1: Create the POSIX test runner**

`tests/run-tests.sh`:
```sh
#!/usr/bin/env sh
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC_MAIN="$ROOT/src/main/java"
SRC_TEST="$ROOT/src/test/java"
OUT="$ROOT/out-test"

rm -rf "$OUT"
mkdir -p "$OUT"

# Compile main + test together
find "$SRC_MAIN" "$SRC_TEST" -name '*.java' > "$OUT/sources.txt"
javac -d "$OUT" -cp "$ROOT/mcp-server/lib/*" @"$OUT/sources.txt"

# Discover and run every *Test class
TESTS=$(find "$SRC_TEST" -name '*Test.java' \
        -exec sh -c 'echo "${0#*src/test/java/}" | sed -e "s|/|.|g" -e "s|\.java$||"' {} \;)

PASSED=0
FAILED=0
for t in $TESTS; do
  echo "==> $t"
  if java -ea -cp "$OUT:$ROOT/mcp-server/lib/*" "$t"; then
    PASSED=$((PASSED+1))
  else
    FAILED=$((FAILED+1))
  fi
done
echo
echo "Total: $((PASSED+FAILED)) — Passed: $PASSED — Failed: $FAILED"
[ "$FAILED" -eq 0 ]
```

- [ ] **Step 2: Create the PowerShell test runner**

`tests/run-tests.ps1`:
```powershell
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
```

- [ ] **Step 3: Create the smoke test that proves the runner works**

`src/test/java/com/example/salesai/SmokeTest.java`:
```java
package com.example.salesai;

public final class SmokeTest {
    public static void main(String[] args) {
        new SmokeTest().run();
    }

    void run() {
        testAssertionsAreEnabled();
        System.out.println("SmokeTest: 1 passed");
    }

    void testAssertionsAreEnabled() {
        boolean enabled = false;
        assert enabled = true;
        if (!enabled) {
            throw new AssertionError(
                "Assertions are not enabled — run with `java -ea`");
        }
    }
}
```

- [ ] **Step 4: Update `.gitignore`**

Add these lines to `.gitignore`:
```
out-test/
out/sources.txt
```

- [ ] **Step 5: Run the smoke test**

POSIX:
```sh
chmod +x tests/run-tests.sh && ./tests/run-tests.sh
```
PowerShell:
```powershell
.\tests\run-tests.ps1
```

Expected output ends with: `Total: 1 — Passed: 1 — Failed: 0`

- [ ] **Step 6: Commit**

```sh
git add tests/run-tests.sh tests/run-tests.ps1 \
        src/test/java/com/example/salesai/SmokeTest.java .gitignore
git commit -m "test: add zero-dep test runner (POSIX + PowerShell)"
```

---

### Task 0.2: AuditEntry sealed interface (additive — no callers break)

**Files:**
- Create: `src/main/java/com/example/salesai/audit/AuditEntry.java`
- Create: `src/main/java/com/example/salesai/audit/TextAuditEntry.java`
- Create: `src/test/java/com/example/salesai/audit/AuditEntryTest.java`

This task introduces the new type without removing the old `log(String, String)` API. Phase 4 Task 4.9 finishes the migration.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/example/salesai/audit/AuditEntryTest.java`:
```java
package com.example.salesai.audit;

import java.time.Instant;

public final class AuditEntryTest {
    public static void main(String[] args) {
        new AuditEntryTest().run();
    }

    void run() {
        testTextEntryCarriesEventDetailTimestamp();
        testIsSealed();
        System.out.println("AuditEntryTest: 2 passed");
    }

    void testTextEntryCarriesEventDetailTimestamp() {
        Instant t = Instant.parse("2026-05-07T14:32:18Z");
        TextAuditEntry e = new TextAuditEntry(t, "STEP", "detail");
        assert e.timestamp().equals(t);
        assert e.event().equals("STEP");
        assert e.detail().equals("detail");
    }

    void testIsSealed() {
        // If this compiles, the sealed permits is correctly declared.
        AuditEntry e = new TextAuditEntry(Instant.now(), "X", "Y");
        assert e instanceof TextAuditEntry;
    }
}
```

- [ ] **Step 2: Run test, verify FAIL**

```sh
./tests/run-tests.sh
```
Expected: compile error (AuditEntry / TextAuditEntry don't exist).

- [ ] **Step 3: Implement `AuditEntry`**

`src/main/java/com/example/salesai/audit/AuditEntry.java`:
```java
package com.example.salesai.audit;

import java.time.Instant;

/**
 * Sealed root of all structured audit entries written by the engine.
 *
 * <p>The {@code TextAuditEntry} variant preserves the legacy
 * {@code event/detail} string-pair shape used by the existing workflow.
 * Phase 4 adds {@code LlmCallAuditEntry} as a second permitted variant
 * carrying structured LLM-call telemetry for SOC2 / ISO 27001 traceability.
 */
public sealed interface AuditEntry
        permits TextAuditEntry,
                com.example.salesai.adapters.llm.LlmCallAuditEntry {

    Instant timestamp();
}
```

(`LlmCallAuditEntry` does not exist yet — that's a Phase 4 task. The forward reference compiles fine because Java resolves `permits` at link time when the sealed interface is loaded; we just need the package and class name to match. If the engineer runs tests now and gets "cannot find symbol", they should comment out the `, com.example.salesai.adapters.llm.LlmCallAuditEntry` line until Task 4.2 — there is a re-add step in Task 4.2.)

For Phase 0 to compile cleanly without Phase 4, use the simpler form:

```java
package com.example.salesai.audit;

import java.time.Instant;

public sealed interface AuditEntry permits TextAuditEntry {
    Instant timestamp();
}
```

Task 4.2 will widen the `permits` clause.

- [ ] **Step 4: Implement `TextAuditEntry`**

`src/main/java/com/example/salesai/audit/TextAuditEntry.java`:
```java
package com.example.salesai.audit;

import java.time.Instant;

public record TextAuditEntry(
        Instant timestamp,
        String event,
        String detail) implements AuditEntry {

    public TextAuditEntry {
        if (timestamp == null) throw new IllegalArgumentException("timestamp");
        if (event == null || event.isBlank()) throw new IllegalArgumentException("event");
        if (detail == null) detail = "";
    }
}
```

- [ ] **Step 5: Run test, verify PASS**

```sh
./tests/run-tests.sh
```
Expected: `AuditEntryTest: 2 passed`

- [ ] **Step 6: Commit**

```sh
git add src/main/java/com/example/salesai/audit/ \
        src/test/java/com/example/salesai/audit/
git commit -m "audit: introduce sealed AuditEntry + TextAuditEntry record"
```

---

### Task 0.3: Extend `AuditLogPort` with structured `log(AuditEntry)` (additive)

**Files:**
- Modify: `src/main/java/com/example/salesai/ports/AuditLogPort.java`
- Modify: `src/main/java/com/example/salesai/adapters/ConsoleAuditLogAdapter.java`
- Create: `src/test/java/com/example/salesai/adapters/ConsoleAuditLogAdapterTest.java`

Backward-compatible: existing `log(String, String)` callers continue to work; new structured callers use `log(AuditEntry)`. `entries()` continues to return `List<String>` for renderer compatibility (renderer migration is out of scope).

- [ ] **Step 1: Write the failing test**

`src/test/java/com/example/salesai/adapters/ConsoleAuditLogAdapterTest.java`:
```java
package com.example.salesai.adapters;

import com.example.salesai.audit.TextAuditEntry;

import java.time.Instant;

public final class ConsoleAuditLogAdapterTest {
    public static void main(String[] args) {
        new ConsoleAuditLogAdapterTest().run();
    }

    void run() {
        testLegacyStringLogStillWorks();
        testStructuredLogAppendsEntry();
        System.out.println("ConsoleAuditLogAdapterTest: 2 passed");
    }

    void testLegacyStringLogStillWorks() {
        ConsoleAuditLogAdapter adapter = new ConsoleAuditLogAdapter();
        adapter.log("STEP", "value");
        assert adapter.entries().size() == 1;
        assert adapter.entries().get(0).contains("STEP");
        assert adapter.entries().get(0).contains("value");
    }

    void testStructuredLogAppendsEntry() {
        ConsoleAuditLogAdapter adapter = new ConsoleAuditLogAdapter();
        adapter.log(new TextAuditEntry(Instant.now(), "MCP_CONNECT", "gmail"));
        assert adapter.entries().size() == 1;
        assert adapter.entries().get(0).contains("MCP_CONNECT");
        assert adapter.entries().get(0).contains("gmail");
    }
}
```

- [ ] **Step 2: Run, verify FAIL**

Expected: `log(AuditEntry)` method missing.

- [ ] **Step 3: Extend `AuditLogPort`**

Replace contents of `src/main/java/com/example/salesai/ports/AuditLogPort.java`:
```java
package com.example.salesai.ports;

import com.example.salesai.audit.AuditEntry;

import java.util.List;

public interface AuditLogPort {

    /** Legacy two-string call; kept so existing workflow code compiles. */
    void log(String event, String detail);

    /** Structured log call; preferred for new callers (Phase 4). */
    void log(AuditEntry entry);

    /** Returns the in-memory log of events captured so far (formatted strings). */
    List<String> entries();
}
```

- [ ] **Step 4: Update `ConsoleAuditLogAdapter`**

Read the existing file first:
```sh
cat src/main/java/com/example/salesai/adapters/ConsoleAuditLogAdapter.java
```

Add this method to the class:
```java
@Override
public void log(com.example.salesai.audit.AuditEntry entry) {
    String formatted = formatEntry(entry);
    log(formatted, "");
}

private static String formatEntry(com.example.salesai.audit.AuditEntry entry) {
    if (entry instanceof com.example.salesai.audit.TextAuditEntry t) {
        return t.event() + " " + t.detail();
    }
    // Phase 4 will add LlmCallAuditEntry formatting here in Task 4.9.
    return entry.toString();
}
```

(The existing `log(String, String)` implementation is unchanged. The new method routes structured entries through the same formatting pipeline.)

- [ ] **Step 5: Run test, verify PASS**

Expected: `ConsoleAuditLogAdapterTest: 2 passed` and `SmokeTest: 1 passed` and `AuditEntryTest: 2 passed` (no regressions).

- [ ] **Step 6: Commit**

```sh
git add src/main/java/com/example/salesai/ports/AuditLogPort.java \
        src/main/java/com/example/salesai/adapters/ConsoleAuditLogAdapter.java \
        src/test/java/com/example/salesai/adapters/ConsoleAuditLogAdapterTest.java
git commit -m "audit: extend AuditLogPort with structured log(AuditEntry)"
```

---

## Phase 2 — Real email via MCP (10 tasks)

Engine becomes an MCP client. After this phase, `--email gmail` works end-to-end against a real Gmail MCP server. Reply drafting still uses the existing `TemplateReplyDraftAdapter` until Phase 4.

---

### Task 2.1: JsonRpc helper for engine side (response parsing)

**Files:**
- Create: `src/main/java/com/example/salesai/mcp/client/JsonRpc.java`
- Create: `src/test/java/com/example/salesai/mcp/client/JsonRpcTest.java`

The existing `mcp-server/.../JsonRpc.java` parses inbound *requests* (server side). The engine side parses inbound *responses*: `{jsonrpc, id, result}` for success or `{jsonrpc, id, error: {code, message}}` for failure.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/example/salesai/mcp/client/JsonRpcTest.java`:
```java
package com.example.salesai.mcp.client;

import com.example.salesai.adapters.MiniJson;

import java.util.List;
import java.util.Map;

public final class JsonRpcTest {
    public static void main(String[] args) {
        new JsonRpcTest().run();
    }

    void run() {
        testBuildRequestEnvelope();
        testParseSuccessResponse();
        testParseErrorResponse();
        System.out.println("JsonRpcTest: 3 passed");
    }

    void testBuildRequestEnvelope() {
        String line = JsonRpc.request(7, "tools/list", Map.of());
        Map<String, Object> root = MiniJson.asObject(MiniJson.parse(line));
        assert "2.0".equals(root.get("jsonrpc"));
        assert ((Number) root.get("id")).intValue() == 7;
        assert "tools/list".equals(root.get("method"));
    }

    void testParseSuccessResponse() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{\"x\":1}}";
        JsonRpc.Response r = JsonRpc.parseResponse(body);
        assert ((Number) r.id()).intValue() == 3;
        assert r.error() == null;
        assert MiniJson.asObject(r.result()).get("x") instanceof Number;
    }

    void testParseErrorResponse() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":3,"
                + "\"error\":{\"code\":-32601,\"message\":\"Method not found\"}}";
        JsonRpc.Response r = JsonRpc.parseResponse(body);
        assert r.result() == null;
        assert r.error() != null;
        assert r.error().code() == -32601;
        assert "Method not found".equals(r.error().message());
    }
}
```

- [ ] **Step 2: Run, verify FAIL**

- [ ] **Step 3: Implement `JsonRpc`**

`src/main/java/com/example/salesai/mcp/client/JsonRpc.java`:
```java
package com.example.salesai.mcp.client;

import com.example.salesai.adapters.MiniJson;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Engine-side JSON-RPC 2.0 helper. Builds outbound request lines and
 * parses inbound response lines.
 */
public final class JsonRpc {

    private JsonRpc() {}

    public record Error(int code, String message) {}

    public record Response(Object id, Object result, Error error) {}

    /** Build a request line (newline NOT included). */
    public static String request(Object id, String method, Map<String, Object> params) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("jsonrpc", "2.0");
        envelope.put("id", id);
        envelope.put("method", method);
        envelope.put("params", params == null ? Map.of() : params);
        return MiniJson.write(envelope);
    }

    /** Build a notification line (no id, no response expected). */
    public static String notification(String method, Map<String, Object> params) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("jsonrpc", "2.0");
        envelope.put("method", method);
        envelope.put("params", params == null ? Map.of() : params);
        return MiniJson.write(envelope);
    }

    public static Response parseResponse(String line) {
        Map<String, Object> root = MiniJson.asObject(MiniJson.parse(line));
        Object id = root.get("id");
        Object result = root.get("result");
        Error error = null;
        if (root.containsKey("error")) {
            Map<String, Object> e = MiniJson.asObject(root.get("error"));
            int code = ((Number) e.getOrDefault("code", 0)).intValue();
            String msg = String.valueOf(e.getOrDefault("message", ""));
            error = new Error(code, msg);
        }
        return new Response(id, result, error);
    }
}
```

- [ ] **Step 4: Run test, verify PASS**

Expected: `JsonRpcTest: 3 passed`

- [ ] **Step 5: Commit**

```sh
git add src/main/java/com/example/salesai/mcp/client/JsonRpc.java \
        src/test/java/com/example/salesai/mcp/client/JsonRpcTest.java
git commit -m "mcp/client: add JsonRpc request builder + response parser"
```

---

### Task 2.2: StdioBridge — newline-delimited JSON over child process

**Files:**
- Create: `src/main/java/com/example/salesai/mcp/client/StdioBridge.java`
- Create: `src/test/java/com/example/salesai/mcp/client/StdioBridgeTest.java`
- Create: `src/test/resources/echo-server.sh`
- Create: `src/test/resources/echo-server.cmd`

`StdioBridge` wraps a subprocess's stdin/stdout. It can `send(line)` and `readNextLine(timeoutMs)`. Stderr is forwarded to the engine's stderr (so MCP server diagnostics surface).

- [ ] **Step 1: Write the failing test**

`src/test/java/com/example/salesai/mcp/client/StdioBridgeTest.java`:
```java
package com.example.salesai.mcp.client;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class StdioBridgeTest {
    public static void main(String[] args) throws Exception {
        new StdioBridgeTest().run();
    }

    void run() throws Exception {
        testEchoesLines();
        testTimeoutFiresWhenNoData();
        System.out.println("StdioBridgeTest: 2 passed");
    }

    void testEchoesLines() throws Exception {
        StdioBridge b = StdioBridge.spawn(echoCommand(), java.util.Map.of());
        try {
            b.send("hello");
            String got = b.readNextLine(2000);
            assert "hello".equals(got) : "got=" + got;
        } finally {
            b.close();
        }
    }

    void testTimeoutFiresWhenNoData() throws Exception {
        StdioBridge b = StdioBridge.spawn(echoCommand(), java.util.Map.of());
        try {
            try {
                b.readNextLine(200);
                throw new AssertionError("expected timeout");
            } catch (java.util.concurrent.TimeoutException expected) {
                // ok
            }
        } finally {
            b.close();
        }
    }

    private static java.util.List<String> echoCommand() {
        Path here = Paths.get("src/test/resources").toAbsolutePath();
        boolean win = System.getProperty("os.name").toLowerCase().contains("win");
        if (win) {
            return java.util.List.of(
                "cmd.exe", "/c",
                here.resolve("echo-server.cmd").toString());
        }
        return java.util.List.of(
            "sh", here.resolve("echo-server.sh").toString());
    }
}
```

`src/test/resources/echo-server.sh`:
```sh
#!/usr/bin/env sh
while IFS= read -r line; do
  printf '%s\n' "$line"
done
```

`src/test/resources/echo-server.cmd`:
```
@echo off
:loop
set /p line=
if "%line%"=="" goto loop
echo %line%
goto loop
```

- [ ] **Step 2: Make echo scripts executable (POSIX only)**

```sh
chmod +x src/test/resources/echo-server.sh
```

- [ ] **Step 3: Implement `StdioBridge`**

`src/main/java/com/example/salesai/mcp/client/StdioBridge.java`:
```java
package com.example.salesai.mcp.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Wraps a subprocess and exposes newline-delimited line I/O over its
 * stdin/stdout. A reader thread enqueues incoming lines so callers can
 * {@link #readNextLine(long)} with a timeout. Stderr is forwarded to
 * the engine's stderr so MCP-server diagnostics surface in the operator's
 * console.
 */
public final class StdioBridge implements AutoCloseable {

    private final Process process;
    private final BufferedWriter stdin;
    private final LinkedBlockingQueue<String> incoming = new LinkedBlockingQueue<>();
    private final Thread stdoutReader;
    private final Thread stderrReader;
    private volatile boolean closed = false;

    public static StdioBridge spawn(List<String> command, Map<String, String> env)
            throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().putAll(env);
        Process p = pb.start();
        return new StdioBridge(p);
    }

    private StdioBridge(Process p) {
        this.process = p;
        this.stdin = new BufferedWriter(
            new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8));
        this.stdoutReader = new Thread(this::pumpStdout, "stdio-bridge-stdout");
        this.stdoutReader.setDaemon(true);
        this.stdoutReader.start();
        this.stderrReader = new Thread(this::pumpStderr, "stdio-bridge-stderr");
        this.stderrReader.setDaemon(true);
        this.stderrReader.start();
    }

    public synchronized void send(String line) throws IOException {
        if (closed) throw new IOException("bridge closed");
        stdin.write(line);
        stdin.write('\n');
        stdin.flush();
    }

    public String readNextLine(long timeoutMs) throws TimeoutException, InterruptedException {
        String line = incoming.poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (line == null) throw new TimeoutException(
            "no line received within " + timeoutMs + "ms");
        return line;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        try { stdin.close(); } catch (IOException ignored) {}
        process.destroy();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    private void pumpStdout() {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.isEmpty()) incoming.offer(line);
            }
        } catch (IOException ignored) {
            // stream closed
        }
    }

    private void pumpStderr() {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                System.err.println("[mcp-stderr] " + line);
            }
        } catch (IOException ignored) {}
    }
}
```

- [ ] **Step 4: Run test, verify PASS**

Expected: `StdioBridgeTest: 2 passed`

- [ ] **Step 5: Commit**

```sh
git add src/main/java/com/example/salesai/mcp/client/StdioBridge.java \
        src/test/java/com/example/salesai/mcp/client/StdioBridgeTest.java \
        src/test/resources/echo-server.sh \
        src/test/resources/echo-server.cmd
git commit -m "mcp/client: add StdioBridge with timeout-aware line I/O"
```

---

### Task 2.3: McpServerConfig record

**Files:**
- Create: `src/main/java/com/example/salesai/mcp/client/McpServerConfig.java`
- Create: `src/test/java/com/example/salesai/mcp/client/McpServerConfigTest.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/com/example/salesai/mcp/client/McpServerConfigTest.java`:
```java
package com.example.salesai.mcp.client;

import java.util.List;
import java.util.Map;

public final class McpServerConfigTest {
    public static void main(String[] args) {
        new McpServerConfigTest().run();
    }

    void run() {
        testRecordHoldsAllFields();
        testRequiresCommand();
        System.out.println("McpServerConfigTest: 2 passed");
    }

    void testRecordHoldsAllFields() {
        McpServerConfig c = new McpServerConfig(
            "gmail", "npx", List.of("-y", "@gongrzhe/server-gmail-autoauth-mcp"),
            Map.of());
        assert "gmail".equals(c.name());
        assert "npx".equals(c.command());
        assert c.args().size() == 2;
        assert c.env().isEmpty();
    }

    void testRequiresCommand() {
        try {
            new McpServerConfig("gmail", "  ", List.of(), Map.of());
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException ok) {}
    }
}
```

- [ ] **Step 2: Run, FAIL**

- [ ] **Step 3: Implement**

`src/main/java/com/example/salesai/mcp/client/McpServerConfig.java`:
```java
package com.example.salesai.mcp.client;

import java.util.List;
import java.util.Map;

public record McpServerConfig(
        String name,
        String command,
        List<String> args,
        Map<String, String> env) {

    public McpServerConfig {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name");
        if (command == null || command.isBlank()) throw new IllegalArgumentException("command");
        args = args == null ? List.of() : List.copyOf(args);
        env = env == null ? Map.of() : Map.copyOf(env);
    }
}
```

- [ ] **Step 4: Run test, PASS**

- [ ] **Step 5: Commit**

```sh
git add src/main/java/com/example/salesai/mcp/client/McpServerConfig.java \
        src/test/java/com/example/salesai/mcp/client/McpServerConfigTest.java
git commit -m "mcp/client: add McpServerConfig record"
```

---

### Task 2.4: McpConfigLoader (parse `mcp-config.json`)

**Files:**
- Create: `src/main/java/com/example/salesai/mcp/client/McpConfigLoader.java`
- Create: `src/test/java/com/example/salesai/mcp/client/McpConfigLoaderTest.java`

Parses Claude Code-compatible `{"mcpServers": {"name": {"command": "...", "args": [...], "env": {...}}}}`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/example/salesai/mcp/client/McpConfigLoaderTest.java`:
```java
package com.example.salesai.mcp.client;

import java.nio.file.Files;
import java.nio.file.Path;

public final class McpConfigLoaderTest {
    public static void main(String[] args) throws Exception {
        new McpConfigLoaderTest().run();
    }

    void run() throws Exception {
        testLoadsClaudeCodeFormat();
        testReturnsEmptyWhenFileMissing();
        testThrowsOnMalformedJson();
        System.out.println("McpConfigLoaderTest: 3 passed");
    }

    void testLoadsClaudeCodeFormat() throws Exception {
        Path tmp = Files.createTempFile("mcp-config", ".json");
        Files.writeString(tmp, """
            {
              "mcpServers": {
                "gmail": {
                  "command": "npx",
                  "args": ["-y", "@gongrzhe/server-gmail-autoauth-mcp"],
                  "env": {"GMAIL_CACHE": "/tmp/g"}
                },
                "outlook": {
                  "command": "uvx",
                  "args": ["mcp-server-outlook"]
                }
              }
            }
            """);
        var configs = McpConfigLoader.load(tmp);
        assert configs.size() == 2;
        assert "npx".equals(configs.get("gmail").command());
        assert configs.get("gmail").args().size() == 2;
        assert "/tmp/g".equals(configs.get("gmail").env().get("GMAIL_CACHE"));
        assert "uvx".equals(configs.get("outlook").command());
    }

    void testReturnsEmptyWhenFileMissing() {
        var configs = McpConfigLoader.load(Path.of("/no/such/file.json"));
        assert configs.isEmpty();
    }

    void testThrowsOnMalformedJson() throws Exception {
        Path tmp = Files.createTempFile("mcp-bad", ".json");
        Files.writeString(tmp, "{ not json");
        try {
            McpConfigLoader.load(tmp);
            throw new AssertionError("expected exception");
        } catch (RuntimeException ok) {}
    }
}
```

- [ ] **Step 2: Run, FAIL**

- [ ] **Step 3: Implement**

`src/main/java/com/example/salesai/mcp/client/McpConfigLoader.java`:
```java
package com.example.salesai.mcp.client;

import com.example.salesai.adapters.MiniJson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class McpConfigLoader {

    private McpConfigLoader() {}

    public static Map<String, McpServerConfig> load(Path file) {
        if (file == null || !Files.exists(file)) return Map.of();
        String body;
        try {
            body = Files.readString(file);
        } catch (IOException e) {
            throw new RuntimeException("cannot read " + file, e);
        }
        Map<String, Object> root = MiniJson.asObject(MiniJson.parse(body));
        Object servers = root.get("mcpServers");
        if (!(servers instanceof Map)) return Map.of();
        Map<String, Object> map = MiniJson.asObject(servers);

        Map<String, McpServerConfig> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String name = e.getKey();
            Map<String, Object> cfg = MiniJson.asObject(e.getValue());
            String command = MiniJson.asString(cfg.get("command"));
            List<String> args = toStringList(cfg.get("args"));
            Map<String, String> env = toStringMap(cfg.get("env"));
            out.put(name, new McpServerConfig(name, command, args, env));
        }
        return out;
    }

    private static List<String> toStringList(Object o) {
        if (o == null) return List.of();
        java.util.List<?> raw = (java.util.List<?>) o;
        java.util.List<String> r = new java.util.ArrayList<>(raw.size());
        for (Object x : raw) r.add(String.valueOf(x));
        return r;
    }

    private static Map<String, String> toStringMap(Object o) {
        if (o == null) return Map.of();
        Map<String, Object> raw = MiniJson.asObject(o);
        Map<String, String> r = new LinkedHashMap<>();
        for (var e : raw.entrySet()) r.put(e.getKey(), String.valueOf(e.getValue()));
        return r;
    }
}
```

- [ ] **Step 4: Run, PASS**

- [ ] **Step 5: Commit**

```sh
git add src/main/java/com/example/salesai/mcp/client/McpConfigLoader.java \
        src/test/java/com/example/salesai/mcp/client/McpConfigLoaderTest.java
git commit -m "mcp/client: add McpConfigLoader for Claude Code-compatible config"
```

---

### Task 2.5: McpClient — spawn + initialize handshake

**Files:**
- Create: `src/main/java/com/example/salesai/mcp/client/McpClient.java`
- Create: `src/test/java/com/example/salesai/mcp/client/McpClientHandshakeTest.java`
- Create: `src/test/resources/fake-mcp-server.sh`
- Create: `src/test/resources/fake-mcp-server.cmd`

This task only covers `spawn()` + `initialize()`. Tools/list and tools/call land in Task 2.6.

- [ ] **Step 1: Write a fake MCP server for tests**

`src/test/resources/fake-mcp-server.sh`:
```sh
#!/usr/bin/env sh
# Fake MCP server. Reads JSON-RPC requests from stdin, replies on stdout.
# Supports: initialize, tools/list (returns one fake tool), tools/call.
while IFS= read -r line; do
  case "$line" in
    *'"method":"initialize"'*)
      id=$(printf '%s' "$line" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')
      printf '{"jsonrpc":"2.0","id":%s,"result":{"protocolVersion":"2025-06-18","capabilities":{},"serverInfo":{"name":"fake","version":"0.0.1"}}}\n' "$id"
      ;;
    *'"method":"tools/list"'*)
      id=$(printf '%s' "$line" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')
      printf '{"jsonrpc":"2.0","id":%s,"result":{"tools":[{"name":"fake.echo","description":"echo","inputSchema":{}}]}}\n' "$id"
      ;;
    *'"method":"tools/call"'*)
      id=$(printf '%s' "$line" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')
      printf '{"jsonrpc":"2.0","id":%s,"result":{"content":[{"type":"text","text":"fake-result"}]}}\n' "$id"
      ;;
    *) ;;
  esac
done
```

`src/test/resources/fake-mcp-server.cmd`:
```
@echo off
:loop
set /p line=
if "%line%"=="" goto loop
echo %line% | findstr /c:"\"method\":\"initialize\"" > nul
if not errorlevel 1 (
  for /f "tokens=2 delims=:," %%a in ('echo %line% ^| findstr /c:"\"id\""') do set id=%%a
  echo {"jsonrpc":"2.0","id":%id%,"result":{"protocolVersion":"2025-06-18","capabilities":{},"serverInfo":{"name":"fake","version":"0.0.1"}}}
  goto loop
)
echo %line% | findstr /c:"\"method\":\"tools/list\"" > nul
if not errorlevel 1 (
  for /f "tokens=2 delims=:," %%a in ('echo %line% ^| findstr /c:"\"id\""') do set id=%%a
  echo {"jsonrpc":"2.0","id":%id%,"result":{"tools":[{"name":"fake.echo","description":"echo","inputSchema":{}}]}}
  goto loop
)
echo %line% | findstr /c:"\"method\":\"tools/call\"" > nul
if not errorlevel 1 (
  for /f "tokens=2 delims=:," %%a in ('echo %line% ^| findstr /c:"\"id\""') do set id=%%a
  echo {"jsonrpc":"2.0","id":%id%,"result":{"content":[{"type":"text","text":"fake-result"}]}}
  goto loop
)
goto loop
```

```sh
chmod +x src/test/resources/fake-mcp-server.sh
```

- [ ] **Step 2: Write the failing test**

`src/test/java/com/example/salesai/mcp/client/McpClientHandshakeTest.java`:
```java
package com.example.salesai.mcp.client;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public final class McpClientHandshakeTest {
    public static void main(String[] args) throws Exception {
        new McpClientHandshakeTest().run();
    }

    void run() throws Exception {
        testInitializeReturnsProtocolVersion();
        System.out.println("McpClientHandshakeTest: 1 passed");
    }

    void testInitializeReturnsProtocolVersion() throws Exception {
        McpServerConfig cfg = new McpServerConfig(
            "fake", fakeCommand(), fakeArgs(), Map.of());
        try (McpClient client = McpClient.spawn(cfg)) {
            McpClient.InitResult r = client.initialize(2000);
            assert "2025-06-18".equals(r.protocolVersion());
            assert "fake".equals(r.serverName());
        }
    }

    private static String fakeCommand() {
        return win() ? "cmd.exe" : "sh";
    }

    private static List<String> fakeArgs() {
        Path here = Paths.get("src/test/resources").toAbsolutePath();
        if (win()) {
            return List.of("/c", here.resolve("fake-mcp-server.cmd").toString());
        }
        return List.of(here.resolve("fake-mcp-server.sh").toString());
    }

    private static boolean win() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
```

- [ ] **Step 3: Run, FAIL**

- [ ] **Step 4: Implement `McpClient.spawn` + `initialize`**

`src/main/java/com/example/salesai/mcp/client/McpClient.java`:
```java
package com.example.salesai.mcp.client;

import com.example.salesai.adapters.MiniJson;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP client. Spawns a server subprocess, performs the JSON-RPC
 * initialize handshake, then exposes tools/list and tools/call.
 *
 * <p>Not thread-safe — one client per workflow run.
 */
public final class McpClient implements AutoCloseable {

    private static final String PROTOCOL_VERSION = "2025-06-18";
    private static final String CLIENT_NAME = "sales-ai-engine";

    private final McpServerConfig config;
    private final StdioBridge bridge;
    private final AtomicLong nextId = new AtomicLong(1);
    private boolean initialized = false;

    public record InitResult(String protocolVersion, String serverName) {}

    public static McpClient spawn(McpServerConfig config) throws IOException {
        List<String> command = new java.util.ArrayList<>();
        command.add(config.command());
        command.addAll(config.args());
        StdioBridge bridge = StdioBridge.spawn(command, config.env());
        return new McpClient(config, bridge);
    }

    private McpClient(McpServerConfig config, StdioBridge bridge) {
        this.config = config;
        this.bridge = bridge;
        // JVM shutdown hook: kill the child if the engine exits unexpectedly.
        Runtime.getRuntime().addShutdownHook(new Thread(this::close,
                "mcp-shutdown-" + config.name()));
    }

    public InitResult initialize(long timeoutMs) throws IOException {
        long id = nextId.getAndIncrement();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.put("capabilities", Map.of());
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", CLIENT_NAME);
        info.put("version", "0.1.0");
        params.put("clientInfo", info);

        bridge.send(JsonRpc.request(id, "initialize", params));
        JsonRpc.Response r = readResponseFor(id, timeoutMs);
        if (r.error() != null) {
            throw new IOException("initialize error " + r.error().code()
                + ": " + r.error().message());
        }
        Map<String, Object> result = MiniJson.asObject(r.result());
        String protoVersion = MiniJson.asString(result.get("protocolVersion"));
        Map<String, Object> serverInfo = MiniJson.asObject(
            result.getOrDefault("serverInfo", Map.of()));
        String serverName = MiniJson.asString(serverInfo.getOrDefault("name", ""));

        // Send the required initialized notification.
        bridge.send(JsonRpc.notification("notifications/initialized", Map.of()));
        initialized = true;
        return new InitResult(protoVersion, serverName);
    }

    private JsonRpc.Response readResponseFor(long expectedId, long timeoutMs)
            throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (true) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) throw new IOException("timeout waiting for response id=" + expectedId);
            String line;
            try {
                line = bridge.readNextLine(remaining);
            } catch (TimeoutException te) {
                throw new IOException("timeout waiting for response id=" + expectedId);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted");
            }
            JsonRpc.Response r = JsonRpc.parseResponse(line);
            // Skip notifications and unrelated responses.
            if (r.id() == null) continue;
            long got = ((Number) r.id()).longValue();
            if (got == expectedId) return r;
        }
    }

    @Override
    public void close() {
        bridge.close();
    }

    public boolean isInitialized() { return initialized; }
    public McpServerConfig config() { return config; }
    StdioBridge bridge() { return bridge; }
    AtomicLong nextId() { return nextId; }
}
```

- [ ] **Step 5: Run, PASS**

Expected: `McpClientHandshakeTest: 1 passed`

- [ ] **Step 6: Commit**

```sh
git add src/main/java/com/example/salesai/mcp/client/McpClient.java \
        src/test/java/com/example/salesai/mcp/client/McpClientHandshakeTest.java \
        src/test/resources/fake-mcp-server.sh \
        src/test/resources/fake-mcp-server.cmd
git commit -m "mcp/client: add McpClient.spawn + initialize handshake"
```

---

### Task 2.6: McpClient — `tools/list` and `tools/call`

**Files:**
- Modify: `src/main/java/com/example/salesai/mcp/client/McpClient.java`
- Create: `src/test/java/com/example/salesai/mcp/client/McpClientToolsTest.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/com/example/salesai/mcp/client/McpClientToolsTest.java`:
```java
package com.example.salesai.mcp.client;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public final class McpClientToolsTest {
    public static void main(String[] args) throws Exception {
        new McpClientToolsTest().run();
    }

    void run() throws Exception {
        testListToolsReturnsToolNames();
        testCallToolReturnsContentText();
        System.out.println("McpClientToolsTest: 2 passed");
    }

    void testListToolsReturnsToolNames() throws Exception {
        try (McpClient client = handshakeFake()) {
            List<String> names = client.listToolNames(2000);
            assert names.contains("fake.echo") : names.toString();
        }
    }

    void testCallToolReturnsContentText() throws Exception {
        try (McpClient client = handshakeFake()) {
            String text = client.callToolText("fake.echo", Map.of("input", "hi"), 2000);
            assert "fake-result".equals(text) : text;
        }
    }

    private static McpClient handshakeFake() throws java.io.IOException {
        boolean win = System.getProperty("os.name").toLowerCase().contains("win");
        Path here = Paths.get("src/test/resources").toAbsolutePath();
        McpServerConfig cfg = new McpServerConfig(
            "fake",
            win ? "cmd.exe" : "sh",
            win
                ? List.of("/c", here.resolve("fake-mcp-server.cmd").toString())
                : List.of(here.resolve("fake-mcp-server.sh").toString()),
            Map.of());
        McpClient c = McpClient.spawn(cfg);
        c.initialize(2000);
        return c;
    }
}
```

- [ ] **Step 2: Run, FAIL** (`listToolNames` / `callToolText` not defined)

- [ ] **Step 3: Add the two methods to `McpClient`**

Append to `McpClient.java` (just before the final `close()` method):
```java
/**
 * Calls {@code tools/list} and returns just the tool names. Full schema
 * isn't needed by the engine since we know each adapter's expected tool
 * names ahead of time (see EmailMcpToolMapping).
 */
public List<String> listToolNames(long timeoutMs) throws IOException {
    if (!initialized) throw new IllegalStateException("not initialized");
    long id = nextId.getAndIncrement();
    bridge.send(JsonRpc.request(id, "tools/list", Map.of()));
    JsonRpc.Response r = readResponseFor(id, timeoutMs);
    if (r.error() != null) {
        throw new IOException("tools/list error: " + r.error().message());
    }
    Map<String, Object> result = MiniJson.asObject(r.result());
    Object toolsRaw = result.getOrDefault("tools", List.of());
    List<?> tools = (List<?>) toolsRaw;
    java.util.List<String> names = new java.util.ArrayList<>();
    for (Object t : tools) {
        Map<String, Object> tm = MiniJson.asObject(t);
        String name = MiniJson.asString(tm.get("name"));
        if (name != null) names.add(name);
    }
    return names;
}

/**
 * Calls {@code tools/call} and returns the concatenated text content.
 * MCP tools may return multiple content items; this joins all
 * {@code type:"text"} items with newlines and ignores other types.
 */
public String callToolText(String name, Map<String, Object> arguments, long timeoutMs)
        throws IOException {
    if (!initialized) throw new IllegalStateException("not initialized");
    long id = nextId.getAndIncrement();
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("name", name);
    params.put("arguments", arguments == null ? Map.of() : arguments);
    bridge.send(JsonRpc.request(id, "tools/call", params));
    JsonRpc.Response r = readResponseFor(id, timeoutMs);
    if (r.error() != null) {
        throw new IOException("tools/call(" + name + ") error: " + r.error().message());
    }
    Map<String, Object> result = MiniJson.asObject(r.result());
    Object contentRaw = result.getOrDefault("content", List.of());
    List<?> content = (List<?>) contentRaw;
    StringBuilder sb = new StringBuilder();
    for (Object c : content) {
        Map<String, Object> cm = MiniJson.asObject(c);
        if ("text".equals(cm.get("type"))) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(MiniJson.asString(cm.get("text")));
        }
    }
    return sb.toString();
}
```

- [ ] **Step 4: Run, PASS**

Expected: `McpClientToolsTest: 2 passed` plus all earlier tests still pass.

- [ ] **Step 5: Commit**

```sh
git add src/main/java/com/example/salesai/mcp/client/McpClient.java \
        src/test/java/com/example/salesai/mcp/client/McpClientToolsTest.java
git commit -m "mcp/client: add listToolNames + callToolText"
```

---

### Task 2.7: EmailMcpToolMapping (Gmail vs Outlook dispatch)

**Files:**
- Create: `src/main/java/com/example/salesai/adapters/email/EmailMcpToolMapping.java`
- Create: `src/test/java/com/example/salesai/adapters/email/EmailMcpToolMappingTest.java`

A small enum-based dispatch: for each `Provider`, what is the search tool name and what JSON shape comes back. No transformative parsing — the adapter feeds raw text to the workflow.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/example/salesai/adapters/email/EmailMcpToolMappingTest.java`:
```java
package com.example.salesai.adapters.email;

public final class EmailMcpToolMappingTest {
    public static void main(String[] args) {
        new EmailMcpToolMappingTest().run();
    }

    void run() {
        testGmailToolName();
        testOutlookToolName();
        testFromConfigName();
        System.out.println("EmailMcpToolMappingTest: 3 passed");
    }

    void testGmailToolName() {
        assert "search_emails".equals(
            EmailMcpToolMapping.GMAIL.searchToolName());
    }

    void testOutlookToolName() {
        assert "list-messages".equals(
            EmailMcpToolMapping.OUTLOOK.searchToolName());
    }

    void testFromConfigName() {
        assert EmailMcpToolMapping.fromConfigName("gmail")
            == EmailMcpToolMapping.GMAIL;
        assert EmailMcpToolMapping.fromConfigName("outlook")
            == EmailMcpToolMapping.OUTLOOK;
        try {
            EmailMcpToolMapping.fromConfigName("unknown");
            throw new AssertionError("expected exception");
        } catch (IllegalArgumentException ok) {}
    }
}
```

- [ ] **Step 2: Run, FAIL**

- [ ] **Step 3: Implement**

`src/main/java/com/example/salesai/adapters/email/EmailMcpToolMapping.java`:
```java
package com.example.salesai.adapters.email;

/**
 * Per-provider MCP tool naming. The Gmail and Outlook MCP servers
 * expose semantically equivalent tools under different names; this enum
 * is the single place that knowledge lives so the rest of the engine
 * can stay provider-agnostic.
 *
 * <p>Tool names track the most popular open-source MCP servers as of
 * 2026-Q2: {@code @gongrzhe/server-gmail-autoauth-mcp} for Gmail and
 * {@code mcp-server-outlook} for Outlook. If those evolve, update here.
 */
public enum EmailMcpToolMapping {

    GMAIL("search_emails", "read_email"),
    OUTLOOK("list-messages", "get-message");

    private final String searchToolName;
    private final String getMessageToolName;

    EmailMcpToolMapping(String searchToolName, String getMessageToolName) {
        this.searchToolName = searchToolName;
        this.getMessageToolName = getMessageToolName;
    }

    public String searchToolName() { return searchToolName; }
    public String getMessageToolName() { return getMessageToolName; }

    public static EmailMcpToolMapping fromConfigName(String name) {
        if (name == null) throw new IllegalArgumentException("null provider");
        return switch (name.toLowerCase()) {
            case "gmail" -> GMAIL;
            case "outlook" -> OUTLOOK;
            default -> throw new IllegalArgumentException(
                "unknown email provider: " + name);
        };
    }
}
```

- [ ] **Step 4: Run, PASS**

- [ ] **Step 5: Commit**

```sh
git add src/main/java/com/example/salesai/adapters/email/EmailMcpToolMapping.java \
        src/test/java/com/example/salesai/adapters/email/EmailMcpToolMappingTest.java
git commit -m "adapters/email: add EmailMcpToolMapping enum (Gmail/Outlook)"
```

---

### Task 2.8: McpEmailThreadAdapter

**Files:**
- Create: `src/main/java/com/example/salesai/adapters/email/McpEmailThreadAdapter.java`
- Create: `src/test/java/com/example/salesai/adapters/email/McpEmailThreadAdapterTest.java`

Implements the existing `EmailThreadPort`. Calls `searchTool` to find threads for a given email address, parses the returned JSON text into `EmailThread`. Failure to parse returns `Optional.empty()` plus a stderr warning (so the workflow gracefully reports "thread not found").

- [ ] **Step 1: Re-read the existing port to know the contract**

```sh
cat src/main/java/com/example/salesai/ports/EmailThreadPort.java
cat src/main/java/com/example/salesai/domain/EmailThread.java
cat src/main/java/com/example/salesai/domain/EmailMessage.java
```

(Engineer should confirm `loadLatestForCustomer(String email)` returns `Optional<EmailThread>`, and what `EmailThread`/`EmailMessage` records look like.)

- [ ] **Step 2: Write the failing test**

`src/test/java/com/example/salesai/adapters/email/McpEmailThreadAdapterTest.java`:
```java
package com.example.salesai.adapters.email;

import com.example.salesai.domain.EmailThread;
import com.example.salesai.mcp.client.McpClient;
import com.example.salesai.mcp.client.McpServerConfig;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class McpEmailThreadAdapterTest {
    public static void main(String[] args) throws Exception {
        new McpEmailThreadAdapterTest().run();
    }

    void run() throws Exception {
        testReturnsEmptyWhenMcpRespondsWithNoMessages();
        testParsesSingleMessageThread();
        System.out.println("McpEmailThreadAdapterTest: 2 passed");
    }

    void testReturnsEmptyWhenMcpRespondsWithNoMessages() {
        FakeMcpClient mc = new FakeMcpClient("[]");
        McpEmailThreadAdapter a = new McpEmailThreadAdapter(
            mc, EmailMcpToolMapping.GMAIL);
        Optional<EmailThread> t = a.loadLatestForCustomer("nobody@example.com");
        assert t.isEmpty();
    }

    void testParsesSingleMessageThread() {
        String mcpReply = """
            [
              {
                "thread_id": "thr-001",
                "subject": "Order ETA?",
                "messages": [
                  {
                    "from": "alice@acme.com",
                    "to": "support@vendor.com",
                    "sent_at": "2026-05-07T10:30:00Z",
                    "body": "When does my order ship?"
                  }
                ]
              }
            ]
            """;
        FakeMcpClient mc = new FakeMcpClient(mcpReply);
        McpEmailThreadAdapter a = new McpEmailThreadAdapter(
            mc, EmailMcpToolMapping.GMAIL);
        Optional<EmailThread> t = a.loadLatestForCustomer("alice@acme.com");
        assert t.isPresent();
        assert "thr-001".equals(t.get().threadId());
        assert t.get().messages().size() == 1;
    }

    /** Test double — implements the small subset McpEmailThreadAdapter calls. */
    static class FakeMcpClient extends McpClient {
        private final String reply;
        FakeMcpClient(String reply) {
            super();  // McpClient must expose a protected no-arg test constructor
            this.reply = reply;
        }
        @Override
        public String callToolText(String name, Map<String, Object> args, long timeoutMs) {
            return reply;
        }
        @Override
        public List<String> listToolNames(long timeoutMs) {
            return List.of("search_emails", "read_email");
        }
        @Override
        public boolean isInitialized() { return true; }
        @Override
        public void close() {}
    }
}
```

(Note: this test requires `McpClient` to have a protected no-arg constructor for testing. Add it as part of step 4.)

- [ ] **Step 3: Run, FAIL**

- [ ] **Step 4: Add a protected test-only constructor to `McpClient`**

In `McpClient.java`, add:
```java
/** Test-only — subclasses can override callToolText/listToolNames without spawning. */
protected McpClient() {
    this.config = new McpServerConfig("test", "noop", List.of(), Map.of());
    this.bridge = null;
}
```

(`final` fields prevent this — change `private final` → `private` on `config` and `bridge`. The shutdown hook in the existing constructor expects `bridge` non-null, so check for null there: `if (bridge != null) bridge.close();` in `close()`. Update `close()` accordingly.)

- [ ] **Step 5: Implement `McpEmailThreadAdapter`**

`src/main/java/com/example/salesai/adapters/email/McpEmailThreadAdapter.java`:
```java
package com.example.salesai.adapters.email;

import com.example.salesai.adapters.MiniJson;
import com.example.salesai.domain.EmailMessage;
import com.example.salesai.domain.EmailThread;
import com.example.salesai.mcp.client.McpClient;
import com.example.salesai.ports.EmailThreadPort;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * EmailThreadPort implementation that fetches threads through a spawned
 * MCP server (Gmail or Outlook). The MCP server is responsible for OAuth;
 * this adapter only speaks JSON tool calls.
 *
 * <p>The adapter expects the MCP tool to return a JSON array of thread
 * objects. Schema:
 *
 * <pre>
 *   [{ "thread_id":"...", "subject":"...", "messages":[
 *      { "from":"...", "to":"...", "sent_at":"ISO-8601", "body":"..." }
 *   ]}]
 * </pre>
 *
 * <p>Most real MCP servers don't return exactly this shape. The expected
 * pattern: write a thin wrapper MCP server (Phase 5 / 7) or pass a
 * {@code --query} flag the underlying MCP supports. For Gmail's
 * {@code search_emails}, this is achieved by passing the customer email
 * as the {@code q} argument and post-filtering.
 */
public final class McpEmailThreadAdapter implements EmailThreadPort {

    private static final long DEFAULT_TIMEOUT_MS = 15_000;

    private final McpClient mcp;
    private final EmailMcpToolMapping mapping;

    public McpEmailThreadAdapter(McpClient mcp, EmailMcpToolMapping mapping) {
        this.mcp = mcp;
        this.mapping = mapping;
    }

    @Override
    public Optional<EmailThread> loadLatestForCustomer(String customerEmail) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("query", "from:" + customerEmail);
        args.put("max_results", 5);

        String text;
        try {
            text = mcp.callToolText(mapping.searchToolName(), args, DEFAULT_TIMEOUT_MS);
        } catch (IOException e) {
            System.err.println("[mcp-email] tool call failed: " + e.getMessage());
            return Optional.empty();
        }

        if (text == null || text.isBlank() || "[]".equals(text.trim())) {
            return Optional.empty();
        }

        try {
            List<?> threads = (List<?>) MiniJson.parse(text);
            if (threads.isEmpty()) return Optional.empty();
            return Optional.of(toThread(threads.get(0)));
        } catch (RuntimeException e) {
            System.err.println("[mcp-email] could not parse MCP reply: "
                + e.getMessage() + "; reply=" + truncate(text, 200));
            return Optional.empty();
        }
    }

    private static EmailThread toThread(Object raw) {
        Map<String, Object> m = MiniJson.asObject(raw);
        String threadId = MiniJson.asString(m.getOrDefault("thread_id", ""));
        String subject = MiniJson.asString(m.getOrDefault("subject", ""));
        List<?> rawMsgs = (List<?>) m.getOrDefault("messages", List.of());
        List<EmailMessage> msgs = new ArrayList<>(rawMsgs.size());
        for (Object r : rawMsgs) msgs.add(toMessage(r));
        return new EmailThread(threadId, subject, msgs);
    }

    private static EmailMessage toMessage(Object raw) {
        Map<String, Object> m = MiniJson.asObject(raw);
        String from = MiniJson.asString(m.getOrDefault("from", ""));
        String to = MiniJson.asString(m.getOrDefault("to", ""));
        String body = MiniJson.asString(m.getOrDefault("body", ""));
        Instant sent = parseInstant(MiniJson.asString(m.get("sent_at")));
        return new EmailMessage(from, to, sent, body);
    }

    private static Instant parseInstant(String s) {
        if (s == null || s.isBlank()) return Instant.EPOCH;
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException e) {
            return Instant.EPOCH;
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
```

(The `EmailMessage` constructor signature here assumes `(String from, String to, Instant sentAt, String body)` — confirm with `cat src/main/java/com/example/salesai/domain/EmailMessage.java` and adjust the constructor call if the existing record's parameters differ.)

- [ ] **Step 6: Run, PASS**

- [ ] **Step 7: Commit**

```sh
git add src/main/java/com/example/salesai/mcp/client/McpClient.java \
        src/main/java/com/example/salesai/adapters/email/McpEmailThreadAdapter.java \
        src/test/java/com/example/salesai/adapters/email/McpEmailThreadAdapterTest.java
git commit -m "adapters/email: add McpEmailThreadAdapter (Gmail+Outlook via MCP)"
```

---

### Task 2.9: Wire `--email` and `--mcp-config` into `SalesAiCli`

**Files:**
- Modify: `src/main/java/com/example/salesai/SalesAiCli.java`

- [ ] **Step 1: Read the current CLI flag-parsing code**

```sh
cat src/main/java/com/example/salesai/SalesAiCli.java | grep -A2 "args\["
```

- [ ] **Step 2: Add the flag parsing**

Add to the existing arg-parsing block (between `--db` parsing and the workflow-construction block):
```java
String emailSource = "mock";
java.nio.file.Path mcpConfigPath = null;
for (int i = 0; i < args.length; i++) {
    if ("--email".equals(args[i]) && i + 1 < args.length) {
        emailSource = args[++i];
    } else if ("--mcp-config".equals(args[i]) && i + 1 < args.length) {
        mcpConfigPath = java.nio.file.Paths.get(args[++i]);
    }
}
```

- [ ] **Step 3: Add the factory wiring (before workflow construction)**

```java
com.example.salesai.ports.EmailThreadPort emailPort;
com.example.salesai.mcp.client.McpClient emailMcp = null;
if ("mock".equals(emailSource)) {
    emailPort = new com.example.salesai.adapters.MockEmailThreadAdapter();
} else {
    java.nio.file.Path cfgPath = mcpConfigPath;
    if (cfgPath == null) cfgPath = java.nio.file.Paths.get("mcp-config.json");
    var cfgs = com.example.salesai.mcp.client.McpConfigLoader.load(cfgPath);
    var cfg = cfgs.get(emailSource);
    if (cfg == null) {
        System.err.println("No MCP server named '" + emailSource
            + "' in " + cfgPath
            + " (available: " + cfgs.keySet() + ")");
        System.exit(2);
        return;
    }
    emailMcp = com.example.salesai.mcp.client.McpClient.spawn(cfg);
    emailMcp.initialize(15_000);
    var mapping = com.example.salesai.adapters.email
        .EmailMcpToolMapping.fromConfigName(emailSource);
    emailPort = new com.example.salesai.adapters.email
        .McpEmailThreadAdapter(emailMcp, mapping);
}
```

- [ ] **Step 4: Replace the existing `MockEmailThreadAdapter` reference in workflow construction**

Find the line that constructs `AdvisorWorkflow` and replace `new MockEmailThreadAdapter()` with the local variable `emailPort`.

- [ ] **Step 5: Add cleanup**

After the workflow runs and report renders, before `System.exit`, add:
```java
if (emailMcp != null) {
    emailMcp.close();
}
```

- [ ] **Step 6: Smoke test (mock path still works)**

```sh
# Compile
javac -d out @<(find src/main/java -name '*.java')
# Or PowerShell equivalent — see existing build conventions.

# Run mock mode (must still produce output)
java -cp out com.example.salesai.SalesAiCli
```
Expected: Same output as before this work — mock mode unchanged.

- [ ] **Step 7: Commit**

```sh
git add src/main/java/com/example/salesai/SalesAiCli.java
git commit -m "cli: wire --email and --mcp-config flags"
```

---

### Task 2.10: Phase 2 documentation + example config

**Files:**
- Create: `docs/integrations/gmail.md`
- Create: `docs/integrations/outlook.md`
- Create: `mcp-config.json.example`
- Modify: `.gitignore` (add `mcp-config.json`)

- [ ] **Step 1: Create `mcp-config.json.example`**

```json
{
  "mcpServers": {
    "gmail": {
      "command": "npx",
      "args": ["-y", "@gongrzhe/server-gmail-autoauth-mcp"],
      "env": {}
    },
    "outlook": {
      "command": "uvx",
      "args": ["mcp-server-outlook"],
      "env": {}
    }
  }
}
```

- [ ] **Step 2: Create `docs/integrations/gmail.md`**

Cover: prerequisites (Node.js 20+), one-time OAuth setup steps for `@gongrzhe/server-gmail-autoauth-mcp` (the user runs `npx @gongrzhe/server-gmail-autoauth-mcp auth` once, browser opens, token cached at `~/.gmail-mcp/credentials.json`), the engine command to use Gmail (`java SalesAiCli --email gmail --mcp-config ./mcp-config.json --customer alice@acme.com`), troubleshooting (token expired? 403 from Google? rate limits?).

Keep it under 100 lines. Link to the npm package's own README for deep details.

- [ ] **Step 3: Create `docs/integrations/outlook.md`**

Same structure for `mcp-server-outlook` (uvx, Microsoft Graph OAuth, token cache location, engine command, troubleshooting).

- [ ] **Step 4: Add `mcp-config.json` to `.gitignore`**

```
mcp-config.json
```

(So local config with potentially-sensitive paths doesn't get committed. The `.example` file is committed.)

- [ ] **Step 5: Commit**

```sh
git add docs/integrations/ mcp-config.json.example .gitignore
git commit -m "docs: Phase 2 — Gmail/Outlook integration guides + example config"
```

---

## Phase 4 — Real LLM via HTTP (12 tasks)

After this phase, `--llm anthropic` (and `openai`, `gemini`, `openai-compatible`) produces real LLM-drafted replies, with the risk gate enforced at the workflow level.

---

### Task 4.1: LlmRequest, LlmResponse, LlmClient interface

**Files:**
- Create: `src/main/java/com/example/salesai/adapters/llm/LlmRequest.java`
- Create: `src/main/java/com/example/salesai/adapters/llm/LlmResponse.java`
- Create: `src/main/java/com/example/salesai/adapters/llm/LlmClient.java`

- [ ] **Step 1: Create `LlmRequest`**

`src/main/java/com/example/salesai/adapters/llm/LlmRequest.java`:
```java
package com.example.salesai.adapters.llm;

public record LlmRequest(
        String systemPrompt,
        String userPrompt,
        int maxTokens,
        double temperature,
        String model) {

    public LlmRequest {
        if (systemPrompt == null) systemPrompt = "";
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("userPrompt");
        }
        if (maxTokens <= 0) maxTokens = 1024;
        if (temperature < 0.0 || temperature > 2.0) {
            throw new IllegalArgumentException("temperature out of range");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model");
        }
    }
}
```

- [ ] **Step 2: Create `LlmResponse`**

`src/main/java/com/example/salesai/adapters/llm/LlmResponse.java`:
```java
package com.example.salesai.adapters.llm;

public record LlmResponse(
        String text,
        int inputTokens,
        int outputTokens,
        String model,
        long latencyMs) {}
```

- [ ] **Step 3: Create `LlmClient` interface**

`src/main/java/com/example/salesai/adapters/llm/LlmClient.java`:
```java
package com.example.salesai.adapters.llm;

import java.io.IOException;

public interface LlmClient {

    /** Calls the underlying LLM and returns a parsed response. */
    LlmResponse complete(LlmRequest request) throws IOException;

    /** Stable provider name for audit (e.g., "anthropic", "openai", "gemini"). */
    String providerName();

    /** Provider-specific default model when --llm-model is not passed. */
    String defaultModel();
}
```

- [ ] **Step 4: Compile check (no test yet — covered in Tasks 4.3+)**

```sh
javac -d out src/main/java/com/example/salesai/adapters/llm/*.java
```
Expected: clean compile.

- [ ] **Step 5: Commit**

```sh
git add src/main/java/com/example/salesai/adapters/llm/LlmRequest.java \
        src/main/java/com/example/salesai/adapters/llm/LlmResponse.java \
        src/main/java/com/example/salesai/adapters/llm/LlmClient.java
git commit -m "llm: add LlmRequest/LlmResponse/LlmClient core types"
```

---

### Task 4.2: LlmCallAuditEntry record + widen `AuditEntry permits`

**Files:**
- Create: `src/main/java/com/example/salesai/adapters/llm/LlmCallAuditEntry.java`
- Modify: `src/main/java/com/example/salesai/audit/AuditEntry.java`
- Create: `src/test/java/com/example/salesai/adapters/llm/LlmCallAuditEntryTest.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/com/example/salesai/adapters/llm/LlmCallAuditEntryTest.java`:
```java
package com.example.salesai.adapters.llm;

import com.example.salesai.audit.AuditEntry;

import java.time.Instant;

public final class LlmCallAuditEntryTest {
    public static void main(String[] args) {
        new LlmCallAuditEntryTest().run();
    }

    void run() {
        testRecordHoldsAllFields();
        testIsAuditEntry();
        testApiKeyFingerprintHidesMostOfTheKey();
        System.out.println("LlmCallAuditEntryTest: 3 passed");
    }

    void testRecordHoldsAllFields() {
        LlmCallAuditEntry e = sample();
        assert "anthropic".equals(e.provider());
        assert "claude-3-5-sonnet-20241022".equals(e.model());
        assert e.inputTokens() == 523;
        assert e.outputTokens() == 128;
        assert e.latencyMs() == 1840L;
    }

    void testIsAuditEntry() {
        AuditEntry e = sample();
        assert e instanceof LlmCallAuditEntry;
    }

    void testApiKeyFingerprintHidesMostOfTheKey() {
        String fp = LlmCallAuditEntry.fingerprint("sk-ant-api03-XXXXXXXXabcd");
        assert fp.startsWith("sk-ant-...");
        assert fp.endsWith("abcd");
        assert !fp.contains("XXXXX");
    }

    private static LlmCallAuditEntry sample() {
        return new LlmCallAuditEntry(
            Instant.parse("2026-05-07T14:32:18Z"),
            "anthropic",
            "claude-3-5-sonnet-20241022",
            "deadbeef",
            2103,
            487,
            523,
            128,
            1840L,
            0.00385,
            "sk-ant-...8a2f",
            "draft_reply",
            "req_a4f1");
    }
}
```

- [ ] **Step 2: Run, FAIL**

- [ ] **Step 3: Implement `LlmCallAuditEntry`**

`src/main/java/com/example/salesai/adapters/llm/LlmCallAuditEntry.java`:
```java
package com.example.salesai.adapters.llm;

import com.example.salesai.audit.AuditEntry;

import java.time.Instant;

public record LlmCallAuditEntry(
        Instant timestamp,
        String provider,
        String model,
        String promptHash,
        int promptLengthChars,
        int responseLengthChars,
        int inputTokens,
        int outputTokens,
        long latencyMs,
        double estimatedCostUsd,
        String apiKeyFingerprint,
        String workflowStep,
        String requestId) implements AuditEntry {

    public LlmCallAuditEntry {
        if (timestamp == null) throw new IllegalArgumentException("timestamp");
        if (provider == null || provider.isBlank()) throw new IllegalArgumentException("provider");
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model");
    }

    /**
     * Redact an API key for logging. Keeps the provider prefix (everything
     * before the first dash after "sk-") and the last 4 characters; replaces
     * the rest with "...".
     */
    public static String fingerprint(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) return "***";
        int firstDashAfterSk = apiKey.indexOf('-', 3);
        String prefix = firstDashAfterSk > 0
            ? apiKey.substring(0, firstDashAfterSk + 1)
            : apiKey.substring(0, Math.min(7, apiKey.length()));
        String suffix = apiKey.substring(apiKey.length() - 4);
        return prefix + "..." + suffix;
    }
}
```

- [ ] **Step 4: Widen `AuditEntry permits`**

Replace contents of `src/main/java/com/example/salesai/audit/AuditEntry.java`:
```java
package com.example.salesai.audit;

import com.example.salesai.adapters.llm.LlmCallAuditEntry;

import java.time.Instant;

public sealed interface AuditEntry
        permits TextAuditEntry, LlmCallAuditEntry {

    Instant timestamp();
}
```

- [ ] **Step 5: Run, PASS**

- [ ] **Step 6: Commit**

```sh
git add src/main/java/com/example/salesai/adapters/llm/LlmCallAuditEntry.java \
        src/main/java/com/example/salesai/audit/AuditEntry.java \
        src/test/java/com/example/salesai/adapters/llm/LlmCallAuditEntryTest.java
git commit -m "llm: add LlmCallAuditEntry + widen AuditEntry sealed permits"
```

---

### Task 4.3: AnthropicLlmClient (TDD with JDK HttpServer mock)

**Files:**
- Create: `src/main/java/com/example/salesai/adapters/llm/AnthropicLlmClient.java`
- Create: `src/test/java/com/example/salesai/adapters/llm/AnthropicLlmClientTest.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/com/example/salesai/adapters/llm/AnthropicLlmClientTest.java`:
```java
package com.example.salesai.adapters.llm;

import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

public final class AnthropicLlmClientTest {

    public static void main(String[] args) throws Exception {
        new AnthropicLlmClientTest().run();
    }

    void run() throws Exception {
        testBuildsCorrectRequestAndParsesResponse();
        testRaises4xx();
        System.out.println("AnthropicLlmClientTest: 2 passed");
    }

    void testBuildsCorrectRequestAndParsesResponse() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicReference<String> capturedAuth = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", ex -> {
            capturedAuth.set(ex.getRequestHeaders().getFirst("x-api-key"));
            byte[] body = ex.getRequestBody().readAllBytes();
            capturedBody.set(new String(body, StandardCharsets.UTF_8));
            String resp = """
                {"content":[{"type":"text","text":"hi back"}],
                 "usage":{"input_tokens":10,"output_tokens":3},
                 "model":"claude-3-5-sonnet-20241022"}
                """;
            ex.sendResponseHeaders(200, resp.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(resp.getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();
        try {
            String base = "http://" + server.getAddress().getHostString()
                + ":" + server.getAddress().getPort();
            AnthropicLlmClient c = new AnthropicLlmClient("sk-ant-test-fake0000abcd", base);
            LlmRequest req = new LlmRequest(
                "system instructions", "say hi",
                100, 0.5, "claude-3-5-sonnet-20241022");
            LlmResponse resp = c.complete(req);
            assert "hi back".equals(resp.text()) : resp.text();
            assert resp.inputTokens() == 10;
            assert resp.outputTokens() == 3;
            assert "sk-ant-test-fake0000abcd".equals(capturedAuth.get());
            assert capturedBody.get().contains("claude-3-5-sonnet-20241022");
            assert capturedBody.get().contains("system instructions");
            assert capturedBody.get().contains("say hi");
        } finally {
            server.stop(0);
        }
    }

    void testRaises4xx() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", ex -> {
            String body = "{\"error\":{\"message\":\"invalid api key\"}}";
            ex.sendResponseHeaders(401, body.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();
        try {
            String base = "http://" + server.getAddress().getHostString()
                + ":" + server.getAddress().getPort();
            AnthropicLlmClient c = new AnthropicLlmClient("bad", base);
            LlmRequest req = new LlmRequest(
                "", "hi", 100, 0.5, "claude-3-5-sonnet-20241022");
            try {
                c.complete(req);
                throw new AssertionError("expected IOException");
            } catch (java.io.IOException ok) {
                assert ok.getMessage().contains("401") : ok.getMessage();
            }
        } finally {
            server.stop(0);
        }
    }
}
```

- [ ] **Step 2: Run, FAIL**

- [ ] **Step 3: Implement `AnthropicLlmClient`**

`src/main/java/com/example/salesai/adapters/llm/AnthropicLlmClient.java`:
```java
package com.example.salesai.adapters.llm;

import com.example.salesai.adapters.MiniJson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AnthropicLlmClient implements LlmClient {

    private static final String DEFAULT_BASE = "https://api.anthropic.com";
    private static final String DEFAULT_MODEL = "claude-3-5-sonnet-20241022";

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient http;

    public AnthropicLlmClient(String apiKey) {
        this(apiKey, DEFAULT_BASE);
    }

    public AnthropicLlmClient(String apiKey, String baseUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("ANTHROPIC_API_KEY missing");
        }
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Override
    public LlmResponse complete(LlmRequest req) throws IOException {
        long start = System.currentTimeMillis();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", req.model());
        body.put("max_tokens", req.maxTokens());
        body.put("temperature", req.temperature());
        if (!req.systemPrompt().isEmpty()) body.put("system", req.systemPrompt());
        body.put("messages", List.of(Map.of(
            "role", "user",
            "content", req.userPrompt())));

        String json = MiniJson.write(body);

        HttpRequest httpReq = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/v1/messages"))
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        HttpResponse<String> resp;
        try {
            resp = http.send(httpReq, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }

        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("anthropic " + resp.statusCode() + ": "
                + truncate(resp.body(), 500));
        }

        Map<String, Object> root = MiniJson.asObject(MiniJson.parse(resp.body()));
        List<?> content = (List<?>) root.getOrDefault("content", List.of());
        StringBuilder text = new StringBuilder();
        for (Object c : content) {
            Map<String, Object> cm = MiniJson.asObject(c);
            if ("text".equals(cm.get("type"))) {
                if (text.length() > 0) text.append('\n');
                text.append(MiniJson.asString(cm.get("text")));
            }
        }
        Map<String, Object> usage = MiniJson.asObject(
            root.getOrDefault("usage", Map.of()));
        int inTokens = ((Number) usage.getOrDefault("input_tokens", 0)).intValue();
        int outTokens = ((Number) usage.getOrDefault("output_tokens", 0)).intValue();
        long latency = System.currentTimeMillis() - start;
        return new LlmResponse(text.toString(), inTokens, outTokens,
            req.model(), latency);
    }

    @Override
    public String providerName() { return "anthropic"; }

    @Override
    public String defaultModel() { return DEFAULT_MODEL; }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
```

- [ ] **Step 4: Run, PASS**

- [ ] **Step 5: Commit**

```sh
git add src/main/java/com/example/salesai/adapters/llm/AnthropicLlmClient.java \
        src/test/java/com/example/salesai/adapters/llm/AnthropicLlmClientTest.java
git commit -m "llm: add AnthropicLlmClient (Messages API, java.net.http)"
```

---

### Task 4.4: OpenAiLlmClient (with `endpointOverride` for local LLM)

**Files:**
- Create: `src/main/java/com/example/salesai/adapters/llm/OpenAiLlmClient.java`
- Create: `src/test/java/com/example/salesai/adapters/llm/OpenAiLlmClientTest.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/com/example/salesai/adapters/llm/OpenAiLlmClientTest.java`:
```java
package com.example.salesai.adapters.llm;

import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

public final class OpenAiLlmClientTest {
    public static void main(String[] args) throws Exception {
        new OpenAiLlmClientTest().run();
    }

    void run() throws Exception {
        testCallsChatCompletionsAndParses();
        testEndpointOverrideReachesCustomServer();
        System.out.println("OpenAiLlmClientTest: 2 passed");
    }

    void testCallsChatCompletionsAndParses() throws Exception {
        AtomicReference<String> bodyRef = new AtomicReference<>();
        AtomicReference<String> authRef = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", ex -> {
            authRef.set(ex.getRequestHeaders().getFirst("Authorization"));
            bodyRef.set(new String(ex.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8));
            String resp = """
                {"choices":[{"message":{"content":"hi from gpt"}}],
                 "usage":{"prompt_tokens":12,"completion_tokens":4},
                 "model":"gpt-4o-2024-08-06"}
                """;
            ex.sendResponseHeaders(200, resp.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(resp.getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();
        try {
            String base = "http://" + server.getAddress().getHostString()
                + ":" + server.getAddress().getPort();
            OpenAiLlmClient c = new OpenAiLlmClient("sk-test", base);
            LlmRequest req = new LlmRequest("sys", "hi", 100, 0.5,
                "gpt-4o-2024-08-06");
            LlmResponse resp = c.complete(req);
            assert "hi from gpt".equals(resp.text());
            assert resp.inputTokens() == 12;
            assert "Bearer sk-test".equals(authRef.get());
            assert bodyRef.get().contains("gpt-4o-2024-08-06");
        } finally {
            server.stop(0);
        }
    }

    void testEndpointOverrideReachesCustomServer() throws Exception {
        // Same body shape as OpenAI — proves a local OpenAI-compatible server works.
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", ex -> {
            String resp = """
                {"choices":[{"message":{"content":"local"}}],
                 "usage":{"prompt_tokens":1,"completion_tokens":1}}
                """;
            ex.sendResponseHeaders(200, resp.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(resp.getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            // Empty key should be allowed for local LLM.
            OpenAiLlmClient c = new OpenAiLlmClient("", base);
            LlmResponse resp = c.complete(new LlmRequest(
                "", "ping", 50, 0.7, "llama3.1:70b"));
            assert "local".equals(resp.text());
        } finally {
            server.stop(0);
        }
    }
}
```

- [ ] **Step 2: Run, FAIL**

- [ ] **Step 3: Implement `OpenAiLlmClient`**

`src/main/java/com/example/salesai/adapters/llm/OpenAiLlmClient.java`:
```java
package com.example.salesai.adapters.llm;

import com.example.salesai.adapters.MiniJson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions client. Also serves any OpenAI-compatible
 * endpoint (Ollama, vLLM, llama.cpp, LM Studio, OpenRouter) by passing a
 * non-default {@code baseUrl} — same wire protocol, different host.
 */
public final class OpenAiLlmClient implements LlmClient {

    private static final String DEFAULT_BASE = "https://api.openai.com";
    private static final String DEFAULT_MODEL = "gpt-4o-2024-08-06";

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient http;

    public OpenAiLlmClient(String apiKey) {
        this(apiKey, DEFAULT_BASE);
    }

    /** apiKey may be empty for local OpenAI-compatible endpoints. */
    public OpenAiLlmClient(String apiKey, String baseUrl) {
        this.apiKey = apiKey == null ? "" : apiKey;
        this.baseUrl = baseUrl == null ? DEFAULT_BASE : baseUrl;
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Override
    public LlmResponse complete(LlmRequest req) throws IOException {
        long start = System.currentTimeMillis();

        java.util.List<Map<String, Object>> messages = new java.util.ArrayList<>();
        if (!req.systemPrompt().isEmpty()) {
            messages.add(Map.of("role", "system", "content", req.systemPrompt()));
        }
        messages.add(Map.of("role", "user", "content", req.userPrompt()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", req.model());
        body.put("messages", messages);
        body.put("max_tokens", req.maxTokens());
        body.put("temperature", req.temperature());

        String json = MiniJson.write(body);

        HttpRequest.Builder b = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/v1/chat/completions"))
            .header("content-type", "application/json")
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(json));
        if (!apiKey.isEmpty()) {
            b.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> resp;
        try {
            resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }

        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("openai " + resp.statusCode() + ": "
                + truncate(resp.body(), 500));
        }

        Map<String, Object> root = MiniJson.asObject(MiniJson.parse(resp.body()));
        List<?> choices = (List<?>) root.getOrDefault("choices", List.of());
        String text = "";
        if (!choices.isEmpty()) {
            Map<String, Object> firstChoice = MiniJson.asObject(choices.get(0));
            Map<String, Object> message = MiniJson.asObject(
                firstChoice.getOrDefault("message", Map.of()));
            text = MiniJson.asString(message.getOrDefault("content", ""));
        }
        Map<String, Object> usage = MiniJson.asObject(
            root.getOrDefault("usage", Map.of()));
        int inTokens = ((Number) usage.getOrDefault("prompt_tokens", 0)).intValue();
        int outTokens = ((Number) usage.getOrDefault("completion_tokens", 0)).intValue();
        long latency = System.currentTimeMillis() - start;
        return new LlmResponse(text, inTokens, outTokens, req.model(), latency);
    }

    @Override
    public String providerName() {
        return baseUrl.equals(DEFAULT_BASE) ? "openai" : "openai-compatible";
    }

    @Override
    public String defaultModel() { return DEFAULT_MODEL; }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
```

- [ ] **Step 4: Run, PASS**

- [ ] **Step 5: Commit**

```sh
git add src/main/java/com/example/salesai/adapters/llm/OpenAiLlmClient.java \
        src/test/java/com/example/salesai/adapters/llm/OpenAiLlmClientTest.java
git commit -m "llm: add OpenAiLlmClient (cloud + local via endpointOverride)"
```

---

### Task 4.5: GeminiLlmClient

**Files:**
- Create: `src/main/java/com/example/salesai/adapters/llm/GeminiLlmClient.java`
- Create: `src/test/java/com/example/salesai/adapters/llm/GeminiLlmClientTest.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/com/example/salesai/adapters/llm/GeminiLlmClientTest.java`:
```java
package com.example.salesai.adapters.llm;

import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

public final class GeminiLlmClientTest {
    public static void main(String[] args) throws Exception {
        new GeminiLlmClientTest().run();
    }

    void run() throws Exception {
        testCallsGenerateContentAndParses();
        System.out.println("GeminiLlmClientTest: 1 passed");
    }

    void testCallsGenerateContentAndParses() throws Exception {
        AtomicReference<String> queryRef = new AtomicReference<>();
        AtomicReference<String> bodyRef = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/models/gemini-1.5-pro-002:generateContent", ex -> {
            queryRef.set(ex.getRequestURI().getQuery());
            bodyRef.set(new String(ex.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8));
            String resp = """
                {"candidates":[{"content":{"parts":[{"text":"hi from gemini"}]}}],
                 "usageMetadata":{"promptTokenCount":7,"candidatesTokenCount":3}}
                """;
            ex.sendResponseHeaders(200, resp.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(resp.getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();
        try {
            String base = "http://" + server.getAddress().getHostString()
                + ":" + server.getAddress().getPort();
            GeminiLlmClient c = new GeminiLlmClient("test-key", base);
            LlmResponse resp = c.complete(new LlmRequest(
                "sys", "hi", 100, 0.5, "gemini-1.5-pro-002"));
            assert "hi from gemini".equals(resp.text()) : resp.text();
            assert resp.inputTokens() == 7;
            assert resp.outputTokens() == 3;
            assert queryRef.get().contains("key=test-key");
            assert bodyRef.get().contains("\"text\":\"hi\"");
        } finally {
            server.stop(0);
        }
    }
}
```

- [ ] **Step 2: Run, FAIL**

- [ ] **Step 3: Implement**

`src/main/java/com/example/salesai/adapters/llm/GeminiLlmClient.java`:
```java
package com.example.salesai.adapters.llm;

import com.example.salesai.adapters.MiniJson;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GeminiLlmClient implements LlmClient {

    private static final String DEFAULT_BASE = "https://generativelanguage.googleapis.com";
    private static final String DEFAULT_MODEL = "gemini-1.5-pro-002";

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient http;

    public GeminiLlmClient(String apiKey) { this(apiKey, DEFAULT_BASE); }

    public GeminiLlmClient(String apiKey, String baseUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("GEMINI_API_KEY missing");
        }
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Override
    public LlmResponse complete(LlmRequest req) throws IOException {
        long start = System.currentTimeMillis();

        // Gemini puts "system" as a separate top-level field; user/model in contents.
        Map<String, Object> body = new LinkedHashMap<>();
        if (!req.systemPrompt().isEmpty()) {
            body.put("systemInstruction", Map.of(
                "parts", List.of(Map.of("text", req.systemPrompt()))));
        }
        body.put("contents", List.of(Map.of(
            "role", "user",
            "parts", List.of(Map.of("text", req.userPrompt())))));
        body.put("generationConfig", Map.of(
            "maxOutputTokens", req.maxTokens(),
            "temperature", req.temperature()));

        String json = MiniJson.write(body);
        String url = baseUrl + "/v1beta/models/" + req.model()
            + ":generateContent?key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);

        HttpRequest httpReq = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("content-type", "application/json")
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        HttpResponse<String> resp;
        try {
            resp = http.send(httpReq, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }

        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("gemini " + resp.statusCode() + ": "
                + truncate(resp.body(), 500));
        }

        Map<String, Object> root = MiniJson.asObject(MiniJson.parse(resp.body()));
        List<?> candidates = (List<?>) root.getOrDefault("candidates", List.of());
        StringBuilder text = new StringBuilder();
        if (!candidates.isEmpty()) {
            Map<String, Object> first = MiniJson.asObject(candidates.get(0));
            Map<String, Object> content = MiniJson.asObject(
                first.getOrDefault("content", Map.of()));
            List<?> parts = (List<?>) content.getOrDefault("parts", List.of());
            for (Object p : parts) {
                Map<String, Object> pm = MiniJson.asObject(p);
                if (text.length() > 0) text.append('\n');
                text.append(MiniJson.asString(pm.getOrDefault("text", "")));
            }
        }
        Map<String, Object> usage = MiniJson.asObject(
            root.getOrDefault("usageMetadata", Map.of()));
        int inTokens = ((Number) usage.getOrDefault("promptTokenCount", 0)).intValue();
        int outTokens = ((Number) usage.getOrDefault("candidatesTokenCount", 0)).intValue();
        long latency = System.currentTimeMillis() - start;
        return new LlmResponse(text.toString(), inTokens, outTokens, req.model(), latency);
    }

    @Override
    public String providerName() { return "gemini"; }

    @Override
    public String defaultModel() { return DEFAULT_MODEL; }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
```

- [ ] **Step 4: Run, PASS**

- [ ] **Step 5: Commit**

```sh
git add src/main/java/com/example/salesai/adapters/llm/GeminiLlmClient.java \
        src/test/java/com/example/salesai/adapters/llm/GeminiLlmClientTest.java
git commit -m "llm: add GeminiLlmClient (generateContent endpoint)"
```

---

### Task 4.6: PromptBuilder

**Files:**
- Create: `src/main/java/com/example/salesai/adapters/llm/PromptBuilder.java`
- Create: `src/test/java/com/example/salesai/adapters/llm/PromptBuilderTest.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/com/example/salesai/adapters/llm/PromptBuilderTest.java`:
```java
package com.example.salesai.adapters.llm;

import com.example.salesai.domain.BusinessIntent;
import com.example.salesai.domain.CommercialHistory;
import com.example.salesai.domain.CustomerProfile;
import com.example.salesai.domain.EmailMessage;
import com.example.salesai.domain.EmailThread;
import com.example.salesai.domain.RiskAssessment;
import com.example.salesai.domain.RiskLevel;

import java.time.Instant;
import java.util.List;

public final class PromptBuilderTest {
    public static void main(String[] args) {
        new PromptBuilderTest().run();
    }

    void run() {
        testSystemPromptForbidsAutoSendAndCommitments();
        testUserPromptIncludesCustomerAndThread();
        System.out.println("PromptBuilderTest: 2 passed");
    }

    void testSystemPromptForbidsAutoSendAndCommitments() {
        String sys = PromptBuilder.systemPrompt();
        assert sys.toLowerCase().contains("never auto-send");
        assert sys.toLowerCase().contains("never promise");
        assert sys.contains("strict JSON");
        assert sys.contains("drafts");
    }

    void testUserPromptIncludesCustomerAndThread() {
        // Build minimal but realistic domain objects.
        // (Adjust constructor calls to match the project's actual records — engineer
        // should verify by reading src/main/java/com/example/salesai/domain/)
        CustomerProfile profile = sampleCustomer();
        EmailThread thread = new EmailThread("thr-1", "Order ETA?",
            List.of(new EmailMessage("alice@acme.com", "support@vendor.com",
                Instant.now(), "When does my order ship?")));
        RiskAssessment risk = new RiskAssessment(
            RiskLevel.LOW, false, List.of(), List.of(), "low risk");

        String prompt = PromptBuilder.userPrompt(
            profile, thread, BusinessIntent.STATUS_INQUIRY, risk);
        assert prompt.contains("ACME") || prompt.contains("Standard");
        assert prompt.contains("Order ETA?");
        assert prompt.contains("STATUS_INQUIRY");
        assert prompt.contains("LOW");
    }

    private static CustomerProfile sampleCustomer() {
        return new CustomerProfile(
            "C-1", "ACME Corp", "alice@acme.com", "Standard",
            "alice@acme.com", "Pat Manager",
            new CommercialHistory(List.of(), List.of(), List.of()));
    }
}
```

(The engineer must adjust the test's `CustomerProfile`/`CommercialHistory`/`EmailThread`/`RiskAssessment`/`EmailMessage` constructor calls to match the actual record signatures in `src/main/java/com/example/salesai/domain/`. Run `cat src/main/java/com/example/salesai/domain/CustomerProfile.java` etc. before writing.)

- [ ] **Step 2: Run, FAIL**

- [ ] **Step 3: Implement**

`src/main/java/com/example/salesai/adapters/llm/PromptBuilder.java`:
```java
package com.example.salesai.adapters.llm;

import com.example.salesai.domain.BusinessIntent;
import com.example.salesai.domain.CustomerProfile;
import com.example.salesai.domain.EmailMessage;
import com.example.salesai.domain.EmailThread;
import com.example.salesai.domain.RiskAssessment;

public final class PromptBuilder {

    private static final String SYSTEM_PROMPT = """
        You are a senior B2B account manager drafting reply emails on
        behalf of a colleague. You are NOT the customer-facing voice —
        a human will read your drafts and choose one before any send.

        Hard rules:
          - Never auto-send. You only draft.
          - Never promise refunds, contract changes, legal commitments,
            or special discounts. Those require manager approval and
            are blocked upstream.
          - Match the customer's tone but stay professional.
          - Reply in the same language the customer wrote in.

        Output strict JSON in this exact shape:
          {"drafts": [
            {"strategy": "formal_safe",
             "subject": "...",
             "body": "..."},
            {"strategy": "warm_relationship",
             "subject": "...",
             "body": "..."}
          ]}

        Both drafts MUST address the customer's question concretely.
        Do not include any text outside the JSON.
        """;

    private PromptBuilder() {}

    public static String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    public static String userPrompt(
            CustomerProfile profile,
            EmailThread thread,
            BusinessIntent intent,
            RiskAssessment risk) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Customer ===\n");
        sb.append("Name: ").append(profile.name()).append('\n');
        sb.append("Tier: ").append(profile.tier()).append('\n');
        sb.append("Account manager: ").append(profile.accountManager()).append('\n');

        sb.append("\n=== Detected ===\n");
        sb.append("Intent: ").append(intent.name()).append('\n');
        sb.append("Risk level: ").append(risk.level().name()).append('\n');
        sb.append("Risk reason: ").append(risk.reason()).append('\n');

        sb.append("\n=== Recent thread (subject: ").append(thread.subject()).append(") ===\n");
        for (EmailMessage m : thread.messages()) {
            sb.append("---\n");
            sb.append("From: ").append(m.from()).append('\n');
            sb.append("To: ").append(m.to()).append('\n');
            sb.append("Sent: ").append(m.sentAt()).append('\n');
            sb.append('\n').append(m.body()).append('\n');
        }

        sb.append("\n=== Task ===\n");
        sb.append("Draft 2 reply options for the human reviewer to choose from.\n");
        return sb.toString();
    }
}
```

- [ ] **Step 4: Run, PASS**

- [ ] **Step 5: Commit**

```sh
git add src/main/java/com/example/salesai/adapters/llm/PromptBuilder.java \
        src/test/java/com/example/salesai/adapters/llm/PromptBuilderTest.java
git commit -m "llm: add PromptBuilder (system prompt + structured user prompt)"
```

---

### Task 4.7: LlmReplyDraftAdapter

**Files:**
- Create: `src/main/java/com/example/salesai/adapters/llm/LlmReplyDraftAdapter.java`
- Create: `src/test/java/com/example/salesai/adapters/llm/LlmReplyDraftAdapterTest.java`

This adapter calls the LLM once per workflow run, parses the JSON response into 2 `ReplyDraft` objects, and writes one `LlmCallAuditEntry`. It does **not** check risk — that's the workflow's job (Task 4.8).

- [ ] **Step 1: Write the failing test**

`src/test/java/com/example/salesai/adapters/llm/LlmReplyDraftAdapterTest.java`:
```java
package com.example.salesai.adapters.llm;

import com.example.salesai.adapters.ConsoleAuditLogAdapter;
import com.example.salesai.audit.AuditEntry;
import com.example.salesai.domain.CommercialHistory;
import com.example.salesai.domain.CustomerProfile;
import com.example.salesai.domain.EmailMessage;
import com.example.salesai.domain.EmailThread;
import com.example.salesai.domain.ReplyDraft;
import com.example.salesai.domain.ReplyStrategy;
import com.example.salesai.ports.ReplyDraftPort;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class LlmReplyDraftAdapterTest {
    public static void main(String[] args) {
        new LlmReplyDraftAdapterTest().run();
    }

    void run() {
        testProducesTwoDraftsFromValidJsonResponse();
        testWritesAuditEntryAfterEachCall();
        System.out.println("LlmReplyDraftAdapterTest: 2 passed");
    }

    void testProducesTwoDraftsFromValidJsonResponse() {
        FakeLlm llm = new FakeLlm("""
            {"drafts":[
              {"strategy":"formal_safe","subject":"Order update","body":"Dear..."},
              {"strategy":"warm_relationship","subject":"Quick update","body":"Hi..."}
            ]}
            """);
        ConsoleAuditLogAdapter audit = new ConsoleAuditLogAdapter();
        ReplyDraftPort port = new LlmReplyDraftAdapter(llm, audit, null, "draft_reply");
        List<ReplyDraft> drafts = port.generate(
            sampleCustomer(), sampleThread(),
            new ReplyStrategy("warm", "answer", List.of(), List.of(), "ship"));
        assert drafts.size() == 2 : "got " + drafts.size();
    }

    void testWritesAuditEntryAfterEachCall() {
        FakeLlm llm = new FakeLlm("""
            {"drafts":[
              {"strategy":"formal_safe","subject":"x","body":"y"},
              {"strategy":"warm_relationship","subject":"x","body":"y"}
            ]}
            """);
        AtomicReference<AuditEntry> captured = new AtomicReference<>();
        com.example.salesai.ports.AuditLogPort spy = new com.example.salesai.ports.AuditLogPort() {
            @Override public void log(String e, String d) {}
            @Override public void log(AuditEntry entry) { captured.set(entry); }
            @Override public java.util.List<String> entries() { return List.of(); }
        };
        ReplyDraftPort port = new LlmReplyDraftAdapter(llm, spy, null, "draft_reply");
        port.generate(sampleCustomer(), sampleThread(),
            new ReplyStrategy("warm", "answer", List.of(), List.of(), "ship"));
        assert captured.get() instanceof LlmCallAuditEntry : "no LlmCallAuditEntry written";
        LlmCallAuditEntry e = (LlmCallAuditEntry) captured.get();
        assert "fake".equals(e.provider());
        assert "fake-model".equals(e.model());
    }

    static CustomerProfile sampleCustomer() {
        return new CustomerProfile("C-1", "ACME", "alice@acme.com", "Standard",
            "alice@acme.com", "Pat", new CommercialHistory(List.of(), List.of(), List.of()));
    }

    static EmailThread sampleThread() {
        return new EmailThread("t1", "subj",
            List.of(new EmailMessage("alice@acme.com", "us@x.com",
                Instant.now(), "body")));
    }

    static class FakeLlm implements LlmClient {
        final String reply;
        FakeLlm(String reply) { this.reply = reply; }
        @Override public LlmResponse complete(LlmRequest r) throws IOException {
            return new LlmResponse(reply, 10, 20, "fake-model", 50L);
        }
        @Override public String providerName() { return "fake"; }
        @Override public String defaultModel() { return "fake-model"; }
    }
}
```

- [ ] **Step 2: Run, FAIL**

- [ ] **Step 3: Implement**

`src/main/java/com/example/salesai/adapters/llm/LlmReplyDraftAdapter.java`:
```java
package com.example.salesai.adapters.llm;

import com.example.salesai.adapters.MiniJson;
import com.example.salesai.classify.RuleBasedIntentClassifier;
import com.example.salesai.domain.BusinessIntent;
import com.example.salesai.domain.CustomerProfile;
import com.example.salesai.domain.EmailThread;
import com.example.salesai.domain.ReplyDraft;
import com.example.salesai.domain.ReplyStrategy;
import com.example.salesai.domain.RiskAssessment;
import com.example.salesai.domain.RiskLevel;
import com.example.salesai.ports.AuditLogPort;
import com.example.salesai.ports.ReplyDraftPort;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LlmReplyDraftAdapter implements ReplyDraftPort {

    private static final int MAX_TOKENS = 1024;
    private static final double TEMPERATURE = 0.3;

    private final LlmClient llm;
    private final AuditLogPort audit;
    private final String apiKeyForFingerprint;
    private final String workflowStep;
    private final RuleBasedIntentClassifier classifier = new RuleBasedIntentClassifier();

    /**
     * @param apiKeyForFingerprint may be null (e.g., local LLM with no key).
     */
    public LlmReplyDraftAdapter(LlmClient llm, AuditLogPort audit,
                                 String apiKeyForFingerprint, String workflowStep) {
        this.llm = llm;
        this.audit = audit;
        this.apiKeyForFingerprint = apiKeyForFingerprint;
        this.workflowStep = workflowStep;
    }

    @Override
    public List<ReplyDraft> generate(
            CustomerProfile customer, EmailThread thread, ReplyStrategy strategy) {

        BusinessIntent intent = classifier.classifyIntent(thread);
        RiskAssessment riskHint = new RiskAssessment(
            RiskLevel.LOW, false, List.of(), List.of(),
            "(workflow has already gated; LLM only sees non-HIGH)");

        String userPrompt = PromptBuilder.userPrompt(customer, thread, intent, riskHint);
        LlmRequest req = new LlmRequest(
            PromptBuilder.systemPrompt(), userPrompt,
            MAX_TOKENS, TEMPERATURE, llm.defaultModel());

        String requestId = "req_" + UUID.randomUUID().toString().substring(0, 8);
        LlmResponse resp;
        try {
            resp = llm.complete(req);
        } catch (IOException e) {
            audit.log("LLM_ERROR", llm.providerName() + ": " + e.getMessage());
            throw new RuntimeException("LLM call failed: " + e.getMessage(), e);
        }

        writeAudit(req, resp, requestId);

        return parseDrafts(resp.text());
    }

    private void writeAudit(LlmRequest req, LlmResponse resp, String requestId) {
        String fullPrompt = req.systemPrompt() + "\n" + req.userPrompt();
        String hash = sha256(fullPrompt);
        double cost = estimateCostUsd(llm.providerName(), resp.model(),
            resp.inputTokens(), resp.outputTokens());
        audit.log(new LlmCallAuditEntry(
            Instant.now(),
            llm.providerName(),
            resp.model(),
            hash,
            fullPrompt.length(),
            resp.text().length(),
            resp.inputTokens(),
            resp.outputTokens(),
            resp.latencyMs(),
            cost,
            apiKeyForFingerprint == null ? "(none)"
                : LlmCallAuditEntry.fingerprint(apiKeyForFingerprint),
            workflowStep,
            requestId));
    }

    private static List<ReplyDraft> parseDrafts(String text) {
        try {
            Map<String, Object> root = MiniJson.asObject(MiniJson.parse(text));
            List<?> raw = (List<?>) root.getOrDefault("drafts", List.of());
            List<ReplyDraft> out = new ArrayList<>(raw.size());
            for (Object r : raw) {
                Map<String, Object> m = MiniJson.asObject(r);
                String strategy = MiniJson.asString(m.getOrDefault("strategy", ""));
                String subject = MiniJson.asString(m.getOrDefault("subject", ""));
                String body = MiniJson.asString(m.getOrDefault("body", ""));
                out.add(new ReplyDraft(strategy, subject, body));
            }
            return out;
        } catch (RuntimeException e) {
            throw new RuntimeException("LLM returned unparseable JSON: " + e.getMessage(), e);
        }
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "(no-sha256)";
        }
    }

    /** Rough cost estimates as of 2026-Q2; update when provider pricing changes. */
    private static double estimateCostUsd(String provider, String model, int inT, int outT) {
        return switch (provider) {
            case "anthropic" -> (inT * 3e-6) + (outT * 15e-6); // $3/M in, $15/M out (Sonnet)
            case "openai"    -> (inT * 2.5e-6) + (outT * 10e-6); // gpt-4o approx
            case "gemini"    -> (inT * 1.25e-6) + (outT * 5e-6); // 1.5 Pro
            default          -> 0.0; // local LLM = free at the API boundary
        };
    }
}
```

(The `ReplyDraft` constructor `(String strategy, String subject, String body)` may not match the actual record. Engineer should `cat src/main/java/com/example/salesai/domain/ReplyDraft.java` and adjust.)

- [ ] **Step 4: Run, PASS**

- [ ] **Step 5: Commit**

```sh
git add src/main/java/com/example/salesai/adapters/llm/LlmReplyDraftAdapter.java \
        src/test/java/com/example/salesai/adapters/llm/LlmReplyDraftAdapterTest.java
git commit -m "llm: add LlmReplyDraftAdapter (audit-traced reply drafting)"
```

---

### Task 4.8: Risk gate in `AdvisorWorkflow` — THE INVARIANT

**Files:**
- Modify: `src/main/java/com/example/salesai/app/AdvisorWorkflow.java`
- Create: `src/test/java/com/example/salesai/app/AdvisorWorkflowRiskGateTest.java`

This is the most important safety task in the entire plan. The test verifies that when `RiskAssessment.level() == HIGH` (or `requiresManagerApproval == true`), the `ReplyDraftPort` is NEVER called.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/example/salesai/app/AdvisorWorkflowRiskGateTest.java`:
```java
package com.example.salesai.app;

import com.example.salesai.adapters.ConsoleAuditLogAdapter;
import com.example.salesai.adapters.ManualApprovalAdapter;
import com.example.salesai.adapters.MockCustomerContextAdapter;
import com.example.salesai.adapters.MockEmailThreadAdapter;
import com.example.salesai.adapters.NoopCrmAdapter;
import com.example.salesai.adapters.RuleBasedRiskPolicyAdapter;
import com.example.salesai.domain.AdvisorRequest;
import com.example.salesai.domain.AdvisorResult;
import com.example.salesai.domain.CustomerProfile;
import com.example.salesai.domain.EmailThread;
import com.example.salesai.domain.ReplyDraft;
import com.example.salesai.domain.ReplyStrategy;
import com.example.salesai.domain.RiskAssessment;
import com.example.salesai.domain.RiskLevel;
import com.example.salesai.ports.ReplyDraftPort;
import com.example.salesai.ports.RiskPolicyPort;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class AdvisorWorkflowRiskGateTest {

    public static void main(String[] args) {
        new AdvisorWorkflowRiskGateTest().run();
    }

    void run() {
        testHighRiskShortCircuitsBeforeReplyDraftPort();
        testLowRiskCallsReplyDraftPort();
        System.out.println("AdvisorWorkflowRiskGateTest: 2 passed");
    }

    void testHighRiskShortCircuitsBeforeReplyDraftPort() {
        AtomicInteger replyCalls = new AtomicInteger(0);
        ReplyDraftPort spy = (c, t, s) -> {
            replyCalls.incrementAndGet();
            return List.of();
        };
        RiskPolicyPort highRisk = (c, t, i, e) ->
            new RiskAssessment(RiskLevel.HIGH, true,
                List.of("refund"), List.of(), "VIP refund + churn signal");

        AdvisorWorkflow w = new AdvisorWorkflow(
            new MockCustomerContextAdapter(),
            new MockEmailThreadAdapter(),
            highRisk,
            spy,
            new NoopCrmAdapter(),
            new ManualApprovalAdapter(),
            new ConsoleAuditLogAdapter());
        AdvisorResult r = w.run(new AdvisorRequest(
            "alice@acme.com", null));

        assert replyCalls.get() == 0
            : "ReplyDraftPort was called " + replyCalls.get() + " times — risk gate failed";
        assert r.blocked() : "result should be blocked";
    }

    void testLowRiskCallsReplyDraftPort() {
        AtomicInteger replyCalls = new AtomicInteger(0);
        ReplyDraftPort spy = (c, t, s) -> {
            replyCalls.incrementAndGet();
            return List.of(new ReplyDraft("formal", "s", "b"),
                           new ReplyDraft("warm", "s", "b"));
        };
        // Use the real rule-based risk for a non-VIP, non-blocked email.
        AdvisorWorkflow w = new AdvisorWorkflow(
            new MockCustomerContextAdapter(),
            new MockEmailThreadAdapter(),
            new RuleBasedRiskPolicyAdapter(),
            spy,
            new NoopCrmAdapter(),
            new ManualApprovalAdapter(),
            new ConsoleAuditLogAdapter());
        // Pick a customer email that will NOT trigger HIGH (engineer should
        // verify by reading MockCustomerContextAdapter — pick a Standard
        // tier customer with a routine inquiry).
        w.run(new AdvisorRequest("inquiry@example.com", null));
        // We don't assert exact call count (workflow may call it once);
        // we only assert it is allowed to be called.
        assert replyCalls.get() >= 1
            : "ReplyDraftPort should be called for low risk";
    }
}
```

(Engineer must verify the existing `MockCustomerContextAdapter` has both a high-risk-triggering customer email *and* a low-risk-triggering one. If not, extend the mock fixtures in this same task.)

- [ ] **Step 2: Run, FAIL** (the workflow currently calls `replyDraft.generate()` regardless of risk)

- [ ] **Step 3: Modify `AdvisorWorkflow`**

In `AdvisorWorkflow.java`, find the existing block (around the existing line 130):
```java
// 7. Generate drafts.
audit.log("GENERATE_DRAFTS", "tone=" + strategy.tone());
List<ReplyDraft> drafts = replyDraft.generate(customer, thread, strategy);
```

Replace with:
```java
// 7. Risk gate — the LLM never sees HIGH risk requests.
//    This is an architectural invariant: see AdvisorWorkflowRiskGateTest.
List<ReplyDraft> drafts;
if (risk.level() == RiskLevel.HIGH || risk.requiresManagerApproval()) {
    audit.log("DRAFTS_BLOCKED_BY_RISK_GATE",
        "level=" + risk.level()
        + " requiresManagerApproval=" + risk.requiresManagerApproval()
        + " reason=" + risk.reason()
        + " (LLM not invoked; data did not leave perimeter)");
    drafts = List.of(new ReplyDraft(
        "blocked",
        "[manager approval required]",
        "Drafts blocked. Reason: " + risk.reason()
            + ". Route to " + customer.accountManager()
            + " before any reply leaves the building."));
} else {
    audit.log("GENERATE_DRAFTS", "tone=" + strategy.tone());
    drafts = replyDraft.generate(customer, thread, strategy);
}
```

(Verify `ReplyDraft` constructor signature with `cat src/main/java/com/example/salesai/domain/ReplyDraft.java` — adjust the `new ReplyDraft(...)` call if the actual record takes different parameters.)

- [ ] **Step 4: Run, PASS**

Both invariant tests must pass. If not, the gate logic is wrong — fix the workflow, do not fix the test.

- [ ] **Step 5: Commit**

```sh
git add src/main/java/com/example/salesai/app/AdvisorWorkflow.java \
        src/test/java/com/example/salesai/app/AdvisorWorkflowRiskGateTest.java
git commit -m "workflow: add risk gate — LLM never invoked for HIGH risk"
```

---

### Task 4.9: ConsoleAuditLogAdapter formatter for `LlmCallAuditEntry`

**Files:**
- Modify: `src/main/java/com/example/salesai/adapters/ConsoleAuditLogAdapter.java`
- Create: `src/test/java/com/example/salesai/adapters/ConsoleAuditLogAdapterLlmTest.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/com/example/salesai/adapters/ConsoleAuditLogAdapterLlmTest.java`:
```java
package com.example.salesai.adapters;

import com.example.salesai.adapters.llm.LlmCallAuditEntry;

import java.time.Instant;

public final class ConsoleAuditLogAdapterLlmTest {
    public static void main(String[] args) {
        new ConsoleAuditLogAdapterLlmTest().run();
    }

    void run() {
        testFormatsLlmCallEntryIntoSingleLine();
        System.out.println("ConsoleAuditLogAdapterLlmTest: 1 passed");
    }

    void testFormatsLlmCallEntryIntoSingleLine() {
        ConsoleAuditLogAdapter a = new ConsoleAuditLogAdapter();
        a.log(new LlmCallAuditEntry(
            Instant.parse("2026-05-07T14:32:18Z"),
            "anthropic", "claude-3-5-sonnet-20241022", "deadbeef",
            2103, 487, 523, 128, 1840L, 0.00385,
            "sk-ant-...8a2f", "draft_reply", "req_a4f1"));
        String s = a.entries().get(0);
        assert s.contains("LLM_CALL");
        assert s.contains("anthropic");
        assert s.contains("claude-3-5-sonnet-20241022");
        assert s.contains("523");
        assert s.contains("128");
        assert s.contains("$0.0039") || s.contains("0.00385");
        assert s.contains("req_a4f1");
        assert !s.contains("sk-ant-api");  // raw key MUST NOT appear
    }
}
```

- [ ] **Step 2: Run, FAIL**

- [ ] **Step 3: Update the formatter**

In `ConsoleAuditLogAdapter.java`, find the `formatEntry` method added in Task 0.3 and replace it with:
```java
private static String formatEntry(com.example.salesai.audit.AuditEntry entry) {
    if (entry instanceof com.example.salesai.audit.TextAuditEntry t) {
        return t.event() + " " + t.detail();
    }
    if (entry instanceof com.example.salesai.adapters.llm.LlmCallAuditEntry l) {
        return String.format(
            "LLM_CALL provider=%s model=%s in_tok=%d out_tok=%d latency=%dms cost=$%.4f key=%s req=%s step=%s",
            l.provider(), l.model(),
            l.inputTokens(), l.outputTokens(),
            l.latencyMs(), l.estimatedCostUsd(),
            l.apiKeyFingerprint(), l.requestId(), l.workflowStep());
    }
    return entry.toString();
}
```

- [ ] **Step 4: Run, PASS**

- [ ] **Step 5: Commit**

```sh
git add src/main/java/com/example/salesai/adapters/ConsoleAuditLogAdapter.java \
        src/test/java/com/example/salesai/adapters/ConsoleAuditLogAdapterLlmTest.java
git commit -m "audit: format LlmCallAuditEntry as structured single line"
```

---

### Task 4.10: Wire `--llm` flags into `SalesAiCli`

**Files:**
- Modify: `src/main/java/com/example/salesai/SalesAiCli.java`

- [ ] **Step 1: Add flag parsing**

Add to the existing arg-parsing block:
```java
String llmProvider = "template";
String llmModel = null;
String llmEndpoint = null;
for (int i = 0; i < args.length; i++) {
    if ("--llm".equals(args[i]) && i + 1 < args.length) llmProvider = args[++i];
    else if ("--llm-model".equals(args[i]) && i + 1 < args.length) llmModel = args[++i];
    else if ("--llm-endpoint".equals(args[i]) && i + 1 < args.length) llmEndpoint = args[++i];
}
```

- [ ] **Step 2: Add the LLM client factory**

Before workflow construction:
```java
com.example.salesai.ports.ReplyDraftPort replyPort;
String llmKeyForAudit = null;
switch (llmProvider) {
    case "template" -> {
        replyPort = new com.example.salesai.adapters.TemplateReplyDraftAdapter();
    }
    case "anthropic" -> {
        String key = System.getenv("ANTHROPIC_API_KEY");
        if (key == null || key.isBlank()) {
            System.err.println("--llm anthropic requires ANTHROPIC_API_KEY env var");
            System.exit(2); return;
        }
        var llm = new com.example.salesai.adapters.llm.AnthropicLlmClient(key);
        replyPort = new com.example.salesai.adapters.llm.LlmReplyDraftAdapter(
            llm, /* audit-port-instance */ auditPort, key, "draft_reply");
        llmKeyForAudit = key;
    }
    case "openai", "openai-compatible" -> {
        String key = System.getenv("OPENAI_API_KEY");
        if ("openai".equals(llmProvider) && (key == null || key.isBlank())) {
            System.err.println("--llm openai requires OPENAI_API_KEY env var");
            System.exit(2); return;
        }
        if (key == null) key = "";
        var llm = (llmEndpoint == null)
            ? new com.example.salesai.adapters.llm.OpenAiLlmClient(key)
            : new com.example.salesai.adapters.llm.OpenAiLlmClient(key, llmEndpoint);
        replyPort = new com.example.salesai.adapters.llm.LlmReplyDraftAdapter(
            llm, auditPort, key.isEmpty() ? null : key, "draft_reply");
        llmKeyForAudit = key;
    }
    case "gemini" -> {
        String key = System.getenv("GEMINI_API_KEY");
        if (key == null || key.isBlank()) {
            System.err.println("--llm gemini requires GEMINI_API_KEY env var");
            System.exit(2); return;
        }
        var llm = new com.example.salesai.adapters.llm.GeminiLlmClient(key);
        replyPort = new com.example.salesai.adapters.llm.LlmReplyDraftAdapter(
            llm, auditPort, key, "draft_reply");
        llmKeyForAudit = key;
    }
    default -> {
        System.err.println("Unknown --llm provider: " + llmProvider);
        System.exit(2); return;
    }
}
```

(Engineer should ensure the `auditPort` variable is named correctly — `cat src/main/java/com/example/salesai/SalesAiCli.java` and find the existing `ConsoleAuditLogAdapter` instance variable name to match.)

- [ ] **Step 3: Replace `TemplateReplyDraftAdapter` reference in workflow construction**

Find the line constructing `AdvisorWorkflow` and replace `new TemplateReplyDraftAdapter()` with `replyPort`.

- [ ] **Step 4: Smoke test (template path still works)**

```sh
java -cp out com.example.salesai.SalesAiCli
```
Expected: works as before — template mode unchanged.

- [ ] **Step 5: Commit**

```sh
git add src/main/java/com/example/salesai/SalesAiCli.java
git commit -m "cli: wire --llm/--llm-model/--llm-endpoint flag set"
```

---

### Task 4.11: End-to-end smoke (template + Anthropic + risk-gate trace)

**Files:**
- Create: `tests/smoke-template.sh` (and `.ps1`)
- Create: `tests/smoke-anthropic.sh` (and `.ps1`)

These are operator-runnable smoke scripts that document working command lines.

- [ ] **Step 1: Create `tests/smoke-template.sh`**

```sh
#!/usr/bin/env sh
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "=== mock + template (must work, zero env) ==="
java -cp out com.example.salesai.SalesAiCli
```

- [ ] **Step 2: Create `tests/smoke-anthropic.sh`**

```sh
#!/usr/bin/env sh
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [ -z "$ANTHROPIC_API_KEY" ]; then
  echo "SKIP: ANTHROPIC_API_KEY not set"
  exit 0
fi

echo "=== mock + Anthropic (low risk → real LLM call) ==="
java -cp out com.example.salesai.SalesAiCli --llm anthropic

echo
echo "=== mock + Anthropic (high risk → blocked, NO LLM call) ==="
# Pick a customer email that the mock+rule-based risk classifies as HIGH.
java -cp out com.example.salesai.SalesAiCli --llm anthropic --customer vip-refund@example.com

echo "Verify the second run's audit log shows DRAFTS_BLOCKED_BY_RISK_GATE"
echo "and contains NO LLM_CALL line."
```

- [ ] **Step 3: Run both smoke scripts manually**

```sh
chmod +x tests/smoke-*.sh
./tests/smoke-template.sh    # must succeed
export ANTHROPIC_API_KEY=...
./tests/smoke-anthropic.sh   # must show real LLM_CALL line for low-risk
                              # and DRAFTS_BLOCKED_BY_RISK_GATE for high-risk
```

- [ ] **Step 4: Commit**

```sh
git add tests/smoke-*.sh tests/smoke-*.ps1
git commit -m "test: add smoke scripts (template + Anthropic + risk-gate trace)"
```

---

### Task 4.12: Phase 4 documentation

**Files:**
- Create: `docs/integrations/anthropic.md`
- Create: `docs/integrations/openai.md`
- Create: `docs/integrations/gemini.md`
- Create: `docs/integrations/local-llm.md`

- [ ] **Step 1: Write `anthropic.md`**

Cover: how to get an API key (console.anthropic.com), recommended model (claude-3-5-sonnet-20241022 — explain why: structured JSON + safety adherence), example command (`export ANTHROPIC_API_KEY=...; java SalesAiCli --llm anthropic`), cost expectations (Sonnet pricing per 1M tokens, typical call ~$0.004), what gets logged in the audit entry.

- [ ] **Step 2: Write `openai.md`**

Same structure, model `gpt-4o-2024-08-06`, pricing.

- [ ] **Step 3: Write `gemini.md`**

Same, model `gemini-1.5-pro-002`, pricing, AI Studio key acquisition.

- [ ] **Step 4: Write `local-llm.md`**

This is the most important doc — direct quote from the manifesto belongs here:

> 對銀行 / 保險 / 製造 / ERP 等監管產業:Java 21、零依賴、可直接內嵌進你的後端——不需要把客戶資料送出去給第三方雲。

Cover:
- Why local LLM (regulated industries, data residency)
- Three popular options: Ollama, vLLM, llama.cpp — all expose OpenAI-compatible API
- Concrete commands for each (Ollama: `ollama pull llama3.1:70b; ollama serve`)
- Engine command: `java SalesAiCli --llm openai-compatible --llm-endpoint http://localhost:11434/v1 --llm-model llama3.1:70b`
- Honest performance comparison: latency typically 5-30s vs cloud's 1-3s, draft quality varies by model
- Hardware requirements (70B needs ~40GB VRAM)

- [ ] **Step 5: Commit**

```sh
git add docs/integrations/anthropic.md docs/integrations/openai.md \
        docs/integrations/gemini.md docs/integrations/local-llm.md
git commit -m "docs: Phase 4 — LLM provider integration guides + local-LLM playbook"
```

---

## Phase Final — Polish (2 tasks)

---

### Task F.1: Update all 4 README languages with new flags + perimeter caveat

**Files:**
- Modify: `README.md`, `README.zh-TW.md`, `README.ja.md`, `README.ko.md`

- [ ] **Step 1: For each README, update the "Run it" section**

Add a "Real integrations" subsection:
- One bullet for `--email gmail|outlook` with link to `docs/integrations/{gmail,outlook}.md`
- One bullet for `--llm anthropic|openai|gemini|openai-compatible` with link to provider docs
- A clear paragraph titled **"Honest deployment notes"**:
  - Java engine remains zero-dep at runtime (`java.net.http` is built into JDK 11+)
  - **Phase 2 deployment requires Node.js** (Gmail/Outlook MCP servers are npm packages)
  - **When using cloud LLM, customer data is sent to that provider** — for regulated industries use `--llm openai-compatible` with a local model (see `docs/integrations/local-llm.md`)

- [ ] **Step 2: For each README, update the architecture diagram**

The mermaid flowchart at line 84 should add a new edge: `tools ==> realLlm["LLM clients (Phase 4)<br/>Anthropic / OpenAI / Gemini / local"]`. Move the existing `tools -.->|Phase 4|` line to a stronger arrow style now that it's implemented.

- [ ] **Step 3: For each README, update the Phase status in the roadmap**

Mark Phase 2 and Phase 4 as complete (✅). Phase 3 remains future.

- [ ] **Step 4: Commit**

```sh
git add README.md README.zh-TW.md README.ja.md README.ko.md
git commit -m "docs(README): document Phase 2/4 flags + data-perimeter caveat (4 languages)"
```

---

### Task F.2: Acceptance criteria validation

This task runs the spec's Section 12 acceptance criteria one by one and produces a pass/fail report.

**Files:**
- Create: `tests/acceptance.sh`

- [ ] **Step 1: Write the acceptance script**

`tests/acceptance.sh`:
```sh
#!/usr/bin/env sh
# Runs every acceptance criterion from the design spec.
# Requires: ANTHROPIC_API_KEY, a seeded SQLite DB, an Ollama or compatible
# server running locally on port 11434.
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PASSED=0; FAILED=0
section() { echo; echo "==> $1"; }
ok()      { PASSED=$((PASSED+1)); echo "  PASS"; }
fail()    { FAILED=$((FAILED+1)); echo "  FAIL: $1"; }

section "AC1: Full mode end-to-end (Gmail + Anthropic + SQLite, low-risk email)"
java -cp out com.example.salesai.SalesAiCli \
  --email gmail --llm anthropic \
  --customer-source jdbc --db jdbc:sqlite:./test-customers.db \
  --customer routine@example.com \
  > /tmp/ac1.out 2>&1
grep -q "LLM_CALL" /tmp/ac1.out && ok || fail "no LLM_CALL recorded"

section "AC2: High-risk email — drafts blocked, NO LLM call"
java -cp out com.example.salesai.SalesAiCli \
  --email gmail --llm anthropic \
  --customer-source jdbc --db jdbc:sqlite:./test-customers.db \
  --customer vip-refund@example.com \
  > /tmp/ac2.out 2>&1
grep -q "DRAFTS_BLOCKED_BY_RISK_GATE" /tmp/ac2.out && \
  ! grep -q "LLM_CALL" /tmp/ac2.out && ok || fail "either no block, or LLM was called"

section "AC3: Local LLM via openai-compatible endpoint"
java -cp out com.example.salesai.SalesAiCli \
  --llm openai-compatible \
  --llm-endpoint http://localhost:11434/v1 \
  --llm-model llama3.1:8b \
  > /tmp/ac3.out 2>&1
grep -q "LLM_CALL" /tmp/ac3.out && ok || fail "no LLM_CALL recorded"

section "AC4: Audit entry has all 13 fields"
grep -E "provider=.* model=.* in_tok=.* out_tok=.* latency=.*ms cost=\\\$.* key=.* req=.* step=" /tmp/ac1.out > /dev/null && ok || fail "audit fields incomplete"

section "AC5: Mock mode still works (60-second demo)"
java -cp out com.example.salesai.SalesAiCli > /tmp/ac5.out 2>&1 && ok || fail "mock mode broken"

section "AC6: All 4 READMEs mention --llm and the perimeter caveat"
for f in README.md README.zh-TW.md README.ja.md README.ko.md; do
  grep -q "\-\-llm" "$f" || { fail "$f missing --llm"; continue; }
  grep -q "openai-compatible" "$f" || { fail "$f missing perimeter caveat"; continue; }
done
[ "$FAILED" -eq 0 ] && ok

section "AC7: Unit tests pass"
./tests/run-tests.sh > /tmp/ac7.out 2>&1 && ok || fail "unit tests broken"

echo
echo "Total: $((PASSED+FAILED)) — Passed: $PASSED — Failed: $FAILED"
[ "$FAILED" -eq 0 ]
```

- [ ] **Step 2: Run acceptance — fix anything that fails**

```sh
chmod +x tests/acceptance.sh
./tests/acceptance.sh
```

- [ ] **Step 3: Commit**

```sh
git add tests/acceptance.sh
git commit -m "test: add acceptance.sh validating all 7 spec AC criteria"
```

---

## Done

After Task F.2 passes, the spec's acceptance criteria are met:
- Real Gmail / Outlook via MCP (Phase 2)
- Real Anthropic / OpenAI / Gemini / local LLM via HTTP (Phase 4)
- Risk gate enforced at workflow level — LLM has no path to high-risk customer data
- Structured audit log with provider / model / tokens / cost / latency
- All 4 README languages updated with honest deployment caveats
- Mock mode unchanged (60-second demo still works)

Total commits: ~27. Total new LOC: ~1500-1800. Modified LOC: ~150.
