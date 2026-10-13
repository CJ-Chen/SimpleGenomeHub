package simplegenomehub.gui;

import java.awt.Component;
import java.awt.Window;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import jigplot.engine.JIGBasePanel;
import simplegenomehub.runtime.HubSession;

/** Binds one plot invocation and its later export dialogs to the originating data session. */
public final class HubPlotOwnership implements Consumer<Component> {
    private final HubSession session;
    private final long generation;

    public HubPlotOwnership(Component owner) {
        session = HubSession.forComponent(owner);
        generation = session == null ? 0 : session.getGeneration();
    }

    public boolean isCurrent() {
        return session == null || session.accepts(generation);
    }

    @FunctionalInterface
    public interface PlotAction {
        void run() throws Exception;
    }

    /** Hold the session while synchronous plotting or a nested chooser is active. */
    public void run(PlotAction action) throws Exception {
        if (session == null) {
            action.run();
            return;
        }
        final HubSession.Task task;
        synchronized (session) {
            if (!isCurrent()) return;
            task = session.beginTask("Create Hub plot", null);
        }
        try (HubSession.Task running = task) {
            action.run();
        }
    }

    @Override
    public void accept(Component component) {
        if (component == null || session == null) return;
        if (!isCurrent()) {
            if (component instanceof Window) ((Window) component).dispose();
            throw new CancellationException("The plot belongs to a previous Hub data session");
        }
        HubSession.attach(component, session);
        if (component instanceof JIGBasePanel) {
            JIGBasePanel panel = (JIGBasePanel) component;
            panel.setOwnedDialogRegistrar(this::accept);
        }
        if (component instanceof Window) {
            SimpleGenomeHubFontManager.registerOwnedWindowTree((Window) component, session);
        } else {
            session.guardActionsIn(component);
        }
    }
}
