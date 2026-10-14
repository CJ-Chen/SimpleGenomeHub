package simplegenomehub.util.fileio;

import simplegenomehub.model.PopulationVcfMetadata;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.zip.GZIPInputStream;

/**
 * Recognition, archival and statistics generation for imported VCF datasets.
 * <p>
 * The original file is stored byte-for-byte (never decompressed or converted) in the data
 * unit's {@code Population/VCF} directory, together with a
 * {@link PopulationVcfMetadata} sidecar holding the statistics and the reference-genome check.
 * No variant analysis or FST calculation happens here.
 */
public final class VcfImportService {

    private static final int BUFFER_SIZE = 1 << 20;

    private VcfImportService() { }

    /** Header information read without scanning the variant table. */
    public static final class FileInfo {
        private final File file;
        private final String version;
        private final List<String> sampleNames;
        private final Map<String, Long> declaredContigs;
        private final long size;
        private final long lastModified;

        private FileInfo(File file, String version, List<String> sampleNames,
                         Map<String, Long> declaredContigs, BasicFileAttributes attributes) {
            this.file = file;
            this.version = version;
            this.sampleNames = sampleNames;
            this.declaredContigs = declaredContigs;
            this.size = attributes.size();
            this.lastModified = attributes.lastModifiedTime().toMillis();
        }

        public File getFile() { return file; }
        public String getVersion() { return version; }
        public int getSampleCount() { return sampleNames.size(); }
        public List<String> getSampleNames() { return new ArrayList<>(sampleNames); }
        public Map<String, Long> getDeclaredContigs() { return new LinkedHashMap<>(declaredContigs); }
        public long getSize() { return size; }
        public boolean isCompressed() { return file.getName().toLowerCase(Locale.ROOT).endsWith(".gz"); }
    }

    /** Result of a successful import: the stored file plus its persisted statistics. */
    public static final class ImportResult {
        private final File vcfFile;
        private final File metadataFile;
        private final PopulationVcfMetadata metadata;

        ImportResult(File vcfFile, File metadataFile, PopulationVcfMetadata metadata) {
            this.vcfFile = vcfFile;
            this.metadataFile = metadataFile;
            this.metadata = metadata;
        }

        public File getVcfFile() { return vcfFile; }
        public File getMetadataFile() { return metadataFile; }
        public PopulationVcfMetadata getMetadata() { return metadata; }
    }

