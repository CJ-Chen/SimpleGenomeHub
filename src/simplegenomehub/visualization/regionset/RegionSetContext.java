package simplegenomehub.visualization.regionset;

import simplegenomehub.model.SpeciesInfo;

import java.io.File;

/**
 * Ties a browser window to the genome unit whose Region Sets it may write to.
 */
public final class RegionSetContext {

    private final simplegenomehub.runtime.HubSession session;
    private final long generation;
    private final File geneSetDir;
    private final File statsFile;
    private final String label;

    public RegionSetContext(File geneSetDir, File statsFile, String label) {
        this.session = simplegenomehub.runtime.HubSession.forDataPath(geneSetDir);
        this.generation = session == null ? 0 : session.getGeneration();
        this.geneSetDir = geneSetDir;
        this.statsFile = statsFile;
        this.label = label;
    }

    public static RegionSetContext fromSpecies(SpeciesInfo species) {
        if (species == null) {
            return null;
        }
        return new RegionSetContext(
            species.getGeneSetDir(),
            species.getStatsFile(),
            species.getSpeciesName() + " " + species.getVersion()
        );
    }

    public File getGeneSetDir() {
        return geneSetDir;
    }

    public File getStatsFile() {
        return statsFile;
    }

    public String getLabel() {
        return label;
    }

    public boolean isAvailable() {
        return geneSetDir != null && (session == null || session.accepts(generation));
    }
}
