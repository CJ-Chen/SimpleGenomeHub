package simplegenomehub.util.fileio;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Creates a temporary GFF/GTF containing selected genes and their descendants.
 */
public final class AnnotationGeneSubsetWriter {

    private AnnotationGeneSubsetWriter() {
    }

    public static File createSubset(File annotationFile, Set<String> geneIds) throws IOException {
        if (annotationFile == null || !annotationFile.isFile()) {
            throw new IOException("Annotation file is not available.");
        }

        LinkedHashSet<String> targetGeneIds = cleanIds(geneIds);
        if (targetGeneIds.isEmpty()) {
            throw new IOException("No target gene IDs were provided.");
        }

        boolean gtf = isGtf(annotationFile);
        String suffix = gtf ? ".gtf" : ".gff3";
        File subsetFile = File.createTempFile("simplegenomehub_selected_genes_", suffix);
        subsetFile.deleteOnExit();

        try {
            int writtenFeatures = gtf
                ? writeGtfSubset(annotationFile, subsetFile, targetGeneIds)
                : writeGffSubset(annotationFile, subsetFile, targetGeneIds);
            if (writtenFeatures == 0) {
                throw new IOException("No annotation records matched the selected gene IDs.");
            }
            return subsetFile;
        } catch (IOException | RuntimeException ex) {
            Files.deleteIfExists(subsetFile.toPath());
            throw ex;
        }
    }

    private static int writeGtfSubset(File source, File target, Set<String> targetGeneIds) throws IOException {
        int writtenFeatures = 0;
        try (BufferedReader reader = Files.newBufferedReader(source.toPath(), StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(target.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) {
                    writer.write(line);
                    writer.newLine();
                    continue;
                }

                String[] fields = line.split("\t", -1);
                if (fields.length < 9) {
                    continue;
                }

                Map<String, String> attributes = parseAttributes(fields[8]);
                if (containsTarget(targetGeneIds, getAttribute(attributes, "gene_id"))) {
                    writer.write(line);
                    writer.newLine();
                    writtenFeatures++;
                }
            }
        }
        return writtenFeatures;
    }

    private static int writeGffSubset(File source, File target, Set<String> targetGeneIds) throws IOException {
        Map<String, Set<String>> childrenByParent = new LinkedHashMap<>();
        LinkedHashSet<String> includedIds = new LinkedHashSet<>(targetGeneIds);

        try (BufferedReader reader = Files.newBufferedReader(source.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("##FASTA")) {
                    break;
                }
                String[] fields = line.split("\t", -1);
                if (fields.length < 9 || line.startsWith("#")) {
                    continue;
                }

                Map<String, String> attributes = parseAttributes(fields[8]);
                List<String> recordIds = splitIds(getAttribute(attributes, "ID"));
                List<String> parentIds = splitIds(getAttribute(attributes, "Parent"));
                if (matchesGene(fields[2], attributes, targetGeneIds)) {
                    includedIds.addAll(recordIds);
                }
                for (String parentId : parentIds) {
                    childrenByParent
                        .computeIfAbsent(parentId, ignored -> new LinkedHashSet<>())
                        .addAll(recordIds);
                }
            }
        }

        Deque<String> pendingIds = new ArrayDeque<>(includedIds);
        while (!pendingIds.isEmpty()) {
            Set<String> childIds = childrenByParent.get(pendingIds.removeFirst());
            if (childIds == null) {
                continue;
            }
            for (String childId : childIds) {
                if (includedIds.add(childId)) {
                    pendingIds.addLast(childId);
                }
            }
        }

        int writtenFeatures = 0;
        try (BufferedReader reader = Files.newBufferedReader(source.toPath(), StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(target.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("##FASTA")) {
                    break;
                }
                if (line.startsWith("#")) {
                    writer.write(line);
                    writer.newLine();
                    continue;
                }

                String[] fields = line.split("\t", -1);
                if (fields.length < 9) {
                    continue;
                }

                Map<String, String> attributes = parseAttributes(fields[8]);
                boolean include = matchesGene(fields[2], attributes, targetGeneIds)
                    || intersects(includedIds, splitIds(getAttribute(attributes, "ID")))
                    || intersects(includedIds, splitIds(getAttribute(attributes, "Parent")));
                if (include) {
                    writer.write(line);
                    writer.newLine();
                    writtenFeatures++;
                }
            }
        }
        return writtenFeatures;
    }

    private static boolean matchesGene(String featureType, Map<String, String> attributes,
                                       Set<String> targetGeneIds) {
        if (containsTarget(targetGeneIds, getAttribute(attributes, "gene_id"))
            || containsTarget(targetGeneIds, getAttribute(attributes, "geneID"))
            || containsTarget(targetGeneIds, getAttribute(attributes, "gene"))) {
            return true;
        }
        return "gene".equalsIgnoreCase(featureType)
            && containsTarget(targetGeneIds, getAttribute(attributes, "ID"));
    }

    private static boolean isGtf(File annotationFile) throws IOException {
        String lowerName = annotationFile.getName().toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".gtf")) {
            return true;
        }

        try (BufferedReader reader = Files.newBufferedReader(annotationFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.split("\t", -1);
                return fields.length >= 9
                    && !fields[8].contains("=")
                    && fields[8].contains("gene_id");
            }
        }
        return false;
    }

    private static Map<String, String> parseAttributes(String rawAttributes) {
        Map<String, String> attributes = new LinkedHashMap<>();
        for (String rawAttribute : rawAttributes.split(";")) {
            String attribute = rawAttribute.trim();
            if (attribute.isEmpty()) {
                continue;
            }

            int equalsIndex = attribute.indexOf('=');
            if (equalsIndex > 0) {
                attributes.put(
                    attribute.substring(0, equalsIndex).trim(),
                    stripQuotes(attribute.substring(equalsIndex + 1).trim())
                );
                continue;
            }

            int separatorIndex = firstWhitespace(attribute);
            if (separatorIndex > 0) {
                attributes.put(
                    attribute.substring(0, separatorIndex).trim(),
                    stripQuotes(attribute.substring(separatorIndex + 1).trim())
                );
            }
        }
        return attributes;
    }

    private static String getAttribute(Map<String, String> attributes, String targetKey) {
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            if (targetKey.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static int firstWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static LinkedHashSet<String> cleanIds(Set<String> ids) {
        LinkedHashSet<String> cleanedIds = new LinkedHashSet<>();
        if (ids == null) {
            return cleanedIds;
        }
        for (String id : ids) {
            if (id != null && !id.trim().isEmpty()) {
                cleanedIds.add(id.trim());
            }
        }
        return cleanedIds;
    }

    private static List<String> splitIds(String value) {
        List<String> ids = new ArrayList<>();
        if (value == null || value.trim().isEmpty()) {
            return ids;
        }
        for (String id : value.split(",")) {
            String trimmedId = id.trim();
            if (!trimmedId.isEmpty()) {
                ids.add(trimmedId);
            }
        }
        return ids;
    }

    private static boolean intersects(Set<String> expectedIds, List<String> actualIds) {
        for (String actualId : actualIds) {
            if (expectedIds.contains(actualId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsTarget(Set<String> targetIds, String value) {
        return intersects(targetIds, splitIds(value));
    }
}
