package simplegenomehub.visualization.regionset;

import simplegenomehub.util.fileio.RegionSetSupport;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Browser agnostic operations backing the Region Set context menu.
 */
public final class RegionSetActions {

    public enum Status {
        SUCCESS,
        DUPLICATE,
        NAME_CONFLICT,
        INVALID_NAME,
        INVALID_INTERVAL,
        NO_CONTEXT,
        IO_ERROR
    }

    public static final class Result {
        private final Status status;
        private final String message;
        private final File file;

        Result(Status status, String message, File file) {
            this.status = status;
            this.message = message;
            this.file = file;
        }

        public Status getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }

        public File getFile() {
            return file;
        }
    }

    private RegionSetActions() {
    }

    public static List<File> listRegionSets(RegionSetContext context) {
        if (context == null || !context.isAvailable()) {
            return new ArrayList<>();
        }
        return RegionSetSupport.listRegionSetFiles(context.getGeneSetDir());
    }

    public static Result addToRegionSet(RegionSetContext context, File regionSetFile, GenomicInterval interval) {
        if (context == null || !context.isAvailable()) {
            return new Result(Status.NO_CONTEXT, "No genome unit context is available.", null);
        }
        if (regionSetFile == null || !regionSetFile.isFile()) {
            return new Result(Status.IO_ERROR, "The selected Region Set file is not available.", regionSetFile);
        }

        RegionSetSupport.Entry entry = toEntry(interval);
        if (entry == null) {
            return new Result(Status.INVALID_INTERVAL,
                "The selected point does not form a valid region (" + describe(interval) + ").", null);
        }

        try {
            boolean added = RegionSetSupport.appendIfAbsent(regionSetFile, entry);
            if (!added) {
                return new Result(Status.DUPLICATE,
                    "This region is already present in " + regionSetFile.getName() + ":", regionSetFile);
            }
            return new Result(Status.SUCCESS,
                "Added to " + RegionSetSupport.extractDisplayName(regionSetFile) + ":", regionSetFile);
        } catch (IllegalArgumentException ex) {
            return new Result(Status.IO_ERROR,
                "The existing Region Set could not be parsed:\n" + ex.getMessage(), regionSetFile);
        } catch (IOException ex) {
            return new Result(Status.IO_ERROR,
                "Failed to update the Region Set:\n" + ex.getMessage(), regionSetFile);
        }
    }

    public static Result createRegionSet(RegionSetContext context, String baseName, GenomicInterval interval) {
        if (context == null || !context.isAvailable()) {
            return new Result(Status.NO_CONTEXT, "No genome unit context is available.", null);
        }
        if (baseName == null || baseName.trim().isEmpty()) {
            return new Result(Status.INVALID_NAME, "Region Set Name must not be empty.", null);
        }
        String name = baseName.trim();
        if (RegionSetSupport.containsInvalidFileNameChars(name)) {
            return new Result(Status.INVALID_NAME, "Region Set Name contains invalid filename characters.", null);
        }

        RegionSetSupport.Entry entry = toEntry(interval);
        if (entry == null) {
            return new Result(Status.INVALID_INTERVAL,
                "The selected point does not form a valid region (" + describe(interval) + ").", null);
        }

        File targetFile = new File(context.getGeneSetDir(), RegionSetSupport.buildFileName(name));
        if (targetFile.exists()) {
            return new Result(Status.NAME_CONFLICT, "Region Set Name conflict: " + targetFile.getName(), targetFile);
        }

        try {
            File created = RegionSetSupport.createRegionSet(context.getGeneSetDir(), name,
                Collections.singletonList(entry));
            return new Result(Status.SUCCESS, "Region Set created:", created);
        } catch (IllegalArgumentException ex) {
            return new Result(Status.INVALID_NAME, ex.getMessage(), null);
        } catch (IOException ex) {
            return new Result(Status.IO_ERROR,
                "Failed to create the Region Set:\n" + ex.getMessage(), targetFile);
        }
    }

    /**
     * Soft validation (option D): if {@code stat.txt} is available, report chromosome
     * name / length mismatches without blocking the write.
     */
    public static List<String> softWarnings(RegionSetContext context, GenomicInterval interval) {
        List<String> warnings = new ArrayList<>();
        if (context == null || interval == null) {
            return warnings;
        }
        File statsFile = context.getStatsFile();
        if (statsFile == null || !statsFile.isFile()) {
            return warnings;
        }

        try {
            Map<String, Long> chromosomeSizes = RegionSetSupport.loadChromosomeSizesFromStats(statsFile);
            if (chromosomeSizes.isEmpty()) {
                return warnings;
            }
            Long chromosomeSize = chromosomeSizes.get(interval.getChromosome());
            if (chromosomeSize == null) {
                warnings.add("Chromosome '" + interval.getChromosome() + "' is not present in stat.txt.");
            } else if (interval.getStart() > chromosomeSize
                || interval.getEnd() > chromosomeSize
                || interval.getStart() < 1) {
                warnings.add("Region " + describe(interval)
                    + " is outside the recorded length of " + interval.getChromosome()
                    + " (" + chromosomeSize + ").");
            }
        } catch (IOException ignored) {
            // Soft validation only: a missing/malformed stat.txt must not block the write.
        }
        return warnings;
    }

    public static String describe(GenomicInterval interval) {
        if (interval == null) {
            return "";
        }
        return interval.getChromosome() + " " + interval.getStart() + " " + interval.getEnd();
    }

    private static RegionSetSupport.Entry toEntry(GenomicInterval interval) {
        if (interval == null || interval.getChromosome() == null
            || interval.getChromosome().trim().isEmpty()) {
            return null;
        }
        long start = Math.min(interval.getStart(), interval.getEnd());
        long end = Math.max(interval.getStart(), interval.getEnd());
        if (start < 1) {
            return null;
        }
        return new RegionSetSupport.Entry(interval.getChromosome().trim(), start, end, 0);
    }
}
