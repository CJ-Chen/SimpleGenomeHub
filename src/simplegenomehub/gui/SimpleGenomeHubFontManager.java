package simplegenomehub.gui;

import simplegenomehub.config.SimpleGenomeHubConfig;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.TitledBorder;
import javax.swing.tree.TreeCellRenderer;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.WindowEvent;
import java.awt.event.ContainerEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.ArrayList;
import simplegenomehub.runtime.HubSession;

/**
 * Applies a user-controlled font scale across existing and newly opened Swing windows.
 */
public final class SimpleGenomeHubFontManager {

    public static final String SCALE_PERCENT_PROPERTY = "uiFontScalePercent";

    private static final int MIN_SCALE_PERCENT = 90;
    private static final int MAX_SCALE_PERCENT = 140;
    private static final int DEFAULT_SCALE_PERCENT = 100;
    private static final int DRAG_APPLY_INTERVAL_MS = 100;

    private static final String BASE_FONT_PROPERTY = "sgh.baseFont";
    private static final String BASE_PREFERRED_SIZE_PROPERTY = "sgh.basePreferredSize";
    private static final String BASE_MINIMUM_SIZE_PROPERTY = "sgh.baseMinimumSize";
    private static final String BASE_MAXIMUM_SIZE_PROPERTY = "sgh.baseMaximumSize";
    private static final String BASE_TABLE_ROW_HEIGHT_PROPERTY = "sgh.baseTableRowHeight";
    private static final String BASE_TREE_ROW_HEIGHT_PROPERTY = "sgh.baseTreeRowHeight";
    private static final String BASE_SPLIT_PANE_DIVIDER_PROPERTY = "sgh.baseSplitPaneDividerSize";

    private static final PropertyChangeSupport SCALE_CHANGES =
        new PropertyChangeSupport(SimpleGenomeHubFontManager.class);

    private static volatile boolean installed;
    private static volatile int currentScalePercent = DEFAULT_SCALE_PERCENT;
    private static volatile int pendingScalePercent = DEFAULT_SCALE_PERCENT;
    private static volatile boolean pendingDragApply;
    private static Timer dragApplyTimer;
    private static final Map<Component, HubSession> ROOTS = new WeakHashMap<>();
    private static AWTEventListener windowListener;

    private SimpleGenomeHubFontManager() {
    }

    public static void install() {
        if (installed) {
            return;
        }

        synchronized (SimpleGenomeHubFontManager.class) {
            if (installed) {
                return;
            }

            currentScalePercent = clampScalePercent(
                SimpleGenomeHubConfig.getInstance().getUiFontScalePercent()
            );
            SimpleGenomeHubStyle.setUserFontScalePercent(currentScalePercent);
            simplegenomehub.population.fst.FstUiStyle.setScalePercentOverride(currentScalePercent);
            installed = true;
        }

    }

    /** Register only this Hub's component tree, never the enclosing TBtools frame. */
    public static void registerRoot(Component root, HubSession session) {
        install();
        ROOTS.put(root, session);
        HubSession.attach(root, session);
        installWindowOpenListener();
        applyScaleToComponentTree(root, currentScalePercent);
    }

    public static void unregisterRoot(Component root) {
        HubSession session = ROOTS.remove(root);
        if (session != null) ROOTS.entrySet().removeIf(entry -> entry.getValue() == session);
        if (ROOTS.isEmpty()) {
            stopDragPreview();
            if (windowListener != null) {
                Toolkit.getDefaultToolkit().removeAWTEventListener(windowListener);
                windowListener = null;
            }
        }
    }

    /** Includes host drawing controllers whose classes are ordinary Swing dialogs. */
    public static void registerOwnedWindowTree(Window window, HubSession session) {
        if (window == null || session == null) return;
        Runnable register = () -> registerOwnedWindowsOnEdt(window, session);
        if (SwingUtilities.isEventDispatchThread()) register.run();
        else try { SwingUtilities.invokeAndWait(register); }
        catch (Exception failure) { throw new IllegalStateException("Cannot register Hub drawing windows", failure); }
    }

    private static void registerOwnedWindowsOnEdt(Window window, HubSession session) {
        if (window instanceof RootPaneContainer)
            ((RootPaneContainer) window).getRootPane().putClientProperty("sgh.drawingWindow", session);
        registerRoot(window, session);
        session.guardActionsIn(window);
        for (Window child : window.getOwnedWindows()) registerOwnedWindowsOnEdt(child, session);
    }

    public static int getScalePercent() {
        install();
        return currentScalePercent;
    }

