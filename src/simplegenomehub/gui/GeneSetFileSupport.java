package simplegenomehub.gui;

import simplegenomehub.util.fileio.GeneSetSupport;
import simplegenomehub.util.fileio.SetNoteSupport;
import simplegenomehub.util.fileio.FileSetTransaction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Collections;
import simplegenomehub.util.fileio.RegionSetSupport;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class GeneSetFileSupport {

    static final String REGION_SET_HEADER = RegionSetSupport.REGION_SET_HEADER;
    static final String GENE_SET_SUFFIX = GeneSetSupport.GENE_SET_SUFFIX;
    static final String REGION_SET_SUFFIX = RegionSetSupport.REGION_SET_SUFFIX;

    enum SetKind {
        GENE,
        REGION
    }

    private GeneSetFileSupport() {
    }

    /**
     * IDs only. Comment lines are skipped and only the first column is used, so the old
     * bare-id files and the new id+chromosome+start+end rows both parse correctly.
     */
    static List<String> parseGeneIds(String rawText) {
        LinkedHashSet<String> uniqueGeneIds = new LinkedHashSet<>(GeneSetSupport.parseIds(rawText));
        return new ArrayList<>(uniqueGeneIds);
    }

    /** Writes id + chromosome + start + end rows (1-based inclusive) whenever known. */
    static void writeGeneSetEntries(File outputFile, List<GeneSetSupport.Entry> entries) throws IOException {
        GeneSetSupport.writeEntries(outputFile, entries);
    }

    static List<GeneSetSupport.Entry> readGeneSetEntries(File geneSetFile) throws IOException {
        return GeneSetSupport.readEntries(geneSetFile);
    }

    /** Commit the main set and notes together, retiring old names only after success. */
    static void writeSetAndNotes(File target, String content, List<String> orderedKeys,
                                 Map<String, SetNoteSupport.Note> notes, File original) throws IOException {
        Path destination = target.toPath().toAbsolutePath().normalize();
        Path notesDestination = SetNoteSupport.notesFileFor(target).toPath().toAbsolutePath().normalize();
        boolean newName = original == null || !destination.equals(original.toPath().toAbsolutePath().normalize());
        if (newName && (Files.exists(destination) || Files.exists(notesDestination)))
            throw new IOException("Set name already exists: " + target.getName());
        Path staged = FileSetTransaction.stageSibling(destination);
        Path stagedNotes = null;
        try {
            Files.writeString(staged, content, StandardCharsets.UTF_8);
            stagedNotes = FileSetTransaction.stageSibling(notesDestination);
            SetNoteSupport.writeStaged(stagedNotes, orderedKeys, notes);
            Map<Path, Path> replacements = new LinkedHashMap<>();
            replacements.put(staged, destination);
            replacements.put(stagedNotes, notesDestination);
            List<Path> retired = new ArrayList<>();
            if (original != null && newName) {
                retired.add(original.toPath());
                retired.add(SetNoteSupport.notesFileFor(original).toPath());
            }
            FileSetTransaction.commit(replacements, retired, newName
                ? java.util.Arrays.asList(destination, notesDestination) : Collections.emptyList());
        } finally {
            FileSetTransaction.discardStage(staged);
            if (stagedNotes != null) FileSetTransaction.discardStage(stagedNotes);
        }
    }

    static void writeGeneSetAndNotes(File target, List<GeneSetSupport.Entry> entries, List<String> inputIds,
                                     Map<String, List<String>> resolvedByInput,
                                     Map<String, SetNoteSupport.Note> notes, File original) throws IOException {
        List<String> savedIds = new ArrayList<>();
        for (GeneSetSupport.Entry entry : entries) savedIds.add(entry.getId());
        writeSetAndNotes(target, GeneSetSupport.format(entries), savedIds,
            SetNoteUiSupport.alignToSavedIds(inputIds, resolvedByInput, notes, savedIds), original);
    }

    static String formatGeneIds(List<String> geneIds) {
        return String.join("\n", geneIds);
    }

    static boolean hasRegionEntries(String rawText) {
        String[] lines = normalizeLineEndings(rawText).split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !REGION_SET_HEADER.equals(trimmed)) {
                return true;
            }
        }
        return false;
    }

    static List<RegionEntry> parseRegionEntries(String rawText) {
        List<RegionSetSupport.Entry> parsedEntries = RegionSetSupport.parse(rawText);
        List<RegionEntry> entries = new ArrayList<>(parsedEntries.size());
        for (RegionSetSupport.Entry parsedEntry : parsedEntries) {
            entries.add(new RegionEntry(
                parsedEntry.getChromosomeName(),
                parsedEntry.getStartPos(),
                parsedEntry.getEndPos(),
                parsedEntry.getLineNumber()
            ));
        }
        return entries;
    }

    static String formatRegionSetContent(List<RegionEntry> entries) {
        List<RegionSetSupport.Entry> convertedEntries = new ArrayList<>(entries.size());
        for (RegionEntry entry : entries) {
            convertedEntries.add(new RegionSetSupport.Entry(
                entry.getChromosomeName(),
                entry.getStartPos(),
                entry.getEndPos(),
                entry.getLineNumber()
            ));
        }
        return RegionSetSupport.format(convertedEntries);
    }

    static String readGeneSetContent(File geneSetFile) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(geneSetFile), StandardCharsets.UTF_8))) {
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

    static SetKind detectSetKind(File geneSetFile) {
        if (geneSetFile == null) {
            return null;
        }
        return detectSetKind(geneSetFile.getName());
    }

    static SetKind detectSetKind(String fileName) {
        if (fileName == null) {
            return null;
        }

        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(REGION_SET_SUFFIX)) {
            return SetKind.REGION;
        }
        if (lowerName.endsWith(GENE_SET_SUFFIX)) {
            return SetKind.GENE;
        }
        return null;
    }

    static boolean looksLikeRegionSetFile(File geneSetFile) {
        return detectSetKind(geneSetFile) == SetKind.REGION;
    }

    static boolean isStandardSetFileName(String fileName) {
        return detectSetKind(fileName) != null;
    }

    static String buildStandardFileName(String baseName, SetKind setKind) {
        if (setKind == null) {
            throw new IllegalArgumentException("Set kind is required.");
        }
        return baseName + (setKind == SetKind.REGION ? REGION_SET_SUFFIX : GENE_SET_SUFFIX);
    }

    static String extractDisplayName(File geneSetFile) {
        if (geneSetFile == null) {
            return "";
        }
        return extractDisplayName(geneSetFile.getName());
    }

    static String extractDisplayName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return "";
        }

        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(GENE_SET_SUFFIX)) {
            return fileName.substring(0, fileName.length() - GENE_SET_SUFFIX.length());
        }
        if (lowerName.endsWith(REGION_SET_SUFFIX)) {
            return fileName.substring(0, fileName.length() - REGION_SET_SUFFIX.length());
        }

        if (lowerName.endsWith(".txt")) {
            return fileName.substring(0, fileName.length() - 4);
        }
        return fileName;
    }

    static boolean containsInvalidFileNameChars(String fileName) {
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

    static Set<String> loadGeneIdsFromAnnotation(File annotationFile) throws IOException {
        Set<String> geneIds = new LinkedHashSet<>();

        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(annotationFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }

                String[] fields = line.split("\t");
                if (fields.length < 9) {
                    continue;
                }

                String featureType = fields[2].trim();
                if (!isGeneFeature(featureType)) {
                    continue;
                }

                String geneId = extractGeneId(fields[8]);
                if (geneId != null && !geneId.isEmpty()) {
                    geneIds.add(geneId);
                }
            }
        }

        return geneIds;
    }

    static Map<String, Long> loadChromosomeSizesFromStats(File statsFile) throws IOException {
        return RegionSetSupport.loadChromosomeSizesFromStats(statsFile);
    }

    static List<String> validateRegionEntries(List<RegionEntry> entries, Map<String, Long> chromosomeSizes) {
        List<String> errors = new ArrayList<>();
        for (RegionEntry entry : entries) {
            Long chromosomeSize = chromosomeSizes.get(entry.getChromosomeName());
            if (chromosomeSize == null) {
                errors.add("Line " + entry.getLineNumber() + ": chromosome not found - " + entry.getChromosomeName());
                continue;
            }

            if (entry.getStartPos() > chromosomeSize || entry.getEndPos() > chromosomeSize) {
                errors.add("Line " + entry.getLineNumber() + ": region exceeds chromosome size for "
                    + entry.getChromosomeName() + " (size=" + chromosomeSize + ")");
            }
        }
        return errors;
    }

    static void writeTextFile(File outputFile, String content) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {
            writer.write(normalizeLineEndings(content));
        }
    }

    static void writeGeneSetFile(File outputFile, List<String> geneIds) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {
            for (String geneId : geneIds) {
                writer.write(geneId);
                writer.newLine();
            }
        }
    }

    private static boolean isGeneFeature(String featureType) {
        String normalized = featureType.toLowerCase();
        return normalized.equals("gene") || normalized.equals("pseudogene");
    }

    private static String extractGeneId(String attributes) {
        String[] parts = attributes.split(";");
        String fallbackName = null;

        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith("ID=")) {
                return trimmed.substring(3).trim();
            }
            if (trimmed.startsWith("gene_id=")) {
                return trimmed.substring("gene_id=".length()).trim();
            }
            if (trimmed.startsWith("geneID=")) {
                return trimmed.substring("geneID=".length()).trim();
            }
            if (trimmed.startsWith("Name=")) {
                fallbackName = trimmed.substring(5).trim();
            }
        }

        return fallbackName;
    }

    private static String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    static final class RegionEntry {
        private final String chromosomeName;
        private final long startPos;
        private final long endPos;
        private final int lineNumber;

        RegionEntry(String chromosomeName, long startPos, long endPos, int lineNumber) {
            this.chromosomeName = chromosomeName;
            this.startPos = startPos;
            this.endPos = endPos;
            this.lineNumber = lineNumber;
        }

        String getChromosomeName() {
            return chromosomeName;
        }

        long getStartPos() {
            return startPos;
        }

        long getEndPos() {
            return endPos;
        }

        int getLineNumber() {
            return lineNumber;
        }
    }
}
