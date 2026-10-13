package simplegenomehub.util.fileio;

import simplegenomehub.config.SimpleGenomeHubVersion;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Maintenance script: stamps a {@code <set>.notes.txt} sidecar next to every
 * {@code *.gene.txt} / {@code *.region.txt} set of the species folders whose data version is
 * older than {@link SimpleGenomeHubVersion#SET_NOTES_VERSION}.
 *
 * <p>The sidecar stores the note and the card colour of every entry, in display order, and
 * is what the note editor and the browsing cards read. Sets written from now on get theirs
 * when they are created, so this script only upgrades data from before that version.
 * An existing sidecar is never touched, which makes re-running it a no-op.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * java -cp "build\classes;lib\*" simplegenomehub.util.fileio.SetNotesMigration [dataRoot] [--dry-run]
 * </pre>
 */
public final class SetNotesMigration {

    private static final Logger logger = Logger.getLogger(SetNotesMigration.class.getName());

    private static final String GENE_SET_DIR = "GeneSet";

    private SetNotesMigration() {
    }

    public static void main(String[] args) {
        File root = null;
        boolean dryRun = false;
        for (String arg : args) {
            if ("--dry-run".equalsIgnoreCase(arg) || "-n".equalsIgnoreCase(arg)) {
                dryRun = true;
            } else if (root == null) {
                root = new File(arg);
            }
        }
        if (root == null) {
            root = configuredDataRoot();
        }
        if (root == null || !root.isDirectory()) {
            System.out.println("Usage: SetNotesMigration <dataRoot> [--dry-run]");
            System.out.println("  <dataRoot> is the folder that holds the species folders.");
            return;
        }

        System.out.println("Data root      : " + root.getAbsolutePath());
        System.out.println("Target version : " + SimpleGenomeHubVersion.SET_NOTES_VERSION);
        System.out.println("Mode           : " + (dryRun ? "dry run (nothing is written)" : "upgrade"));
        System.out.println();

        File[] children = root.listFiles(File::isDirectory);
        if (children == null) {
            System.out.println("No species folder found.");
            return;
        }

        int species = 0;
        int upgraded = 0;
        int sidecars = 0;

        for (File speciesDir : children) {
            if (!new File(speciesDir, GENE_SET_DIR).isDirectory()) {
                continue;
            }
            species++;
            String stamped = GeneSetCoordinateMigration.readStampedVersion(speciesDir);
            if (!GeneSetCoordinateMigration.isBelow(stamped, SimpleGenomeHubVersion.SET_NOTES_VERSION)) {
                System.out.println("skip    " + speciesDir.getName() + "  (v." + stamped + ", already current)");
                continue;
            }
            try {
                Result result = upgradeSpecies(speciesDir, dryRun);
                if (result.getCreatedCount() > 0) {
                    upgraded++;
                }
                sidecars += result.getCreatedCount();
                System.out.println((result.getCreatedCount() > 0 ? "upgrade " : "ok      ") + speciesDir.getName()
                    + "  (v." + (stamped == null ? "none" : stamped) + ")"
                    + "  sets=" + result.setCount
                    + "  sidecars=" + result.getCreatedCount());
            } catch (IOException ex) {
                System.out.println("FAILED  " + speciesDir.getName() + ": " + ex.getMessage());
            }
        }

        System.out.println();
        System.out.println("Species folders with sets : " + species);
        System.out.println("Species folders upgraded  : " + upgraded);
        System.out.println("Sidecars written          : " + sidecars);
        if (dryRun) {
            System.out.println("Dry run: no file was modified.");
        }
    }

    /** What stamping the sets of one species folder did. */
    public static final class Result {
        /** Sets that were looked at. */
        public final int setCount;
        /** Names of the sets that had no sidecar and got one. */
        public final List<String> createdSidecars;

        Result(int setCount, List<String> createdSidecars) {
            this.setCount = setCount;
            this.createdSidecars = createdSidecars;
        }

        public int getCreatedCount() {
            return createdSidecars.size();
        }
    }

    /**
     * Gives every set of {@code speciesDir} a sidecar, unless it already has one. Sets whose
     * file cannot be read (or that are empty) are left alone.
     */
    public static Result upgradeSpecies(File speciesDir, boolean dryRun) throws IOException {
        List<String> createdSidecars = new ArrayList<>();
        int setCount = 0;

        File geneSetDir = speciesDir == null ? null : new File(speciesDir, GENE_SET_DIR);
        if (geneSetDir == null || !geneSetDir.isDirectory()) {
            return new Result(setCount, createdSidecars);
        }

        File[] setFiles = geneSetDir.listFiles((dir, name) ->
            GeneSetSupport.isGeneSetFileName(name) || RegionSetSupport.isRegionSetFileName(name));
        if (setFiles == null) {
            return new Result(setCount, createdSidecars);
        }
        Arrays.sort(setFiles, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        for (File setFile : setFiles) {
            setCount++;
            File notesFile = SetNoteSupport.notesFileFor(setFile);
            if (notesFile != null && notesFile.isFile()) {
                continue;
            }

            List<String> keys;
            try {
                keys = SetNoteSupport.readKeys(setFile);
            } catch (IllegalArgumentException ex) {
                logger.log(Level.WARNING, "Skipped unreadable set while stamping notes sidecars: " + setFile, ex);
                continue;
            }
            if (keys.isEmpty()) {
                continue;
            }

            if (!dryRun) {
                SetNoteSupport.write(setFile, keys, null);
            }
            createdSidecars.add(setFile.getName());
        }

        return new Result(setCount, createdSidecars);
    }

    /** The data root the application is configured with; null when unavailable. */
    private static File configuredDataRoot() {
        try {
            return simplegenomehub.config.SimpleGenomeHubConfig.getInstance().getDataRootDir();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
