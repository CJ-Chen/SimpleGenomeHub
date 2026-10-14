package simplegenomehub.util.fileio;

import simplegenomehub.model.GenomeData;
import simplegenomehub.model.SpeciesInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reference chromosome names and lengths used to validate imported VCF datasets.
 * <p>
 * Lengths come from {@code stat.txt} (written for every imported FASTA sequence) and
 * fall back to the samtools-style {@code .fai} index when the statistics file is absent.
 */
public final class VcfReferenceTable {

    private static final Logger logger = Logger.getLogger(VcfReferenceTable.class.getName());

    private final Map<String, Long> lengths = new LinkedHashMap<>();
    private final String source;
    private final long genomeSize;
    private final String speciesName;
    private final String speciesVersion;

    private VcfReferenceTable(Map<String, Long> lengths, String source, long genomeSize,
                              String speciesName, String speciesVersion) {
        this.lengths.putAll(lengths);
        this.source = source;
        this.genomeSize = genomeSize;
        this.speciesName = speciesName;
        this.speciesVersion = speciesVersion;
    }

    public static VcfReferenceTable unavailable(SpeciesInfo species) {
        return new VcfReferenceTable(new LinkedHashMap<>(), "unavailable", 0,
            species != null ? species.getSpeciesName() : "",
            species != null ? species.getVersion() : "");
    }

    /** Builds a table directly from name/length pairs (used by tests and callers with their own source). */
    public static VcfReferenceTable of(Map<String, Long> lengths, String source) {
        return of(lengths, source, "", "");
    }

    /** Builds a table directly from name/length pairs (used by tests and callers with their own source). */
    public static VcfReferenceTable of(Map<String, Long> lengths, String source,
                                       String speciesName, String speciesVersion) {
        Map<String, Long> entries = new LinkedHashMap<>();
        if (lengths != null) {
            for (Map.Entry<String, Long> entry : lengths.entrySet()) {
                if (entry.getKey() != null && !entry.getKey().isEmpty() && entry.getValue() != null) {
                    entries.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return new VcfReferenceTable(entries, source, sum(entries),
            speciesName == null ? "" : speciesName,
            speciesVersion == null ? "" : speciesVersion);
    }

    /** Builds the table from stat.txt, falling back to the FASTA index. */
    public static VcfReferenceTable fromSpecies(SpeciesInfo species) {
        if (species == null) {
            return unavailable(null);
        }

        File statsFile = species.getStatsFile();
        if (statsFile != null && statsFile.isFile()) {
            GenomeData data = GenomeData.loadFromFile(statsFile);
            Map<String, Long> fromStats = new LinkedHashMap<>();
            for (GenomeData.ChromosomeStat stat : data.getChromosomeStats()) {
                if (stat != null && stat.getName() != null && !stat.getName().isEmpty()) {
                    fromStats.put(stat.getName(), stat.getSize());
                }
            }
            if (!fromStats.isEmpty()) {
                long genomeSize = data.getGenomeSize() > 0 ? data.getGenomeSize() : sum(fromStats);
                return new VcfReferenceTable(fromStats, "stat.txt", genomeSize,
                    species.getSpeciesName(), species.getVersion());
            }
            logger.info("stat.txt contains no chromosome entries for " + species.getSpeciesDirectoryName());
        }

        VcfReferenceTable fromIndex = fromFastaIndex(species);
        if (fromIndex != null) {
            return fromIndex;
        }

        logger.warning("No reference chromosome table available for " + species.getSpeciesDirectoryName());
        return unavailable(species);
    }

    private static VcfReferenceTable fromFastaIndex(SpeciesInfo species) {
        File genomeFile = species.getGenomeFile();
        if (genomeFile == null) {
            return null;
        }
        File indexFile = new File(genomeFile.getAbsolutePath() + ".fai");
        if (!indexFile.isFile()) {
            return null;
        }
        try {
            Map<String, FastaIndexBuilder.IndexEntry> entries = FastaIndexBuilder.loadIndex(indexFile);
            if (entries == null || entries.isEmpty()) {
                return null;
            }
            Map<String, Long> fromIndex = new LinkedHashMap<>();
            for (Map.Entry<String, FastaIndexBuilder.IndexEntry> entry : entries.entrySet()) {
                fromIndex.put(entry.getKey(), entry.getValue().getLength());
            }
            return new VcfReferenceTable(fromIndex, "FASTA index", sum(fromIndex),
                species.getSpeciesName(), species.getVersion());
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to read FASTA index: " + indexFile.getAbsolutePath(), e);
            return null;
        }
    }

    private static long sum(Map<String, Long> lengths) {
        long total = 0;
        for (Long value : lengths.values()) {
            if (value != null) {
                total += value;
            }
        }
        return total;
    }

    public boolean isAvailable() {
        return !lengths.isEmpty();
    }

    public String getSource() {
        return source;
    }

    public long getGenomeSize() {
        return genomeSize;
    }

    public int getSequenceCount() {
        return lengths.size();
    }

    public String getSpeciesName() {
        return speciesName;
    }

    public String getSpeciesVersion() {
        return speciesVersion;
    }

    public List<String> getSequenceNames() {
        return new ArrayList<>(lengths.keySet());
    }

    public Long lengthOf(String referenceName) {
        return referenceName == null ? null : lengths.get(referenceName);
    }

    /**
     * Resolves a VCF chromosome name to the reference name.
     *
     * @return the exact reference name, or null when nothing matched
     */
    public String resolve(String vcfName) {
        if (vcfName == null || vcfName.isEmpty()) {
            return null;
        }
        if (lengths.containsKey(vcfName)) {
            return vcfName;
        }
        String normalized = normalizeName(vcfName);
        if (normalized.isEmpty()) {
            return null;
        }
        for (String referenceName : lengths.keySet()) {
            if (normalizeName(referenceName).equals(normalized)) {
                return referenceName;
            }
        }
        return null;
    }

    public boolean isExactMatch(String vcfName) {
        return vcfName != null && lengths.containsKey(vcfName);
    }

    /**
     * Normalizes a chromosome name for tolerant matching: drops a leading
     * {@code chr} prefix, removes leading zeros and lowercases the remainder.
     * This maps {@code chr1}, {@code Chr01} and {@code 1} onto the same key.
     */
    public static String normalizeName(String rawName) {
        if (rawName == null) {
            return "";
        }
        String name = rawName.trim();
        if (name.isEmpty()) {
            return "";
        }
        if (name.regionMatches(true, 0, "chr", 0, 3) && name.length() > 3) {
            name = name.substring(3);
        }
        int firstMeaningful = 0;
        while (firstMeaningful < name.length() - 1 && name.charAt(firstMeaningful) == '0') {
            firstMeaningful++;
        }
        name = name.substring(firstMeaningful);
        return name.toLowerCase(Locale.ROOT);
    }
}
