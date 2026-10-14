package simplegenomehub.gui;

import simplegenomehub.runtime.HubWorker;

import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.util.fileio.GeneSetSupport;
import simplegenomehub.util.fileio.SetNoteSupport;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

final class EditGeneSetDialog extends JDialog {

    private static final Logger logger = Logger.getLogger(EditGeneSetDialog.class.getName());

    private final SpeciesInfo species;
    private final File originalFile;
    private final Runnable onUpdateSuccess;
    private final JTextField geneSetNameField;
    private final JTextArea geneIdsArea;
    private final JButton saveButton;
    private final JButton noteButton;
    private volatile boolean geneSetUpdated;
    private Map<String, SetNoteSupport.Note> pendingNotes = new LinkedHashMap<>();

    EditGeneSetDialog(Window parent, SpeciesInfo species, File geneSetFile, Runnable onUpdateSuccess) {
        super(parent, "Edit Gene Set", ModalityType.MODELESS);
        simplegenomehub.runtime.HubSession.attach(this, simplegenomehub.runtime.HubSession.forComponent(parent));
        this.species = species;
        simplegenomehub.runtime.HubSession.attach(this, simplegenomehub.runtime.HubSession.forDataPath(species.getSpeciesDir()));
        this.originalFile = geneSetFile;
        this.onUpdateSuccess = onUpdateSuccess;
        this.geneSetNameField = new JTextField();
        this.geneIdsArea = new JTextArea(14, 48);
        this.saveButton = new JButton("Save");
        this.noteButton = new JButton("Note");

        initializeComponents();
        setupLayout();
        setupEventHandlers();
        loadExistingContent();
        pendingNotes = new LinkedHashMap<>(SetNoteSupport.read(geneSetFile));

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(640, 520);
        setMinimumSize(new Dimension(560, 460));
        setLocationRelativeTo(parent);
    }

    private void initializeComponents() {
        geneSetNameField.setFont(SimpleGenomeHubStyle.FONT_SANS_PLAIN_12);
        geneSetNameField.setPreferredSize(SimpleGenomeHubStyle.SIZE_FIELD_260_X_30);

        geneIdsArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        geneIdsArea.setLineWrap(false);
        geneIdsArea.setWrapStyleWord(false);
        geneIdsArea.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(SimpleGenomeHubUi.CARD_BORDER, 1),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        saveButton.setEnabled(false);
        saveButton.setBackground(SimpleGenomeHubStyle.DIALOG_PRIMARY_BUTTON);
        saveButton.setForeground(SimpleGenomeHubStyle.DIALOG_PRIMARY_BUTTON_TEXT);
        saveButton.setFocusPainted(false);
        saveButton.setPreferredSize(new Dimension(120, 38));

        noteButton.setEnabled(false);
        noteButton.setBackground(SimpleGenomeHubStyle.DIALOG_SECONDARY_BUTTON);
        noteButton.setForeground(SimpleGenomeHubStyle.DIALOG_SECONDARY_BUTTON_TEXT);
        noteButton.setFocusPainted(false);
        noteButton.setPreferredSize(new Dimension(120, 38));
        noteButton.setToolTipText("Edit the note and the card colour of each ID");
    }

    private void setupLayout() {
        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        root.setBackground(SimpleGenomeHubUi.DIALOG_BACKGROUND);

        JPanel formPanel = new JPanel(new BorderLayout(0, 10));
        formPanel.setOpaque(false);

        JPanel namePanel = new JPanel(new BorderLayout(0, 6));
        namePanel.setOpaque(false);
        JLabel nameLabel = new JLabel("Gene Set Name");
        nameLabel.setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_12);
        namePanel.add(nameLabel, BorderLayout.NORTH);
        namePanel.add(geneSetNameField, BorderLayout.CENTER);
        formPanel.add(namePanel, BorderLayout.NORTH);

