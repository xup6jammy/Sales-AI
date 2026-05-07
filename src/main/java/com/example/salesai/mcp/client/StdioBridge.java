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
