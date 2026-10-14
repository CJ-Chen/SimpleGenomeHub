package simplegenomehub.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;
import java.util.logging.Level;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;
import simplegenomehub.runtime.HubSession;

/** Captures console output in a small set of rolling log files. */
public final class RuntimeLogManager {

    private static final long MAX_LOG_BYTES = 5L * 1024L * 1024L;
    private static final int MAX_LOG_FILES = 5;
    private static final String LOG_FILE_NAME = "SimpleGenomeHub.log";
    private static final DateTimeFormatter TIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static volatile boolean initialized;
    private static File logDirectory;
    private static File currentLogFile;
    private static RotatingLogOutputStream logOutputStream;
    private static final Map<HubSession, SessionLog> SESSION_LOGS = new WeakHashMap<>();

    private static final class SessionLog {
        final File directory;
        final File file;
        final FileHandler handler;
        SessionLog(File directory, File file, FileHandler handler) {
            this.directory = directory; this.file = file; this.handler = handler;
        }
    }

    private RuntimeLogManager() {
    }

    /** Scoped JUL logging; embedded mode never replaces the host's streams or handlers. */
    public static synchronized void attach(HubSession session) {
        if (session == null || SESSION_LOGS.containsKey(session) || session.getState() != HubSession.State.ACTIVE) return;
        FileHandler created = null;
        Logger logger = Logger.getLogger("simplegenomehub");
        try {
            File directory = new File(resolveLogDirectory(), "session-" + UUID.randomUUID());
            Files.createDirectories(directory.toPath());
            File file = new File(directory, LOG_FILE_NAME);
            FileHandler handler = new FileHandler(file.getAbsolutePath(), (int) MAX_LOG_BYTES, MAX_LOG_FILES, true);
            created = handler;
            handler.setEncoding(StandardCharsets.UTF_8.name());
            handler.setFormatter(new SimpleFormatter());
            handler.setLevel(Level.ALL);
            java.lang.ref.WeakReference<HubSession> owner = new java.lang.ref.WeakReference<>(session);
            handler.setFilter(record -> {
                HubSession bound = owner.get();
                HubSession current = HubSession.current();
                return bound != null && bound.getState() != HubSession.State.CLOSED
                    && (current == null || current == bound);
            });
            logger.addHandler(handler);
            // FileHandler appends a generation suffix when count > 1.
            File active = new File(file.getAbsolutePath() + ".0");
            SESSION_LOGS.put(session, new SessionLog(directory, active, handler));
            session.addCleanup(() -> {
                synchronized (RuntimeLogManager.class) {
                    logger.removeHandler(handler); handler.close(); SESSION_LOGS.remove(session);
                }
            });
            logger.info("SimpleGenomeHub session opened: " + session.getDataRoot());
        } catch (IOException | SecurityException | IllegalStateException failure) {
            if (created != null) { logger.removeHandler(created); created.close(); }
            SESSION_LOGS.remove(session);
            Logger.getLogger(RuntimeLogManager.class.getName()).log(Level.WARNING, "Cannot initialize Hub session log", failure);
        }
    }

    public static synchronized File getCurrentLogFile(HubSession session) {
        attach(session);
        SessionLog log = SESSION_LOGS.get(session);
        return log == null ? null : log.file;
    }

