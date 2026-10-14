package simplegenomehub.util.fileio;

import simplegenomehub.config.SimpleGenomeHubVersion;
import simplegenomehub.model.PopulationVcfMetadata;
import simplegenomehub.model.PopulationVcfMetadata.ChromosomeSummary;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Compares an imported VCF against the reference genome of its data unit and turns a scan
 * result into the persisted {@link PopulationVcfMetadata}.
 * <p>
 * Only a complete chromosome-name mismatch blocks an import; every other discrepancy is
 * recorded as a warning so the dataset stays usable and the problem remains visible.
 */
public final class VcfValidationService {

    private VcfValidationService() {
    }

    /** Early check based on the VCF header alone, used before copying the file. */
    public static final class Preflight {
        private final String status;
        private final String message;
        private final boolean blocked;

        private Preflight(String status, String message, boolean blocked) {
            this.status = status;
            this.message = message;
            this.blocked = blocked;
        }

        public String getStatus() { return status; }
        public String getMessage() { return message; }
        public boolean isBlocked() { return blocked; }
    }

    /**
     * Checks declared {@code ##contig} names against the reference table.
     * Returns a non-blocking result when nothing can be decided yet.
     */
    public static Preflight preflight(Collection<String> declaredContigNames, VcfReferenceTable reference) {
        if (reference == null || !reference.isAvailable()) {
            return new Preflight(PopulationVcfMetadata.STATUS_WARN,
                "Reference chromosome lengths are unavailable; the check runs after import.", false);
        }
        List<String> declared = new ArrayList<>();
        if (declaredContigNames != null) {
            for (String name : declaredContigNames) {
                if (name != null && !name.trim().isEmpty()) {
                    declared.add(name.trim());
                }
            }
        }
        if (declared.isEmpty()) {
            return new Preflight(PopulationVcfMetadata.STATUS_WARN,
                "No ##contig declarations in the header; chromosome names are checked after import.", false);
        }

        int matched = 0;
        int normalizedOnly = 0;
        List<String> unmatched = new ArrayList<>();
        for (String name : declared) {
            if (reference.isExactMatch(name)) {
                matched++;
            } else if (reference.resolve(name) != null) {
                matched++;
                normalizedOnly++;
            } else if (unmatched.size() < 5) {
                unmatched.add(name);
            }
        }

        if (matched == 0) {
            return new Preflight(PopulationVcfMetadata.STATUS_FAIL,
                "None of the " + declared.size() + " declared chromosomes match this genome"
                    + (unmatched.isEmpty() ? "." : " (for example: " + String.join(", ", unmatched) + ").")
                    + " The VCF appears to belong to a different assembly.", true);
        }

        String message = matched + " of " + declared.size() + " declared chromosomes match this genome";
        if (normalizedOnly > 0) {
            message += " (" + normalizedOnly + " only after chr-prefix/leading-zero normalization)";
        }
        return new Preflight(normalizedOnly > 0 ? PopulationVcfMetadata.STATUS_WARN : PopulationVcfMetadata.STATUS_PASS,
            message + ".", false);
    }

