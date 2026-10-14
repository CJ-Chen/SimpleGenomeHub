package simplegenomehub.gui;

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

final class CreateGeneSetDialog extends JDialog {

    private static final Logger logger = Logger.getLogger(CreateGeneSetDialog.class.getName());
    private static final String REGION_SET_DEFAULT_OPTION = "Select a Region Set";
    private static final String REGION_SET_EMPTY_OPTION = "No Region Sets";

    private final SpeciesInfo species;
    private final Runnable onCreateSuccess;
    private final JTextField geneSetNameField;
    private final JTextArea geneIdsArea;
    private final JComboBox<String> regionSetComboBox;
    private final JLabel regionSetStatusLabel;
    private final JButton createButton;
    private final JButton noteButton;
    private boolean geneSetCreated;
    private boolean extractingGenes;
    private Map<String, SetNoteSupport.Note> pendingNotes = new LinkedHashMap<>();
    private List<File> availableRegionSetFiles = new ArrayList<>();

    CreateGeneSetDialog(Window parent, SpeciesInfo species, Runnable onCreateSuccess) {
        super(parent, "Create a Gene Set", ModalityType.MODELESS);
        this.species = species;
        this.onCreateSuccess = onCreateSuccess;
        this.geneSetNameField = new JTextField();
        this.geneIdsArea = new JTextArea(14, 48);
        this.regionSetComboBox = new JComboBox<>();
        this.regionSetStatusLabel = new JLabel(" ");
        this.createButton = new JButton("Create");
        this.noteButton = new JButton("Note");

        initializeComponents();
        setupLayout();
        setupEventHandlers();
        refreshRegionSetOptions();

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

        regionSetComboBox.setFont(SimpleGenomeHubStyle.FONT_SANS_PLAIN_12);
        regionSetComboBox.setToolTipText("Extract genes that are fully contained in an existing Region Set");
        regionSetStatusLabel.setFont(SimpleGenomeHubStyle.FONT_SANS_PLAIN_11);
        regionSetStatusLabel.setForeground(new Color(120, 128, 140));

        createButton.setEnabled(false);
        createButton.setBackground(SimpleGenomeHubStyle.DIALOG_PRIMARY_BUTTON);
        createButton.setForeground(SimpleGenomeHubStyle.DIALOG_PRIMARY_BUTTON_TEXT);
        createButton.setFocusPainted(false);
        createButton.setPreferredSize(new Dimension(120, 38));

        noteButton.setEnabled(false);
        noteButton.setBackground(SimpleGenomeHubStyle.DIALOG_SECONDARY_BUTTON);
        noteButton.setForeground(SimpleGenomeHubStyle.DIALOG_SECONDARY_BUTTON_TEXT);
        noteButton.setFocusPainted(false);
        noteButton.setPreferredSize(new Dimension(120, 38));
        noteButton.setToolTipText("Add a note and a card colour to the IDs above");
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

        JPanel regionSetPanel = new JPanel(new BorderLayout(0, 6));
        regionSetPanel.setOpaque(false);
        JLabel regionSetLabel = new JLabel("Extract Genes from Region Set");
        regionSetLabel.setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_12);
        regionSetPanel.add(regionSetLabel, BorderLayout.NORTH);
        regionSetPanel.add(regionSetComboBox, BorderLayout.CENTER);
        regionSetPanel.add(regionSetStatusLabel, BorderLayout.SOUTH);

        JPanel topPanel = new JPanel(new BorderLayout(0, 10));
        topPanel.setOpaque(false);
        topPanel.add(namePanel, BorderLayout.NORTH);
        topPanel.add(regionSetPanel, BorderLayout.CENTER);
        formPanel.add(topPanel, BorderLayout.NORTH);

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
        actionPanel.add(createButton);

        root.add(formPanel, BorderLayout.CENTER);
        root.add(actionPanel, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private void setupEventHandlers() {
        DocumentListener updateListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateCreateButtonState();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateCreateButtonState();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateCreateButtonState();
            }
        };

