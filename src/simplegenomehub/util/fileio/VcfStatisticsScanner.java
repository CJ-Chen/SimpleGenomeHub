package simplegenomehub.util.fileio;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.zip.GZIPInputStream;

/**
 * Single streaming pass over a VCF file that collects the statistics stored next to an
 * imported dataset: sample names, declared contig lengths, per-chromosome variant counts,
 * variant type breakdown and per-chromosome position ranges.
 * <p>
 * Genotypes are not parsed; this keeps the scan proportional to the number of records.
 */
public final class VcfStatisticsScanner {

    /** Block size for progress reporting while reading. */
    private static final int BUFFER_SIZE = 1 << 20;

    public static final int PHASE_HEADER = 0;
    public static final int PHASE_RECORDS = 1;

    private VcfStatisticsScanner() {
    }

    /** Per-chromosome counters collected during the scan. */
    public static final class ChromosomeStats {
        private final String name;
        private long variantCount;
        private long snpCount;
        private long indelCount;
        private long maxPosition;

        ChromosomeStats(String name) {
            this.name = name;
        }

        public String getName() { return name; }
        public long getVariantCount() { return variantCount; }
        public long getSnpCount() { return snpCount; }
        public long getIndelCount() { return indelCount; }
        public long getMaxPosition() { return maxPosition; }
    }

    /** Everything the metadata builder needs from one scan. */
    public static final class Result {
        private final List<String> sampleNames = new ArrayList<>();
        private final Map<String, Long> declaredContigLengths = new LinkedHashMap<>();
        private final Map<String, ChromosomeStats> chromosomeStats = new LinkedHashMap<>();
        private String fileFormat = "";
        private long totalRecords;
        private long snpCount;
        private long indelCount;
        private long multiallelicCount;
        private long otherCount;

        public List<String> getSampleNames() { return sampleNames; }
        public int getSampleCount() { return sampleNames.size(); }
        public Map<String, Long> getDeclaredContigLengths() { return declaredContigLengths; }
        public Map<String, ChromosomeStats> getChromosomeStats() { return chromosomeStats; }
        public String getFileFormat() { return fileFormat; }
        public long getTotalRecords() { return totalRecords; }
        public long getSnpCount() { return snpCount; }
        public long getIndelCount() { return indelCount; }
        public long getMultiallelicCount() { return multiallelicCount; }
        public long getOtherCount() { return otherCount; }
    }

