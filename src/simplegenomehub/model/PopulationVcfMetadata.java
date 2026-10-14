package simplegenomehub.model;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import simplegenomehub.util.fileio.JsonMetadataSupport;
import simplegenomehub.util.fileio.FileSetTransaction;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Statistics and reference-genome check result for one imported VCF dataset.
 * <p>
 * Stored as {@code <vcf file name>.metadata.json} next to the VCF file inside
 * {@code <species>/Population/VCF/}, following the expression/annotation metadata
 * convention. Fields are public because this class is a serialization DTO.
 */
public class PopulationVcfMetadata {

    private static final Logger logger = Logger.getLogger(PopulationVcfMetadata.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Suffix appended to the stored VCF file name. */
    public static final String FILE_SUFFIX = ".metadata.json";

    /** Current metadata schema version. */
    public static final int SCHEMA_VERSION = 1;

    /** Reference table could not be built; names and lengths were not checked. */
    public static final String REFERENCE_UNAVAILABLE = "unavailable";

    public static final String STATUS_PASS = "PASS";
    public static final String STATUS_WARN = "WARN";
    public static final String STATUS_FAIL = "FAIL";

    private transient JsonObject originalJson;

    // ---- file identity ----
    public int schemaVersion = SCHEMA_VERSION;
    public String fileName = "";
    public String originalFileName = "";
    public String originalFilePath = "";
    public long fileSize;
    public boolean compressed;
    public String importDate = "";
    public String toolVersion = "";

    // ---- reference genome ----
    public String speciesName = "";
    public String speciesVersion = "";
    public String referenceSource = REFERENCE_UNAVAILABLE;
    public long referenceGenomeSize;
    public int referenceSequenceCount;

    // ---- samples ----
    public int sampleCount;
    public List<String> sampleNames = new ArrayList<>();

    // ---- variant totals ----
    public String fileFormat = "";
    public long totalRecords;
    public long snpCount;
    public long indelCount;
    public long multiallelicCount;
    public long otherCount;

    // ---- per-chromosome detail ----
    public List<ChromosomeSummary> chromosomes = new ArrayList<>();

    // ---- reference check ----
    public String checkStatus = STATUS_WARN;
    public int matchedChromosomeCount;
    public int exactNameMatchCount;
    public int normalizedNameMatchCount;
    public int lengthMatchCount;
    public int lengthMismatchCount;
    public int lengthNotDeclaredCount;
    public long outOfRangeVariantCount;
    public List<String> vcfOnlyChromosomes = new ArrayList<>();
    public List<String> genomeOnlyChromosomes = new ArrayList<>();
    public List<String> warnings = new ArrayList<>();
    public List<String> failures = new ArrayList<>();

    /** Per-chromosome statistics plus its reference-check outcome. */
    public static final class ChromosomeSummary {
        public static final String MATCH_EXACT = "EXACT";
        public static final String MATCH_NORMALIZED = "NORMALIZED";
        public static final String MATCH_NONE = "UNMATCHED";

        public String vcfName = "";
        /** Reference sequence name this VCF chromosome was matched to (null when unmatched). */
        public String genomeName;
        public String matchType = MATCH_NONE;
        /** Length declared by a VCF ##contig header line (null when not declared). */
        public Long declaredLength;
        /** Length from the reference table (null when unavailable). */
        public Long genomeLength;
        public String lengthStatus = LENGTH_NOT_CHECKED;
        public long maxPosition;
        public long variantCount;
        public long snpCount;
        public long indelCount;
        public boolean positionOutOfRange;

        public static final String LENGTH_MATCH = "MATCH";
        public static final String LENGTH_MISMATCH = "MISMATCH";
        public static final String LENGTH_NOT_DECLARED = "NOT_DECLARED";
        public static final String LENGTH_NO_REFERENCE = "NO_REFERENCE";
        public static final String LENGTH_NOT_CHECKED = LENGTH_NO_REFERENCE;

        public String getVcfName() { return vcfName; }
        public String getGenomeName() { return genomeName; }
        public String getMatchType() { return matchType; }
        public Long getDeclaredLength() { return declaredLength; }
        public Long getGenomeLength() { return genomeLength; }
        public String getLengthStatus() { return lengthStatus; }
        public long getMaxPosition() { return maxPosition; }
        public long getVariantCount() { return variantCount; }
        public long getSnpCount() { return snpCount; }
        public long getIndelCount() { return indelCount; }
        public boolean isPositionOutOfRange() { return positionOutOfRange; }
        public boolean isMatched() { return !MATCH_NONE.equals(matchType); }
    }

    /** Metadata file that belongs to the given stored VCF file. */
    public static File fileFor(File vcfFile) {
        if (vcfFile == null) {
            return null;
        }
        return new File(vcfFile.getParentFile(), vcfFile.getName() + FILE_SUFFIX);
    }

    public static boolean existsFor(File vcfFile) {
        File metadataFile = fileFor(vcfFile);
        return metadataFile != null && metadataFile.isFile();
    }

    public boolean isPass() { return STATUS_PASS.equals(checkStatus); }
    public boolean isFail() { return STATUS_FAIL.equals(checkStatus); }

    public String getFormattedImportDate() {
        try {
            return LocalDateTime.parse(importDate).format(TIME_FORMAT);
        } catch (RuntimeException e) {
            return importDate == null || importDate.isEmpty() ? "-" : importDate;
        }
    }

    public String getReferenceDescription() {
        if (REFERENCE_UNAVAILABLE.equals(referenceSource)) {
            return "unavailable";
        }
        return referenceSource + " (" + referenceSequenceCount + " sequences)";
    }

    /** Writes this metadata as pretty-printed UTF-8 JSON. */
    public boolean save(File target) {
        if (target == null) {
            return false;
        }
        try {
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                logger.warning("Failed to create metadata directory: " + parent.getAbsolutePath());
                return false;
            }
            simplegenomehub.util.fileio.AtomicFiles.withLock(target.toPath(), () -> {
                JsonObject existing = target.isFile() && target.length() > 0 ? JsonMetadataSupport.read(target) : originalJson;
                JsonObject merged = JsonMetadataSupport.merge(existing, GSON.toJsonTree(this)).getAsJsonObject();
                JsonMetadataSupport.write(target, merged);
                originalJson = merged;
                return null;
            });
            return true;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to save VCF metadata: " + target.getAbsolutePath(), e);
            return false;
        }
    }

