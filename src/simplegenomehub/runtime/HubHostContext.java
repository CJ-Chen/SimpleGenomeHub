package simplegenomehub.runtime;

import java.awt.Component;
import java.awt.Window;
import javax.swing.SwingUtilities;

/** The small boundary between a Hub page and its enclosing application. */
public final class HubHostContext {
    public enum Mode { EMBEDDED, STANDALONE }
    private final Mode mode;
    private volatile Component owner;

    public HubHostContext(Mode mode, Component owner) {
        this.mode = java.util.Objects.requireNonNull(mode, "mode");
        this.owner = owner;
    }
    public static HubHostContext embedded(Component owner) {
        return new HubHostContext(Mode.EMBEDDED, owner);
    }
    public static HubHostContext standalone(Component owner) {
        return new HubHostContext(Mode.STANDALONE, owner);
    }
    public Mode getMode() { return mode; }
    public boolean isEmbedded() { return mode == Mode.EMBEDDED; }
    public synchronized void bindOwnerIfAbsent(Component component) {
        if (owner == null) owner = component;
    }
    public Window getOwnerWindow() {
        return owner instanceof Window ? (Window) owner : owner == null ? null
            : SwingUtilities.getWindowAncestor(owner);
    }
}