    /** Reports scan progress in the 0-100 range of this phase. */
    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(int percent, int phase);
    }

    /**
     * Scans the given VCF once.
     *
     * @param compressed whether the file is gzip-compressed (staging names hide the extension)
     * @throws InterruptedIOException when cancelled
     */
    public static Result scan(File vcfFile, boolean compressed, ProgressListener progress,
                              BooleanSupplier cancelled) throws IOException {
        if (vcfFile == null || !vcfFile.isFile()) {
            throw new IOException("VCF file not found: " + (vcfFile == null ? "null" : vcfFile.getAbsolutePath()));
        }
        Result result = new Result();
        long fileSize = Math.max(1L, vcfFile.length());

        try (CountingInputStream counting = new CountingInputStream(Files.newInputStream(vcfFile.toPath()));
             InputStream decoded = compressed ? new GZIPInputStream(counting, BUFFER_SIZE) : counting;
             BufferedReader reader = new BufferedReader(new InputStreamReader(decoded, StandardCharsets.UTF_8), BUFFER_SIZE)) {

            String line;
            boolean headerDone = false;
            int lastPercent = -1;
            while ((line = reader.readLine()) != null) {
                checkCancelled(cancelled);
                if (line.isEmpty()) {
                    continue;
                }
                if (line.charAt(0) == '#') {
                    if (!headerDone) {
                        readHeaderLine(line, result);
                    }
                    continue;
                }
                headerDone = true;
                recordLine(line, result);

                int percent = (int) Math.min(99L, counting.getCount() * 100L / fileSize);
                if (percent != lastPercent) {
                    lastPercent = percent;
                    if (progress != null) {
                        progress.onProgress(percent, PHASE_RECORDS);
                    }
                }
            }
        }

        if (result.fileFormat.isEmpty()) {
            throw new IOException("Not a recognized VCF file: missing ##fileformat=VCFv... header.");
        }
        if (progress != null) {
            progress.onProgress(100, PHASE_RECORDS);
        }
        return result;
    }

    /** Convenience overload that infers gzip compression from the file extension. */
    public static Result scan(File vcfFile, ProgressListener progress, BooleanSupplier cancelled) throws IOException {
        boolean compressed = vcfFile != null
            && vcfFile.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".gz");
        return scan(vcfFile, compressed, progress, cancelled);
    }

    private static void readHeaderLine(String line, Result result) {
        if (line.startsWith("##fileformat=")) {
            result.fileFormat = line.substring("##fileformat=".length()).trim();
            return;
        }
        if (line.startsWith("##contig=")) {
            String id = extractAttribute(line, "ID=");
            if (id == null || id.isEmpty()) {
                return;
            }
            Long length = null;
            String rawLength = extractAttribute(line, "length=");
            if (rawLength != null) {
                try {
                    length = Long.parseLong(rawLength.trim());
                } catch (NumberFormatException ignored) {
                    length = null;
                }
            }
            result.declaredContigLengths.put(id, length);
            return;
        }
        if (line.startsWith("#CHROM")) {
            String[] columns = line.split("\t", -1);
            for (int i = 9; i < columns.length; i++) {
                String sample = columns[i].trim();
                if (!sample.isEmpty()) {
                    result.sampleNames.add(sample);
                }
            }
        }
    }

    /** Extracts {@code key=value} from a structured header value such as {@code <ID=chr1,length=100>}. */
    private static String extractAttribute(String line, String key) {
        int start = line.indexOf(key);
        if (start < 0) {
            return null;
        }
        start += key.length();
        int end = start;
        while (end < line.length()) {
            char c = line.charAt(end);
            if (c == ',' || c == '>' || c == ' ') {
                break;
            }
            end++;
        }
        return line.substring(start, end);
    }

    private static void recordLine(String line, Result result) {
        int tab1 = line.indexOf('\t');
        if (tab1 <= 0) {
            result.otherCount++;
            return;
        }
        int tab2 = line.indexOf('\t', tab1 + 1);
        if (tab2 < 0) {
            result.otherCount++;
            return;
        }
        int tab3 = line.indexOf('\t', tab2 + 1);
        if (tab3 < 0) {
            result.otherCount++;
            return;
        }
        int tab4 = line.indexOf('\t', tab3 + 1);
        if (tab4 < 0) {
            result.otherCount++;
            return;
        }
        int tab5 = line.indexOf('\t', tab4 + 1);
        if (tab5 < 0) {
            result.otherCount++;
            return;
        }

        String chrom = line.substring(0, tab1);
        String ref = line.substring(tab3 + 1, tab4);
        String alt = line.substring(tab4 + 1, tab5);

        long position;
        try {
            position = Long.parseLong(line.substring(tab1 + 1, tab2).trim());
        } catch (NumberFormatException e) {
            result.otherCount++;
            return;
        }

        result.totalRecords++;
        ChromosomeStats stats = result.chromosomeStats.computeIfAbsent(chrom, ChromosomeStats::new);
        stats.variantCount++;
        if (position > stats.maxPosition) {
            stats.maxPosition = position;
        }

        if (alt.indexOf(',') >= 0) {
            // Multi-allelic sites are tracked separately; they are not usable as biallelic SNPs.
            result.multiallelicCount++;
            return;
        }
        if (alt.indexOf('<') >= 0 || alt.indexOf('[') >= 0 || alt.indexOf(']') >= 0 || alt.indexOf('.') >= 0) {
            result.otherCount++;
            return;
        }
        if (ref.length() == 1 && alt.length() == 1) {
            result.snpCount++;
            stats.snpCount++;
        } else {
            result.indelCount++;
            stats.indelCount++;
        }
    }

    private static void checkCancelled(BooleanSupplier cancelled) throws InterruptedIOException {
        if ((cancelled != null && cancelled.getAsBoolean()) || Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("Scan cancelled.");
        }
    }

    /** Counts consumed bytes so gzip input still yields monotonic progress. */
    private static final class CountingInputStream extends FilterInputStream {
        private long count;

        CountingInputStream(InputStream delegate) {
            super(delegate);
        }

        long getCount() {
            return count;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value != -1) {
                count++;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                count += read;
            }
            return read;
        }
    }
}
