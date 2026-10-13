package simplegenomehub.runtime;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

/**
 * A Hub-owned Future whose lease covers the real producer and every accepted UI
 * callback. It deliberately does not use SwingWorker's shared EDT accumulator.
 */
public abstract class HubWorker<T, V> implements RunnableFuture<T> {
    public enum CancellationPolicy { WAIT_COMPLETION, INTERRUPT_SAFE }
    public enum StateValue { PENDING, STARTED, DONE }

    private static final Logger LOGGER = Logger.getLogger(HubWorker.class.getName());
    private final Object gate = new Object();
    private final ArrayDeque<UiCallback> callbacks = new ArrayDeque<>();
    private final List<PropertyChangeListener> listeners = new ArrayList<>();
    private final AtomicBoolean submitted = new AtomicBoolean();
    private final AtomicBoolean runStarted = new AtomicBoolean();
    private final WorkerFuture future = new WorkerFuture();
    private final CancellationPolicy cancellationPolicy;
    private volatile boolean bound;
    private HubSession session;
    private HubSession.Task lease;
    private File dataRoot;
    private long generation;
    private boolean producerFinished;
    private boolean doneDelivered;
    private boolean drainScheduled;
    private int callbacksExecuting;
    private int progress;
    private volatile StateValue state = StateValue.PENDING;
    private volatile Throwable callbackFailure;
    private volatile int callbackFailureCount;

    protected HubWorker() { this(CancellationPolicy.WAIT_COMPLETION); }
    protected HubWorker(CancellationPolicy policy) {
        cancellationPolicy = java.util.Objects.requireNonNull(policy, "policy");
    }

    protected abstract T doInBackground() throws Exception;
    protected void process(List<V> chunks) { }
    protected void done() { }

    private final class WorkerFuture extends FutureTask<T> {
        WorkerFuture() { super(() -> HubWorker.this.doInBackground()); }
        void fail(Throwable failure) { super.setException(failure); }
    }

    private static final class UiCallback {
        final String name;
        final Runnable action;
        UiCallback(String name, Runnable action) { this.name = name; this.action = action; }
    }

    final void bind(HubSession ownerSession) {
        if (!submitted.compareAndSet(false, true))
            throw new IllegalStateException("A Hub worker can only be submitted once");
        session = ownerSession;
        if (session != null) {
            synchronized (session) {
                generation = session.getGeneration();
                dataRoot = session.getDataRoot();
                lease = session.beginTask(getClass().getName(),
                    cancellationPolicy == CancellationPolicy.INTERRUPT_SAFE ? () -> cancel(true) : null);
            }
        }
        bound = true;
    }

    final void submissionFailed(Throwable failure) {
        future.fail(failure);
        finishProducer();
    }

    @Override public final void run() {
        if (!submitted.get()) throw new IllegalStateException("Submit Hub workers through HubSession.execute");
        if (!runStarted.compareAndSet(false, true))
            throw new IllegalStateException("A Hub worker can only run once");
        state = StateValue.STARTED;
        try {
            enqueue("state", () -> notifyListeners("state", StateValue.PENDING, StateValue.STARTED));
            HubSession.runInContext(session, future::run);
        } catch (RuntimeException | Error failure) {
            future.fail(failure);
            throw failure;
        }
    }

    final void executionReturned() { finishProducer(); }

    private void finishProducer() {
        synchronized (gate) {
            if (producerFinished) return;
            producerFinished = true;
            callbacks.add(new UiCallback("done", () -> {
                try {
                    if (isCapturedRootAvailable()) done();
                } finally {
                    state = StateValue.DONE;
                    notifyListeners("state", StateValue.STARTED, StateValue.DONE);
                    synchronized (gate) { doneDelivered = true; }
                }
            }));
            scheduleDrain();
        }
    }

    /** Each published payload is copied before it enters this worker's EDT queue. */
    @SafeVarargs protected final void publish(V... chunks) {
        List<V> copy = new ArrayList<>(Arrays.asList(chunks.clone()));
        enqueue("process", () -> { if (isPresentationCurrent()) process(copy); });
    }

    protected final void setProgress(int value) {
        if (value < 0 || value > 100) throw new IllegalArgumentException("Progress must be in 0..100");
        final int previous;
        synchronized (gate) {
            ensureBound();
            if (producerFinished) throw new IllegalStateException("Worker has finished publishing progress");
            previous = progress;
            if (previous == value) return;
            progress = value;
            enqueueLocked("progress", () -> notifyListeners("progress", previous, value));
        }
    }

