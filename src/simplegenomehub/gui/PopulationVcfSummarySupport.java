package simplegenomehub.gui;

import simplegenomehub.model.PopulationVcfMetadata;
import simplegenomehub.model.PopulationVcfMetadata.ChromosomeSummary;
import simplegenomehub.model.SpeciesInfo;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Formats the imported-VCF statistics shown in the Other Data card.
 */
final class PopulationVcfSummarySupport {

    private static final int MAX_LISTED_CHROMOSOMES = 12;
    /** Wrapping width for the full sample-name listing, in characters of the monospaced font. */
    private static final int SAMPLE_NAME_LINE_WIDTH = 56;
    private static final String SAMPLE_NAME_INDENT = "  ";

    private PopulationVcfSummarySupport() {
    }

    /** Loads the metadata sidecar of the selected VCF file; null when it is missing or malformed. */
    static PopulationVcfMetadata loadMetadata(File vcfFile) {
        return PopulationVcfMetadata.load(PopulationVcfMetadata.fileFor(vcfFile));
    }

    /** Human-readable file size that stays informative for small datasets. */
    static String formatFileSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.2f MB", bytes / 1048576.0);
        }
        return String.format(Locale.ROOT, "%.2f GB", bytes / 1073741824.0);
    }

    static String buildSummaryText(SpeciesInfo species, File vcfFile) {
        if (vcfFile == null || !vcfFile.isFile()) {
            return "No VCF dataset selected.";
        }
        return buildSummaryText(species, vcfFile, loadMetadata(vcfFile), false);
    }

    /**
     * Same layout as the Other Data card.
     *
     * @param metadata  statistics to render; for a temporary dataset these come from an
     *                  in-memory scan instead of a stored sidecar
     * @param temporary true for a dragged-in file that is analyzed in place and never saved
     */
    static String buildSummaryText(SpeciesInfo species, File vcfFile,
                                   PopulationVcfMetadata metadata, boolean temporary) {
        if (vcfFile == null || !vcfFile.isFile()) {
            return "No VCF dataset selected.";
        }

        StringBuilder text = new StringBuilder();

        text.append("VCF DATASET\n");
        text.append("===========\n");
        text.append("File: ").append(vcfFile.getName()).append('\n');
        if (species != null) {
            text.append("Genome: ").append(species.getSpeciesName())
                .append(" (").append(species.getVersion()).append(")\n");
        }
        text.append("Size: ").append(formatFileSize(vcfFile.length()))
            .append(metadata != null && metadata.compressed ? " (gzip compressed)" : "").append('\n');

        if (metadata == null) {
            text.append('\n');
            text.append("No statistics are stored for this file.\n");
            text.append("Re-import the VCF to generate them.\n");
            return text.toString();
        }

        text.append("Format: ").append(metadata.fileFormat.isEmpty() ? "-" : metadata.fileFormat).append('\n');
        if (temporary) {
            text.append("Type: temporary dataset (analyzed in place, never copied or saved)\n");
            text.append("Analyzed: ").append(metadata.getFormattedImportDate()).append('\n');
        } else {
            text.append("Imported: ").append(metadata.getFormattedImportDate()).append('\n');
        }

        text.append('\n').append("SAMPLES\n");
        text.append("=======\n");
        text.append("Total samples: ").append(metadata.sampleCount).append('\n');
        text.append("Sample names:\n").append(formatSampleNames(metadata.sampleNames));

        text.append('\n').append("VARIANTS\n");
        text.append("========\n");
        text.append("Total records: ").append(metadata.totalRecords).append('\n');
        text.append("SNPs: ").append(metadata.snpCount).append('\n');
        text.append("Indels: ").append(metadata.indelCount).append('\n');
        text.append("Multi-allelic: ").append(metadata.multiallelicCount).append('\n');
        if (metadata.otherCount > 0) {
            text.append("Other/symbolic: ").append(metadata.otherCount).append('\n');
        }

        text.append('\n').append("REFERENCE CHECK\n");
        text.append("===============\n");
        text.append("Status: ").append(metadata.checkStatus).append('\n');
        text.append("Reference: ").append(metadata.getReferenceDescription()).append('\n');
        text.append("Chromosomes matched: ").append(metadata.matchedChromosomeCount)
            .append(" / ").append(metadata.chromosomes.size()).append('\n');
        text.append("Name matching: ").append(metadata.exactNameMatchCount).append(" exact, ")
            .append(metadata.normalizedNameMatchCount).append(" normalized\n");
        text.append("Length check: ").append(metadata.lengthMatchCount).append(" consistent, ")
            .append(metadata.lengthMismatchCount).append(" mismatched, ")
            .append(metadata.lengthNotDeclaredCount).append(" not declared\n");
        text.append("Positions beyond reference: ").append(metadata.outOfRangeVariantCount).append('\n');

        text.append('\n').append("CHROMOSOMES\n");
        text.append("===========\n");
        text.append(String.format(Locale.ROOT, "%-14s %-14s %-11s %12s %10s%n",
            "VCF", "Reference", "Length", "Variants", "Max pos"));
        int listed = 0;
        for (ChromosomeSummary summary : metadata.chromosomes) {
            if (listed++ >= MAX_LISTED_CHROMOSOMES) {
                text.append("... ").append(metadata.chromosomes.size() - MAX_LISTED_CHROMOSOMES)
                    .append(" more chromosome(s)\n");
                break;
            }
            text.append(String.format(Locale.ROOT, "%-14s %-14s %-11s %12d %10d%n",
                summary.getVcfName(),
                summary.getGenomeName() == null ? "-" : summary.getGenomeName(),
                describeLength(summary),
                summary.getVariantCount(),
                summary.getMaxPosition()));
        }

        if (!metadata.vcfOnlyChromosomes.isEmpty()) {
            text.append('\n').append("Chromosomes absent from the reference: ")
                .append(joinLimited(metadata.vcfOnlyChromosomes, MAX_LISTED_CHROMOSOMES)).append('\n');
        }
        if (!metadata.genomeOnlyChromosomes.isEmpty()) {
            text.append("Reference sequences without variants: ")
                .append(joinLimited(metadata.genomeOnlyChromosomes, MAX_LISTED_CHROMOSOMES)).append('\n');
        }

        if (!metadata.failures.isEmpty()) {
            text.append('\n').append("FAILURES\n");
            text.append("========\n");
            for (String failure : metadata.failures) {
                text.append("- ").append(failure).append('\n');
            }
        }
        if (!metadata.warnings.isEmpty()) {
            text.append('\n').append("WARNINGS\n");
            text.append("========\n");
            for (String warning : metadata.warnings) {
                text.append("- ").append(warning).append('\n');
            }
        }

        return text.toString();
    }

    private static String describeLength(ChromosomeSummary summary) {
        switch (summary.getLengthStatus()) {
            case ChromosomeSummary.LENGTH_MATCH:
                return "ok";
            case ChromosomeSummary.LENGTH_MISMATCH:
                return "mismatch";
            case ChromosomeSummary.LENGTH_NOT_DECLARED:
                return "not declared";
            default:
                return "-";
        }
    }

    /**
     * Lists every sample name, wrapped over several lines so the card stays readable for
     * datasets with hundreds of samples. Each name is printed in full.
     */
    private static String formatSampleNames(List<String> sampleNames) {
        return wrapNameList(sampleNames, SAMPLE_NAME_INDENT, SAMPLE_NAME_LINE_WIDTH);
    }

    /**
     * Wraps a name list over several lines with the given indent; every name is printed in full.
     * Shared with the FST result card so both list population members the same way.
     */
    static String wrapNameList(List<String> names, String indent, int lineWidth) {
        if (names == null || names.isEmpty()) {
            return indent + "none\n";
        }

        StringBuilder listing = new StringBuilder();
        StringBuilder line = new StringBuilder(indent);
        for (String name : names) {
            String separator = line.length() > indent.length() ? ", " : "";
            if (line.length() > indent.length()
                    && line.length() + separator.length() + name.length() > lineWidth) {
                listing.append(line).append('\n');
                line.setLength(0);
                line.append(indent);
                separator = "";
            }
            line.append(separator).append(name);
        }
        if (line.length() > indent.length()) {
            listing.append(line).append('\n');
        }
        return listing.toString();
    }

    private static String joinLimited(List<String> values, int limit) {
        if (values.size() <= limit) {
            return String.join(", ", values);
        }
        return String.join(", ", values.subList(0, limit)) + ", ...";
    }
}
