package simplegenomehub.gui;

import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.util.fileio.GeneSetSupport;

import javax.swing.*;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

final class GeneSetGeneIdValidationSupport {

    private GeneSetGeneIdValidationSupport() {
    }

    static ValidationResult validateGeneIds(SpeciesInfo species, List<String> inputIds) throws Exception {
        return validateGeneIds(species, inputIds, false);
    }

    static ValidationResult validateGeneIdsFailFast(SpeciesInfo species, List<String> inputIds) throws Exception {
        return validateGeneIds(species, inputIds, true);
    }

    private static ValidationResult validateGeneIds(SpeciesInfo species, List<String> inputIds,
                                                     boolean stopOnFirstMissingId) throws Exception {
        GeneDataRetriever dataRetriever = new GeneDataRetriever();
        // Loaded once: the same index answers the resolution below and the locations we hand
        // back, so saving a set never has to parse the annotation a second time.
        GeneSetImportSupport.AnnotationIndex annotationIndex = GeneSetImportSupport.loadAnnotationIndex(species);
        Map<String, GeneSetSupport.Entry> entriesById = annotationIndex.entriesById();

        LinkedHashSet<String> resolvedTranscriptIds = new LinkedHashSet<>();
        List<String> missingIds = new ArrayList<>();
        List<String> correctedLines = new ArrayList<>();
        // Which input id produced which saved ids, so per-id notes can follow the resolution.
        Map<String, List<String>> resolvedIdsByInputId = new LinkedHashMap<>();

        for (String inputId : inputIds) {
            if (inputId == null || inputId.trim().isEmpty()) {
                continue;
            }

            String trimmedInputId = inputId.trim();
            List<String> resolved = new ArrayList<>();
            String note = null;

            String canonicalTranscriptId = annotationIndex.findCanonicalTranscriptId(trimmedInputId);
            if (canonicalTranscriptId != null) {
                resolved.add(canonicalTranscriptId);
                if (!canonicalTranscriptId.equals(trimmedInputId)) {
                    note = "Transcript found: " + trimmedInputId + " -> " + canonicalTranscriptId;
                }
            }

            if (resolved.isEmpty()) {
                List<String> geneTranscriptIds = annotationIndex.getTranscriptsForGene(trimmedInputId);
                if (!geneTranscriptIds.isEmpty()) {
                    resolved.addAll(geneTranscriptIds);
                    note = "Gene expanded: " + trimmedInputId + " -> " + describe(geneTranscriptIds);
                }
            }

            if (resolved.isEmpty()) {
                String matchedTranscriptId = annotationIndex.findBestTranscriptMatch(trimmedInputId);
                if (matchedTranscriptId != null) {
                    resolved.add(matchedTranscriptId);
                    if (!matchedTranscriptId.equals(trimmedInputId)) {
                        note = "Transcript matched: " + trimmedInputId + " -> " + matchedTranscriptId;
                    }
                }
            }

            if (resolved.isEmpty()) {
                GeneSearchResult searchResult = dataRetriever.searchGene(trimmedInputId, species);
                if (searchResult != null && searchResult.isFound()) {
                    List<String> resolvedGeneTranscripts =
                        annotationIndex.getTranscriptsForGene(searchResult.getResolvedGeneId());
                    if (!resolvedGeneTranscripts.isEmpty()) {
                        resolved.addAll(resolvedGeneTranscripts);
                        note = "Gene found by search: " + trimmedInputId + " -> "
                            + describe(resolvedGeneTranscripts);
                    }
                }
            }

            if (resolved.isEmpty()) {
                String matchedGeneId = annotationIndex.findBestGeneMatch(trimmedInputId);
                if (matchedGeneId != null) {
                    List<String> matchedGeneTranscripts = annotationIndex.getTranscriptsForGene(matchedGeneId);
                    if (!matchedGeneTranscripts.isEmpty()) {
                        resolved.addAll(matchedGeneTranscripts);
                        note = "Gene matched: " + trimmedInputId + " -> " + describe(matchedGeneTranscripts);
                    }
                }
            }

            if (resolved.isEmpty()) {
                missingIds.add(trimmedInputId);
                if (stopOnFirstMissingId) {
                    return new ValidationResult(new ArrayList<>(resolvedTranscriptIds), missingIds, correctedLines,
                        entriesFor(entriesById, resolvedTranscriptIds), resolvedIdsByInputId);
                }
                continue;
            }

            resolvedIdsByInputId.put(trimmedInputId, new ArrayList<>(resolved));
            resolvedTranscriptIds.addAll(resolved);
            if (note != null) {
                correctedLines.add(note);
            }
        }

        return new ValidationResult(new ArrayList<>(resolvedTranscriptIds), missingIds, correctedLines,
            entriesFor(entriesById, resolvedTranscriptIds), resolvedIdsByInputId);
    }

