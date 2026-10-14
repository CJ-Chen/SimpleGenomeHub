package simplegenomehub.gui;

import simplegenomehub.model.PopulationVcfMetadata;
import simplegenomehub.runtime.HubSession;
import simplegenomehub.runtime.HubWorker;
import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.population.fst.PopulationFstRunner;
import simplegenomehub.util.fileio.VcfImportService;
import simplegenomehub.util.fileio.VcfStatisticsScanner;
import simplegenomehub.util.fileio.VcfValidationService;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Population variation workspace for one genome unit.
 * <p>
 * Lists the VCF datasets already imported into the data unit and accepts a dragged-in VCF for a
 * temporary run. Temporary files are scanned in place and never copied or saved, which keeps very
 * large VCFs usable without duplicating them. The statistics shown for either source are the same
 * text the Other Data card displays.
 * <p>
 * Only the workspace is implemented here; FST calculation is added in a later step.
 */
public class PopulationVariationDialog extends JDialog {
    private static final Logger LOGGER = Logger.getLogger(PopulationVariationDialog.class.getName());

    private final SpeciesInfo targetSpecies;
    private final List<DatasetEntry> datasets = new ArrayList<>();
    private final DatasetTableModel tableModel = new DatasetTableModel();
    private final JTable datasetTable = new JTable(tableModel);
    private final VcfFileDropPanel fileDropPanel;
    private final JTextArea detailsArea = new JTextArea();
    private final JButton runFstButton = new JButton("Run FST");
    private final JButton closeButton = new JButton("Close");

    /** One selectable dataset: an imported file or a dragged-in temporary file. */
    private static final class DatasetEntry {
        private final File file;
        private final boolean temporary;
        private PopulationVcfMetadata metadata;

        private DatasetEntry(File file, boolean temporary, PopulationVcfMetadata metadata) {
            this.file = file;
            this.temporary = temporary;
            this.metadata = metadata;
        }
    }

    private DatasetEntry currentEntry;
    private HubWorker<PopulationVcfMetadata, Integer> scanWorker;
    private File scanTarget;
    private boolean syncingSelection;
    private long scanEpoch;
    private boolean closed;

    public PopulationVariationDialog(Window owner, SpeciesInfo species) {
        super(owner, "Population Variation Analysis - " + species.getSpeciesName(), ModalityType.MODELESS);
        this.targetSpecies = species;
        HubSession.attach(this, HubSession.forDataPath(species.getSpeciesDir()));

        loadImportedDatasets();

        fileDropPanel = new VcfFileDropPanel(species, "VCF File (temporary, not saved)",
            "Drag one .vcf or .vcf.gz file here to analyze it without importing.",
            "Temporary file: it is analyzed in place and never copied or saved.");

        initializeComponents();
        setupLayout();
        setupEventHandlers();

        setSize(920, 760);
        setMinimumSize(new Dimension(760, 600));
        setLocationRelativeTo(owner);
        showPrompt();
        updateRunButton();
    }

    // ------------------------------------------------------------------ data

    private void loadImportedDatasets() {
        File[] vcfFiles = targetSpecies.listPopulationVcfFiles();
        for (File vcfFile : vcfFiles) {
            datasets.add(new DatasetEntry(vcfFile, false, PopulationVcfSummarySupport.loadMetadata(vcfFile)));
        }
        LOGGER.info("Loaded " + datasets.size() + " imported VCF dataset(s) for "
            + targetSpecies.getSpeciesDirectoryName());
    }