    /** Reports import progress in the 0-100 range along with a human-readable phase. */
    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(int percent, String phase);
    }

    public static boolean isSupportedFile(File file) {
        if (file == null) return false;
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".vcf") || name.endsWith(".vcf.gz");
    }

    /** Reads only the header, not a potentially multi-gigabyte variant table. */
    public static FileInfo inspect(File file) throws IOException {
        if (!isSupportedFile(file)) {
            throw new IOException("Please select a .vcf or .vcf.gz file.");
        }
        Path path = file.toPath().toRealPath();
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
        if (!attributes.isRegularFile() || attributes.size() == 0 || !Files.isReadable(path)) {
            throw new IOException("The selected file is empty or cannot be read.");
        }
        boolean compressed = file.getName().toLowerCase(Locale.ROOT).endsWith(".gz");
        try (InputStream raw = Files.newInputStream(path);
             InputStream input = compressed ? new GZIPInputStream(raw, 65536) : raw;
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8), 65536)) {
            String first = reader.readLine();
            if (first != null && first.startsWith("\uFEFF")) first = first.substring(1);
            if (first == null || !first.matches("##fileformat=VCFv[0-9]+\\.[0-9]+")) {
                throw new IOException("Not a recognized VCF file: missing ##fileformat=VCFv... header.");
            }
            String version = first.substring("##fileformat=".length());
            Map<String, Long> declaredContigs = new LinkedHashMap<>();
            String line;
            while ((line = reader.readLine()) != null) {
                checkCancelled(null);
                if (line.startsWith("##contig=")) {
                    String id = extractAttribute(line, "ID=");
                    if (id != null && !id.isEmpty()) {
                        Long length = null;
                        String rawLength = extractAttribute(line, "length=");
                        if (rawLength != null) {
                            try {
                                length = Long.parseLong(rawLength.trim());
                            } catch (NumberFormatException ignored) {
                                length = null;
                            }
                        }
                        declaredContigs.put(id, length);
                    }
                    continue;
                }
                if (line.startsWith("##")) continue;
                String[] columns = line.split("\t", -1);
                String[] required = {"#CHROM", "POS", "ID", "REF", "ALT", "QUAL", "FILTER", "INFO"};
                if (columns.length < required.length) {
                    throw new IOException("Invalid VCF column header: expected #CHROM, POS, ID, REF, ALT, QUAL, FILTER, INFO.");
                }
                for (int i = 0; i < required.length; i++) {
                    if (!required[i].equals(columns[i])) {
                        throw new IOException("Invalid VCF column header: expected " + required[i] + " in column " + (i + 1) + ".");
                    }
                }
                if (columns.length > 8 && (columns.length < 10 || !"FORMAT".equals(columns[8]))) {
                    throw new IOException("Genotype columns must start with FORMAT followed by sample names.");
                }
                List<String> samples = new ArrayList<>();
                Set<String> seen = new java.util.HashSet<>();
                for (int i = 9; i < columns.length; i++) {
                    if (columns[i].trim().isEmpty() || !seen.add(columns[i])) {
                        throw new IOException("The VCF header contains empty or duplicate sample names.");
                    }
                    samples.add(columns[i].trim());
                }
                FileInfo info = new FileInfo(file.getAbsoluteFile(), version, samples, declaredContigs, attributes);
                ensureUnchanged(info);
                return info;
            }
        }
        throw new IOException("The VCF file has no #CHROM column header.");
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

    /**
     * Copies the VCF into the data unit's VCF directory, scans it once for statistics, verifies
     * chromosome names and lengths against the reference genome and publishes both files.
     * <p>
     * Nothing is published when the import is cancelled or when no chromosome name matches the
     * reference genome. Serialized so two in-process imports cannot race the no-replace move.
     */
    public static synchronized ImportResult importFile(FileInfo info, File vcfDirectory, VcfReferenceTable reference,
                                                       ProgressListener progress, BooleanSupplier cancelled) throws IOException {
        report(progress, 0, "Preparing import");
        checkCancelled(cancelled);
        ensureUnchanged(info);

        if (vcfDirectory == null || !vcfDirectory.isDirectory()) {
            throw new IOException("The selected genome data directory is no longer available.");
        }
        Path directory = vcfDirectory.toPath();
        FileSetTransaction.recover(directory);
        Path target = directory.resolve(info.getFile().getName());
        if (Files.exists(target)) {
            throw new IOException("A file with this name has already been imported:\n" + target
                + "\nPlease rename the source file if this is a different dataset.");
        }
        File metadataTarget = PopulationVcfMetadata.fileFor(target.toFile());
        if (metadataTarget.exists()) {
            throw new IOException("A statistics file already exists for this dataset:\n" + metadataTarget
                + "\nPlease rename the source file if this is a different dataset.");
        }

        Path stagedVcf = null;
        Path stagedMetadata = null;
        try {
            stagedVcf = FileSetTransaction.stageSibling(target);
            stagedMetadata = FileSetTransaction.stageSibling(metadataTarget.toPath());
            copyFile(info, stagedVcf, progress, cancelled);

            checkCancelled(cancelled);
            report(progress, 35, "Analyzing variants");
            VcfStatisticsScanner.Result scan = VcfStatisticsScanner.scan(stagedVcf.toFile(), info.isCompressed(),
                (percent, phase) -> report(progress, 35 + (int) (percent * 0.55), "Analyzing variants"),
                cancelled);

            checkCancelled(cancelled);
            report(progress, 92, "Checking reference genome");
            PopulationVcfMetadata metadata = VcfValidationService.buildMetadata(target.toFile(), info, scan, reference);
            if (metadata.isFail()) {
                throw new IOException("Import cancelled: this VCF does not match the selected genome.\n"
                    + (metadata.failures.isEmpty() ? "Chromosome names do not match." : metadata.failures.get(0)));
            }

            checkCancelled(cancelled);
            if (!metadata.save(stagedMetadata.toFile())) {
                throw new IOException("Failed to write the statistics file for this dataset.");
            }

            report(progress, 97, "Publishing dataset");
            checkCancelled(cancelled);
            ensureUnchanged(info);
            Map<Path, Path> publication = new LinkedHashMap<>();
            publication.put(stagedVcf, target);
            publication.put(stagedMetadata, metadataTarget.toPath());
            FileSetTransaction.commit(publication, java.util.Collections.emptyList(), publication.values());
            report(progress, 100, "Done");
            return new ImportResult(target.toFile(), metadataTarget, metadata);
        } finally {
            try { FileSetTransaction.discardStage(stagedVcf); }
            finally { FileSetTransaction.discardStage(stagedMetadata); }
        }
    }

    private static void copyFile(FileInfo info, Path destination, ProgressListener progress,
                                 BooleanSupplier cancelled) throws IOException {
        long copied = 0;
        int lastPercent = -1;
        try (InputStream input = Files.newInputStream(info.getFile().toPath());
             OutputStream output = Files.newOutputStream(destination)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int count;
            while ((count = input.read(buffer)) != -1) {
                checkCancelled(cancelled);
                output.write(buffer, 0, count);
                copied += count;
                int percent = (int) Math.min(34, copied * 35.0 / Math.max(1L, info.getSize()));
                if (percent != lastPercent) {
                    lastPercent = percent;
                    report(progress, percent, "Copying VCF file");
                }
            }
        }
        if (copied != info.getSize()) {
            throw new IOException("Source file changed during import. Please select it again.");
        }
    }

    private static void report(ProgressListener progress, int percent, String phase) {        if (progress != null) {
            progress.onProgress(Math.max(0, Math.min(100, percent)), phase);
        }
    }

    private static void ensureUnchanged(FileInfo info) throws IOException {
        BasicFileAttributes current = Files.readAttributes(info.file.toPath(), BasicFileAttributes.class);
        if (!current.isRegularFile() || current.size() != info.size
                || current.lastModifiedTime().toMillis() != info.lastModified) {
            throw new IOException("The source file changed after recognition. Please select it again.");
        }
    }

    private static void checkCancelled(BooleanSupplier cancelled) throws InterruptedIOException {
        if ((cancelled != null && cancelled.getAsBoolean()) || Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("Import cancelled.");
        }
    }
}
