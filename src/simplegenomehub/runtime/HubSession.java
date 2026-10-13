package simplegenomehub.runtime;

import java.awt.Component;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComponent;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import javax.swing.AbstractButton;
import javax.swing.JMenu;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

/**
 * Lifetime of one Hub page. A cancelled Future is not evidence that a writer has
 * stopped: task leases are released by the executing thread's finally block.
 */
public final class HubSession {
    public enum State { ACTIVE, CLOSING, CLOSED }
    private static final ThreadLocal<HubSession> EXECUTING = new ThreadLocal<>();
    private static final Map<Component, HubSession> OWNERS = new WeakHashMap<>();
    private static final Set<HubSession> SESSIONS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final AtomicInteger THREAD_IDS = new AtomicInteger();
    private final HubHostContext host;
    private final Set<Task> tasks = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Process> processes = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Window> windows = Collections.newSetFromMap(new WeakHashMap<>());
    private final List<Runnable> cleanup = new ArrayList<>();
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "SGH-task-" + THREAD_IDS.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });
    private State state = State.ACTIVE;
    private long generation;
    private HubDataRootLock rootLock;
    private CompletableFuture<Boolean> closeFuture;

    public HubSession(HubHostContext host, File root) throws IOException {
        this.host = java.util.Objects.requireNonNull(host, "host");
        if (root != null) rootLock = HubDataRootLock.acquire(root);
        synchronized (SESSIONS) { SESSIONS.add(this); }
    }

    public HubHostContext getHostContext() { return host; }
    public synchronized State getState() { return state; }
    public synchronized boolean isActive() { return state == State.ACTIVE; }
    public synchronized long getGeneration() { return generation; }
    public synchronized boolean accepts(long value) { return state == State.ACTIVE && generation == value; }
    public synchronized File getDataRoot() { return rootLock == null ? null : rootLock.getRoot().toFile(); }
    public synchronized int getRunningTaskCount() { return tasks.size(); }
    public synchronized boolean hasRunningTasks() { return !tasks.isEmpty() || !processes.isEmpty(); }

    public static HubSession current() {
        HubSession executing = EXECUTING.get();
        if (executing != null) return executing;
        if (SwingUtilities.isEventDispatchThread()) {
            java.awt.AWTEvent event = java.awt.EventQueue.getCurrentEvent();
            if (event != null && event.getSource() instanceof Component) {
                HubSession eventOwner = findOwner((Component) event.getSource());
                if (eventOwner != null) return eventOwner;
            }
        }
        synchronized (SESSIONS) {
            HubSession only = null;
            for (HubSession session : SESSIONS) {
                if (session.getState() == State.CLOSED) continue;
                if (only != null) return null;
                only = session;
            }
            return only;
        }
    }

    public static void attach(Component component, HubSession session) {
        if (component == null || session == null) return;
        HubSession previous;
        synchronized (OWNERS) { previous = OWNERS.put(component, session); }
        if (previous != null && previous != session && component instanceof Window)
            synchronized (previous) { previous.windows.remove(component); }
        if (component instanceof Window) session.registerWindow((Window) component);
    }

    public static HubSession forComponent(Component component) {
        HubSession owner = findOwner(component);
        return owner == null ? current() : owner;
    }

    private static HubSession findOwner(Component component) {
        Component cursor = component;
        while (cursor != null) {
            synchronized (OWNERS) {
                HubSession session = OWNERS.get(cursor);
                if (session != null) return session;
            }
            if (cursor instanceof Window) cursor = ((Window) cursor).getOwner();
            else cursor = cursor.getParent();
        }
        return null;
    }

    /** Explicit component/window ownership, without guessing the sole session. */
    public static HubSession getExplicitOwner(Component component) { return findOwner(component); }

    /** Resolve model-backed dialogs before they start work in their constructor. */
    public static HubSession forDataPath(File file) {
        if (file == null) return current();
        final java.nio.file.Path path;
        try { path = file.getCanonicalFile().toPath(); }
        catch (IOException failure) { throw new IllegalArgumentException("Cannot resolve Hub data path", failure); }
        synchronized (SESSIONS) {
            HubSession match = null;
            int depth = -1;
            for (HubSession session : SESSIONS) {
                File root = session.getDataRoot();
                if (root != null && path.startsWith(root.toPath()) && root.toPath().getNameCount() > depth) {
                    match = session;
                    depth = root.toPath().getNameCount();
                }
            }
            if (match != null) return match;
            if (!SESSIONS.isEmpty()) throw new IllegalStateException("This data path does not belong to a live Hub session: " + path);
        }
        return null;
    }

    public synchronized void registerWindow(Window window) { windows.add(window); }
    public synchronized List<Window> getOwnedWindows() { return new ArrayList<>(windows); }

    /**
     * Legacy drawing controls write synchronously after modal choosers return.
     * Count the whole callback, including its nested event loop, as a real task.
     */
    public void guardActionsIn(Component component) {
        for (Component parent = component; parent != null; parent = parent.getParent())
            if (parent instanceof JFileChooser || parent instanceof JOptionPane) return;
        if (component instanceof AbstractButton) {
            AbstractButton button = (AbstractButton) component;
            if (button.getClientProperty("sgh.actionSession") != this) {
                java.awt.event.ActionListener[] listeners = button.getActionListeners();
                if (listeners.length != 0) {
                    final long expectedGeneration = getGeneration();
                    for (java.awt.event.ActionListener listener : listeners) button.removeActionListener(listener);
                    button.addActionListener(event -> {
                        final Task action;
                        synchronized (HubSession.this) {
                            if (!accepts(expectedGeneration)) return;
                            action = beginTask("Drawing control: " + button.getText(), null);
                        }
                        try (Task running = action) {
                            for (java.awt.event.ActionListener listener : listeners) listener.actionPerformed(event);
                        }
                    });
                    button.putClientProperty("sgh.actionSession", this);
                }
            }
        }
        if (component instanceof JMenu) guardActionsIn(((JMenu) component).getPopupMenu());
        if (component instanceof java.awt.Container)
            for (Component child : ((java.awt.Container) component).getComponents()) guardActionsIn(child);
    }
    public synchronized void addCleanup(Runnable action) {
        if (state != State.ACTIVE) throw new IllegalStateException("Hub is closing");
        cleanup.add(action);
    }

    public synchronized Task beginTask(String description, Runnable cancellation) {
        if (state != State.ACTIVE) throw new RejectedExecutionException("Hub is closing");
        Task task = new Task(description, cancellation);
        tasks.add(task);
        return task;
    }

    public final class Task implements AutoCloseable {
        private final String description;
        private final Runnable cancellation;
        private boolean finished;
        private Task(String description, Runnable cancellation) {
            this.description = description;
            this.cancellation = cancellation;
        }
        public String getDescription() { return description; }
        @Override public void close() {
            synchronized (HubSession.this) {
                if (finished) return;
                finished = true;
                tasks.remove(this);
                HubSession.this.notifyAll();
            }
        }
    }

    /** Execute only workers with an explicit, exception-safe completion contract. */
    public static void execute(Component owner, HubWorker<?, ?> worker) {
        HubSession session = forComponent(owner);
        if (session == null) {
            synchronized (SESSIONS) {
                if (!SESSIONS.isEmpty()) throw new IllegalStateException("Cannot identify this task's Hub session");
            }
        }
        worker.bind(session);
        Runnable execution = () -> {
            try { worker.run(); }
            finally { worker.executionReturned(); }
        };
        try {
            if (session == null) {
                Thread standalone = new Thread(execution, "SGH-task-" + THREAD_IDS.incrementAndGet());
                standalone.setDaemon(true);
                standalone.start();
            } else {
                session.executor.execute(execution);
            }
        } catch (RuntimeException | Error failure) {
            worker.submissionFailed(failure);
            throw failure;
        }
    }

    /** Carry the originating session through both the producer and its EDT callbacks. */
    static void runInContext(HubSession session, Runnable action) {
        HubSession previous = EXECUTING.get();
        if (session == null) EXECUTING.remove(); else EXECUTING.set(session);
        try { action.run(); }
        finally {
            if (previous == null) EXECUTING.remove(); else EXECUTING.set(previous);
        }
    }

    /** The process remains registered until it really exits, even if the caller is cancelled. */
    public AutoCloseable registerProcess(Process process) {
        synchronized (this) {
            if (state != State.ACTIVE) {
                destroyProcess(process);
                throw new RejectedExecutionException("Hub is closing");
            }
            processes.add(process);
        }
        process.onExit().whenComplete((exited, failure) -> {
            synchronized (HubSession.this) { processes.remove(process); HubSession.this.notifyAll(); }
        });
        return () -> {
            if (!process.isAlive()) synchronized (HubSession.this) {
                processes.remove(process); HubSession.this.notifyAll();
            }
        };
    }

    public interface RootChange {
        Object prepare(File newRoot) throws Exception;
        void commit(File newRoot, Object prepared) throws Exception;
    }

    /** Keep the old lease and model until candidate loading and config persistence succeed. */
    public synchronized void changeDataRoot(File root, RootChange change) throws Exception {
        if (state != State.ACTIVE || hasRunningTasks())
            throw new IllegalStateException("Wait for Hub tasks to finish before changing the data directory");
        File canonical = root.getCanonicalFile();
        if (getDataRoot() != null && getDataRoot().equals(canonical)) return;
        HubDataRootLock candidate = HubDataRootLock.acquire(canonical);
        try {
            Object prepared = change.prepare(canonical);
            change.commit(canonical, prepared);
            HubDataRootLock previous = rootLock;
            rootLock = candidate;
            candidate = null;
            generation++;
            if (previous != null) previous.close();
        } finally { if (candidate != null) candidate.close(); }
    }

    /** Never blocks Swing's EDT. Its result becomes true only after all actual writers exit. */
    public CompletionStage<Boolean> closeAsync() {
        final List<Task> cancel;
        final List<Process> stop;
        synchronized (this) {
            if (closeFuture != null) return closeFuture;
            state = State.CLOSING;
            generation++;
            closeFuture = new CompletableFuture<>();
            cancel = new ArrayList<>(tasks);
            stop = new ArrayList<>(processes);
        }
        Thread closer = new Thread(() -> {
            try {
                for (Task task : cancel) if (task.cancellation != null) {
                    try { task.cancellation.run(); }
                    catch (Throwable failure) { logCloseFailure("Cancel " + task.description, failure); }
                }
                for (Process process : stop) {
                    List<ProcessHandle> owned = new ArrayList<>();
                    owned.add(process.toHandle());
                    process.descendants().forEach(owned::add);
                    try { destroyProcess(process); }
                    catch (Throwable failure) {
                        // Keep its registration until the process really exits.
                        // Failure to request termination cannot free the root.
                        logCloseFailure("Stop Hub process", failure);
                    }
                    // Retain even descendants whose parent exited after a
                    // failed termination request; its onExit registration alone
                    // cannot establish that those writers have finished.
                    boolean interrupted = false;
                    for (ProcessHandle handle : owned) while (handle.isAlive()) {
                        try { handle.onExit().get(100, java.util.concurrent.TimeUnit.MILLISECONDS); }
                        catch (InterruptedException waiting) { interrupted = true; }
                        catch (java.util.concurrent.TimeoutException waiting) { }
                        catch (java.util.concurrent.ExecutionException failure) {
                            logCloseFailure("Wait for Hub process", failure);
                        }
                    }
                    if (interrupted) Thread.currentThread().interrupt();
                }
                synchronized (HubSession.this) {
                    while (!tasks.isEmpty() || !processes.isEmpty()) HubSession.this.wait(100);
                }
                executor.shutdown();
                for (Runnable action : new ArrayList<>(cleanup)) {
                    try { action.run(); }
                    catch (Throwable failure) { logCloseFailure("Clean up Hub resources", failure); }
                }
                synchronized (HubSession.this) {
                    if (rootLock != null) rootLock.close();
                    rootLock = null;
                    state = State.CLOSED;
                }
                synchronized (OWNERS) { OWNERS.values().removeIf(value -> value == this); }
                synchronized (SESSIONS) { SESSIONS.remove(this); }
                closeFuture.complete(Boolean.TRUE);
            } catch (Throwable failure) { closeFuture.completeExceptionally(failure); }
        }, "SGH-close");
        closer.setDaemon(true);
        closer.start();
        return closeFuture;
    }

    private static void logCloseFailure(String operation, Throwable failure) {
        try {
            java.util.logging.Logger.getLogger(HubSession.class.getName())
                .log(java.util.logging.Level.SEVERE, operation + " failed; continuing close", failure);
        } catch (Throwable loggingFailure) { /* Continue the remaining cleanup even if logging fails. */ }
    }

    private static void destroyProcess(Process process) {
        List<ProcessHandle> children = new ArrayList<>();
        process.descendants().forEach(children::add);
        for (int index = children.size() - 1; index >= 0; index--) children.get(index).destroy();
        process.destroy();
        for (ProcessHandle child : children) if (child.isAlive()) child.destroyForcibly();
        if (process.isAlive()) process.destroyForcibly();
        boolean interrupted = false;
        while (process.isAlive()) {
            try { process.waitFor(); } catch (InterruptedException ignored) { interrupted = true; }
        }
        for (ProcessHandle child : children) while (child.isAlive()) {
            try { child.onExit().get(100, java.util.concurrent.TimeUnit.MILLISECONDS); }
            catch (InterruptedException ignored) { interrupted = true; }
            catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ignored) { }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }
}
