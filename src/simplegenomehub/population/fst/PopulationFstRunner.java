package simplegenomehub.population.fst;

import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.util.fileio.VcfImportService;
import simplegenomehub.util.fileio.VcfImportService.FileInfo;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point that binds one SimpleGenomeHub genome unit and one VCF dataset to an FST run.
 * <p>
 * The ported FSTdraw code works on plain file paths; this class supplies them, points the run
 * output at {@code <genome unit>/Population/FST} and installs the result callback.
 */
public final class PopulationFstRunner {

    private static final Logger LOGGER = Logger.getLogger(PopulationFstRunner.class.getName());

    /** Default window and step, matching the values the original program started with. */
    public static final int DEFAULT_WINDOW_SIZE = 1_000_000;
    public static final int DEFAULT_STEP_SIZE = 100_000;

    private PopulationFstRunner() {
    }

    /**
     * Opens the group/comparison strategy window for the given VCF file.
     *
     * @param owner       window that owns the dialog
     * @param species     genome unit the dataset belongs to (or was dropped for)
     * @param vcfFile     imported dataset inside {@code Population/VCF}, or a temporary file
     * @param onCompleted invoked with the finished {@code w<window>_s<step>} result folder
     */
    public static void openStrategyWindow(Window owner, SpeciesInfo species, File vcfFile,
                                          FstResultHandler onCompleted) {
        if (species == null || vcfFile == null) {
            return;
        }
        List<String> samples;
        try {
            FileInfo info = VcfImportService.inspect(vcfFile);
            samples = info.getSampleNames();
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Cannot read the VCF header for FST analysis", ex);
            JOptionPane.showMessageDialog(owner,
                "Cannot read the VCF file:\n" + ex.getMessage(),
                "VCF Not Usable", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (samples.isEmpty()) {
            JOptionPane.showMessageDialog(owner,
                "This VCF has no sample columns, so no population can be defined.",
                "No Samples", JOptionPane.WARNING_MESSAGE);
            return;
        }

        File fstRoot = species.getPopulationFstDir();
        if (fstRoot == null) {
            JOptionPane.showMessageDialog(owner,
                "The FST directory is not available for this genome.",
                "Directory Missing", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!fstRoot.isDirectory() && !fstRoot.mkdirs()) {
            JOptionPane.showMessageDialog(owner,
                "Cannot create the FST directory:\n" + fstRoot.getAbsolutePath(),
                "Directory Not Writable", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Every run gets its own <timestamp>/w<window>_s<step>/ folder under Population/FST.
        FSTOutputDirectory.reset();
        FSTOutputDirectory.setBaseDirectory(fstRoot);

        FST1JieMian1 dialog = new FST1JieMian1(owner, samples, vcfFile.getAbsolutePath(),
            pathOf(species.getGenomeFile()), annotationPathOf(species),
            DEFAULT_WINDOW_SIZE, DEFAULT_STEP_SIZE);
        dialog.setRunSpeciesIdentity(species.getSpeciesName(), species.getVersion());
        dialog.setResultHandler(resultDirectory -> {
            if (onCompleted != null) {
                onCompleted.onResultsReady(resultDirectory);
            } else {
                // Default: show the charts for the freshly written results.
                PopulationFstViewer.openResultWindow(dialog, species, resultDirectory,
                    dialog.isSortChromosomesByNumber());
            }
        });
        dialog.setVisible(true);
    }

    private static String pathOf(File file) {
        return file == null ? null : file.getAbsolutePath();
    }

    /** First GFF/GTF file of the data unit; used later by the chart viewer for gene tracks. */
    private static String annotationPathOf(SpeciesInfo species) {
        File annotationDir = species.getAnnotationDir();
        if (annotationDir == null || !annotationDir.isDirectory()) {
            return null;
        }
        File[] files = annotationDir.listFiles((dir, name) -> {
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            return lower.endsWith(".gff") || lower.endsWith(".gff3") || lower.endsWith(".gtf");
        });
        if (files == null || files.length == 0) {
            return null;
        }
        java.util.Arrays.sort(files, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        return files[0].getAbsolutePath();
    }
}