    /**
     * Builds the persisted metadata for a stored VCF file from its scan result.
     *
     * @param storedFile the VCF file already placed in the data unit's VCF directory
     * @param info       header information gathered when the file was recognized
     * @param scan       statistics from the single streaming pass
     * @param reference  reference chromosome table of the owning genome
     */
    public static PopulationVcfMetadata buildMetadata(File storedFile, VcfImportService.FileInfo info,
                                                     VcfStatisticsScanner.Result scan,
                                                     VcfReferenceTable reference) {
        PopulationVcfMetadata metadata = new PopulationVcfMetadata();
        metadata.fileName = storedFile.getName();
        metadata.originalFileName = info.getFile().getName();
        metadata.originalFilePath = info.getFile().getAbsolutePath();
        metadata.fileSize = info.getSize();
        metadata.compressed = info.isCompressed();
        metadata.importDate = LocalDateTime.now().toString();
        metadata.toolVersion = SimpleGenomeHubVersion.APPLICATION_VERSION;

        metadata.fileFormat = scan.getFileFormat();
        metadata.speciesName = reference != null ? reference.getSpeciesName() : "";
        metadata.speciesVersion = reference != null ? reference.getSpeciesVersion() : "";
        metadata.referenceSource = reference != null ? reference.getSource() : PopulationVcfMetadata.REFERENCE_UNAVAILABLE;
        metadata.referenceGenomeSize = reference != null ? reference.getGenomeSize() : 0L;
        metadata.referenceSequenceCount = reference != null ? reference.getSequenceCount() : 0;

        metadata.sampleNames = new ArrayList<>(scan.getSampleNames());
        metadata.sampleCount = metadata.sampleNames.size();

        metadata.totalRecords = scan.getTotalRecords();
        metadata.snpCount = scan.getSnpCount();
        metadata.indelCount = scan.getIndelCount();
        metadata.multiallelicCount = scan.getMultiallelicCount();
        metadata.otherCount = scan.getOtherCount();

        Set<String> matchedReferenceNames = new LinkedHashSet<>();
        boolean referenceAvailable = reference != null && reference.isAvailable();
        int notDeclared = 0;

        for (VcfStatisticsScanner.ChromosomeStats stats : scan.getChromosomeStats().values()) {
            ChromosomeSummary summary = new ChromosomeSummary();
            summary.vcfName = stats.getName();
            summary.variantCount = stats.getVariantCount();
            summary.snpCount = stats.getSnpCount();
            summary.indelCount = stats.getIndelCount();
            summary.maxPosition = stats.getMaxPosition();
            summary.declaredLength = scan.getDeclaredContigLengths().get(stats.getName());

            if (referenceAvailable) {
                if (reference.isExactMatch(stats.getName())) {
                    summary.matchType = ChromosomeSummary.MATCH_EXACT;
                    summary.genomeName = stats.getName();
                } else {
                    String resolved = reference.resolve(stats.getName());
                    if (resolved != null) {
                        summary.matchType = ChromosomeSummary.MATCH_NORMALIZED;
                        summary.genomeName = resolved;
                    }
                }
            }

            if (summary.genomeName != null) {
                matchedReferenceNames.add(summary.genomeName);
                summary.genomeLength = reference.lengthOf(summary.genomeName);
            }

            if (summary.genomeLength == null) {
                summary.lengthStatus = ChromosomeSummary.LENGTH_NO_REFERENCE;
            } else if (summary.declaredLength == null) {
                summary.lengthStatus = ChromosomeSummary.LENGTH_NOT_DECLARED;
                notDeclared++;
            } else if (summary.declaredLength.longValue() == summary.genomeLength.longValue()) {
                summary.lengthStatus = ChromosomeSummary.LENGTH_MATCH;
                metadata.lengthMatchCount++;
            } else {
                summary.lengthStatus = ChromosomeSummary.LENGTH_MISMATCH;
                metadata.lengthMismatchCount++;
            }

            if (summary.genomeLength != null && summary.maxPosition > summary.genomeLength) {
                summary.positionOutOfRange = true;
                metadata.outOfRangeVariantCount += summary.variantCount;
            }

            switch (summary.matchType) {
                case ChromosomeSummary.MATCH_EXACT:
                    metadata.matchedChromosomeCount++;
                    metadata.exactNameMatchCount++;
                    break;
                case ChromosomeSummary.MATCH_NORMALIZED:
                    metadata.matchedChromosomeCount++;
                    metadata.normalizedNameMatchCount++;
                    break;
                default:
                    if (metadata.vcfOnlyChromosomes.size() < 50) {
                        metadata.vcfOnlyChromosomes.add(stats.getName());
                    }
                    break;
            }

            metadata.chromosomes.add(summary);
        }
        metadata.lengthNotDeclaredCount = notDeclared;

        if (referenceAvailable) {
            for (String referenceName : reference.getSequenceNames()) {
                if (!matchedReferenceNames.contains(referenceName) && metadata.genomeOnlyChromosomes.size() < 50) {
                    metadata.genomeOnlyChromosomes.add(referenceName);
                }
            }
        }

        evaluateVerdict(metadata, referenceAvailable, notDeclared);
        return metadata;
    }

