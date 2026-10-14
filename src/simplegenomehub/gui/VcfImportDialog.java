package simplegenomehub.gui;

import simplegenomehub.model.PopulationVcfMetadata;
import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.util.PopulationImportGuard;
import simplegenomehub.util.fileio.VcfImportService;
import simplegenomehub.util.fileio.VcfImportService.ImportResult;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.InterruptedIOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Genome-bound VCF import dialog. Recognizes the file header, compares declared chromosomes
 * with the reference genome and stores the file plus its statistics in the data unit's
 * {@code Population/VCF} directory. Population calculations are deliberately not included.
 */
public class VcfImportDialog extends JDialog {
    private static final Logger LOGGER = Logger.getLogger(VcfImportDialog.class.getName());

    private final SpeciesInfo targetSpecies;
    private final File vcfDirectory;
    private final Runnable onImported;
    private final VcfFileDropPanel fileDropPanel;
    private final JButton importButton = new JButton("Import");
    private final JButton cancelButton = new JButton("Cancel");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final AtomicBoolean cancelRequested = new AtomicBoolean();
    private boolean importing;
    private boolean closeAfterCancellation;

    public VcfImportDialog(Window owner, SpeciesInfo species, Runnable onImported) {
        // Modeless on purpose: the main window stays usable while this dialog is open. Operations
        // that would move, delete or re-root the bound genome are refused by PopulationImportGuard.
        super(owner, "Import VCF File", ModalityType.MODELESS);
        if (species == null || species.getPopulationVcfDir() == null) {
            throw new IllegalArgumentException("A genome must be selected before importing a VCF file.");
        }
        this.targetSpecies = species;
        this.vcfDirectory = species.getPopulationVcfDir();
        this.onImported = onImported;
        this.fileDropPanel = new VcfFileDropPanel(species, "VCF File",
            "Drag one .vcf or .vcf.gz file into this area.\n\n"
                + "The header is recognized first, chromosome names are compared with this genome, "
                + "then the file is stored with its statistics.",
            "The original file is stored unchanged; statistics are generated during import.");
        setupLayout();
        setupEventHandlers();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(520, 420));
        setSize(640, 500);
        setLocationRelativeTo(owner);
    }

    private void setupLayout() {
        JPanel content = new JPanel(new BorderLayout(10, 12));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setContentPane(content);

        JPanel genomePanel = new JPanel(new BorderLayout());
        genomePanel.setBorder(BorderFactory.createTitledBorder("Selected Genome"));
        JTextField genomeField = new JTextField(targetSpecies.getSpeciesName() + " (" + targetSpecies.getVersion() + ")");
        genomeField.setEditable(false);
        genomeField.setToolTipText(targetSpecies.getSpeciesDir().getAbsolutePath());
        genomePanel.add(genomeField);
        content.add(genomePanel, BorderLayout.NORTH);

        content.add(fileDropPanel, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 8));
        JLabel storageLabel = new JLabel("Save to: Population / VCF (with statistics)");
        storageLabel.setToolTipText(vcfDirectory.getAbsolutePath());
        footer.add(storageLabel, BorderLayout.NORTH);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        footer.add(progressBar, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        importButton.setEnabled(false);
        buttons.add(cancelButton);
        buttons.add(importButton);
        footer.add(buttons, BorderLayout.SOUTH);
        content.add(footer, BorderLayout.SOUTH);
    }

    private void setupEventHandlers() {
        importButton.addActionListener(e -> performImport());
        cancelButton.addActionListener(e -> dispose());
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { dispose(); }
        });
        fileDropPanel.setChangeListener(() -> {
            if (!importing) {
                importButton.setEnabled(fileDropPanel.hasUsableFile());
            }
        });
    }

    private void performImport() {
        if (importing || !fileDropPanel.hasUsableFile()) return;
        final VcfFileDropPanel.Recognized recognized = fileDropPanel.getRecognized();

        // Re-resolve the destination: the genome may have been renamed while this dialog was open.
        final File destinationDirectory = targetSpecies.getPopulationVcfDir();
        final File speciesDirectory = targetSpecies.getSpeciesDir();
        if (destinationDirectory == null || !destinationDirectory.isDirectory()
                || speciesDirectory == null || !speciesDirectory.isDirectory()) {
            JOptionPane.showMessageDialog(this,
                "The selected genome was moved or removed. Please reopen the import window.",
                "Genome Unavailable", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!PopulationImportGuard.tryBegin(speciesDirectory, "VCF import")) {
            JOptionPane.showMessageDialog(this,
                PopulationImportGuard.describeBusyFor(speciesDirectory)
                    + "\nAnother import for the same genome must finish first.",
                "Import Already Running", JOptionPane.WARNING_MESSAGE);
            return;
        }

        importing = true;
        cancelRequested.set(false);
        fileDropPanel.setInteractionEnabled(false);
        importButton.setEnabled(false);
        progressBar.setValue(0);
        progressBar.setString("Starting import...");
        progressBar.setVisible(true);

        SwingWorker<ImportResult, String> worker = new SwingWorker<ImportResult, String>() {
            @Override protected ImportResult doInBackground() throws Exception {
                try {
                    return VcfImportService.importFile(recognized.getInfo(), destinationDirectory,
                        recognized.getReference(),
                        (percent, phase) -> {
                            setProgress(percent);
                            publish(phase);
                        }, cancelRequested::get);
                } finally {
                    // Released as soon as the worker stops, even if the dialog is gone.
                    PopulationImportGuard.end(speciesDirectory);
                }
            }

            @Override protected void process(List<String> phases) {
                if (!phases.isEmpty() && !cancelRequested.get()) {
                    progressBar.setString(phases.get(phases.size() - 1) + "... " + progressBar.getValue() + "%");
                }
            }

            @Override protected void done() {
                importing = false;
                try {
                    ImportResult imported = get();
                    if (onImported != null) {
                        try {
                            onImported.run();
                        } catch (RuntimeException ex) {
                            LOGGER.log(Level.WARNING, "VCF imported, but genome display refresh failed", ex);
                        }
                    }
                    JOptionPane.showMessageDialog(VcfImportDialog.this,
                        buildCompletionMessage(imported),
                        "Import Complete", JOptionPane.INFORMATION_MESSAGE);
                    dispose();
                } catch (Exception ex) {
                    Throwable cause = errorCause(ex);
                    if (!(cause instanceof InterruptedIOException)) {
                        LOGGER.log(Level.WARNING, "VCF import failed", cause);
                        JOptionPane.showMessageDialog(VcfImportDialog.this,
                            "Failed to import VCF file:\n" + cause.getMessage(),
                            "Import Failed", JOptionPane.ERROR_MESSAGE);
                    }
                    if (closeAfterCancellation) {
                        dispose();
                    } else {
                        fileDropPanel.setInteractionEnabled(true);
                        fileDropPanel.setStatusMessage("Import cancelled or failed. The file is still selected.");
                        cancelButton.setEnabled(true);
                        progressBar.setVisible(false);
                        importButton.setEnabled(fileDropPanel.hasUsableFile());
                    }
                }
            }
        };
        worker.addPropertyChangeListener(event -> {
            if ("progress".equals(event.getPropertyName())) {
                progressBar.setValue((Integer) event.getNewValue());
            }
        });
        worker.execute();
    }

    private String buildCompletionMessage(ImportResult imported) {
        PopulationVcfMetadata metadata = imported.getMetadata();
        StringBuilder message = new StringBuilder();
        message.append("VCF file imported successfully.\n\n");
        message.append(imported.getVcfFile().getAbsolutePath()).append("\n\n");
        message.append("Samples: ").append(metadata.sampleCount).append('\n');
        message.append("Variant records: ").append(metadata.totalRecords).append('\n');
        message.append("SNPs: ").append(metadata.snpCount)
            .append(" | Indels: ").append(metadata.indelCount);
        if (metadata.multiallelicCount > 0) {
            message.append(" | Multi-allelic: ").append(metadata.multiallelicCount);
        }
        message.append("\n\nReference check: ").append(metadata.checkStatus)
            .append(" (").append(metadata.matchedChromosomeCount).append('/')
            .append(metadata.chromosomes.size()).append(" chromosomes matched)");
        if (!metadata.warnings.isEmpty()) {
            message.append("\n\nWarnings:\n");
            for (String warning : metadata.warnings) {
                message.append("- ").append(warning).append('\n');
            }
        }
        message.append("\nStatistics were saved next to the file and are shown in the Other Data card.");
        return message.toString();
    }

    private static Throwable errorCause(Exception exception) {
        return exception instanceof ExecutionException && exception.getCause() != null
            ? exception.getCause() : exception;
    }

    @Override public void dispose() {
        if (importing) {
            closeAfterCancellation = true;
            cancelRequested.set(true);
            cancelButton.setEnabled(false);
            progressBar.setString("Cancelling import...");
            return; // Wait for the worker to close streams and remove the staged files.
        }
        fileDropPanel.disposePanel();
        super.dispose();
    }
}
