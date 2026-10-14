package simplegenomehub.config;

/**
 * Centralized application version strings used by startup migration
 * and stat.txt header stamping.
 */
public final class SimpleGenomeHubVersion {

    public static final String APPLICATION_VERSION = "0.6.0";
    public static final String STATS_FILE_HEADER = "#SimpleGenomeHub v." + APPLICATION_VERSION;

    /**
     * Data version that introduced the chromosome/start/end columns in {@code *.gene.txt}
     * files. Species folders stamped below this version can be upgraded with
     * {@link simplegenomehub.util.fileio.GeneSetCoordinateMigration}.
     */
    public static final String GENE_SET_COORDINATE_VERSION = "0.5.1";

    /**
     * Data version that introduced the {@code <set>.notes.txt} sidecar holding the note and
     * the card colour of every entry of a {@code *.gene.txt} / {@code *.region.txt} set.
     * Species folders stamped below this version get a sidecar per set at startup and through
     * {@link simplegenomehub.util.fileio.SetNotesMigration}.
     */
    public static final String SET_NOTES_VERSION = "0.7.0";

    private SimpleGenomeHubVersion() {
    }
}
