package simplegenomehub.util.fileio;

import simplegenomehub.config.SimpleGenomeHubVersion;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maintenance script: adds chromosome/start/end to the {@code *.gene.txt} files of every
 * species folder whose data version is older than
 * {@link SimpleGenomeHubVersion#GENE_SET_COORDINATE_VERSION}.
 *
 * <p>The data version of a species folder is the stamp the application writes as the first
 * line of its {@code stat.txt} ({@code #SimpleGenomeHub v.0.4}); a folder without that
 * stamp counts as legacy. Gene sets written from now on carry the location columns
 * themselves, so the browser can place a gene without re-reading the annotation.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * java -cp "build\classes;lib\*" simplegenomehub.util.fileio.GeneSetCoordinateMigration [dataRoot] [--dry-run]
 * </pre>
 *
 * <p>The first rewrite of a file keeps a {@code &lt;name&gt;.gene.txt.bak} copy, rows that
 * already carry a location are left untouched, and ids that cannot be found in the
 * annotation stay as bare ids instead of being dropped - so the script is safe to re-run.</p>
 */
public final class GeneSetCoordinateMigration {

    private static final String STATS_FILE_NAME = "stat.txt";
    private static final String GENE_SET_DIR = "GeneSet";
    private static final String ANNOTATION_DIR = "Annotation";

    private GeneSetCoordinateMigration() {
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
            System.out.println("Usage: GeneSetCoordinateMigration <dataRoot> [--dry-run]");
            System.out.println("  <dataRoot> is the folder that holds the species folders.");
            return;
        }

        System.out.println("Data root      : " + root.getAbsolutePath());
        System.out.println("Target version : " + SimpleGenomeHubVersion.GENE_SET_COORDINATE_VERSION);
        System.out.println("Mode           : " + (dryRun ? "dry run (nothing is written)" : "upgrade"));
        System.out.println();

        File[] children = root.listFiles(File::isDirectory);
        if (children == null) {
            System.out.println("No species folder found.");
            return;
        }

        int species = 0;
        int upgraded = 0;
        int files = 0;
        int rows = 0;
        int unresolved = 0;

        for (File speciesDir : children) {
            if (!new File(speciesDir, GENE_SET_DIR).isDirectory()) {
                continue;
            }
            species++;
            String stamped = readStampedVersion(speciesDir);
            if (!isBelow(stamped, SimpleGenomeHubVersion.GENE_SET_COORDINATE_VERSION)) {
                System.out.println("skip    " + speciesDir.getName() + "  (v." + stamped + ", already current)");
                continue;
            }
            try {
                Result result = upgradeSpecies(speciesDir, dryRun);
                if (result.filesChanged > 0) {
                    upgraded++;
                }
                files += result.filesChanged;
                rows += result.rowsWritten;
                unresolved += result.unresolved;
                System.out.println((result.filesChanged > 0 ? "upgrade " : "ok      ") + speciesDir.getName()
                    + "  (v." + (stamped == null ? "none" : stamped) + ")"
                    + "  files=" + result.filesChanged + "  rows=" + result.rowsWritten
                    + "  unresolved=" + result.unresolved);
            } catch (IOException ex) {
                System.out.println("FAILED  " + speciesDir.getName() + ": " + ex.getMessage());
            }
        }

        System.out.println();
        System.out.println("Species folders with gene sets : " + species);
        System.out.println("Species folders upgraded       : " + upgraded);
        System.out.println("Gene set files rewritten       : " + files);
        System.out.println("Rows given coordinates         : " + rows);
        System.out.println("Rows kept as bare ids          : " + unresolved);
        if (dryRun) {
            System.out.println("Dry run: no file was modified.");
        }
    }

    /** What upgrading one species folder did. */
    public static final class Result {
        public final int filesChanged;
        public final int rowsWritten;
        public final int unresolved;

        Result(int filesChanged, int rowsWritten, int unresolved) {
            this.filesChanged = filesChanged;
            this.rowsWritten = rowsWritten;
            this.unresolved = unresolved;
        }
    }

    /** Rewrites the gene sets of one species folder and reports what happened. */
    public static Result upgradeSpecies(File speciesDir, boolean dryRun) throws IOException {
        Map<String, Location> byId = indexAnnotation(pickAnnotationFile(new File(speciesDir, ANNOTATION_DIR)));
        int filesChanged = 0;
        int rowsWritten = 0;
        int unresolved = 0;

        for (File geneSetFile : GeneSetSupport.listGeneSetFiles(new File(speciesDir, GENE_SET_DIR))) {
            List<GeneSetSupport.Entry> entries = GeneSetSupport.readEntries(geneSetFile);
            List<GeneSetSupport.Entry> upgraded = new ArrayList<>();
            int gained = 0;
            int fileUnresolved = 0;
            for (GeneSetSupport.Entry entry : entries) {
                Location location = entry.hasCoordinate() ? null : byId.get(entry.getId());
                if (location != null) {
                    upgraded.add(new GeneSetSupport.Entry(entry.getId(), location.chromosome,
                        location.start, location.end, entry.getLineNumber()));
                    gained++;
                    continue;
                }
                if (!entry.hasCoordinate()) {
                    fileUnresolved++;
                }
                upgraded.add(entry);
            }

            // Nothing new to store (an id that the annotation does not know stays bare),
            // so leave the file alone - re-running the script is then a no-op.
            if (gained == 0) {
                unresolved += fileUnresolved;
                continue;
            }

            filesChanged++;
            rowsWritten += gained;
            unresolved += fileUnresolved;
            if (!dryRun) {
                backup(geneSetFile);
                GeneSetSupport.writeEntries(geneSetFile, upgraded);
            }
            System.out.println("    " + (dryRun ? "(dry) " : "") + geneSetFile.getName()
                + "  rows=" + upgraded.size() + "  gained=" + gained + "  unresolved=" + fileUnresolved);
        }
        return new Result(filesChanged, rowsWritten, unresolved);
    }

    /** True when {@code stamped} is older than {@code target}; an unknown version counts as older. */
    public static boolean isBelow(String stamped, String target) {
        if (stamped == null || stamped.trim().isEmpty()) {
            return true;
        }
        int[] current = parseVersion(stamped);
        int[] required = parseVersion(target);
        for (int i = 0; i < Math.max(current.length, required.length); i++) {
            int a = i < current.length ? current[i] : 0;
            int b = i < required.length ? required[i] : 0;
            if (a != b) {
                return a < b;
            }
        }
        return false;
    }

    /** The data version stamped as the first line of {@code stat.txt}, or null when absent. */
    public static String readStampedVersion(File speciesDir) {
        File statsFile = new File(speciesDir, STATS_FILE_NAME);
        if (!statsFile.isFile()) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(statsFile), StandardCharsets.UTF_8))) {
            String firstLine = reader.readLine();
            if (firstLine == null) {
                return null;
            }
            int marker = firstLine.indexOf("v.");
            if (marker < 0) {
                return null;
            }
            String version = firstLine.substring(marker + 2).trim();
            return version.isEmpty() ? null : version;
        } catch (IOException ex) {
            return null;
        }
    }

    private static int[] parseVersion(String version) {
        String[] parts = version.trim().split("\\.");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                numbers[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ex) {
                numbers[i] = 0;
            }
        }
        return numbers;
    }

    /** A location found in the annotation; start/end grow to the span of all matching rows. */
    private static final class Location {
        final String chromosome;
        long start;
        long end;

        Location(String chromosome, long start, long end) {
            this.chromosome = chromosome;
            this.start = start;
            this.end = end;
        }
    }

    /**
     * Indexes gene and transcript ids from the species annotation. Ids appearing on several
     * rows (for example a multi-exon CDS) get the span of all of them.
     */
    private static Map<String, Location> indexAnnotation(File annotationFile) throws IOException {
        Map<String, Location> byId = new LinkedHashMap<>();
        if (annotationFile == null) {
            System.out.println("    (no annotation file - ids stay bare)");
            return byId;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(annotationFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                String[] fields = line.split("\t", -1);
                if (fields.length < 9) {
                    continue;
                }
                long start;
                long end;
                try {
                    start = Long.parseLong(fields[3].trim());
                    end = Long.parseLong(fields[4].trim());
                } catch (NumberFormatException ex) {
                    continue;
                }
                String chromosome = fields[0].trim();
                for (String id : attributeIds(fields[8])) {
                    Location location = byId.get(id);
                    if (location == null) {
                        byId.put(id, new Location(chromosome, start, end));
                    } else {
                        location.start = Math.min(location.start, start);
                        location.end = Math.max(location.end, end);
                    }
                }
            }
        }
        return byId;
    }

    private static List<String> attributeIds(String attributes) {
        List<String> ids = new ArrayList<>();
        for (String key : new String[]{"ID", "transcript_id", "gene_id", "Parent"}) {
            for (String part : attributes.split(";")) {
                String trimmed = part.trim();
                if (!trimmed.startsWith(key + "=")) {
                    continue;
                }
                for (String value : trimmed.substring(key.length() + 1).split(",")) {
                    String id = value.trim();
                    if (!id.isEmpty()) {
                        ids.add(id);
                    }
                }
            }
        }
        return ids;
    }

    private static File pickAnnotationFile(File annotationDir) {
        if (annotationDir == null || !annotationDir.isDirectory()) {
            return null;
        }
        File[] files = annotationDir.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".gff") || lower.endsWith(".gff3") || lower.endsWith(".gtf");
        });
        return files == null || files.length == 0 ? null : files[0];
    }

    private static void backup(File geneSetFile) throws IOException {
        File backup = new File(geneSetFile.getAbsolutePath() + ".bak");
        if (!backup.isFile()) {
            Files.copy(geneSetFile.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        }
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