        geneSetNameField.getDocument().addDocumentListener(updateListener);
        geneIdsArea.getDocument().addDocumentListener(updateListener);
        createButton.addActionListener(e -> createGeneSet());
        noteButton.addActionListener(e -> editNotes());
        regionSetComboBox.addActionListener(e -> {
            if (!regionSetComboBox.isEnabled()) {
                return;
            }
            extractGenesFromSelectedRegionSet();
        });
    }

    private void refreshRegionSetOptions() {
        availableRegionSetFiles = GeneSetImportSupport.loadAvailableRegionSetFiles(species);

        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement(availableRegionSetFiles.isEmpty() ? REGION_SET_EMPTY_OPTION : REGION_SET_DEFAULT_OPTION);
        for (File regionSetFile : availableRegionSetFiles) {
            model.addElement(GeneSetFileSupport.extractDisplayName(regionSetFile));
        }

        regionSetComboBox.setModel(model);
        regionSetComboBox.setSelectedIndex(0);
        regionSetComboBox.setEnabled(!availableRegionSetFiles.isEmpty() && !extractingGenes);
        regionSetStatusLabel.setText(" ");
    }

    private void extractGenesFromSelectedRegionSet() {
        int selectedIndex = regionSetComboBox.getSelectedIndex();
        if (selectedIndex <= 0) {
            return;
        }

        int fileIndex = selectedIndex - 1;
        if (fileIndex < 0 || fileIndex >= availableRegionSetFiles.size()) {
            regionSetComboBox.setSelectedIndex(0);
            return;
        }

        File selectedRegionSetFile = availableRegionSetFiles.get(fileIndex);
        String displayName = GeneSetFileSupport.extractDisplayName(selectedRegionSetFile);
        extractingGenes = true;
        regionSetComboBox.setEnabled(false);
        regionSetStatusLabel.setText("Extracting genes from " + displayName + "...");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        SwingWorker<List<String>, Void> worker = new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                return GeneSetImportSupport.collectContainedGeneIds(species, selectedRegionSetFile);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                extractingGenes = false;
                regionSetComboBox.setEnabled(!availableRegionSetFiles.isEmpty());

                try {
                    List<String> containedGeneIds = get();
                    if (containedGeneIds.isEmpty()) {
                        regionSetStatusLabel.setText("No fully contained genes found.");
                        JOptionPane.showMessageDialog(CreateGeneSetDialog.this,
                            "No genes are fully contained in the selected Region Set.",
                            "No Genes Found",
                            JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }

                    if (!geneIdsArea.getText().trim().isEmpty()) {
                        int choice = JOptionPane.showConfirmDialog(CreateGeneSetDialog.this,
                            "Replace the current gene IDs with " + containedGeneIds.size()
                                + " gene(s) fully contained in '" + displayName + "'?",
                            "Replace Gene IDs",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE);
                        if (choice != JOptionPane.YES_OPTION) {
                            regionSetStatusLabel.setText("Extraction cancelled.");
                            return;
                        }
                    }

                    geneIdsArea.setText(String.join("\n", containedGeneIds));
                    geneIdsArea.setCaretPosition(0);
                    regionSetStatusLabel.setText("Extracted " + containedGeneIds.size()
                        + " gene(s) fully contained in '" + displayName + "'.");
                } catch (Exception ex) {
                    Throwable cause = ex instanceof java.util.concurrent.ExecutionException && ex.getCause() != null
                        ? ex.getCause()
                        : ex;
                    String message = cause.getMessage() != null ? cause.getMessage() : "Unknown error";
                    regionSetStatusLabel.setText("Extraction failed.");
                    JOptionPane.showMessageDialog(CreateGeneSetDialog.this,
                        "Failed to extract genes from the selected Region Set:\n" + message,
                        "Extraction Failed",
                        JOptionPane.ERROR_MESSAGE);
                } finally {
                    regionSetComboBox.setSelectedIndex(0);
                    updateCreateButtonState();
                }
            }
        };

        worker.execute();
    }

    private void updateCreateButtonState() {
        boolean hasName = !geneSetNameField.getText().trim().isEmpty();
        boolean hasGeneIds = !geneIdsArea.getText().trim().isEmpty();
        createButton.setEnabled(hasName && hasGeneIds);
        noteButton.setEnabled(hasGeneIds);
    }

    /** Opens the note editor for the ids currently typed; no id is validated here. */
    private void editNotes() {
        List<String> geneIds = parseGeneIds(geneIdsArea.getText());
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

    private void createGeneSet() {
        String geneSetName = geneSetNameField.getText().trim();
        List<String> geneIds = parseGeneIds(geneIdsArea.getText());

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
        if (geneSetDir == null && !species.createDirectoryStructure()) {
            JOptionPane.showMessageDialog(this,
                "Failed to initialize GeneSet directory.",
                "Directory Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        geneSetDir = species.getGeneSetDir();
        if (geneSetDir == null) {
            JOptionPane.showMessageDialog(this,
                "GeneSet directory is not available.",
                "Directory Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (!geneSetDir.exists() && !geneSetDir.mkdirs()) {
            JOptionPane.showMessageDialog(this,
                "Failed to create GeneSet directory:\n" + geneSetDir.getAbsolutePath(),
                "Directory Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        File outputFile = new File(geneSetDir,
            GeneSetFileSupport.buildStandardFileName(geneSetName, GeneSetFileSupport.SetKind.GENE));
        if (outputFile.exists()) {
            JOptionPane.showMessageDialog(this,
                "Gene Set Name conflict.",
                "Gene Set Name Conflict",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        setControlsEnabled(false);

        SwingWorker<GeneSetGeneIdValidationSupport.ValidationResult, Void> worker =
            new SwingWorker<GeneSetGeneIdValidationSupport.ValidationResult, Void>() {
            @Override
            protected GeneSetGeneIdValidationSupport.ValidationResult doInBackground() throws Exception {
                return GeneSetGeneIdValidationSupport.validateGeneIdsFailFast(species, geneIds);
            }

            @Override
            protected void done() {
                if (!CreateGeneSetDialog.this.isDisplayable()) {
                    return;
                }

                try {
                    GeneSetGeneIdValidationSupport.ValidationResult validationResult = get();
                    if (validationResult.hasIssues()) {
                        GeneSetGeneIdValidationSupport.showValidationResult(
                            CreateGeneSetDialog.this, validationResult);
                    }

                    if (!validationResult.getMissingGeneIds().isEmpty()) {
                        return;
                    }

                    GeneSetFileSupport.writeGeneSetEntries(outputFile, validationResult.getResolvedEntries());

                    List<String> savedIds = new ArrayList<>();
                    for (GeneSetSupport.Entry entry : validationResult.getResolvedEntries()) {
                        savedIds.add(entry.getId());
                    }
                    SetNoteUiSupport.writeNotes(outputFile, savedIds, SetNoteUiSupport.alignToSavedIds(
                        geneIds, validationResult.getResolvedIdsByInputId(), pendingNotes, savedIds));

                    geneSetCreated = true;
                    JOptionPane.showMessageDialog(CreateGeneSetDialog.this,
                        "Gene set created successfully:\n" + outputFile.getName(),
                        "Create Complete",
                        JOptionPane.INFORMATION_MESSAGE);
                    dispose();
                    if (onCreateSuccess != null) {
                        onCreateSuccess.run();
                    }
                } catch (Exception ex) {
                    logger.warning("Failed to create gene set: " + ex.getMessage());
                    JOptionPane.showMessageDialog(CreateGeneSetDialog.this,
                        "Failed to create gene set:\n" + ex.getMessage(),
                        "Create Failed",
                        JOptionPane.ERROR_MESSAGE);
                } finally {
                    setControlsEnabled(true);
                }
            }
        };

        worker.execute();
    }

    private void setControlsEnabled(boolean enabled) {
        geneSetNameField.setEnabled(enabled);
        geneIdsArea.setEnabled(enabled);
        regionSetComboBox.setEnabled(enabled && !availableRegionSetFiles.isEmpty() && !extractingGenes);
        createButton.setEnabled(enabled && !geneSetNameField.getText().trim().isEmpty()
            && !geneIdsArea.getText().trim().isEmpty());
        noteButton.setEnabled(enabled && !geneIdsArea.getText().trim().isEmpty());
    }

    private List<String> parseGeneIds(String rawText) {
        return GeneSetFileSupport.parseGeneIds(rawText);
    }

    boolean isGeneSetCreated() {
        return geneSetCreated;
    }
}
