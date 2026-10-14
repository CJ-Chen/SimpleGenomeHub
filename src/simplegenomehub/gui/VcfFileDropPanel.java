package simplegenomehub.gui;

import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.util.fileio.VcfImportService;
import simplegenomehub.util.fileio.VcfImportService.FileInfo;
import simplegenomehub.util.fileio.VcfReferenceTable;
import simplegenomehub.util.fileio.VcfValidationService;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reusable "drop or browse one VCF file, recognize its header and compare it with the
 * reference genome" panel.
 * <p>
 * Shared by the VCF import dialog and the population variation dialog so both accept exactly
 * the same files and report the same recognition result. The panel never reads the variant
 * table; a full statistics scan is the caller's decision.
 */
final class VcfFileDropPanel extends JPanel {

    private static final int RECOGNITION_DELAY_MILLIS = 350;

    /** Outcome of recognizing one file. */
    static final class Recognized {
        private final File file;
        private final FileInfo info;
        private final VcfReferenceTable reference;
        private final VcfValidationService.Preflight preflight;

        private Recognized(File file, FileInfo info, VcfReferenceTable reference,
                           VcfValidationService.Preflight preflight) {
            this.file = file;
            this.info = info;
            this.reference = reference;
            this.preflight = preflight;
        }

        File getFile() { return file; }
        FileInfo getInfo() { return info; }
        VcfReferenceTable getReference() { return reference; }
        VcfValidationService.Preflight getPreflight() { return preflight; }
    }

    private final SpeciesInfo species;
    private final String recognizedHint;
    private final String placeholderText;
    private final JTextField filePathField = new JTextField();
    private final JButton browseButton = new JButton("Browse...");
    private final JTextArea infoArea = new JTextArea();
    private final Timer recognitionTimer;
    private final AtomicBoolean interactionEnabled = new AtomicBoolean(true);
    private final AtomicBoolean active = new AtomicBoolean(true);

    private SwingWorker<Recognized, Void> recognitionWorker;
    private Recognized recognized;
    private VcfReferenceTable cachedReference;
    private Runnable changeListener;
    /** Set while the path field is filled programmatically, so the debounce does not fire twice. */
    private boolean suppressDebounce;

    VcfFileDropPanel(SpeciesInfo species, String title, String placeholderText, String recognizedHint) {
        this.species = species;
        this.placeholderText = placeholderText;
        this.recognizedHint = recognizedHint;
        recognitionTimer = new Timer(RECOGNITION_DELAY_MILLIS, e -> recognizeFile());
        recognitionTimer.setRepeats(false);
        setLayout(new BorderLayout(8, 10));
        setBorder(BorderFactory.createTitledBorder(title));

        filePathField.setToolTipText("Drop a .vcf or .vcf.gz file here, or browse to select it.");
        JPanel pathPanel = new JPanel(new BorderLayout(8, 0));
        pathPanel.add(filePathField, BorderLayout.CENTER);
        pathPanel.add(browseButton, BorderLayout.EAST);
        add(pathPanel, BorderLayout.NORTH);

        infoArea.setEditable(false);
        infoArea.setLineWrap(true);
        infoArea.setWrapStyleWord(true);
        infoArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_11);
        infoArea.setBackground(new Color(248, 248, 248));
        infoArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        infoArea.setText(placeholderText);
        JScrollPane infoScrollPane = new JScrollPane(infoArea);
        // Keeps the panel compact when it is laid out with a preferred height (see the variation
        // dialog); in a BorderLayout center the pane still stretches to the available space.
        infoScrollPane.setPreferredSize(new Dimension(200, 150));
        add(infoScrollPane, BorderLayout.CENTER);

