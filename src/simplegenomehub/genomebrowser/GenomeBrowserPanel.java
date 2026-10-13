package simplegenomehub.genomebrowser;

import simplegenomehub.blast.BlastConfig;
import simplegenomehub.gui.BlastDialog;
import simplegenomehub.gui.GeneInfoViewerDialog;
import simplegenomehub.gui.SimpleGenomeHubUi;
import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.model.SpeciesManager;
import simplegenomehub.util.fileio.GeneSetSupport;
import simplegenomehub.util.fileio.RegionSetSupport;
import simplegenomehub.util.fileio.SetNoteSupport;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 * Reusable genome-browser body: a single-row toolbar plus the lazily loaded
 * {@link GenomeBrowserCanvas}. Hosted by {@link GenomeBrowserFrame} and by the
 * FST result window's lower split pane.
 */
public final class GenomeBrowserPanel extends JPanel {

    private static final Logger logger = Logger.getLogger(GenomeBrowserPanel.class.getName());

    private static final int COMPONENT_HEIGHT = 28;

    private final File fastaFile;
    private final File gffFile;

    /** Genome unit the files belong to; enables the BLAST and gene-info entries. May be null. */
    private final SpeciesInfo speciesContext;
    private final SpeciesManager speciesManager;

    private final JPanel controlsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
    private final JComboBox<String> chromosomeCombo = new JComboBox<>();
    private final JTextField positionField = new JTextField(10);
    private final JComboBox<String> strandCombo = new JComboBox<>(new String[]{"Both", "Forward", "Reverse"});
    private final JComboBox<String> codonCombo = new JComboBox<>(CodonTable.NAMES);
    private final JButton aminoButton = new JButton("aa");
    private final JButton wrapButton = new JButton("Wrap");
    private final JLabel coordinateLabel = new JLabel("Mouse: -");
    private final JLabel statusLabel = new JLabel("Loading genome and annotation...");

    private GenomeBrowserData data;
    private BrowserViewState state;
    private GenomeBrowserCanvas canvas;
    private GeneSearchField searchField;
    private boolean updatingControls;
    private boolean ready;
    private Runnable onViewChanged;
    private String pendingChromosome;
    private int pendingStart = -1;
    private int pendingEnd = -1;
    private String pendingHighlightChromosome;
    private long pendingHighlightStart = -1;
    private long pendingHighlightEnd = -1;
    private SetCardDialog geneSetDialog;
    private SetCardDialog positionSetDialog;

    public GenomeBrowserPanel(File fastaFile, File gffFile) {
        this(fastaFile, gffFile, null, null);
    }

    /**
     * @param speciesContext the genome unit those files belong to; the BLAST and gene-info menu
     *                       entries need it and stay disabled while it is null
     * @param speciesManager the manager those entries open their windows with; may be null too
     */
    public GenomeBrowserPanel(File fastaFile, File gffFile, SpeciesInfo speciesContext,
                              SpeciesManager speciesManager) {
        super(new BorderLayout());
        this.fastaFile = fastaFile;
        this.gffFile = gffFile;
        this.speciesContext = speciesContext;
        this.speciesManager = speciesManager;
        setBackground(Color.WHITE);
        buildToolBar();
        statusLabel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        add(statusLabel, BorderLayout.CENTER);
        loadData();
    }

    public boolean isReady() { return ready; }

    public String currentChromosome() { return state == null ? null : state.chromosome(); }

    public GenomeBrowserCanvas canvas() { return canvas; }

    public void setOnViewChanged(Runnable listener) { this.onViewChanged = listener; }

    public int[] visibleRange() {
        if (state == null || state.length() == 0 || canvas == null) return null;
        long from = state.normalized((long) Math.floor(state.start())) + 1;
        long last = state.normalized((long) Math.ceil(state.end(stateWidth())) - 1) + 1;
        return new int[]{(int) from, (int) last};
    }

    /** Jumps to (and switches to) a chromosome range; safe to call before loading completes. */
    public void setPosition(String chromosome, int start, int end) {
        if (!ready) {
            pendingChromosome = chromosome;
            pendingStart = start;
            pendingEnd = end;
            return;
        }
        if (chromosome != null && !chromosome.equals(state.chromosome())) {
            selectChromosome(chromosome);
        }
        canvas.showRange(Math.max(0, start - 1), Math.max(start, end));
        refreshState();
    }