    /** Reads metadata, or returns null when the file is missing or malformed. */
    public static PopulationVcfMetadata load(File source) {
        if (source == null || !source.isFile() || !FileSetTransaction.isPublished(source.toPath())) {
            return null;
        }
        try {
            JsonObject json = JsonMetadataSupport.read(source);
            PopulationVcfMetadata metadata = GSON.fromJson(json, PopulationVcfMetadata.class);
            if (metadata == null) {
                return null;
            }
            metadata.originalJson = json;
            metadata.normalizeAfterLoad();
            return metadata;
        } catch (IOException | JsonSyntaxException e) {
            logger.log(Level.WARNING, "Failed to read VCF metadata: " + source.getAbsolutePath(), e);
            return null;
        }
    }

    /** Guards against null collections produced by older or hand-edited JSON. */
    private void normalizeAfterLoad() {
        if (sampleNames == null) sampleNames = new ArrayList<>();
        if (chromosomes == null) chromosomes = new ArrayList<>();
        if (vcfOnlyChromosomes == null) vcfOnlyChromosomes = new ArrayList<>();
        if (genomeOnlyChromosomes == null) genomeOnlyChromosomes = new ArrayList<>();
        if (warnings == null) warnings = new ArrayList<>();
        if (failures == null) failures = new ArrayList<>();
        if (checkStatus == null || checkStatus.isEmpty()) checkStatus = STATUS_WARN;
        for (ChromosomeSummary summary : chromosomes) {
            if (summary != null && (summary.matchType == null || summary.matchType.isEmpty())) {
                summary.matchType = ChromosomeSummary.MATCH_NONE;
            }
        }
    }
}
