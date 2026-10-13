/*
 * SimpleGenomeHub Main GUI Panel
 */
package simplegenomehub.gui;

import simplegenomehub.config.SimpleGenomeHubConfig;
import simplegenomehub.model.*;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletionStage;
import simplegenomehub.runtime.HubHostContext;
import simplegenomehub.runtime.HubSession;
import biocjava.GUIexcutors.TabCloseGuard;

/**
 * Main GUI panel for SimpleGenomeHub application
 * Based on TBtools GUI patterns with Swing components
 * 
 * @author SimpleGenomeHub
 */
public class SimpleGenomeHubMainPanel extends JPanel implements SpeciesManager.SpeciesManagerListener, TabCloseGuard {
    
    // Core components
    private SimpleGenomeHubConfig config;
    private SpeciesManager speciesManager;
    private final HubSession session;
    
    // GUI components
    private SpeciesTreePanel speciesTreePanel;
    private SpeciesInfoPanel speciesInfoPanel;
    private JLabel statusLabel;
        
    /**
     * Constructor
     */
    public SimpleGenomeHubMainPanel() {
        this(HubHostContext.embedded(null));
    }

    public SimpleGenomeHubMainPanel(HubHostContext hostContext) {
        hostContext.bindOwnerIfAbsent(this);
        config = SimpleGenomeHubConfig.getInstance();
        try {
            session = new HubSession(hostContext, config.getDataRootDir());
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot open Simple Genome Hub: " + failure.getMessage(), failure);
        }
        HubSession.attach(this, session);
        try {
            initializeComponents();
            setupLayout();
            setupEventHandlers();
            SimpleGenomeHubFontManager.registerRoot(this, session);
        } catch (RuntimeException | Error failure) {
            session.addCleanup(() -> {
                if (speciesManager != null) speciesManager.shutdown();
                SwingUtilities.invokeLater(() -> SimpleGenomeHubFontManager.unregisterRoot(this));
            });
            session.closeAsync();
            throw failure;
        }
        session.addCleanup(() -> {
            speciesManager.removeListener(this);
            speciesManager.shutdown();
            Runnable disposeUi = () -> {
                for (Window window : session.getOwnedWindows()) window.dispose();
                SimpleGenomeHubFontManager.unregisterRoot(this);
            };
            if (SwingUtilities.isEventDispatchThread()) disposeUi.run();
            else try { SwingUtilities.invokeAndWait(disposeUi); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
        });
        // Don't call refreshData() here since SpeciesManager constructor already loads data
        // refreshData();
        
        // Just update UI components to reflect the loaded data
        SwingUtilities.invokeLater(() -> {
            if (!session.isActive()) return;
            speciesTreePanel.clearFilter();
            speciesInfoPanel.setSpecies(null);
            setStatus("Ready - " + speciesManager.getSpeciesCount() + " species loaded");
        });
    }

    /**
     * Initialize core components
     */
    private void initializeComponents() {
        SimpleGenomeHubFontManager.install();
        speciesManager = new SpeciesManager(session.getDataRoot());
        speciesManager.setSession(session);
        speciesManager.addListener(this);
        
        // Create GUI components
        speciesTreePanel = new SpeciesTreePanel(speciesManager);
        speciesInfoPanel = new SpeciesInfoPanel(speciesManager);
        statusLabel = new JLabel("Ready");
    }
    
    /**
     * Setup panel layout
     */
    private void setupLayout() {
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);
        
        add(createTitleBannerPanel(), BorderLayout.NORTH);

        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPane.setDividerLocation(450);
        mainSplitPane.setResizeWeight(0.33);
        SimpleGenomeHubUi.styleSplitPane(mainSplitPane);
        mainSplitPane.setLeftComponent(speciesTreePanel);
        mainSplitPane.setRightComponent(speciesInfoPanel);

        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setOpaque(false);
        contentPanel.setBorder(SimpleGenomeHubUi.createInnerPadding(14, 14, 12, 14));
        contentPanel.add(mainSplitPane, BorderLayout.CENTER);

        add(contentPanel, BorderLayout.CENTER);

        JPanel statusPanel = createStatusPanel();
        add(statusPanel, BorderLayout.SOUTH);
    }
    
    /**
     * Create the centered title banner based on the AAAA reference image
     */
    private JPanel createTitleBannerPanel() {
        final String titleText = "SimpleGenomeHub - Genome Management System";
        final Color sideBarColor = new Color(218, 232, 247);

        JLabel titleLabel = new JLabel(titleText);
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        titleLabel.setForeground(SimpleGenomeHubUi.TITLE_BLUE);
        titleLabel.setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_24);

