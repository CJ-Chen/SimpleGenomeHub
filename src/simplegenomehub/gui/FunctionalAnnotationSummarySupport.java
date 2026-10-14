package simplegenomehub.gui;

import simplegenomehub.model.AnnotationMetadata;
import simplegenomehub.model.GeneAnnotationData;
import simplegenomehub.model.GeneAnnotationData.AnnotationType;
import simplegenomehub.model.SpeciesInfo;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;

final class FunctionalAnnotationSummarySupport {

    private static final DateTimeFormatter IMPORT_TIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private FunctionalAnnotationSummarySupport() {
    }

    static GeneAnnotationData ensureAnnotationMetadataLoaded(SpeciesInfo species) {
        if (species == null) {
            return null;
        }

        GeneAnnotationData annotationData = species.getFunctionalAnnotations();
        if (annotationData == null && species.hasFunctionalAnnotations()) {
            species.loadFunctionalAnnotations();
            annotationData = species.getFunctionalAnnotations();
        }
        return annotationData;
    }

    static int getAnnotationCountForType(SpeciesInfo species, GeneAnnotationData annotationData,
                                         AnnotationType type) {
        if (species == null || type == null) {
            return 0;
        }

        if (annotationData != null) {
            Map<AnnotationType, Integer> counts = annotationData.getAnnotationCounts();
            Integer count = counts.get(type);
            if (count != null && count > 0) {
                return count;
            }
        }

        AnnotationMetadata metadata = resolveMetadata(species, type);
        if (metadata != null) {
            return metadata.getTotalAnnotations();
        }

        return 1;
    }

    static String buildSummaryText(SpeciesInfo species, boolean includeHeader, boolean includeLastImport) {
        GeneAnnotationData annotationData = ensureAnnotationMetadataLoaded(species);

        StringBuilder summary = new StringBuilder();
        if (includeHeader) {
            summary.append("FUNCTIONAL ANNOTATION SUMMARY\n");
            summary.append("================================\n\n");
        }

        summary.append("Species: ").append(species != null ? species.getSpeciesName() : "-").append("\n");

        Set<AnnotationType> availableTypes = species != null
            ? species.getAvailableAnnotationTypes()
            : java.util.Collections.emptySet();

        if (availableTypes.isEmpty()) {
            summary.append("Total annotated genes: 0\n\n");
            summary.append("Annotation Types:\n");
            if (includeHeader) {
                summary.append("-----------------\n");
            }
            summary.append("No annotations imported yet.\n");
            if (includeHeader) {
                summary.append("\nUse 'Import Annotations' to add\n");
                summary.append("GO, KEGG, or other functional\n");
                summary.append("annotation data.\n");
            }
            return summary.toString();
        }

        int totalAnnotatedGenes = 0;
        int totalAnnotations = 0;
        StringBuilder typeLines = new StringBuilder();

        for (AnnotationType type : availableTypes) {
            int count = getAnnotationCountForType(species, annotationData, type);
            typeLines.append(String.format("%-12s: %,6d annotations%n", type.getShortName(), count));
            totalAnnotations += count;

            AnnotationMetadata metadata = resolveMetadata(species, type);
            if (metadata != null) {
                totalAnnotatedGenes = Math.max(totalAnnotatedGenes, metadata.getUniqueGenes());
            }
        }

        summary.append("Total annotated genes: ").append(totalAnnotatedGenes).append("\n\n");
        summary.append("Annotation Types:\n");
        if (includeHeader) {
            summary.append("-----------------\n");
        }
        summary.append(typeLines);
        summary.append(String.format("%nTotal: %,d annotations%n", totalAnnotations));

        if (includeLastImport) {
            appendLastImport(summary, species, annotationData, availableTypes);
        }

        return summary.toString();
    }

    private static void appendLastImport(StringBuilder summary, SpeciesInfo species,
                                         GeneAnnotationData annotationData,
                                         Set<AnnotationType> availableTypes) {
        if (summary == null || species == null || availableTypes == null || availableTypes.isEmpty()) {
            return;
        }

        if (annotationData != null && annotationData.getImportTime() != null) {
            summary.append("\nLast import: ");
            summary.append(annotationData.getImportTime().format(IMPORT_TIME_FORMAT));
            return;
        }

        AnnotationType firstType = availableTypes.iterator().next();
        AnnotationMetadata metadata = resolveMetadata(species, firstType);
        if (metadata != null && metadata.getImportDate() != null) {
            summary.append("\nLast import: ");
            summary.append(metadata.getImportDate().format(IMPORT_TIME_FORMAT));
        }
    }

    private static AnnotationMetadata resolveMetadata(SpeciesInfo species, AnnotationType type) {
        if (species == null || type == null) {
            return null;
        }

        AnnotationMetadata metadata = species.getAnnotationMetadata(type);
        if (metadata != null) {
            return metadata;
        }

        if (species.loadAnnotationMetadata(type)) {
            return species.getAnnotationMetadata(type);
        }

        return null;
    }
}