    private void initializeComponents() {
        datasetTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        datasetTable.setRowHeight(24);
        datasetTable.getTableHeader().setReorderingAllowed(false);
        int[] widths = {260, 80, 100, 90, 120};
        for (int i = 0; i < widths.length; i++) {
            datasetTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        detailsArea.setEditable(false);
        detailsArea.setLineWrap(false);
        detailsArea.setWrapStyleWord(false);
        detailsArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        detailsArea.setBackground(Color.WHITE);
        detailsArea.setBorder(SimpleGenomeHubUi.createInnerPadding(8, 8, 8, 8));

        runFstButton.setEnabled(false);
    }

    private void setupLayout() {
        JPanel content = new JPanel(new BorderLayout(10, 12));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setContentPane(content);

        JPanel genomePanel = new JPanel(new BorderLayout());
        genomePanel.setBorder(BorderFactory.createTitledBorder("Selected Genome"));
        JTextField genomeField = new JTextField(
            targetSpecies.getSpeciesName() + " (" + targetSpecies.getVersion() + ")");
        genomeField.setEditable(false);
        genomeField.setToolTipText(targetSpecies.getSpeciesDir().getAbsolutePath());
        genomePanel.add(genomeField);
        content.add(genomePanel, BorderLayout.NORTH);

        JPanel listPanel = new JPanel(new BorderLayout());
        listPanel.setBorder(BorderFactory.createTitledBorder(
            "Imported VCF Datasets (" + datasets.size() + ")"));
        JScrollPane tableScrollPane = new JScrollPane(datasetTable);
        ModernScrollBarStyle.applyTo(tableScrollPane);
        listPanel.add(tableScrollPane, BorderLayout.CENTER);
        listPanel.setPreferredSize(new Dimension(0, 170));

        JPanel detailsPanel = new JPanel(new BorderLayout(0, 8));
        detailsPanel.setBorder(BorderFactory.createTitledBorder("Dataset Details"));
        JScrollPane detailsScrollPane = new JScrollPane(detailsArea);
        detailsScrollPane.getViewport().setBackground(Color.WHITE);
        detailsScrollPane.setMinimumSize(new Dimension(0, 140));
        ModernScrollBarStyle.applyTo(detailsScrollPane);
        detailsPanel.add(detailsScrollPane, BorderLayout.CENTER);

        // The drop area takes only the height its own text needs; the details area gets the rest
        // and both can be re-proportioned by dragging the divider.
        JSplitPane sourceSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, fileDropPanel, detailsPanel);
        SimpleGenomeHubUi.styleSplitPane(sourceSplitPane);
        sourceSplitPane.setDividerLocation(218);
        sourceSplitPane.setResizeWeight(0.2);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, listPanel, sourceSplitPane);
        SimpleGenomeHubUi.styleSplitPane(splitPane);
        splitPane.setDividerLocation(140);
        splitPane.setResizeWeight(0.15);
        content.add(splitPane, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        closeButton.setPreferredSize(new Dimension(110, 30));
        runFstButton.setPreferredSize(new Dimension(130, 30));
        buttons.add(closeButton);
        buttons.add(runFstButton);
        content.add(buttons, BorderLayout.SOUTH);
    }

