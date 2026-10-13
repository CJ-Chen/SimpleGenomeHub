package simplegenomehub.util.fileio;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Canonical reader/writer for {@code .region.txt} files.
 *
 * <p>The file format is a {@code #####Region Set:} header line followed by one
 * {@code ChrName StartPos EndPos} entry per line. This class is UI-free so that
 * every browser (FST, GWAS, ...) can share the exact same storage format.</p>
 */
public final class RegionSetSupport {

    public static final String REGION_SET_HEADER = "#####Region Set:";
    public static final String REGION_SET_SUFFIX = ".region.txt";

    private RegionSetSupport() {
    }

    /** A single {@code ChrName StartPos EndPos} entry (1-based, inclusive). */
    public static final class Entry {
        private final String chromosomeName;
        private final long startPos;
        private final long endPos;
        private final int lineNumber;

        public Entry(String chromosomeName, long startPos, long endPos, int lineNumber) {
            this.chromosomeName = chromosomeName;
            this.startPos = startPos;
            this.endPos = endPos;
            this.lineNumber = lineNumber;
        }

        public String getChromosomeName() {
            return chromosomeName;
        }

        public long getStartPos() {
            return startPos;
        }

        public long getEndPos() {
            return endPos;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public boolean sameRegion(Entry other) {
            if (other == null || chromosomeName == null) {
                return false;
            }
            return chromosomeName.equals(other.chromosomeName)
                && startPos == other.startPos
                && endPos == other.endPos;
        }

        public String toLine() {
            return chromosomeName + " " + startPos + " " + endPos;
        }
    }

    public static List<File> listRegionSetFiles(File geneSetDir) {
        List<File> files = new ArrayList<>();
        if (geneSetDir == null || !geneSetDir.isDirectory()) {
            return files;
        }

        File[] regionSetFiles = geneSetDir.listFiles((dir, name) -> isRegionSetFileName(name));
        if (regionSetFiles == null || regionSetFiles.length == 0) {
            return files;
        }

        Arrays.sort(regionSetFiles, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        files.addAll(Arrays.asList(regionSetFiles));
        return files;
    }

    public static boolean isRegionSetFileName(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(REGION_SET_SUFFIX);
    }

    public static String buildFileName(String baseName) {
        return baseName + REGION_SET_SUFFIX;
    }

    public static String extractDisplayName(File regionSetFile) {
        if (regionSetFile == null) {
            return "";
        }
        String fileName = regionSetFile.getName();
        if (isRegionSetFileName(fileName)) {
            return fileName.substring(0, fileName.length() - REGION_SET_SUFFIX.length());
        }
        return fileName;
    }

    public static boolean containsInvalidFileNameChars(String fileName) {
        return fileName.contains("\\")
            || fileName.contains("/")
            || fileName.contains(":")
            || fileName.contains("*")
            || fileName.contains("?")
            || fileName.contains("\"")
            || fileName.contains("<")
            || fileName.contains(">")
            || fileName.contains("|");
    }

    public static boolean hasEntries(String rawText) {
        String[] lines = normalizeLineEndings(rawText).split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !REGION_SET_HEADER.equals(trimmed)) {
                return true;
            }
        }
        return false;
    }

    public static List<Entry> parse(String rawText) {
        String[] lines = normalizeLineEndings(rawText).split("\n");
        List<Entry> entries = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.isEmpty() || REGION_SET_HEADER.equals(trimmed)) {
                continue;
            }

            String[] columns = trimmed.split("\\s+");
            int lineNumber = i + 1;
            if (columns.length != 3) {
                errors.add("Line " + lineNumber + ": expected 'ChrName StartPos EndPos'");
                continue;
            }

            long startPos;
            long endPos;
            try {
                startPos = Long.parseLong(columns[1]);
                endPos = Long.parseLong(columns[2]);
            } catch (NumberFormatException e) {
                errors.add("Line " + lineNumber + ": StartPos and EndPos must be integers");
                continue;
            }

            if (startPos < 1 || endPos < 1) {
                errors.add("Line " + lineNumber + ": StartPos and EndPos must be greater than 0");
                continue;
            }

            if (startPos > endPos) {
                errors.add("Line " + lineNumber + ": StartPos cannot be greater than EndPos");
                continue;
            }

            entries.add(new Entry(columns[0], startPos, endPos, lineNumber));
        }

        if (entries.isEmpty()) {
            errors.add("No valid region entries were provided.");
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("\n", errors));
        }
        return entries;
    }

    public static String format(List<Entry> entries) {
        StringBuilder builder = new StringBuilder(REGION_SET_HEADER);
        if (entries != null) {
            for (Entry entry : entries) {
                builder.append('\n').append(entry.toLine());
            }
        }
        return builder.toString();
    }

    public static String readContent(File regionSetFile) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(regionSetFile), StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (!firstLine) {
                    builder.append('\n');
                }
                builder.append(line);
                firstLine = false;
            }
        }
        return builder.toString();
    }

    public static List<Entry> readEntries(File regionSetFile) throws IOException {
        return parse(readContent(regionSetFile));
    }

    public static void writeContent(File targetFile, String content) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(targetFile), StandardCharsets.UTF_8))) {
            writer.write(normalizeLineEndings(content));
        }
    }

    /**
     * Appends {@code entry} to an existing region set unless the exact same
     * region is already present.
     *
     * @return {@code true} when the entry was written, {@code false} when it was a duplicate
     */
    public static boolean appendIfAbsent(File regionSetFile, Entry entry) throws IOException {
        List<Entry> entries = readEntries(regionSetFile);
        for (Entry existing : entries) {
            if (existing.sameRegion(entry)) {
                return false;
            }
        }
        entries.add(entry);
        writeContent(regionSetFile, format(entries));
        return true;
    }

    public static File createRegionSet(File geneSetDir, String baseName, List<Entry> entries) throws IOException {
        if (geneSetDir == null) {
            throw new IOException("GeneSet directory is not available.");
        }
        if (baseName == null || baseName.trim().isEmpty()) {
            throw new IllegalArgumentException("Region Set Name must not be empty.");
        }
        String name = baseName.trim();
        if (containsInvalidFileNameChars(name)) {
            throw new IllegalArgumentException("Region Set Name contains invalid filename characters.");
        }
        if (!geneSetDir.exists() && !geneSetDir.mkdirs()) {
            throw new IOException("Failed to create GeneSet directory: " + geneSetDir.getAbsolutePath());
        }

        File targetFile = new File(geneSetDir, buildFileName(name));
        if (targetFile.exists()) {
            throw new IOException("Region Set Name conflict: " + targetFile.getName());
        }
        writeContent(targetFile, format(entries));
        return targetFile;
    }

    public static Map<String, Long> loadChromosomeSizesFromStats(File statsFile) throws IOException {
        Map<String, String> chromosomeNamesByPrefix = new LinkedHashMap<>();
        Map<String, Long> chromosomeSizesByPrefix = new LinkedHashMap<>();
        Map<String, Long> chromosomeSizes = new LinkedHashMap<>();
        boolean inChromosomeBlock = false;

        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(statsFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!inChromosomeBlock) {
                    if ("#####################chromosome stat".equals(trimmed)) {
                        inChromosomeBlock = true;
                    }
                    continue;
                }

                if (trimmed.startsWith("#####################")) {
                    break;
                }
                if (trimmed.isEmpty()) {
                    continue;
                }

                int separatorIndex = trimmed.indexOf('=');
                if (separatorIndex <= 0) {
                    continue;
                }

                String key = trimmed.substring(0, separatorIndex).trim();
                String value = trimmed.substring(separatorIndex + 1).trim();
                if (key.endsWith(".name")) {
                    chromosomeNamesByPrefix.put(key.substring(0, key.length() - 5), value);
                } else if (key.endsWith(".size")) {
                    try {
                        chromosomeSizesByPrefix.put(
                            key.substring(0, key.length() - 5),
                            Long.parseLong(value.replace(",", ""))
                        );
                    } catch (NumberFormatException ignored) {
                        // Ignore malformed size lines and let validation fail later if needed.
                    }
                }
            }
        }

        for (Map.Entry<String, String> entry : chromosomeNamesByPrefix.entrySet()) {
            Long chromosomeSize = chromosomeSizesByPrefix.get(entry.getKey());
            if (chromosomeSize != null) {
                chromosomeSizes.put(entry.getValue(), chromosomeSize);
            }
        }
        return chromosomeSizes;
    }

    private static String normalizeLineEndings(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
    }
}
