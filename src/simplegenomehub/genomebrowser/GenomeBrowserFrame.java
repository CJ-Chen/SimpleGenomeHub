package simplegenomehub.genomebrowser;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.model.SpeciesManager;

import java.io.File;
import javax.swing.JFrame;

/** Standalone window hosting a {@link GenomeBrowserPanel}. */
public final class GenomeBrowserFrame extends JFrame {

    private final GenomeBrowserPanel panel;

    public static void open(Window owner, File fastaFile, File gffFile, String title) {
        openAt(owner, fastaFile, gffFile, title, null, 0, 0, null, null);
    }

    /** Same, with the genome unit the files belong to (enables BLAST / gene info in the menu). */
    public static void open(Window owner, File fastaFile, File gffFile, String title,
                            SpeciesInfo species, SpeciesManager speciesManager) {
        openAt(owner, fastaFile, gffFile, title, null, 0, 0, species, speciesManager);
    }

    /**
     * Opens the browser and jumps to a chromosome range. The range is kept while the data is
     * still loading, so the window opens on the requested locus.
     */
    public static void openAt(Window owner, File fastaFile, File gffFile, String title,
                              String chromosome, int start, int end) {
        openAt(owner, fastaFile, gffFile, title, chromosome, start, end, null, null);
    }

    public static void openAt(Window owner, File fastaFile, File gffFile, String title,
                              String chromosome, int start, int end,
                              SpeciesInfo species, SpeciesManager speciesManager) {
        GenomeBrowserFrame frame = new GenomeBrowserFrame(owner, fastaFile, gffFile, title, species, speciesManager);
        frame.setVisible(true);
        if (chromosome != null && !chromosome.trim().isEmpty() && start >= 1) {
            frame.panel.setPosition(chromosome.trim(), start, Math.max(start, end));
        }
    }

    private GenomeBrowserFrame(Window owner, File fastaFile, File gffFile, String title,
                               SpeciesInfo species, SpeciesManager speciesManager) {
        super(title == null ? "Genome Browser" : title);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Color.WHITE);
        panel = new GenomeBrowserPanel(fastaFile, gffFile, species, speciesManager);
        add(panel, BorderLayout.CENTER);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                if (panel != null) panel.close();
            }
        });
        setSize(1250, 780);
        setLocationRelativeTo(owner);
    }
}