    private void setupEventHandlers() {
        datasetTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onDatasetSelected();
            }
        });
        fileDropPanel.setChangeListener(this::onDropPanelChanged);
        runFstButton.addActionListener(e -> onRunFst());
        closeButton.addActionListener(e -> dispose());
    }

    private void onDatasetSelected() {
        if (syncingSelection) {
            return;
        }
        int row = datasetTable.getSelectedRow();
        if (row < 0 || row >= datasets.size()) {
            return;
        }
        // A stored dataset and a dragged-in file are mutually exclusive sources.
        syncingSelection = true;
        try {
            fileDropPanel.clearSelection();
        } finally {
            syncingSelection = false;
        }
        showStoredDataset(row);
    }

    private void onDropPanelChanged() {
        if (syncingSelection) {
            return;
        }
        if (fileDropPanel.hasUsableFile()) {
            syncingSelection = true;
            try {
                datasetTable.clearSelection();
            } finally {
                syncingSelection = false;
            }
            startTemporaryScan(fileDropPanel.getRecognized());
            return;
        }

        // Nothing usable in the drop area: keep showing the selected imported dataset.
        int row = datasetTable.getSelectedRow();
        if (row >= 0 && row < datasets.size()) {
            showStoredDataset(row);
        } else {
            currentEntry = null;
            showPrompt();
            updateRunButton();
        }
    }

    private void showStoredDataset(int row) {
        DatasetEntry entry = datasets.get(row);
        currentEntry = entry;
        if (entry.metadata == null) {
            fileDropPanel.setStatusMessage("");
        }
        detailsArea.setText(PopulationVcfSummarySupport.buildSummaryText(targetSpecies, entry.file));
        detailsArea.setCaretPosition(0);
        updateRunButton();
    }

    // ------------------------------------------------------------------ temporary scan

    /**
     * Scans a dragged-in file in place so its details match the Other Data card. The file is never
     * copied or saved; the result stays in memory for this window only.
     */
    private void startTemporaryScan(VcfFileDropPanel.Recognized recognized) {
        cancelRunningScan();
        final File file = recognized.getFile();
        final long epoch = scanEpoch;
        scanTarget = file;
        currentEntry = new DatasetEntry(file, true, null);
        detailsArea.setText("Analyzing " + file.getName() + " ...\n\n"
            + "The file is read in place; nothing is copied or saved.");
        detailsArea.setCaretPosition(0);
        updateRunButton();

        scanWorker = new HubWorker<PopulationVcfMetadata, Integer>(HubWorker.CancellationPolicy.INTERRUPT_SAFE) {
            @Override protected PopulationVcfMetadata doInBackground() throws Exception {
                VcfStatisticsScanner.Result scan = VcfStatisticsScanner.scan(file,
                    recognized.getInfo().isCompressed(),
                    (percent, phase) -> {
                        setProgress(percent);
                        publish(percent);
                    },
                    this::isCancelled);
                return VcfValidationService.buildMetadata(file, recognized.getInfo(), scan,
                    recognized.getReference());
            }

            @Override protected void process(List<Integer> percents) {
                if (percents.isEmpty() || closed || epoch != scanEpoch || scanTarget == null || !scanTarget.equals(file)) {
                    return;
                }
                int percent = percents.get(percents.size() - 1);
                detailsArea.setText("Analyzing " + file.getName() + " ... " + percent + "%\n\n"
                    + "The file is read in place; nothing is copied or saved.");
            }

            @Override protected void done() {
                if (!isPresentationCurrent()) return;
                // A newer selection must never be overwritten by an older scan.
                if (closed || epoch != scanEpoch || scanTarget == null || !scanTarget.equals(file)) {
                    return;
                }
                try {
                    PopulationVcfMetadata metadata = get();
                    currentEntry = new DatasetEntry(file, true, metadata);
                    detailsArea.setText(
                        PopulationVcfSummarySupport.buildSummaryText(targetSpecies, file, metadata, true));
                    detailsArea.setCaretPosition(0);
                } catch (CancellationException ignored) {
                    // Superseded by another selection or the window closed.
                } catch (Exception ex) {
                    Throwable cause = ex instanceof ExecutionException && ex.getCause() != null
                        ? ex.getCause() : ex;
                    LOGGER.log(Level.WARNING, "Temporary VCF analysis failed", cause);
                    detailsArea.setText("Could not analyze this file:\n" + cause.getMessage());
                }
                updateRunButton();
            }
        };
        scanWorker.addPropertyChangeListener(event -> {
            if ("progress".equals(event.getPropertyName()) && !closed && epoch == scanEpoch && scanTarget != null && scanTarget.equals(file)) {
                setTitle("Population Variation Analysis - " + targetSpecies.getSpeciesName()
                    + " (" + event.getNewValue() + "%)");
            }
        });
        HubSession.execute(this, scanWorker);
    }

    private void cancelRunningScan() {
        scanEpoch++;
        if (scanWorker != null && !scanWorker.isDone()) {
            scanWorker.cancel(true);
        }
        scanWorker = null;
        scanTarget = null;
    }

    // ------------------------------------------------------------------ actions

    private void onRunFst() {
        if (currentEntry == null || !currentEntry.file.isFile()) {
            return;
        }
        final File vcfFile = currentEntry.file;
        final boolean temporary = currentEntry.temporary;
        PopulationFstRunner.openStrategyWindow(this, targetSpecies, vcfFile, resultDirectory -> {
            String message = "FST calculation finished.\n\nResults: "
                + resultDirectory.getAbsolutePath()
                + (temporary ? "\n\n(this run used a temporary VCF that is not stored in the database)" : "");
            JOptionPane.showMessageDialog(this, message,
                "Calculation Finished", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private void showPrompt() {
        detailsArea.setText("Select an imported dataset above, or drop a VCF file to analyze.\n\n"
            + (datasets.isEmpty()
                ? "No VCF dataset has been imported for this genome yet.\n"
                    + "You can still drop a file for a temporary run."
                : "Imported datasets: " + datasets.size()));
        detailsArea.setCaretPosition(0);
    }

    private void updateRunButton() {
        runFstButton.setEnabled(currentEntry != null && currentEntry.file.isFile());
        runFstButton.setToolTipText(currentEntry == null
            ? "Select or drop a VCF file first."
            : "Run FST on " + currentEntry.file.getName());
    }

    /** File currently selected for analysis; null when nothing is selected. */
    File getSelectedDatasetFile() {
        return currentEntry == null ? null : currentEntry.file;
    }

    boolean isTemporarySelection() {
        return currentEntry != null && currentEntry.temporary;
    }

    PopulationVcfMetadata getSelectedMetadata() {
        return currentEntry == null ? null : currentEntry.metadata;
    }

    boolean isAnalyzing() {
        return scanWorker != null && !scanWorker.isDone();
    }

    /** Table model for the imported VCF datasets. */
    private final class DatasetTableModel extends AbstractTableModel {
        private final String[] columnNames = {"File", "Samples", "Variants", "Size", "Imported"};

        @Override public int getRowCount() {
            return datasets.size();
        }

        @Override public int getColumnCount() {
            return columnNames.length;
        }

        @Override public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override public Class<?> getColumnClass(int columnIndex) {
            return String.class;
        }

        @Override public Object getValueAt(int rowIndex, int columnIndex) {
            if (rowIndex < 0 || rowIndex >= datasets.size()) {
                return null;
            }
            DatasetEntry entry = datasets.get(rowIndex);
            PopulationVcfMetadata metadata = entry.metadata;
            switch (columnIndex) {
                case 0:
                    return entry.file.getName();
                case 1:
                    return metadata == null ? "N/A" : String.valueOf(metadata.sampleCount);
                case 2:
                    return metadata == null ? "N/A" : String.valueOf(metadata.totalRecords);
                case 3:
                    return PopulationVcfSummarySupport.formatFileSize(entry.file.length());
                case 4:
                    return metadata == null ? "N/A" : metadata.getFormattedImportDate();
                default:
                    return null;
            }
        }
    }

    @Override public void dispose() {
        closed = true;
        cancelRunningScan();
        fileDropPanel.disposePanel();
        super.dispose();
    }
}
