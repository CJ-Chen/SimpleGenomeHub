package simplegenomehub.runtime;

import biocjava.bioDoer.BLAST.ToolProcessRunner;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Separate stdout data from bounded stderr diagnostics, retaining ownership until both readers exit. */
final class HubStreamingProcessRunner {
    private HubStreamingProcessRunner() { }

    static HubProcessRunner.StreamingResult run(HubSession session, ProcessBuilder builder,
            HubProcessRunner.LineConsumer consumer, long timeout, TimeUnit unit)
            throws IOException, InterruptedException {
        java.util.Objects.requireNonNull(consumer, "stdout consumer");
        if (timeout <= 0) throw new IllegalArgumentException("Timeout must be positive");
        checkCancelled(session);
        builder.redirectErrorStream(false);
        builder.redirectOutput(ProcessBuilder.Redirect.PIPE);
        builder.redirectError(ProcessBuilder.Redirect.PIPE);
        Process process = builder.start();
        AtomicReference<Throwable> readerFailure = new AtomicReference<>();
        StringBuilder diagnostics = new StringBuilder();
        Thread stdout = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    checkCancelled(session);
                    consumer.accept(line);
                }
            } catch (Throwable failure) { readerFailure.compareAndSet(null, failure); }
        }, "SGH-tool-stdout");
        Thread stderr = new Thread(() -> {
            try (InputStreamReader reader = new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)) {
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) >= 0) synchronized (diagnostics) {
                    diagnostics.append(buffer, 0, count);
                    if (diagnostics.length() > 65536) diagnostics.delete(0, diagnostics.length() - 65536);
                }
            } catch (IOException ignored) { /* Closing a cancelled process closes its pipes. */ }
        }, "SGH-tool-stderr");
        stdout.setDaemon(true);
        stderr.setDaemon(true);
        try {
            if (session != null) session.registerProcess(process);
            process.getOutputStream().close();
            stdout.start();
            stderr.start();
            long deadline = System.nanoTime() + unit.toNanos(timeout);
            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                checkCancelled(session);
                checkReaderFailure(readerFailure.get());
                checkDeadline(deadline, builder);
            }
            while (stdout.isAlive() || stderr.isAlive()) {
                checkCancelled(session);
                checkReaderFailure(readerFailure.get());
                checkDeadline(deadline, builder);
                stdout.join(50);
                stderr.join(50);
            }
            checkCancelled(session);
            checkReaderFailure(readerFailure.get());
            synchronized (diagnostics) {
                return new HubProcessRunner.StreamingResult(process.exitValue(), diagnostics.toString());
            }
        } catch (InterruptedException cancelled) {
            Thread.currentThread().interrupt();
            throw cancelled;
        } finally {
            if (process.isAlive()) ToolProcessRunner.terminateAndWait(process);
            try { process.getInputStream().close(); } catch (IOException ignored) { }
            try { process.getErrorStream().close(); } catch (IOException ignored) { }
            // A parser failure or cancellation cannot leave a reader writing to
            // a result after the caller's task lease has been released.
            stdout.interrupt();
            stderr.interrupt();
            joinUninterruptibly(stdout);
            joinUninterruptibly(stderr);
        }
    }

    private static void checkDeadline(long deadline, ProcessBuilder builder) throws IOException {
        if (System.nanoTime() - deadline >= 0)
            throw new IOException("Tool execution timed out: " + builder.command().get(0));
    }

    private static void checkCancelled(HubSession session) throws InterruptedException {
        if (Thread.currentThread().isInterrupted() || (session != null && !session.isActive()))
            throw new InterruptedException("Hub tool execution cancelled");
    }

    private static void checkReaderFailure(Throwable failure) throws IOException, InterruptedException {
        if (failure == null) return;
        if (failure instanceof InterruptedException) throw (InterruptedException) failure;
        if (failure instanceof IOException) throw (IOException) failure;
        throw new IOException("Failed to consume tool output", failure);
    }

    private static void joinUninterruptibly(Thread thread) {
        boolean interrupted = Thread.interrupted();
        while (thread.isAlive()) {
            try { thread.join(); }
            catch (InterruptedException ignored) { interrupted = true; }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }
}
