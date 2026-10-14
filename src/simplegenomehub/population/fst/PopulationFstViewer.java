package simplegenomehub.population.fst;

import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.population.fst.chart.FSTVisualizationManager;

import java.awt.Component;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;

/**
 * Opens the ported FST chart window for one {@code w<window>_s<step>} result folder.
 * <p>
 * Used both right after a calculation and when reopening an earlier run from the data tree.
 */
public final class PopulationFstViewer {

    private PopulationFstViewer() {
    }

    /**
     * @param parentComponent window that owns any dialog shown by the viewer
     * @param species         genome unit the run belongs to (supplies FASTA and annotation)
     * @param resultDirectory the {@code w<window>_s<step>} folder holding the result files
     * @param sortByNumber    order chromosomes by the number in their name
     */
    public static void openResultWindow(Component parentComponent, SpeciesInfo species,
                                        File resultDirectory, boolean sortByNumber) {
        if (resultDirectory == null || !resultDirectory.isDirectory()) {
            return;
        }
        // Chromosome lengths and the optional gene track come from the owning data unit.
        FSTVisualizationManager.openVisualizationFromDirectory(
            resultDirectory.getAbsolutePath(),
            annotationPathOf(species),
            pathOf(species == null ? null : species.getGenomeFile()),
            parentComponent,
            sortByNumber,
            true,
            species);
    }

    private static String pathOf(File file) {
        return file == null ? null : file.getAbsolutePath();
    }

    /** First GFF/GTF file of the data unit, used for the gene track under the chart. */
    private static String annotationPathOf(SpeciesInfo species) {
        if (species == null || species.getAnnotationDir() == null) {
            return null;
        }
        File annotationDir = species.getAnnotationDir();
        if (!annotationDir.isDirectory()) {
            return null;
        }
        File[] files = annotationDir.listFiles((dir, name) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.endsWith(".gff") || lower.endsWith(".gff3") || lower.endsWith(".gtf");
        });
        if (files == null || files.length == 0) {
            return null;
        }
        Arrays.sort(files, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        return files[0].getAbsolutePath();
    }
}
