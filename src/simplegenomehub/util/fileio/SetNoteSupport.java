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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sidecar reader/writer for the per-entry note and card colour of one saved set.
 *
 * <p>The set files keep their existing format, the note of an entry lives next to them in
 * {@code <SetFileName>.notes.txt}, so every reader of {@code .gene.txt} and
 * {@code .region.txt} (browser cards, BLAST, enrichment, extraction, ...) keeps working
 * untouched.</p>
 *
 * <p>The sidecar is plain quoted CSV, one record per entry, in display order:</p>
 *
 * <pre>
 * #####Set Notes:
 * "Gene1","salt tolerance","255,235,156"
 * "Gene2","",""
 * </pre>
 *
 * <p>Column one is the entry id - a gene/transcript id, or {@code Chr:start-end} for a
 * region set -, column two the note and column three the card colour as red,green,blue
 * numbers. An empty colour column means "no colour of its own".</p>
 */
public final class SetNoteSupport {

    public static final String NOTES_HEADER = "#####Set Notes:";
    public static final String NOTES_SUFFIX = ".notes.txt";

    /** Note text plus the optional card colour of one entry. */
    public static final class Note {
        private final String text;
        private final Integer rgb;

        public Note(String text, Integer rgb) {
            this.text = text == null ? "" : text.trim();
            this.rgb = rgb;
        }

        public String getText() {
            return text;
        }

        /** {@code null} when the entry keeps the default card colour. */
        public Integer getRgb() {
            return rgb;
        }

        public boolean hasText() {
            return !text.isEmpty();
        }

        public boolean isEmpty() {
            return text.isEmpty() && rgb == null;
        }
    }

    private SetNoteSupport() {
    }

    /** The sidecar that belongs to {@code setFile}, whether or not it exists yet. */
    public static File notesFileFor(File setFile) {
        if (setFile == null) {
            return null;
        }
        return new File(setFile.getParentFile(), setFile.getName() + NOTES_SUFFIX);
    }

    /** The key a region entry is stored and displayed under. */
    public static String regionKey(String chromosomeName, long startPos, long endPos) {
        String name = chromosomeName == null ? "" : chromosomeName.trim();
        return name + ":" + Math.min(startPos, endPos) + "-" + Math.max(startPos, endPos);
    }

    /**
     * Keys of {@code setFile} in file order, without duplicates: the gene/transcript ids, or
     * {@code Chr:start-end} for a region set. This is what a sidecar is stamped from when it
     * is created for a set that was written before notes existed.
     */
    public static List<String> readKeys(File setFile) throws IOException {
        List<String> keys = new ArrayList<>();
        if (setFile == null || !setFile.isFile()) {
            return keys;
        }

        if (RegionSetSupport.isRegionSetFileName(setFile.getName())) {
            for (RegionSetSupport.Entry entry : RegionSetSupport.readEntries(setFile)) {
                keys.add(regionKey(entry.getChromosomeName(), entry.getStartPos(), entry.getEndPos()));
            }
        } else {
            for (GeneSetSupport.Entry entry : GeneSetSupport.readEntries(setFile)) {
                if (!entry.getId().isEmpty()) {
                    keys.add(entry.getId());
                }
            }
        }

        return new ArrayList<>(new java.util.LinkedHashSet<>(keys));
    }

    /** Notes of {@code setFile} in file order; an empty map when the sidecar is missing. */
    public static Map<String, Note> read(File setFile) {
        LinkedHashMap<String, Note> notes = new LinkedHashMap<>();
        File notesFile = notesFileFor(setFile);
        if (notesFile == null || !notesFile.isFile()) {
            return notes;
        }

        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(notesFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                List<String> fields = splitFields(trimmed);
                if (fields.isEmpty()) {
                    continue;
                }
                String key = fields.get(0).trim();
                if (key.isEmpty()) {
                    continue;
                }
                String text = fields.size() > 1 ? fields.get(1) : "";
                Integer rgb = fields.size() > 2 ? parseRgb(fields.get(2)) : null;
                notes.put(key, new Note(text, rgb));
            }
        } catch (IOException ex) {
            // An unreadable sidecar simply means "no notes yet".
            return new LinkedHashMap<>();
        }
        return notes;
    }

    /**
     * Writes one record per id of {@code orderedKeys}, so the file mirrors the set and the
     * browsing order follows the file. An empty id list removes the sidecar again.
     */
    public static void write(File setFile, List<String> orderedKeys, Map<String, Note> notes) throws IOException {
        File notesFile = notesFileFor(setFile);
        if (notesFile == null) {
            return;
        }
        if (orderedKeys == null || orderedKeys.isEmpty()) {
            delete(setFile);
            return;
        }

        StringBuilder builder = new StringBuilder(NOTES_HEADER).append('\n');
        for (String key : orderedKeys) {
            if (key == null || key.trim().isEmpty()) {
                continue;
            }
            String trimmedKey = key.trim();
            builder.append(formatLine(trimmedKey, notes == null ? null : notes.get(trimmedKey))).append('\n');
        }

        File parent = notesFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create the note directory: " + parent.getAbsolutePath());
        }
        try (BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(notesFile), StandardCharsets.UTF_8))) {
            writer.write(builder.toString());
        }
    }

    /** Removes the sidecar of {@code setFile} - used when the set itself is renamed away. */
    public static void delete(File setFile) {
        File notesFile = notesFileFor(setFile);
        if (notesFile != null && notesFile.isFile() && !notesFile.delete()) {
            // A stale sidecar is harmless: it never shows up in the set listings.
        }
    }

    private static String formatLine(String key, Note note) {
        return quote(key)
            + "," + quote(note == null ? "" : note.getText())
            + "," + quote(formatRgb(note == null ? null : note.getRgb()));
    }

    /** {@code "255,235,156"}; an empty string when the entry has no colour. */
    public static String formatRgb(Integer rgb) {
        if (rgb == null) {
            return "";
        }
        return ((rgb >> 16) & 0xFF) + "," + ((rgb >> 8) & 0xFF) + "," + (rgb & 0xFF);
    }

    /** Accepts {@code 255,235,156}, {@code 255 235 156} or {@code #FFEB9C}; null when unusable. */
    private static Integer parseRgb(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }

        if (value.startsWith("#")) {
            String hex = value.substring(1).trim();
            try {
                if (hex.length() == 6) {
                    return Integer.parseInt(hex, 16) & 0xFFFFFF;
                }
                if (hex.length() == 3) {
                    int red = Integer.parseInt(hex.substring(0, 1), 16);
                    int green = Integer.parseInt(hex.substring(1, 2), 16);
                    int blue = Integer.parseInt(hex.substring(2, 3), 16);
                    return ((red * 17) << 16) | ((green * 17) << 8) | (blue * 17);
                }
            } catch (NumberFormatException ex) {
                return null;
            }
            return null;
        }

        String[] parts = value.split("[,;\\s]+");
        if (parts.length != 3) {
            return null;
        }
        try {
            int red = clamp(Integer.parseInt(parts[0].trim()));
            int green = clamp(Integer.parseInt(parts[1].trim()));
            int blue = clamp(Integer.parseInt(parts[2].trim()));
            return (red << 16) | (green << 8) | blue;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static String quote(String value) {
        String sanitized = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
        return "\"" + sanitized.replace("\"", "\"\"") + "\"";
    }

    /** Splits one CSV record, honouring quotes and doubled quotes. */
    private static List<String> splitFields(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);
            if (inQuotes) {
                if (character == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(character);
                }
            } else if (character == '"') {
                inQuotes = true;
            } else if (character == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        fields.add(current.toString());
        return fields;
    }
}