    public static void setScalePercent(int scalePercent) {
        applyScalePercent(scalePercent, true);
    }

    public static void previewScalePercent(int scalePercent) {
        install();

        int clampedScalePercent = clampScalePercent(scalePercent);
        pendingScalePercent = clampedScalePercent;
        pendingDragApply = true;

        Timer timer = ensureDragApplyTimer();
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    public static void addScaleChangeListener(PropertyChangeListener listener) {
        SCALE_CHANGES.addPropertyChangeListener(listener);
    }

    public static void removeScaleChangeListener(PropertyChangeListener listener) {
        SCALE_CHANGES.removePropertyChangeListener(listener);
    }

    public static JComponent createScaleControl() {
        install();

        final JSlider scaleSlider = new JSlider(MIN_SCALE_PERCENT, MAX_SCALE_PERCENT, getScalePercent());
        scaleSlider.setOpaque(false);
        scaleSlider.setFocusable(false);
        scaleSlider.setMajorTickSpacing(10);
        scaleSlider.setMinorTickSpacing(5);
        scaleSlider.setSnapToTicks(true);
        scaleSlider.setPaintTicks(false);
        scaleSlider.setToolTipText("Adjust application font size");

        final JLabel valueLabel = new JLabel(getScalePercent() + "%");
        valueLabel.setPreferredSize(new Dimension(42, valueLabel.getPreferredSize().height));

        final PropertyChangeListener scaleListener = event -> {
            if (!SCALE_PERCENT_PROPERTY.equals(event.getPropertyName())) {
                return;
            }

            int value = (Integer) event.getNewValue();
            if (scaleSlider.getValue() != value) {
                scaleSlider.setValue(value);
            }
            valueLabel.setText(value + "%");
        };

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0)) {
            @Override
            public void addNotify() {
                super.addNotify();
                addScaleChangeListener(scaleListener);
            }

            @Override
            public void removeNotify() {
                removeScaleChangeListener(scaleListener);
                super.removeNotify();
            }
        };
        panel.setOpaque(false);

        JLabel compactLabel = new JLabel("Font");
        compactLabel.setFont(SimpleGenomeHubStyle.plain(compactLabel.getFont(), 11f));
        JLabel smallA = new JLabel("A");
        smallA.setFont(SimpleGenomeHubStyle.plain(smallA.getFont(), 11f));
        JLabel largeA = new JLabel("A");
        largeA.setFont(SimpleGenomeHubStyle.bold(largeA.getFont(), 13f));

        scaleSlider.addChangeListener(event -> {
            int value = scaleSlider.getValue();
            valueLabel.setText(value + "%");
            if (scaleSlider.getValueIsAdjusting()) {
                previewScalePercent(value);
                return;
            }

            stopDragPreview();
            setScalePercent(value);
        });