    public final int getProgress() { synchronized (gate) { return progress; } }
    public final StateValue getState() { return future.isDone() ? StateValue.DONE : state; }
    public final Throwable getCallbackFailure() { return callbackFailure; }
    public final int getCallbackFailureCount() { return callbackFailureCount; }

    public final void addPropertyChangeListener(PropertyChangeListener listener) {
        synchronized (gate) { listeners.add(java.util.Objects.requireNonNull(listener, "listener")); }
    }
    public final void removePropertyChangeListener(PropertyChangeListener listener) {
        synchronized (gate) { listeners.remove(listener); }
    }

    /** For a callback that must be queued from a process/done callback itself. */
    protected final void postToEdt(Runnable callback) {
        synchronized (gate) {
            ensureBound();
            // Callback descendants are accepted while their parent is still
            // counted, even if the background producer has already returned.
            if (doneDelivered && callbacksExecuting == 0)
                throw new IllegalStateException("Worker UI callbacks have already completed");
            enqueueLocked("nested UI", callback);
        }
    }

    private void enqueue(String name, Runnable callback) {
        synchronized (gate) {
            ensureBound();
            if (producerFinished) throw new IllegalStateException("Worker has finished publishing callbacks");
            enqueueLocked(name, callback);
        }
    }
    private void ensureBound() {
        if (!bound) throw new IllegalStateException("Submit Hub workers before publishing UI callbacks");
    }
    private void enqueueLocked(String name, Runnable callback) {
        callbacks.add(new UiCallback(name, callback));
        scheduleDrain();
    }
    private void scheduleDrain() {
        if (drainScheduled) return;
        drainScheduled = true;
        SwingUtilities.invokeLater(this::drainUi);
    }

    private void drainUi() {
        long started = System.nanoTime();
        int delivered = 0;
        for (;;) {
            UiCallback callback;
            synchronized (gate) {
                callback = callbacks.poll();
                if (callback == null) {
                    drainScheduled = false;
                    releaseIfFinished();
                    return;
                }
                callbacksExecuting++;
            }
            try {
                HubSession.runInContext(session, callback.action);
            } catch (Throwable failure) {
                recordCallbackFailure(callback.name, failure);
            } finally {
                synchronized (gate) {
                    callbacksExecuting--;
                    releaseIfFinished();
                }
            }
            // Large imports must leave time for paint, close and cancel events.
            if (++delivered >= 64 || System.nanoTime() - started >= TimeUnit.MILLISECONDS.toNanos(10)) {
                SwingUtilities.invokeLater(this::drainUi);
                return;
            }
        }
    }

    private void notifyListeners(String property, Object previous, Object next) {
        if (!isPresentationCurrent()) return;
        List<PropertyChangeListener> snapshot;
        synchronized (gate) { snapshot = new ArrayList<>(listeners); }
        PropertyChangeEvent event = new PropertyChangeEvent(this, property, previous, next);
        for (PropertyChangeListener listener : snapshot) {
            try { listener.propertyChange(event); }
            catch (Throwable failure) { recordCallbackFailure(property + " listener", failure); }
        }
    }

    private boolean isCapturedRootAvailable() {
        if (session == null) return true;
        synchronized (session) {
            if (session.getState() == HubSession.State.CLOSED) return false;
            // CLOSING owns the same root until this queue drains. Accepted done
            // and cleanup callbacks must still finish before its lease ends.
            return java.util.Objects.equals(dataRoot, session.getDataRoot())
                && (session.getState() == HubSession.State.CLOSING || session.getGeneration() == generation);
        }
    }

    protected final boolean isPresentationCurrent() {
        return session == null || session.accepts(generation);
    }

    private void recordCallbackFailure(String callback, Throwable failure) {
        callbackFailure = failure;
        callbackFailureCount++;
        try { LOGGER.log(Level.SEVERE, "Hub worker " + getClass().getName() + " failed in " + callback, failure); }
        catch (Throwable loggingFailure) { /* Lease release must not depend on logging. */ }
    }

    private void releaseIfFinished() {
        if (producerFinished && doneDelivered && callbacks.isEmpty() && callbacksExecuting == 0 && lease != null) {
            HubSession.Task finished = lease;
            lease = null;
            finished.close();
        }
    }

    @Override public final boolean cancel(boolean mayInterruptIfRunning) { return future.cancel(mayInterruptIfRunning); }
    @Override public final boolean isCancelled() { return future.isCancelled(); }
    @Override public final boolean isDone() { return future.isDone(); }
    @Override public final T get() throws InterruptedException, ExecutionException { return future.get(); }
    @Override public final T get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException { return future.get(timeout, unit); }
}