        setupEventHandlers();
    }

    private void setupEventHandlers() {
        filePathField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { selectionChanged(); }
            @Override public void removeUpdate(DocumentEvent e) { selectionChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { selectionChanged(); }
        });
        filePathField.addActionListener(e -> {
            recognitionTimer.stop();
            recognizeFile();
        });
        browseButton.addActionListener(e -> browseForFile());

        TransferHandler dropHandler = new TransferHandler() {
            @Override public boolean canImport(TransferSupport support) {
                return interactionEnabled.get() && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    List<?> files = (List<?>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (files.size() != 1 || !(files.get(0) instanceof File)) {
                        selectionChanged();
                        recognitionTimer.stop();
                        setStatusMessage("Please drop one VCF file at a time.");
                        return false;
                    }
                    File file = (File) files.get(0);
                    selectFileAndRecognize(file);
                    return true;
                } catch (Exception ex) {
                    selectionChanged();
                    recognitionTimer.stop();
                    setStatusMessage("Unable to read the dropped file: " + ex.getMessage());
                    return false;
                }
            }
        };
        filePathField.setTransferHandler(dropHandler);
        infoArea.setTransferHandler(dropHandler);
        setTransferHandler(dropHandler);
    }

    private void browseForFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select VCF File");
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileFilter() {
            @Override public boolean accept(File file) {
                return file.isDirectory() || VcfImportService.isSupportedFile(file);
            }
            @Override public String getDescription() { return "VCF Files (*.vcf, *.vcf.gz)"; }
        });
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectFileAndRecognize(chooser.getSelectedFile());
        }
    }

    /** Fills the path field without arming the debounce, then recognizes immediately. */
    private void selectFileAndRecognize(File file) {
        suppressDebounce = true;
        try {
            filePathField.setText(file.getAbsolutePath());
        } finally {
            suppressDebounce = false;
        }
        recognitionTimer.stop();
        recognizeFile();
    }

    private void selectionChanged() {
        if (suppressDebounce) {
            return;
        }
        recognized = null;
        if (recognitionWorker != null) {
            recognitionWorker.cancel(true);
            recognitionWorker = null;
        }
        infoArea.setText("Waiting for a .vcf or .vcf.gz file...");
        recognitionTimer.restart();
        notifyChange();
    }

    private void recognizeFile() {
        if (!active.get() || !interactionEnabled.get()) {
            return;
        }
        if (recognitionWorker != null) {
            recognitionWorker.cancel(true);
        }
        recognized = null;
        String text = filePathField.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        if (text.length() > 1 && text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1);
        }
        final File file = new File(text);
        infoArea.setText("Recognizing VCF header...");
        recognitionWorker = new SwingWorker<Recognized, Void>() {
            @Override protected Recognized doInBackground() throws Exception {
                FileInfo info = VcfImportService.inspect(file);
                VcfReferenceTable reference = resolveReference();
                VcfValidationService.Preflight preflight =
                    VcfValidationService.preflightDeclared(info.getDeclaredContigs(), reference);
                return new Recognized(file, info, reference, preflight);
            }

            @Override protected void done() {
                // An earlier slow read must never replace a newer file selection.
                if (recognitionWorker != this || isCancelled() || !active.get()) return;
                try {
                    Recognized result = get();
                    recognized = result;
                    infoArea.setText(buildRecognitionText(file, result));
                    infoArea.setCaretPosition(0);
                    notifyChange();
                } catch (CancellationException ignored) {
                    // Selection changed or the owning window closed.
                } catch (Exception ex) {
                    recognized = null;
                    infoArea.setText("Cannot use this file:\n" + errorCause(ex).getMessage());
                    notifyChange();
                }
            }
        };
        recognitionWorker.execute();
    }

    private VcfReferenceTable resolveReference() {
        VcfReferenceTable reference = cachedReference;
        if (reference == null) {
            reference = VcfReferenceTable.fromSpecies(species);
            cachedReference = reference;
        }
        return reference;
    }

    private String buildRecognitionText(File file, Recognized result) {
        FileInfo info = result.info;
        StringBuilder text = new StringBuilder();
        text.append("File: ").append(file.getName()).append('\n');
        text.append("Format: ").append(info.getVersion())
            .append(info.isCompressed() ? " (gzip compressed)" : " (plain text)").append('\n');
        text.append("Size: ").append(PopulationVcfSummarySupport.formatFileSize(info.getSize())).append('\n');
        text.append("Samples: ").append(info.getSampleCount());
        if (info.getSampleCount() > 0) {
            List<String> names = info.getSampleNames();
            int shown = Math.min(5, names.size());
            text.append(" (first ").append(shown).append(": ")
                .append(String.join(", ", names.subList(0, shown)))
                .append(names.size() > shown ? ", ..." : "").append(')');
        }
        text.append("\n\nReference check (").append(result.reference.isAvailable()
            ? result.reference.getSource() + ", " + result.reference.getSequenceCount() + " sequences"
            : "unavailable").append("):\n");
        text.append(result.preflight.getStatus()).append(" - ").append(result.preflight.getMessage()).append('\n');
        text.append('\n');
        if (result.preflight.isBlocked()) {
            text.append("This file does not match the selected genome, so it cannot be used.");
        } else {
            text.append(recognizedHint);
        }
        return text.toString();
    }

    private static Throwable errorCause(Exception exception) {
        return exception instanceof ExecutionException && exception.getCause() != null
            ? exception.getCause() : exception;
    }

    // ------------------------------------------------------------------ API

    /** The recognized file, or null while nothing valid is selected. */
    File getSelectedFile() {
        return recognized == null ? null : recognized.file;
    }

    Recognized getRecognized() {
        return recognized;
    }

    /** True when a file is recognized and its chromosomes match this genome. */
    boolean hasUsableFile() {
        return recognized != null && !recognized.preflight.isBlocked();
    }

    String getFilePathText() {
        return filePathField.getText();
    }

    void setStatusMessage(String message) {        infoArea.setText(message);
        infoArea.setCaretPosition(0);
    }

    /** Clears the drop field; used when another dataset is selected in a list. */
    void clearSelection() {
        interactionEnabled.set(true);
        recognitionTimer.stop();
        if (recognitionWorker != null) {
            recognitionWorker.cancel(true);
            recognitionWorker = null;
        }
        recognized = null;
        suppressDebounce = true;
        try {
            filePathField.setText("");
        } finally {
            suppressDebounce = false;
        }
        infoArea.setText(placeholderText);
        notifyChange();
    }

    /** Disables dropping, browsing and typing while the owner is busy. */
    void setInteractionEnabled(boolean enabled) {
        interactionEnabled.set(enabled);
        filePathField.setEnabled(enabled);
        browseButton.setEnabled(enabled);
    }

    void setChangeListener(Runnable changeListener) {
        this.changeListener = changeListener;
    }

    private void notifyChange() {
        if (changeListener != null) {
            changeListener.run();
        }
    }

    /** Stops the debounce timer and any running recognition. Called when the owner closes. */
    void disposePanel() {
        active.set(false);
        recognitionTimer.stop();
        if (recognitionWorker != null) {
            recognitionWorker.cancel(true);
            recognitionWorker = null;
        }
    }
}