        panel.add(compactLabel);
        panel.add(smallA);
        panel.add(scaleSlider);
        panel.add(largeA);
        panel.add(valueLabel);
        return panel;
    }

    private static void installWindowOpenListener() {
        if (windowListener != null) return;
        windowListener = event -> {
            if (event instanceof ContainerEvent && event.getID() == ContainerEvent.COMPONENT_ADDED) {
                Component child = ((ContainerEvent) event).getChild();
                if (belongsToRegisteredRoot(child)) {
                    SwingUtilities.invokeLater(() -> {
                        HubSession owner = HubSession.getExplicitOwner(child);
                        if (owner != null && isDrawingComponent(child)) owner.guardActionsIn(child);
                        applyScaleToComponentTree(child, currentScalePercent);
                    });
                }
                return;
            }
            if (!(event instanceof WindowEvent)) {
                return;
            }
            if (event.getID() != WindowEvent.WINDOW_OPENED) {
                return;
            }

            Window window = ((WindowEvent) event).getWindow();
            // Ordinary TBtools dialogs share the same frame owner. Ownership of
            // a frame alone therefore cannot identify an SGH dialog.
            HubSession explicitOwner = HubSession.getExplicitOwner(window);
            if (window == null || (explicitOwner == null
                && !window.getClass().getName().startsWith("simplegenomehub."))) {
                return;
            }
            HubSession session = explicitOwner == null ? HubSession.forComponent(window) : explicitOwner;
            if (session == null) return;
            HubSession.attach(window, session);
            if (!session.isActive()) { window.dispose(); return; }
            ROOTS.put(window, session);
            SwingUtilities.invokeLater(() -> {
                if (!session.isActive()) return;
                if (window instanceof JDialog) SimpleGenomeHubUi.applyDialogStyle((JDialog) window);
                applyScaleToWindow(window, currentScalePercent);
            });
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(windowListener,
            AWTEvent.WINDOW_EVENT_MASK | AWTEvent.CONTAINER_EVENT_MASK);
    }

    private static boolean belongsToRegisteredRoot(Component component) {
        for (Component cursor = component; cursor != null; cursor = cursor.getParent())
            if (ROOTS.containsKey(cursor)) return true;
        HubSession owner = HubSession.getExplicitOwner(component);
        return owner != null && ROOTS.containsValue(owner);
    }

    private static boolean isDrawingComponent(Component component) {
        Window window = component instanceof Window ? (Window) component : SwingUtilities.getWindowAncestor(component);
        for (; window != null; window = window.getOwner())
            if (window instanceof RootPaneContainer && ((RootPaneContainer) window).getRootPane()
                .getClientProperty("sgh.drawingWindow") != null) return true;
        return false;
    }

    private static Timer ensureDragApplyTimer() {
        if (dragApplyTimer == null) {
            dragApplyTimer = new Timer(DRAG_APPLY_INTERVAL_MS, event -> {
                if (!pendingDragApply) {
                    return;
                }

                pendingDragApply = false;
                applyScalePercent(pendingScalePercent, false);
            });
            dragApplyTimer.setRepeats(true);
            dragApplyTimer.setCoalesce(true);
        }
        return dragApplyTimer;
    }

    private static void stopDragPreview() {
        pendingDragApply = false;
        if (dragApplyTimer != null && dragApplyTimer.isRunning()) {
            dragApplyTimer.stop();
        }
    }

    private static void applyScalePercent(int scalePercent, boolean persistToConfig) {
        install();

        int clampedScalePercent = clampScalePercent(scalePercent);
        int previousScalePercent = currentScalePercent;
        if (previousScalePercent == clampedScalePercent) {
            if (persistToConfig) {
                SimpleGenomeHubConfig.getInstance().setUiFontScalePercent(clampedScalePercent);
            }
            return;
        }

        currentScalePercent = clampedScalePercent;
        SimpleGenomeHubStyle.setUserFontScalePercent(clampedScalePercent);
        simplegenomehub.population.fst.FstUiStyle.setScalePercentOverride(clampedScalePercent);
        applyScaleToOpenWindows(previousScalePercent);

        if (persistToConfig) {
            SimpleGenomeHubConfig.getInstance().setUiFontScalePercent(clampedScalePercent);
        }

        SCALE_CHANGES.firePropertyChange(
            SCALE_PERCENT_PROPERTY,
            previousScalePercent,
            clampedScalePercent
        );
    }

    private static void applyScaleToOpenWindows(int referenceScalePercent) {
        for (Component root : new ArrayList<>(ROOTS.keySet())) {
            if (root instanceof Window) applyScaleToWindow((Window) root, referenceScalePercent);
            else if (root != null) {
                applyScaleToComponentTree(root, referenceScalePercent);
                root.invalidate();
                if (root instanceof JComponent) ((JComponent) root).revalidate();
                root.repaint();
            }
        }
    }

    private static void applyScaleToWindow(Window window, int referenceScalePercent) {
        Container root = resolveRootContainer(window);
        if (root == null) {
            return;
        }

        applyScaleToComponentTree(root, referenceScalePercent);
        window.invalidate();
        window.validate();
        window.repaint();
    }

    private static Container resolveRootContainer(Window window) {
        if (window instanceof RootPaneContainer) {
            JRootPane rootPane = ((RootPaneContainer) window).getRootPane();
            if (rootPane != null) {
                return rootPane;
            }
        }
        return window;
    }

    private static void applyScaleToComponentTree(Component component, int referenceScalePercent) {
        if (component == null) {
            return;
        }

        scaleComponentFont(component, referenceScalePercent);

        if (component instanceof JComponent) {
            JComponent swingComponent = (JComponent) component;
            scaleExplicitSizes(swingComponent, referenceScalePercent);
            scaleBorderFonts(swingComponent.getBorder(), referenceScalePercent);
        }

        if (component instanceof JTable) {
            scaleTableMetrics((JTable) component, referenceScalePercent);
        } else if (component instanceof JTree) {
            JTree tree = (JTree) component;
            scaleTreeMetrics(tree, referenceScalePercent);
            scaleTreeRenderer(tree, referenceScalePercent);
        } else if (component instanceof JSplitPane) {
            scaleSplitPaneMetrics((JSplitPane) component, referenceScalePercent);
        }

        if (component instanceof JMenu) {
            applyScaleToComponentTree(((JMenu) component).getPopupMenu(), referenceScalePercent);
        }

        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                applyScaleToComponentTree(child, referenceScalePercent);
            }
        }
    }

    private static void scaleComponentFont(Component component, int referenceScalePercent) {
        if (!(component instanceof JComponent)) {
            return;
        }

        Font currentFont = component.getFont();
        if (currentFont == null) {
            return;
        }

        JComponent swingComponent = (JComponent) component;
        Font baseFont = (Font) swingComponent.getClientProperty(BASE_FONT_PROPERTY);
        if (baseFont == null) {
            baseFont = normalizeFontForScale(currentFont, referenceScalePercent);
            swingComponent.putClientProperty(BASE_FONT_PROPERTY, baseFont);
        } else {
            Font expectedCurrentFont = scaleFontForScale(baseFont, referenceScalePercent);
            if (!fontsMatch(currentFont, expectedCurrentFont)) {
                baseFont = normalizeFontForScale(currentFont, referenceScalePercent);
                swingComponent.putClientProperty(BASE_FONT_PROPERTY, baseFont);
            }
        }

        Font scaledFont = scaleFontForScale(baseFont, currentScalePercent);
        if (!fontsMatch(currentFont, scaledFont)) {
            component.setFont(scaledFont);
        }
    }

    private static void scaleExplicitSizes(JComponent component, int referenceScalePercent) {
        if (component.isPreferredSizeSet()) {
            Dimension basePreferredSize = resolveBaseDimension(
                component,
                BASE_PREFERRED_SIZE_PROPERTY,
                component.getPreferredSize(),
                referenceScalePercent
            );
            component.setPreferredSize(scaleDimensionForScale(basePreferredSize, currentScalePercent));
        }

        if (component.isMinimumSizeSet()) {
            Dimension baseMinimumSize = resolveBaseDimension(
                component,
                BASE_MINIMUM_SIZE_PROPERTY,
                component.getMinimumSize(),
                referenceScalePercent
            );
            component.setMinimumSize(scaleDimensionForScale(baseMinimumSize, currentScalePercent));
        }

        if (component.isMaximumSizeSet()) {
            Dimension baseMaximumSize = resolveBaseDimension(
                component,
                BASE_MAXIMUM_SIZE_PROPERTY,
                component.getMaximumSize(),
                referenceScalePercent
            );
            component.setMaximumSize(scaleDimensionForScale(baseMaximumSize, currentScalePercent));
        }
    }

    private static void scaleTableMetrics(JTable table, int referenceScalePercent) {
        Object storedValue = table.getClientProperty(BASE_TABLE_ROW_HEIGHT_PROPERTY);
        int currentRowHeight = table.getRowHeight();
        if (currentRowHeight <= 0) {
            return;
        }

        int baseRowHeight = storedValue instanceof Integer ? (Integer) storedValue : currentRowHeight;
        if (storedValue != null) {
            int expectedRowHeight = scaleIntForScale(baseRowHeight, referenceScalePercent);
            if (currentRowHeight != expectedRowHeight) {
                baseRowHeight = currentRowHeight;
            }
        }
        table.putClientProperty(BASE_TABLE_ROW_HEIGHT_PROPERTY, baseRowHeight);
        table.setRowHeight(scaleIntForScale(baseRowHeight, currentScalePercent));
    }

    private static void scaleTreeMetrics(JTree tree, int referenceScalePercent) {
        int currentRowHeight = tree.getRowHeight();
        if (currentRowHeight <= 0) {
            return;
        }

        Object storedValue = tree.getClientProperty(BASE_TREE_ROW_HEIGHT_PROPERTY);
        int baseRowHeight = storedValue instanceof Integer ? (Integer) storedValue : currentRowHeight;
        if (storedValue != null) {
            int expectedRowHeight = scaleIntForScale(baseRowHeight, referenceScalePercent);
            if (currentRowHeight != expectedRowHeight) {
                baseRowHeight = currentRowHeight;
            }
        }
        tree.putClientProperty(BASE_TREE_ROW_HEIGHT_PROPERTY, baseRowHeight);
        tree.setRowHeight(scaleIntForScale(baseRowHeight, currentScalePercent));
    }

    private static void scaleTreeRenderer(JTree tree, int referenceScalePercent) {
        TreeCellRenderer renderer = tree.getCellRenderer();
        if (!(renderer instanceof Component)) {
            return;
        }

        applyScaleToComponentTree((Component) renderer, referenceScalePercent);
    }

    private static void scaleSplitPaneMetrics(JSplitPane splitPane, int referenceScalePercent) {
        int currentDividerSize = splitPane.getDividerSize();
        Object storedValue = splitPane.getClientProperty(BASE_SPLIT_PANE_DIVIDER_PROPERTY);
        int baseDividerSize = storedValue instanceof Integer ? (Integer) storedValue : currentDividerSize;
        if (storedValue != null) {
            int expectedDividerSize = scaleIntForScale(baseDividerSize, referenceScalePercent);
            if (currentDividerSize != expectedDividerSize) {
                baseDividerSize = currentDividerSize;
            }
        }
        splitPane.putClientProperty(BASE_SPLIT_PANE_DIVIDER_PROPERTY, baseDividerSize);
        splitPane.setDividerSize(scaleIntForScale(baseDividerSize, currentScalePercent));
    }

    private static void scaleBorderFonts(Border border, int referenceScalePercent) {
        if (border == null) {
            return;
        }

        if (border instanceof TitledBorder) {
            TitledBorder titledBorder = (TitledBorder) border;
            Font currentTitleFont = titledBorder.getTitleFont();
            if (currentTitleFont != null) {
                Font baseTitleFont = normalizeFontForScale(currentTitleFont, referenceScalePercent);
                titledBorder.setTitleFont(scaleFontForScale(baseTitleFont, currentScalePercent));
            }
            return;
        }

        if (border instanceof CompoundBorder) {
            CompoundBorder compoundBorder = (CompoundBorder) border;
            scaleBorderFonts(compoundBorder.getOutsideBorder(), referenceScalePercent);
            scaleBorderFonts(compoundBorder.getInsideBorder(), referenceScalePercent);
        }
    }

    private static Dimension resolveBaseDimension(JComponent component,
                                                  String propertyKey,
                                                  Dimension currentDimension,
                                                  int referenceScalePercent) {
        if (currentDimension == null) {
            return null;
        }

        Object storedValue = component.getClientProperty(propertyKey);
        Dimension baseDimension = storedValue instanceof Dimension
            ? new Dimension((Dimension) storedValue)
            : new Dimension(currentDimension);

        if (storedValue != null) {
            Dimension expectedDimension = scaleDimensionForScale(baseDimension, referenceScalePercent);
            if (!dimensionsMatch(currentDimension, expectedDimension)) {
                baseDimension = new Dimension(currentDimension);
            }
        }

        component.putClientProperty(propertyKey, new Dimension(baseDimension));
        return baseDimension;
    }

    private static Font normalizeFontForScale(Font font, int scalePercent) {
        float scaleFactor = getScaleFactor(scalePercent);
        float baseSize = Math.max(1.0f, font.getSize2D() / scaleFactor);
        return font.deriveFont(font.getStyle(), baseSize);
    }

    private static Font scaleFontForScale(Font baseFont, int scalePercent) {
        float scaledSize = Math.max(1.0f, baseFont.getSize2D() * getScaleFactor(scalePercent));
        return baseFont.deriveFont(baseFont.getStyle(), scaledSize);
    }

    private static Dimension scaleDimensionForScale(Dimension baseDimension, int scalePercent) {
        if (baseDimension == null) {
            return null;
        }

        return new Dimension(
            scaleIntForScale(baseDimension.width, scalePercent),
            scaleIntForScale(baseDimension.height, scalePercent)
        );
    }

    private static int scaleIntForScale(int baseValue, int scalePercent) {
        if (baseValue == Integer.MAX_VALUE || baseValue <= 0) {
            return baseValue;
        }
        return Math.max(1, Math.round(baseValue * getScaleFactor(scalePercent)));
    }

    private static float getScaleFactor(int scalePercent) {
        return Math.max(0.5f, scalePercent / 100.0f);
    }

    private static boolean fontsMatch(Font first, Font second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        return first.getStyle() == second.getStyle()
            && first.getFamily().equals(second.getFamily())
            && Math.abs(first.getSize2D() - second.getSize2D()) < 0.05f;
    }

    private static boolean dimensionsMatch(Dimension first, Dimension second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        return first.width == second.width && first.height == second.height;
    }

    private static int clampScalePercent(int scalePercent) {
        return Math.max(MIN_SCALE_PERCENT, Math.min(MAX_SCALE_PERCENT, scalePercent));
    }
}