    public static synchronized File getLogDirectory(HubSession session) {
        attach(session);
        SessionLog log = SESSION_LOGS.get(session);
        return log == null ? null : log.directory;
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        try {
            logDirectory = resolveLogDirectory();
            Files.createDirectories(logDirectory.toPath());
            currentLogFile = new File(logDirectory, LOG_FILE_NAME);
            rotatePreviousSession(currentLogFile);

            logOutputStream = new RotatingLogOutputStream(currentLogFile);
            System.setOut(createTeePrintStream(originalOut, logOutputStream));
            System.setErr(createTeePrintStream(originalErr, logOutputStream));
            configureJavaUtilLoggingEncoding();
            initialized = true;

            System.out.println();
            System.out.println("============================================================");
            System.out.println("SimpleGenomeHub started at " + LocalDateTime.now().format(TIME_FORMAT));
            System.out.println("Runtime log: " + currentLogFile.getAbsolutePath());
            System.out.println("============================================================");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                synchronized (RuntimeLogManager.class) {
                    if (logOutputStream != null) {
                        try {
                            logOutputStream.flush();
                        } catch (IOException ignored) {
                            // The process is already shutting down.
                        }
                    }
                }
            }, "SimpleGenomeHub-log-shutdown"));
        } catch (Exception ex) {
            initialized = true;
            originalErr.println("Failed to initialize the SimpleGenomeHub runtime log: " + ex.getMessage());
            ex.printStackTrace(originalErr);
        }
    }

    public static File getLogDirectory() {
        if (!initialized) return getLogDirectory(HubSession.current());
        return logDirectory;
    }

    public static File getCurrentLogFile() {
        if (!initialized) return getCurrentLogFile(HubSession.current());
        return currentLogFile;
    }

    public static void logUncaughtException(Thread thread, Throwable error) {
        String threadName = thread == null ? "unknown" : thread.getName();
        System.err.println();
        System.err.println("Uncaught exception on thread " + threadName + " at "
            + LocalDateTime.now().format(TIME_FORMAT));
        if (error != null) {
            error.printStackTrace(System.err);
        }
        System.err.flush();
    }

    private static PrintStream createTeePrintStream(PrintStream console, OutputStream logStream)
        throws IOException {
        return new PrintStream(
            new TeeOutputStream(console, logStream), true, StandardCharsets.UTF_8.name());
    }

    private static void configureJavaUtilLoggingEncoding() throws IOException {
        Logger rootLogger = Logger.getLogger("");
        for (Handler handler : rootLogger.getHandlers()) {
            handler.setEncoding(StandardCharsets.UTF_8.name());
        }
    }

    private static File resolveLogDirectory() {
        String override = System.getProperty("simplegenomehub.logs.dir");
        if (override != null && !override.trim().isEmpty()) return new File(override);
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String userHome = System.getProperty("user.home", ".");

        if (osName.contains("win")) {
            return new File(userHome,
                "AppData" + File.separator + "Local" + File.separator
                    + "SimpleGenomeHub" + File.separator + "logs");
        }

        if (osName.contains("mac") || osName.contains("darwin")) {
            return new File(userHome,
                "Library" + File.separator + "Logs" + File.separator + "SimpleGenomeHub");
        }

        return new File(userHome,
            ".local" + File.separator + "state" + File.separator
                + "SimpleGenomeHub" + File.separator + "logs");
    }

    private static void rotatePreviousSession(File activeFile) throws IOException {
        if (!activeFile.isFile() || activeFile.length() == 0L) {
            return;
        }
        rotateFiles(activeFile);
    }

    private static void rotateFiles(File activeFile) throws IOException {
        File oldest = backupFile(activeFile, MAX_LOG_FILES - 1);
        Files.deleteIfExists(oldest.toPath());

        for (int index = MAX_LOG_FILES - 2; index >= 1; index--) {
            File source = backupFile(activeFile, index);
            if (source.isFile()) {
                Files.move(source.toPath(), backupFile(activeFile, index + 1).toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            }
        }

        if (activeFile.isFile()) {
            Files.move(activeFile.toPath(), backupFile(activeFile, 1).toPath(),
                StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static File backupFile(File activeFile, int index) {
        return new File(activeFile.getParentFile(), activeFile.getName() + "." + index);
    }

    private static final class RotatingLogOutputStream extends OutputStream {

        private final File activeFile;
        private FileOutputStream output;
        private long bytesWritten;

        private RotatingLogOutputStream(File activeFile) throws IOException {
            this.activeFile = activeFile;
            openOutput();
        }

        @Override
        public synchronized void write(int value) throws IOException {
            ensureCapacity(1);
            output.write(value);
            bytesWritten++;
        }

        @Override
        public synchronized void write(byte[] data, int offset, int length) throws IOException {
            if (data == null) {
                throw new NullPointerException("data");
            }
            int remaining = length;
            int currentOffset = offset;
            while (remaining > 0) {
                if (bytesWritten >= MAX_LOG_BYTES) {
                    rotateOutput();
                }
                int writable = (int) Math.min(remaining, MAX_LOG_BYTES - bytesWritten);
                output.write(data, currentOffset, writable);
                bytesWritten += writable;
                currentOffset += writable;
                remaining -= writable;
            }
        }

        @Override
        public synchronized void flush() throws IOException {
            output.flush();
        }

        private void ensureCapacity(int incomingBytes) throws IOException {
            if (bytesWritten > 0 && bytesWritten + incomingBytes > MAX_LOG_BYTES) {
                rotateOutput();
            }
        }

        private void rotateOutput() throws IOException {
            output.flush();
            output.close();
            rotateFiles(activeFile);
            openOutput();
        }

        private void openOutput() throws IOException {
            output = new FileOutputStream(activeFile, true);
            bytesWritten = activeFile.length();
        }
    }

    private static final class TeeOutputStream extends OutputStream {

        private final OutputStream console;
        private final OutputStream log;

        private TeeOutputStream(OutputStream console, OutputStream log) {
            this.console = console;
            this.log = log;
        }

        @Override
        public synchronized void write(int value) throws IOException {
            if (console != null) {
                console.write(value);
            }
            log.write(value);
        }

        @Override
        public synchronized void write(byte[] data, int offset, int length) throws IOException {
            if (console != null) {
                console.write(data, offset, length);
            }
            log.write(data, offset, length);
        }

        @Override
        public synchronized void flush() throws IOException {
            if (console != null) {
                console.flush();
            }
            log.flush();
        }
    }
}