    private static void evaluateVerdict(PopulationVcfMetadata metadata, boolean referenceAvailable, int notDeclared) {
        boolean hasChromosomes = !metadata.chromosomes.isEmpty();

        if (!referenceAvailable) {
            metadata.warnings.add("Reference chromosome lengths are unavailable for this genome, "
                + "so chromosome names and lengths were not verified.");
        } else if (hasChromosomes && metadata.matchedChromosomeCount == 0) {
            String example = metadata.vcfOnlyChromosomes.isEmpty()
                ? "" : " (for example: " + String.join(", ", metadata.vcfOnlyChromosomes.subList(
                    0, Math.min(5, metadata.vcfOnlyChromosomes.size()))) + ")";
            metadata.failures.add("None of the " + metadata.chromosomes.size()
                + " chromosomes in this VCF match the reference genome" + example + ".");
        }
        if (metadata.normalizedNameMatchCount > 0) {
            metadata.warnings.add(metadata.normalizedNameMatchCount
                + " chromosome name(s) matched only after removing the chr prefix or leading zeros.");
        }
        if (metadata.lengthMismatchCount > 0) {
            metadata.warnings.add(metadata.lengthMismatchCount
                + " chromosome(s) declare a length that differs from the reference genome.");
        }
        if (metadata.outOfRangeVariantCount > 0) {
            metadata.warnings.add(metadata.outOfRangeVariantCount
                + " variant(s) have positions beyond the reference chromosome length.");
        }
        if (!metadata.vcfOnlyChromosomes.isEmpty()) {
            metadata.warnings.add(metadata.vcfOnlyChromosomes.size()
                + " chromosome name(s) were not found in the reference genome"
                + (metadata.vcfOnlyChromosomes.size() > 5 ? " (first 5: "
                    + String.join(", ", metadata.vcfOnlyChromosomes.subList(0, 5)) + ")" : ": "
                    + String.join(", ", metadata.vcfOnlyChromosomes)) + ".");
        }
        if (referenceAvailable && notDeclared > 0 && metadata.lengthMatchCount == 0
                && metadata.lengthMismatchCount == 0) {
            metadata.warnings.add("The VCF declares no ##contig lengths, so sequence lengths could not be verified.");
        }

        if (!metadata.failures.isEmpty()) {
            metadata.checkStatus = PopulationVcfMetadata.STATUS_FAIL;
        } else if (!metadata.warnings.isEmpty()) {
            metadata.checkStatus = PopulationVcfMetadata.STATUS_WARN;
        } else {
            metadata.checkStatus = PopulationVcfMetadata.STATUS_PASS;
        }
    }

    /** One-line verdict used for logs and import messages. */
    public static String describe(PopulationVcfMetadata metadata) {
        if (metadata == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(metadata.checkStatus);
        if (metadata.isFail()) {
            sb.append(" - ").append(metadata.failures.isEmpty() ? "failed" : metadata.failures.get(0));
            return sb.toString();
        }
        sb.append(String.format(Locale.ROOT, " - %d/%d chromosomes matched, %d variant records (%d SNP, %d indel)",
            metadata.matchedChromosomeCount, metadata.chromosomes.size(),
            metadata.totalRecords, metadata.snpCount, metadata.indelCount));
        if (!metadata.failures.isEmpty()) {
            sb.append(" | ").append(metadata.failures.get(0));
        }
        return sb.toString();
    }

    /** Builds a reference table map view for callers that only have raw name/length pairs. */
    public static Preflight preflightDeclared(Map<String, Long> declaredContigs, VcfReferenceTable reference) {
        return preflight(declaredContigs == null ? java.util.Collections.emptyList() : declaredContigs.keySet(), reference);
    }
}