    /** Marks the FST window selected in the result browser (1-based inclusive sequence range). */
    public void setHighlightRegion(String chromosome, long start, long end) {
        if (!ready) {
            pendingHighlightChromosome = chromosome;
            pendingHighlightStart = start;
            pendingHighlightEnd = end;
            return;
        }
        canvas.setHighlightRegion(chromosome, start, end);
    }

    public void close() {
        if (data != null) data.close();
    }

    private void buildToolBar() {
        controlsRow.setOpaque(false);

        controlsRow.add(new JLabel("Chromosome:"));
        chromosomeCombo.setPrototypeDisplayValue("scaffold_000000");
        fixSize(chromosomeCombo, 130);
        chromosomeCombo.addActionListener(e -> {
            if (updatingControls || canvas == null) return;
            Object selected = chromosomeCombo.getSelectedItem();
            if (selected != null) {
                canvas.showChromosome((String) selected);
                refreshState();
            }
        });
        controlsRow.add(chromosomeCombo);

        controlsRow.add(new JLabel("Position:"));
        fixTextSize(positionField, 20);
        positionField.setToolTipText("Enter a position (1,200) or a range (1,200-3,400)");
        positionField.addActionListener(e -> goToPosition());
        controlsRow.add(positionField);
        JButton go = new JButton("Go");
        fixSize(go, 60);
        go.addActionListener(e -> goToPosition());
        controlsRow.add(go);
        controlsRow.add(separator());

        controlsRow.add(new JLabel("Strand:"));
        fixSize(strandCombo, 90);
        strandCombo.addActionListener(e -> {
            if (state == null) return;
            switch (Math.max(0, strandCombo.getSelectedIndex())) {
                case 1: state.setStrandMode(BrowserViewState.StrandMode.FORWARD); break;
                case 2: state.setStrandMode(BrowserViewState.StrandMode.REVERSE); break;
                default: state.setStrandMode(BrowserViewState.StrandMode.BOTH); break;
            }
        });
        controlsRow.add(strandCombo);
        controlsRow.add(separator());

        aminoButton.setToolTipText("Show or hide the amino-acid rows");
        fixSize(aminoButton, 56);
        aminoButton.addActionListener(e -> {
            if (canvas != null) canvas.setShowAminoAcids(!canvas.isShowAminoAcids());
        });
        controlsRow.add(aminoButton);

        controlsRow.add(separator());
        fixSize(wrapButton, 72);
        wrapButton.addActionListener(e -> {
            if (canvas == null) return;
            canvas.setWrapped(!canvas.isWrapped());
            refreshWrapButton();
        });
        refreshWrapButton();
        controlsRow.add(wrapButton);

        controlsRow.add(new JLabel("Codon table:"));
        codonCombo.setPrototypeDisplayValue("11 Bacterial");
        fixSize(codonCombo, 150);
        codonCombo.setSelectedIndex(0);
        codonCombo.addActionListener(e -> {
            if (state == null) return;
            int index = Math.max(0, codonCombo.getSelectedIndex());
            if (index < CodonTable.NUMBERS.length) state.setTranslationTable(CodonTable.NUMBERS[index]);
        });
        controlsRow.add(codonCombo);

        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, BrowserPalette.BORDER),
            BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        bar.add(controlsRow, BorderLayout.CENTER);
        bar.add(coordinateLabel, BorderLayout.EAST);
        add(bar, BorderLayout.NORTH);
        setControlsEnabled(false);
    }

    private static JComponent separator() {
        JLabel line = new JLabel();
        line.setOpaque(true);
        line.setBackground(new Color(198, 205, 210));
        line.setPreferredSize(new Dimension(1, 20));
        line.setMaximumSize(new Dimension(1, 20));
        return line;
    }

    private static void fixSize(javax.swing.JComponent component, int width) {
        Dimension size = new Dimension(width, COMPONENT_HEIGHT);
        component.setPreferredSize(size);
        component.setMaximumSize(size);
    }

    /**
     * JTextField 的可见宽度只认 columns（setPreferredSize 的宽度会被忽略），
     * 所以宽度用 columns 指定、高度单独统一。
     */
    private static void fixTextSize(JTextField field, int columns) {
        field.setColumns(columns);
        Dimension size = new Dimension(field.getPreferredSize().width, COMPONENT_HEIGHT);
        field.setPreferredSize(size);
        field.setMaximumSize(size);
    }

    private void setControlsEnabled(boolean enabled) {
        chromosomeCombo.setEnabled(enabled);
        positionField.setEnabled(enabled);
        strandCombo.setEnabled(enabled);
        codonCombo.setEnabled(enabled);
        aminoButton.setEnabled(enabled);
        wrapButton.setEnabled(enabled);
    }

    /**
     * 折行按钮只是个普通 JButton（外观与 Go 完全一致），
     * 所以把状态写进文字里：线性时显示 Wrap，折行时显示 Unwrap。
     */
    private void refreshWrapButton() {
        boolean isWrapped = canvas != null && canvas.isWrapped();
        wrapButton.setText(isWrapped ? "Unwrap" : "Wrap");
        wrapButton.setToolTipText(isWrapped
            ? "Back to the single-row (linear) view"
            : "Wrap the whole chromosome into rows and scroll vertically");
    }

    private void loadData() {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<GenomeBrowserData, Void>() {
            @Override protected GenomeBrowserData doInBackground() throws Exception {
                return fastaFile == null ? null : new GenomeBrowserData(fastaFile, gffFile);
            }

            @Override protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    data = get();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    statusLabel.setText("<html>Failed to open the genome:<br>" + cause.getMessage() + "</html>");
                    return;
                }
                if (data == null || data.chromosomes().isEmpty()) {
                    statusLabel.setText("No genome FASTA sequence is available for this result.");
                    return;
                }
                installBrowser();
            }
        }.execute();
    }

    private void installBrowser() {
        state = new BrowserViewState();
        canvas = new GenomeBrowserCanvas(state, data);
        canvas.setCoordinateListener(position ->
            coordinateLabel.setText("Mouse: " + (position < 0 ? "-" : formatPosition(position))));
        canvas.setChangeListener(this::refreshState);
        remove(statusLabel);
        add(canvas, BorderLayout.CENTER);

        searchField = new GeneSearchField(data.annotation(), this::selectLocusFromSearch);
        canvas.setContextMenuHandler(this::showBrowserContextMenu);
        fixTextSize(searchField, 16);
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        searchPanel.setOpaque(false);
        searchPanel.add(new JLabel("Gene:"));
        searchPanel.add(searchField);
        controlsRow.add(searchPanel);
        controlsRow.revalidate();

        updatingControls = true;
        for (String chromosome : data.chromosomes()) chromosomeCombo.addItem(chromosome);
        updatingControls = false;
        setControlsEnabled(true);
        if (chromosomeCombo.getItemCount() > 0) {
            chromosomeCombo.setSelectedIndex(0);
            canvas.showChromosome((String) chromosomeCombo.getSelectedItem());
        }
        ready = true;
        revalidate();
        repaint();
        SwingUtilities.invokeLater(() -> {
            refreshState();
            if (pendingHighlightStart >= 0) {
                String chromosome = pendingHighlightChromosome;
                long start = pendingHighlightStart, end = pendingHighlightEnd;
                pendingHighlightStart = -1;
                canvas.setHighlightRegion(chromosome, start, end);
            }
            if (pendingStart >= 0) {
                int start = pendingStart, end = pendingEnd;
                String chromosome = pendingChromosome;
                pendingStart = -1;
                setPosition(chromosome, start, end);
            }
        });
    }

    private void selectChromosome(String chromosome) {
        updatingControls = true;
        chromosomeCombo.setSelectedItem(chromosome);
        updatingControls = false;
        canvas.showChromosome(chromosome);
    }

    private void goToPosition() {
        if (canvas == null || state == null || state.length() == 0) return;
        String text = positionField.getText().trim().replace(",", "");
        if (text.isEmpty()) return;
        try {
            String normalized = text.replace("..", "-");
            int separator = normalized.indexOf('-');
            if (separator > 0) {
                long from = Math.max(1, Long.parseLong(normalized.substring(0, separator).trim()));
                long to = Math.max(from, Long.parseLong(normalized.substring(separator + 1).trim()));
                canvas.showRange(from - 1, to);
            } else {
                canvas.jumpTo(Long.parseLong(normalized) - 1);
            }
            refreshState();
        } catch (NumberFormatException ex) {
            java.awt.Toolkit.getDefaultToolkit().beep();
        }
    }

    // ------------------------------------------------------- gene / position sets

    /**
     * Shared entry point for "show this gene", used by the gene search box and by the
     * gene-set cards so both behave identically (switch chromosome, select, centre).
     */
    private void selectLocusFromSearch(AnnotationIndex.Locus locus) {
        if (locus == null || canvas == null || state == null) return;
        if (locus.chromosome != null && !locus.chromosome.equals(state.chromosome())) {
            selectChromosome(locus.chromosome);
        }
        canvas.selectLocus(locus);
        if (searchField != null) {
            searchField.setTextQuietly(locus.id == null ? locus.displayName() : locus.id);
        }
        refreshState();
    }

    /** Looks an id up with the browser's own gene search. */
    private boolean goToGene(String id) {
        if (id == null || id.trim().isEmpty() || data == null) return false;
        List<AnnotationIndex.Locus> matches = data.annotation().search(id.trim());
        if (matches.isEmpty()) return false;
        selectLocusFromSearch(matches.get(0));
        return true;
    }

    /**
     * Centres the view on a 1-based inclusive range and marks it on the ruler exactly like
     * the FST window does. The zoom is deliberately left untouched.
     */
    private boolean jumpToPosition(String chromosome, long start, long end) {
        if (chromosome == null || chromosome.trim().isEmpty() || canvas == null || state == null) {
            return false;
        }
        String name = chromosome.trim();
        String canonical = null;
        for (String candidate : data.chromosomes()) {
            if (candidate.equalsIgnoreCase(name)) {
                canonical = candidate;
                break;
            }
        }
        if (canonical == null) return false;
        if (!canonical.equals(state.chromosome())) selectChromosome(canonical);
        long from = Math.max(1, Math.min(start, end));
        long to = Math.max(from, Math.max(start, end));
        canvas.jumpTo(Math.round((from - 1 + to) / 2.0));
        setHighlightRegion(canonical, from, to);
        refreshState();
        return true;
    }

    private File geneSetDirectory() {
        File[] candidates = {
            speciesDirectoryOf(gffFile),
            speciesDirectoryOf(fastaFile)
        };
        for (File speciesDir : candidates) {
            if (speciesDir == null) continue;
            File geneSetDir = new File(speciesDir, "GeneSet");
            if (geneSetDir.isDirectory()) return geneSetDir;
        }
        return null;
    }

    /** {@code <species>/Annotation/x.gff3} -> {@code <species>}. */
    private static File speciesDirectoryOf(File genomeOrAnnotationFile) {
        if (genomeOrAnnotationFile == null) return null;
        File parent = genomeOrAnnotationFile.getParentFile();
        return parent == null ? null : parent.getParentFile();
    }

    private void showBrowserContextMenu(java.awt.event.MouseEvent event) {
        if (canvas == null) return;
        // The menu is built from what is selected, never from where the right click landed:
        // a boxed range keeps its copy entries, a picked gene keeps its gene entries.
        JPopupMenu menu = new JPopupMenu();
        if (canvas.hasSelection()) {
            menu.add(copyItem("Copy forward DNA", false, false));
            menu.add(copyItem("Copy reverse DNA", true, false));
            menu.addSeparator();
            menu.add(copyItem("Copy forward amino acids", false, true));
            menu.add(copyItem("Copy reverse amino acids", true, true));
            menu.addSeparator();
        }

        menu.add(blastItem());
        menu.add(geneInfoItem());
        menu.addSeparator();

        File geneSetDir = geneSetDirectory();
        JMenu geneMenu = new JMenu("Open gene set");
        addSetItems(geneMenu, geneSetDir == null ? new ArrayList<>() : GeneSetSupport.listGeneSetFiles(geneSetDir),
            "(no gene sets found)", this::openGeneSet);
        JMenu positionMenu = new JMenu("Open position set");
        addSetItems(positionMenu, geneSetDir == null ? new ArrayList<>() : RegionSetSupport.listRegionSetFiles(geneSetDir),
            "(no position sets found)", this::openPositionSet);
        menu.add(geneMenu);
        menu.add(positionMenu);

        SimpleGenomeHubUi.stylePopupMenu(menu);
        menu.show(event.getComponent(), event.getX(), event.getY());
    }

    private void addSetItems(JMenu menu, List<File> files, String emptyText, java.util.function.Consumer<File> action) {
        if (files.isEmpty()) {
            JMenuItem empty = new JMenuItem(emptyText);
            empty.setEnabled(false);
            menu.add(empty);
            return;
        }
        for (File file : files) {
            JMenuItem item = new JMenuItem(setDisplayName(file));
            item.addActionListener(e -> action.accept(file));
            menu.add(item);
        }
    }

    private static String setDisplayName(File file) {
        return GeneSetSupport.isGeneSetFileName(file.getName())
            ? GeneSetSupport.extractDisplayName(file)
            : RegionSetSupport.extractDisplayName(file);
    }

    /**
     * Runs BLAST on whatever is selected: the boxed sequence range, or the picked gene's spliced
     * transcript when only a gene is picked.
     */
    private JMenuItem blastItem() {
        JMenuItem item = new JMenuItem("Run BLAST");
        boolean hasTarget = canvas.hasSelection() || canvas.selectedLocus() != null;
        boolean hasGenomeUnit = speciesContext != null && speciesManager != null;
        item.setEnabled(hasTarget && hasGenomeUnit);
        item.setToolTipText(!hasGenomeUnit
            ? "Open the browser from a genome unit to run BLAST"
            : hasTarget ? "BLAST the selected sequence or gene" : "Select a sequence range or a gene first");
        item.addActionListener(e -> runBlastOnSelection());
        return item;
    }

    /** Opens the gene information of the picked gene; grey while no gene is picked. */
    private JMenuItem geneInfoItem() {
        JMenuItem item = new JMenuItem("Open Gene Info");
        boolean hasGene = canvas.selectedLocus() != null;
        boolean hasGenomeUnit = speciesContext != null && speciesManager != null;
        item.setEnabled(hasGene && hasGenomeUnit);
        item.setToolTipText(!hasGenomeUnit
            ? "Open the browser from a genome unit to open the gene information"
            : hasGene ? "Open the gene information of the picked gene" : "Pick a gene first");
        item.addActionListener(e -> openSelectedGeneInfo());
        return item;
    }

    private void runBlastOnSelection() {
        if (canvas == null || speciesContext == null || speciesManager == null) return;
        boolean hasRange = canvas.hasSelection();
        String sequence = hasRange ? canvas.selectedValue(false, false) : canvas.geneSequence();
        String label = hasRange ? canvas.selectionLabel() : canvas.geneLabel();
        if (sequence.isEmpty()) return;
        // A boxed range is genomic DNA; a picked gene is submitted as its spliced transcript.
        BlastConfig.SequenceType sequenceType = hasRange
            ? BlastConfig.SequenceType.GENOME
            : BlastConfig.SequenceType.TRANSCRIPT;
        BlastDialog dialog = new BlastDialog(ownerWindow(), speciesContext, speciesManager,
            sequenceType, blastQuery(label, sequence));
        dialog.setVisible(true);
    }

    /** FASTA record handed to BLAST for one selected region. */
    static String blastQuery(String label, String sequence) {
        return ">" + (label == null || label.isEmpty() ? "selected_region" : label) + "\n" + sequence + "\n";
    }

    private void openSelectedGeneInfo() {
        AnnotationIndex.Locus locus = canvas == null ? null : canvas.selectedLocus();
        if (locus == null || speciesContext == null || speciesManager == null) return;
        GeneInfoViewerDialog dialog = new GeneInfoViewerDialog(ownerWindow(), speciesManager, speciesContext);
        dialog.showGene(speciesContext, locus.id);
        dialog.setVisible(true);
    }

    private JMenuItem copyItem(String label, boolean reverse, boolean amino) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new StringSelection(canvas.selectedValue(reverse, amino)), null));
        return item;
    }

    private void openGeneSet(File file) {
        if (geneSetDialog != null) geneSetDialog.dispose();
        geneSetDialog = new SetCardDialog(ownerWindow(), "Gene Set: " + GeneSetSupport.extractDisplayName(file),
            cardOptions(GeneSetSupport.listGeneSetFiles(file.getParentFile()), true),
            this::loadGeneCards);
        geneSetDialog.setVisible(true);
    }

    private void openPositionSet(File file) {
        if (positionSetDialog != null) positionSetDialog.dispose();
        positionSetDialog = new SetCardDialog(ownerWindow(), "Position Set: " + RegionSetSupport.extractDisplayName(file),
            cardOptions(RegionSetSupport.listRegionSetFiles(file.getParentFile()), false),
            this::loadPositionCards);
        positionSetDialog.setVisible(true);
    }

    private List<SetCardDialog.Option> cardOptions(List<File> files, boolean geneSet) {
        List<SetCardDialog.Option> options = new ArrayList<>();
        for (File file : files) {
            String name = geneSet ? GeneSetSupport.extractDisplayName(file) : RegionSetSupport.extractDisplayName(file);
            options.add(new SetCardDialog.Option(name, file));
        }
        return options;
    }

    private Window ownerWindow() {
        return SwingUtilities.getWindowAncestor(this);
    }

    private List<SetCardDialog.Card> loadGeneCards(File file) {
        List<SetCardDialog.Card> cards = new ArrayList<>();
        try {
            Map<String, SetNoteSupport.Note> notes = SetNoteSupport.read(file);
            for (GeneSetSupport.Entry entry : GeneSetSupport.readEntries(file)) {
                SetNoteSupport.Note note = notes.get(entry.getId());
                String location = entry.hasCoordinate()
                    ? entry.getChromosomeName() + ":" + entry.getStartPos() + "-" + entry.getEndPos()
                    : "(no coordinates stored)";
                String tooltip = entry.getId() + "   " + location;
                if (note != null && note.hasText()) {
                    tooltip = tooltip + "   " + note.getText();
                }
                cards.add(new SetCardDialog.Card(entry.getId(), tooltip, card -> goToGeneEntry(entry),
                    cardColor(note)));
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Failed to read gene set: " + file, ex);
        }
        return cards;
    }

    /** The stored card colour of an entry, or null when it keeps the default look. */
    private static Color cardColor(SetNoteSupport.Note note) {
        return note == null || note.getRgb() == null ? null : new Color(note.getRgb());
    }

    private List<SetCardDialog.Card> loadPositionCards(File file) {
        List<SetCardDialog.Card> cards = new ArrayList<>();
        try {
            Map<String, SetNoteSupport.Note> notes = SetNoteSupport.read(file);
            for (RegionSetSupport.Entry entry : RegionSetSupport.readEntries(file)) {
                String label = SetNoteSupport.regionKey(
                    entry.getChromosomeName(), entry.getStartPos(), entry.getEndPos());
                SetNoteSupport.Note note = notes.get(label);
                String tooltip = note != null && note.hasText() ? label + "   " + note.getText() : label;
                cards.add(new SetCardDialog.Card(label, tooltip,
                    card -> jumpToPosition(entry.getChromosomeName(), entry.getStartPos(), entry.getEndPos()),
                    cardColor(note)));
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Failed to read position set: " + file, ex);
        }
        return cards;
    }

    /** Gene cards use the browser's gene search; stored coordinates are the fallback. */
    private boolean goToGeneEntry(GeneSetSupport.Entry entry) {
        if (goToGene(entry.getId())) return true;
        return entry.hasCoordinate()
            && jumpToPosition(entry.getChromosomeName(), entry.getStartPos(), entry.getEndPos());
    }

    private void refreshState() {
        refreshWrapButton();
        if (state == null || state.length() == 0 || canvas == null) return;
        if (!positionField.hasFocus()) {
            long[] wrappedRow = canvas.wrappedFirstRowRange();
            if (wrappedRow != null) {
                positionField.setText(formatPosition(wrappedRow[0]) + "-" + formatPosition(wrappedRow[1]));
            } else {
                int[] range = visibleRange();
                if (range != null) {
                    positionField.setText(formatPosition(range[0]) + "-" + formatPosition(range[1]));
                }
            }
        }
        if (onViewChanged != null) onViewChanged.run();
    }

    private int stateWidth() {
        int width = canvas == null ? 0 : canvas.getWidth()
            - GenomeBrowserCanvas.LEFT - GenomeBrowserCanvas.RIGHT - 18;
        return Math.max(1, width);
    }

    private static String formatPosition(long position) {
        return String.format(java.util.Locale.US, "%,d", position);
    }
}
