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
import java.util.List;

/**
 * Gene-set file format, the sibling of {@link RegionSetSupport}.
 *
 * <p>Rows are tab separated and hold the transcript/gene id plus the location the
 * id was resolved to, so the file stays usable without re-reading the annotation:</p>
 *
 * <pre>
 * #####Gene Set:
 * lcv2_00144&#9;contig01&#9;2001&#9;3200
 * </pre>
 *
 * <p>Positions are 1-based inclusive, exactly like {@code stat.txt}, {@code *.region.txt}
 * and the GFF3 itself. Files written before coordinates were stored (bare ids, one per
 * line, no header) are still parsed correctly: comment lines are skipped and only the
 * first column is used as the id.</p>
 */
public final class GeneSetSupport {

    public static final String GENE_SET_HEADER = "#####Gene Set:";
    public static final String GENE_SET_SUFFIX = ".gene.txt";

    /** One gene-set row: an id plus (optionally) where it sits on the genome. */
    public static final class Entry {
        private final String id;
        private final String chromosomeName;
        private final long startPos;
        private final long endPos;
        private final int lineNumber;

        public Entry(String id, String chromosomeName, long startPos, long endPos, int lineNumber) {
            this.id = id == null ? "" : id.trim();
            this.chromosomeName = chromosomeName == null ? "" : chromosomeName.trim();
            this.startPos = Math.min(startPos, endPos);
            this.endPos = Math.max(startPos, endPos);
            this.lineNumber = lineNumber;
        }

        public String getId() {
            return id;
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

        /** True when this row carries a usable location. */
        public boolean hasCoordinate() {
            return !chromosomeName.isEmpty() && startPos >= 1;
        }

        public String toLine() {
            if (!hasCoordinate()) {
                return id;
            }
            return id + "\t" + chromosomeName + "\t" + startPos + "\t" + endPos;
        }
    }

    private GeneSetSupport() {
    }

    public static boolean isGeneSetFileName(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(GENE_SET_SUFFIX);
    }

    public static String extractDisplayName(File geneSetFile) {
        return geneSetFile == null ? "" : extractDisplayName(geneSetFile.getName());
    }

    public static String extractDisplayName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return "";
        }
        if (fileName.toLowerCase().endsWith(GENE_SET_SUFFIX)) {
            return fileName.substring(0, fileName.length() - GENE_SET_SUFFIX.length());
        }
        return fileName;
    }

    public static List<File> listGeneSetFiles(File geneSetDir) {
        List<File> files = new ArrayList<>();
        if (geneSetDir == null || !geneSetDir.isDirectory()) {
            return files;
        }
        File[] found = geneSetDir.listFiles((dir, name) -> isGeneSetFileName(name));
        if (found != null) {
            files.addAll(Arrays.asList(found));
        }
        files.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        return files;
    }

    /**
     * Parses ids only (first column), skipping comments and blank lines.
     * Legacy bare-id files and the new tab-separated files both work.
     */
    public static List<String> parseIds(String rawText) {
        List<String> ids = new ArrayList<>();
        for (Entry entry : parse(rawText)) {
            if (!entry.getId().isEmpty()) {
                ids.add(entry.getId());
            }
        }
        return ids;
    }

    /**
     * Parses every row. Rows may be {@code id} alone or {@code id chr start end};
     * malformed rows are skipped (callers can compare the file's line count if needed).
     */
    public static List<Entry> parse(String rawText) {
        List<Entry> entries = new ArrayList<>();
        if (rawText == null) {
            return entries;
        }
        String[] lines = normalizeLineEndings(rawText).split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] columns = trimmed.split("\\s+");
            int lineNumber = i + 1;
            if (columns.length < 4) {
                entries.add(new Entry(columns[0], "", 0, 0, lineNumber));
                continue;
            }
            long start;
            long end;
            try {
                start = Long.parseLong(columns[2]);
                end = Long.parseLong(columns[3]);
            } catch (NumberFormatException ex) {
                // A row whose coordinates are unusable still carries a valid id.
                entries.add(new Entry(columns[0], "", 0, 0, lineNumber));
                continue;
            }
            entries.add(new Entry(columns[0], columns[1], start, end, lineNumber));
        }
        return entries;
    }

    /** Serialises entries; rows without a location keep the legacy bare-id form. */
    public static String format(List<Entry> entries) {
        StringBuilder builder = new StringBuilder();
        boolean anyCoordinate = false;
        if (entries != null) {
            for (Entry entry : entries) {
                if (entry != null && entry.hasCoordinate()) {
                    anyCoordinate = true;
                    break;
                }
            }
        }
        if (anyCoordinate) {
            builder.append(GENE_SET_HEADER).append('\n');
        }
        if (entries != null) {
            for (Entry entry : entries) {
                if (entry == null || entry.getId().isEmpty()) {
                    continue;
                }
                builder.append(entry.toLine()).append('\n');
            }
        }
        return builder.toString();
    }

    public static String readContent(File geneSetFile) throws IOException {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(geneSetFile), StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        }
    }

    public static List<Entry> readEntries(File geneSetFile) throws IOException {
        return parse(readContent(geneSetFile));
    }

    public static void writeContent(File targetFile, String content) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(targetFile), StandardCharsets.UTF_8))) {
            writer.write(normalizeLineEndings(content));
        }
    }

    public static void writeEntries(File targetFile, List<Entry> entries) throws IOException {
        writeContent(targetFile, format(entries));
    }

    private static String normalizeLineEndings(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace("\r", "\n");
    }
}