        JPanel geneIdPanel = new JPanel(new BorderLayout(0, 6));
        geneIdPanel.setOpaque(false);
        JLabel geneIdLabel = new JLabel("Gene / mRNA / Transcript ID");
        geneIdLabel.setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_12);
        geneIdPanel.add(geneIdLabel, BorderLayout.NORTH);
        geneIdPanel.add(new JScrollPane(geneIdsArea), BorderLayout.CENTER);
        formPanel.add(geneIdPanel, BorderLayout.CENTER);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        actionPanel.setOpaque(false);
        actionPanel.add(noteButton);
        actionPanel.add(saveButton);

        root.add(formPanel, BorderLayout.CENTER);
        root.add(actionPanel, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private void setupEventHandlers() {
        DocumentListener updateListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateSaveButtonState();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateSaveButtonState();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateSaveButtonState();
            }
        };

        geneSetNameField.getDocument().addDocumentListener(updateListener);
        geneIdsArea.getDocument().addDocumentListener(updateListener);
        saveButton.addActionListener(e -> saveGeneSet());
        noteButton.addActionListener(e -> editNotes());
    }

    private void loadExistingContent() {
        if (originalFile == null) {
            return;
        }

        String fileName = originalFile.getName();
        geneSetNameField.setText(GeneSetFileSupport.extractDisplayName(fileName));

        try {
            // IDs only: coordinates are re-resolved on save, so the editor stays clean
            geneIdsArea.setText(GeneSetFileSupport.formatGeneIds(
                GeneSetFileSupport.parseGeneIds(GeneSetFileSupport.readGeneSetContent(originalFile))));
            geneIdsArea.setCaretPosition(0);
        } catch (Exception ex) {
            logger.warning("Failed to read gene set file: " + ex.getMessage());
            JOptionPane.showMessageDialog(this,
                "Failed to load gene set file:\n" + ex.getMessage(),
                "Load Failed",
                JOptionPane.ERROR_MESSAGE);
        }

        updateSaveButtonState();
    }

    private void updateSaveButtonState() {
        boolean hasName = !geneSetNameField.getText().trim().isEmpty();
        boolean hasGeneIds = !geneIdsArea.getText().trim().isEmpty();
        saveButton.setEnabled(hasName && hasGeneIds);
        noteButton.setEnabled(hasGeneIds);
    }

    /** Opens the note editor for the ids currently typed; no id is validated here. */
    private void editNotes() {
        List<String> geneIds = GeneSetFileSupport.parseGeneIds(geneIdsArea.getText());
        if (geneIds.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Enter at least one ID first.",
                "No IDs",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String geneSetName = geneSetNameField.getText().trim();
        Map<String, SetNoteSupport.Note> editedNotes = SetNoteUiSupport.editNotes(
            this,
            "Notes - " + (geneSetName.isEmpty() ? "Gene Set" : geneSetName),
            geneIds,
            pendingNotes
        );
        if (editedNotes != null) {
            pendingNotes = editedNotes;
        }
    }

    private void saveGeneSet() {
        String geneSetName = geneSetNameField.getText().trim();
        List<String> geneIds = GeneSetFileSupport.parseGeneIds(geneIdsArea.getText());

        if (geneSetName.isEmpty() || geneIds.isEmpty()) {
            return;
        }

        if (GeneSetFileSupport.containsInvalidFileNameChars(geneSetName)) {
            JOptionPane.showMessageDialog(this,
                "Gene Set Name contains invalid filename characters.",
                "Invalid Gene Set Name",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        File geneSetDir = species.getGeneSetDir();
        if (geneSetDir == null || !geneSetDir.exists()) {
            JOptionPane.showMessageDialog(this,
                "GeneSet directory is not available.",
                "Directory Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        File targetFile = new File(geneSetDir,
            GeneSetFileSupport.buildStandardFileName(geneSetName, GeneSetFileSupport.SetKind.GENE));
        if (!targetFile.equals(originalFile) && targetFile.exists()) {
            JOptionPane.showMessageDialog(this,
                "Gene Set Name conflict.",
                "Gene Set Name Conflict",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        final Map<String, SetNoteSupport.Note> notesSnapshot = new LinkedHashMap<>(pendingNotes);
        setControlsEnabled(false);

        HubWorker<GeneSetGeneIdValidationSupport.ValidationResult, Void> worker =
            new HubWorker<GeneSetGeneIdValidationSupport.ValidationResult, Void>() {
            @Override
            protected GeneSetGeneIdValidationSupport.ValidationResult doInBackground() throws Exception {
                GeneSetGeneIdValidationSupport.ValidationResult validationResult =
                    GeneSetGeneIdValidationSupport.validateGeneIds(species, geneIds);
                if (validationResult.getMissingGeneIds().isEmpty()) {
                    if (isCancelled()) throw new java.util.concurrent.CancellationException();
                    GeneSetFileSupport.writeGeneSetAndNotes(targetFile, validationResult.getResolvedEntries(), geneIds,
                        validationResult.getResolvedIdsByInputId(), notesSnapshot, originalFile);
                    geneSetUpdated = true;
                }
                return validationResult;
            }

            @Override
            protected void done() {
                try {
                    if (!isPresentationCurrent()) return;
                    if (!EditGeneSetDialog.this.isDisplayable()) {
                        return;
                    }

                    try {
                        GeneSetGeneIdValidationSupport.ValidationResult validationResult = get();
                        if (validationResult.hasIssues()) {
                            GeneSetGeneIdValidationSupport.showValidationResult(
                                EditGeneSetDialog.this, validationResult);
                        }

                        if (!validationResult.getMissingGeneIds().isEmpty()) {
                            return;
                        }

                        // The background task committed the validated file before completing.

                        geneSetUpdated = true;
                        JOptionPane.showMessageDialog(EditGeneSetDialog.this,
                            "Gene set updated successfully:\n" + targetFile.getName(),
                            "Save Complete",
                            JOptionPane.INFORMATION_MESSAGE);
                        dispose();
                        if (onUpdateSuccess != null && isPresentationCurrent()) {
                            onUpdateSuccess.run();
                        }
                    } catch (Exception ex) {
                        logger.warning("Failed to update gene set: " + ex.getMessage());
                        JOptionPane.showMessageDialog(EditGeneSetDialog.this,
                            "Failed to update gene set:\n" + ex.getMessage(),
                            "Save Failed",
                            JOptionPane.ERROR_MESSAGE);
                    }
                } finally {
                    setControlsEnabled(isPresentationCurrent());
                }
            }
        };

        simplegenomehub.runtime.HubSession.execute(this, worker);
    }

    private void setControlsEnabled(boolean enabled) {
        geneSetNameField.setEnabled(enabled);
        geneIdsArea.setEnabled(enabled);
        saveButton.setEnabled(enabled
            && !geneSetNameField.getText().trim().isEmpty()
            && !geneIdsArea.getText().trim().isEmpty());
        noteButton.setEnabled(enabled && !geneIdsArea.getText().trim().isEmpty());
    }

    boolean isGeneSetUpdated() {
        return geneSetUpdated;
    }
}