        JPanel titlePanel = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);

                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setPaint(new LinearGradientPaint(
                        0, 0, getWidth(), 0,
                        new float[]{0.0f, 0.16f, 0.5f, 0.84f, 1.0f},
                        new Color[]{
                            new Color(218, 232, 247),
                            new Color(235, 243, 252),
                            new Color(249, 254, 255),
                            new Color(235, 243, 252),
                            new Color(218, 232, 247)
                        }
                    ));
                    g2.fillRect(0, 0, getWidth(), getHeight());

                    Font titleFont = titleLabel.getFont() != null
                        ? titleLabel.getFont()
                        : SimpleGenomeHubStyle.FONT_SANS_BOLD_24;
                    FontMetrics fontMetrics = g2.getFontMetrics(titleFont);
                    int titleWidth = fontMetrics.stringWidth(titleText);
                    int titleLeft = (getWidth() - titleWidth) / 2;
                    int titleRight = titleLeft + titleWidth;
                    int sidePadding = 34;
                    int titleGap = 30;
                    int barHeight = 4;
                    int barY = getHeight() / 2 - barHeight / 2 + 3;

                    g2.setColor(sideBarColor);
                    int leftBarWidth = titleLeft - titleGap - sidePadding;
                    if (leftBarWidth > 24) {
                        g2.fillRect(sidePadding, barY, leftBarWidth, barHeight);
                    }

                    int rightBarX = titleRight + titleGap;
                    int rightBarWidth = getWidth() - sidePadding - rightBarX;
                    if (rightBarWidth > 24) {
                        g2.fillRect(rightBarX, barY, rightBarWidth, barHeight);
                    }

                    g2.setColor(new Color(156, 179, 204));
                    g2.drawLine(0, 0, getWidth(), 0);
                    g2.setColor(new Color(124, 151, 180));
                    g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
                } finally {
                    g2.dispose();
                }
            }
        };
        titlePanel.setOpaque(false);
        titlePanel.setPreferredSize(new Dimension(10, 84));
        titlePanel.add(titleLabel);

        return titlePanel;
    }
    
    /**
     * Create status panel
     */
    private JPanel createStatusPanel() {
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createEtchedBorder());
        statusPanel.setBackground(new Color(236, 243, 252));
        statusPanel.add(statusLabel, BorderLayout.WEST);

        String dataDir = config.isDataRootConfigured() ? 
            config.getDataRootDir().getAbsolutePath() : "Not configured";
        JLabel dataDirLabel = new JLabel("Data Directory: " + dataDir);
        dataDirLabel.setFont(SimpleGenomeHubStyle.plain(dataDirLabel.getFont(), 11f));

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightPanel.setOpaque(false);
        rightPanel.add(dataDirLabel);
        rightPanel.add(SimpleGenomeHubFontManager.createScaleControl());
        statusPanel.add(rightPanel, BorderLayout.EAST);
        
        return statusPanel;
    }
    
    /**
     * Setup event handlers
     */
    private void setupEventHandlers() {
        speciesTreePanel.setRefreshAction(this::refreshData);
        speciesTreePanel.setConfigurationAction(this::showConfigurationDialog);
        speciesTreePanel.setImportGenomeAction(this::showImportDialog);

        // Listen to species tree selection
        speciesTreePanel.addSelectionListener(new SpeciesTreePanel.SelectionListener() {
            @Override
            public void onSpeciesSelected(SpeciesInfo species) {
                speciesInfoPanel.applyTreeSelection(speciesTreePanel.getCurrentSelectionContext());
            }
        });
    }
    
    /**
     * Show configuration dialog
     */
    private void showConfigurationDialog() {
        if (!session.isActive()) return;
        ConfigurationDialog dialog = new ConfigurationDialog(
            SwingUtilities.getWindowAncestor(this), config, speciesManager);
        HubSession.attach(dialog, session);
        dialog.setVisible(true);
    }
    
    /**
     * Show import dialog
     */
    private void showImportDialog() {
        if (!session.isActive()) return;
        if (!config.isDataRootConfigured()) {
            JOptionPane.showMessageDialog(this, 
                "Please configure the data directory first using the Config button.",
                "Configuration Required", JOptionPane.WARNING_MESSAGE);
            showConfigurationDialog();
            return;
        }
        
        SpeciesImportDialog dialog = new SpeciesImportDialog(
            SwingUtilities.getWindowAncestor(this), speciesManager);
        HubSession.attach(dialog, session);
        dialog.setVisible(true);
    }

    /**
     * Removed methods: showSearchDialog, showValidationDialog, showPerformanceDialog, showHelpDialog
     * These features were deemed unnecessary for the current application scope
     */
    
    /**
     * Refresh all data
     */
    private void refreshData() {
        final long generation = session.getGeneration();
        SwingUtilities.invokeLater(() -> {
            if (!session.accepts(generation)) return;
            setStatus("Refreshing species data...");
            
            // Refresh species manager
            speciesManager.loadSpeciesData();
            
            // Clear any search filters and update tree
            speciesTreePanel.clearFilter();
            
            // Clear info panel
            speciesInfoPanel.setSpecies(null);
            
            int count = speciesManager.getSpeciesCount();
            setStatus("Ready - " + count + " species loaded");
        });
    }
    
    /**
     * Set status message
     */
    private void setStatus(String message) {
        statusLabel.setText(message);
    }
    
    // SpeciesManagerListener implementation
    
    @Override
    public void onSpeciesAdded(SpeciesInfo species) {
        final long generation = session.getGeneration();
        SwingUtilities.invokeLater(() -> {
            if (!session.accepts(generation)) return;
            speciesTreePanel.refreshTree();
            setStatus("Species added: " + species.getSpeciesDirectoryName());
        });
    }
    
    @Override
    public void onSpeciesRemoved(SpeciesInfo species) {
        final long generation = session.getGeneration();
        SwingUtilities.invokeLater(() -> {
            if (!session.accepts(generation)) return;
            speciesTreePanel.refreshTree();
            speciesInfoPanel.setSpecies(null);
            setStatus("Species removed: " + species.getSpeciesDirectoryName());
        });
    }
    
    @Override
    public void onSpeciesUpdated(SpeciesInfo species) {
        final long generation = session.getGeneration();
        SwingUtilities.invokeLater(() -> {
            if (!session.accepts(generation)) return;
            speciesTreePanel.refreshTree();
            if (speciesInfoPanel.getCurrentSpecies() == species) {
                speciesInfoPanel.setSpecies(species);
            }
            setStatus("Species updated: " + species.getSpeciesDirectoryName());
        });
    }
    
    @Override
    public void onDataDirectoryChanged(File newDir) {
        SwingUtilities.invokeLater(() -> {
            if (!session.isActive() || session.getDataRoot() == null
                || !session.getDataRoot().equals(newDir)) return;
            // Model dialogs retain species objects from the old root. Retire
            // them before accepting writes in the replacement session.
            for (Window window : session.getOwnedWindows())
                if (!(window instanceof ConfigurationDialog)) window.dispose();
            refreshData();
            setStatus("Data directory changed to: " + newDir.getAbsolutePath());
            
            // Update status panel
            removeAll();
            setupLayout();
            revalidate();
            repaint();
        });
    }
    
    
    /**
     * Main method for running as standalone application
     */
    /**
     * Get the species manager for external access (e.g., from Gene Viewer)
     */
    public SpeciesManager getSpeciesManager() {
        return speciesManager;
    }

    public HubSession getHubSession() { return session; }

    @Override public boolean requestClose(Component parentForDialog) {
        // Asynchronous callers use requestCloseAsync. Synchronous callers must
        // keep the page until its completion has actually been observed.
        return session.getState() == HubSession.State.CLOSED;
    }

    @Override public CompletionStage<Boolean> requestCloseAsync(Component parentForDialog) {
        if (session.isActive() && session.hasRunningTasks()) {
            int answer = JOptionPane.showOptionDialog(parentForDialog,
                "Hub tasks are still running. Wait for them to finish before closing?",
                "Close Simple Genome Hub", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE, null,
                new String[]{"Wait and close", "Keep page open"}, "Keep page open");
            if (answer != JOptionPane.YES_OPTION)
                return java.util.concurrent.CompletableFuture.completedFuture(Boolean.FALSE);
        }
        setStatus("Closing - waiting for Hub tasks and processes...");
        disableTree(this);
        for (Window window : session.getOwnedWindows()) disableTree(window);
        return session.closeAsync();
    }

    private static void disableTree(Component component) {
        component.setEnabled(false);
        if (component instanceof Container)
            for (Component child : ((Container) component).getComponents()) disableTree(child);
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Use default Swing look and feel
            
            // Create main frame
            JFrame frame = new JFrame("SimpleGenomeHub");
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.setSize(1510, 860);
            frame.setLocationRelativeTo(null);
            
            // Add main panel
            SimpleGenomeHubMainPanel mainPanel = new SimpleGenomeHubMainPanel(HubHostContext.standalone(frame));
            frame.add(mainPanel);
            frame.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosing(java.awt.event.WindowEvent event) {
                    mainPanel.requestCloseAsync(frame).whenComplete((closed, failure) ->
                        SwingUtilities.invokeLater(() -> {
                            if (failure != null) JOptionPane.showMessageDialog(frame,
                                failure.getMessage(), "Cannot close Hub", JOptionPane.ERROR_MESSAGE);
                            else if (Boolean.TRUE.equals(closed)) frame.dispose();
                        }));
                }
            });
            
            // Set window icon (if available)
            try {
                // You could add an icon here
                // frame.setIconImage(Toolkit.getDefaultToolkit().getImage("icon.png"));
            } catch (Exception e) {
                // Ignore icon loading errors
            }
            
            frame.setVisible(true);
        });
    }
}