    /**
     * Names what an input id resolved to. A single transcript is spelled out - the plain
     * count ("-> 1 transcripts") told the user nothing about which transcript was picked.
     */
    private static String describe(List<String> transcriptIds) {
        if (transcriptIds.size() == 1) {
            return transcriptIds.get(0);
        }
        if (transcriptIds.size() <= 3) {
            return transcriptIds.size() + " transcripts: " + String.join(", ", transcriptIds);
        }
        return transcriptIds.size() + " transcripts";
    }

    /** Locations of the resolved transcripts, ready to be written into the set file. */
    private static List<GeneSetSupport.Entry> entriesFor(
            Map<String, GeneSetSupport.Entry> entriesById, Collection<String> transcriptIds) {
        List<GeneSetSupport.Entry> entries = new ArrayList<>();
        for (String transcriptId : transcriptIds) {
            GeneSetSupport.Entry entry = entriesById.get(transcriptId);
            entries.add(entry == null ? new GeneSetSupport.Entry(transcriptId, "", 0, 0, 0) : entry);
        }
        return entries;
    }

    static void showValidationResult(Component parent, ValidationResult validationResult) {
        JTextArea messageArea = new JTextArea(buildValidationMessage(validationResult), 16, 56);
        messageArea.setEditable(false);
        messageArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        messageArea.setCaretPosition(0);

        String title = validationResult.getMissingGeneIds().isEmpty()
            ? "Transcript IDs Corrected"
            : "ID Validation";

        JOptionPane.showMessageDialog(
            parent,
            new JScrollPane(messageArea),
            title,
            validationResult.getMissingGeneIds().isEmpty()
                ? JOptionPane.INFORMATION_MESSAGE
                : JOptionPane.WARNING_MESSAGE
        );
    }

    private static String buildValidationMessage(ValidationResult validationResult) {
        StringBuilder builder = new StringBuilder();

        if (!validationResult.getMissingGeneIds().isEmpty()) {
            builder.append("IDs Not Found:\n");
            for (String geneId : validationResult.getMissingGeneIds()) {
                builder.append(geneId).append('\n');
            }
        }

        if (!validationResult.getResolvedEntries().isEmpty()) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append("Resolved IDs:\n");
            for (GeneSetSupport.Entry entry : validationResult.getResolvedEntries()) {
                builder.append("  ").append(entry.getId());
                if (entry.hasCoordinate()) {
                    builder.append("   ").append(entry.getChromosomeName())
                        .append(':').append(entry.getStartPos()).append('-').append(entry.getEndPos());
                }
                builder.append('\n');
            }
        }

        if (!validationResult.getCorrectedGeneLines().isEmpty()) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append("Corrections:\n");
            for (String correctedLine : validationResult.getCorrectedGeneLines()) {
                builder.append("  ").append(correctedLine).append('\n');
            }
        }

        return builder.toString().trim();
    }

    static final class ValidationResult {
        private final List<String> resolvedGeneIds;
        private final List<String> missingGeneIds;
        private final List<String> correctedGeneLines;
        /** The resolved transcripts with their chromosome and location: what gets saved. */
        private final List<GeneSetSupport.Entry> resolvedEntries;
        /** Input id -> the ids it resolved to; missing when the input id was already canonical. */
        private final Map<String, List<String>> resolvedIdsByInputId;

        ValidationResult(List<String> resolvedGeneIds, List<String> missingGeneIds,
                         List<String> correctedGeneLines, List<GeneSetSupport.Entry> resolvedEntries,
                         Map<String, List<String>> resolvedIdsByInputId) {
            this.resolvedGeneIds = resolvedGeneIds;
            this.missingGeneIds = missingGeneIds;
            this.correctedGeneLines = correctedGeneLines;
            this.resolvedEntries = resolvedEntries;
            this.resolvedIdsByInputId = resolvedIdsByInputId;
        }

        Map<String, List<String>> getResolvedIdsByInputId() {
            return resolvedIdsByInputId;
        }

        List<GeneSetSupport.Entry> getResolvedEntries() {
            return resolvedEntries;
        }

        List<String> getResolvedGeneIds() {
            return resolvedGeneIds;
        }

        List<String> getMissingGeneIds() {
            return missingGeneIds;
        }

        List<String> getCorrectedGeneLines() {
            return correctedGeneLines;
        }

        boolean hasIssues() {
            return !missingGeneIds.isEmpty() || !correctedGeneLines.isEmpty();
        }
    }
}
