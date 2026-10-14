package simplegenomehub.regression;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import simplegenomehub.model.GeneAnnotationData;
import simplegenomehub.model.GeneAnnotationData.AnnotationType;
import simplegenomehub.model.GeneAnnotationData.GeneAnnotation;

/** Annotation formats must survive comments, extensions and failed publication. */
public final class AnnotationPersistenceRegression {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static Path write(Path directory, String name, String text) throws IOException {
        return Files.writeString(directory.resolve(name), text);
    }
    private static GeneAnnotationData data() { return new GeneAnnotationData("fixture", "Annotations"); }

    private static void editedFieldsRoundTrip(Path temporary) throws Exception {
        for (AnnotationType type : new AnnotationType[]{AnnotationType.PFAM, AnnotationType.INTERPRO, AnnotationType.CUSTOM}) {
            String evidenceHeader = type == AnnotationType.PFAM ? "Domain" : type == AnnotationType.INTERPRO ? "Type" : "Class";
            String idHeader = type == AnnotationType.CUSTOM ? "Annotation_ID" : type.getShortName() + "_ID";
            Path source = write(temporary, "edited-" + type + ".tsv", "# retain this original source\n#GeneID\t"
                + idHeader + "\tFuture\tDescription\tTerm\t" + evidenceHeader + "\tScore\tBlank\n"
                + "gEdit\tA1\tkeep 中文\tOld description\tOld term\tOld evidence\t0.2500\t\n");
            byte[] original = Files.readAllBytes(source);
            GeneAnnotationData annotations = data();
            check(annotations.loadCustomAnnotations(source.toFile(), type), "Load reordered named columns " + type);
            GeneAnnotation entry = annotations.getGeneAnnotations("gEdit", type).get(0);
            check("Old term".equals(entry.getTerm()) && "Old description".equals(entry.getDescription())
                && "Old evidence".equals(entry.getEvidence()) && entry.getScore() == 0.25,
                "Standard getters did not follow named columns " + type);
            entry.setTerm(entry.getTerm());
            entry.setDescription(entry.getDescription());
            entry.setEvidence(entry.getEvidence());
            entry.setScore(entry.getScore());
            check("0.2500".equals(entry.getColumnValue(6)), "Unchanged setter normalized original value " + type);
            Path target = temporary.resolve("edited-saved-" + type + ".tsv");
            annotations.saveToFileChecked(target.toFile(), type);
            if (type == AnnotationType.CUSTOM)
                check(Arrays.equals(original, Files.readAllBytes(target)), "Unedited CUSTOM setters prevented source preservation");
            entry.setTerm("New term");
            entry.setDescription("New description");
            entry.setEvidence("New evidence");
            entry.setScore(0.75);
            annotations.saveToFileChecked(target.toFile(), type);
            GeneAnnotationData reopened = data();
            check(reopened.loadCustomAnnotations(target.toFile(), type), "Reopen edited fields " + type);
            GeneAnnotation restored = reopened.getGeneAnnotations("gEdit", type).get(0);
            check("New term".equals(restored.getTerm()) && "New description".equals(restored.getDescription())
                && "New evidence".equals(restored.getEvidence()) && restored.getScore() == 0.75,
                "Edited standard fields were lost during save/reopen " + type);
            check("keep 中文".equals(restored.getColumnValue(2)) && "".equals(restored.getColumnValue(7)),
                "Standard field edit changed unknown or empty columns " + type);
            check(Arrays.equals(original, Files.readAllBytes(source)), "Editing rewrote original source " + type);
            entry.setAttribute("extension", "retained attribute");
            GeneAnnotation editedCopy = entry.copyForEdit("gRenamed", "A2", type);
            editedCopy.setDescription("Copied edit");
            GeneAnnotationData copied = data();
            copied.setColumnHeaders(type, annotations.getColumnHeaders(type));
            copied.addAnnotation(editedCopy);
            Path copyTarget = temporary.resolve("copied-edit-" + type + ".tsv");
            copied.saveToFileChecked(copyTarget.toFile(), type);
            GeneAnnotationData copyReopened = data();
            check(copyReopened.loadCustomAnnotations(copyTarget.toFile(), type), "Reopen editor copy " + type);
            GeneAnnotation copyRow = copyReopened.getGeneAnnotations("gRenamed", type).get(0);
            check("A2".equals(copyRow.getAnnotationId()) && "Copied edit".equals(copyRow.getDescription())
                && "keep 中文".equals(copyRow.getColumnValue(2)), "Editor copy lost IDs or dynamic fields " + type);
            check("gEdit".equals(entry.getColumnValue(0)) && "A1".equals(entry.getColumnValue(1))
                && "retained attribute".equals(editedCopy.getAttributes().get("extension")),
                "Editor copy mutated source arrays or lost attributes " + type);
        }
        Path source = write(temporary, "missing-field.tsv", "# comment\n#GeneID\tDescription\tFuture\ng1\tOriginal\tkeep\n");
        GeneAnnotationData annotations = data();
        check(annotations.loadCustomAnnotations(source.toFile(), AnnotationType.CUSTOM), "Missing field schema load");
        GeneAnnotation entry = annotations.getGeneAnnotations("g1", AnnotationType.CUSTOM).get(0);
        Path target = temporary.resolve("missing-field-saved.tsv");
        annotations.saveToFileChecked(target.toFile(), AnnotationType.CUSTOM);
        byte[] stableTarget = Files.readAllBytes(target);
        entry.setTerm("Cannot fit the source schema");
        try {
            annotations.saveToFileChecked(target.toFile(), AnnotationType.CUSTOM);
            throw new AssertionError("Source-copy shortcut silently lost an edit without a matching column");
        } catch (IOException expected) {
            check(expected.getMessage().contains("Term") && expected.getMessage().contains("g1"),
                "Missing column edit error has no actionable field/gene context");
        }
        check(Arrays.equals(stableTarget, Files.readAllBytes(target)), "Failed edit publication changed the existing destination");
        entry.setTerm("");
        annotations.saveToFileChecked(target.toFile(), AnnotationType.CUSTOM);
        check(Arrays.equals(stableTarget, Files.readAllBytes(target)), "Reverting unsupported edit did not restore source preservation");
        for (String invalidDescription : new String[]{"First line\nSecond line", "First line\rSecond line", "First column\tSecond column"}) {
            entry.setDescription(invalidDescription);
            try {
                annotations.saveToFileChecked(target.toFile(), AnnotationType.CUSTOM);
                throw new AssertionError("Description containing a TSV delimiter was published");
            } catch (IOException expected) {
                check(expected.getMessage().contains("g1") && expected.getMessage().contains("Description")
                    && expected.getMessage().contains("(2)"), "Invalid TSV field error lacks gene/column context");
            }
            check(Arrays.equals(stableTarget, Files.readAllBytes(target)), "Invalid TSV edit replaced the valid destination");
            check(Arrays.equals(stableTarget, Files.readAllBytes(source)), "Invalid TSV edit changed original import source");
        }
        entry.setDescription("Original");
        annotations.saveToFileChecked(target.toFile(), AnnotationType.CUSTOM);
        check(Arrays.equals(stableTarget, Files.readAllBytes(target)), "Reverting invalid TSV edit prevented source preservation");
        entry.setDescription("Edited existing column");
        annotations.saveToFileChecked(target.toFile(), AnnotationType.CUSTOM);
        GeneAnnotationData reopened = data();
        check(reopened.loadCustomAnnotations(target.toFile(), AnnotationType.CUSTOM), "Reopen sparse CUSTOM schema");
        check("Edited existing column".equals(reopened.getGeneAnnotations("g1", AnnotationType.CUSTOM).get(0).getDescription()),
            "Description edit in sparse CUSTOM schema was lost");
    }

    private static void ontologyEncodingRoundTrip(Path root) throws Exception {
        for (AnnotationType type : new AnnotationType[]{AnnotationType.GO, AnnotationType.KEGG}) {
            GeneAnnotationData model = data();
            GeneAnnotation entry = new GeneAnnotation("geneUtf", type == AnnotationType.GO ? "GO:0000001" : "K00001", "光合作用 café", type);
            entry.setDescription("功能描述 β");
            model.addAnnotation(entry);
            Path target = root.resolve("encoding-" + type + ".tsv");
            model.saveToFileChecked(target.toFile(), type);
            check(Files.readString(target).contains("光合作用 café") && Files.readString(target).contains("功能描述 β"),
                "Saved ontology table was not UTF-8 " + type);
            GeneAnnotationData reopened = data();
            check(type == AnnotationType.GO ? reopened.loadGOAnnotations(target.toFile()) : reopened.loadKEGGAnnotations(target.toFile()),
                "Ontology-specific reload failed " + type);
            GeneAnnotation restored = reopened.getGeneAnnotations("geneUtf", type).get(0);
            check("光合作用 café".equals(restored.getTerm()) && "功能描述 β".equals(restored.getDescription()),
                "Ontology-specific reader changed Unicode content under " + java.nio.charset.Charset.defaultCharset() + " " + type);
        }
    }

    private static void unnamedColumnRoundTrip(Path root) throws Exception {
        Path source = write(root, "unnamed-column.tsv", "#Gene_ID\tAnnotation_ID\tDescription\t\ngeneA\tA\tdesc\tfuture\n");
        GeneAnnotationData model = data();
        check(model.loadCustomAnnotations(source.toFile(), AnnotationType.CUSTOM), "Unnamed final column import failed");
        check(model.getColumnHeaders(AnnotationType.CUSTOM).length == 4
            && "".equals(model.getColumnHeaders(AnnotationType.CUSTOM)[3]), "Trailing empty header name was dropped");
        model.getGeneAnnotations("geneA", AnnotationType.CUSTOM).get(0).setDescription("edited");
        Path target = root.resolve("unnamed-column-saved.tsv");
        model.saveToFileChecked(target.toFile(), AnnotationType.CUSTOM);
        GeneAnnotationData reopened = data();
        check(reopened.loadCustomAnnotations(target.toFile(), AnnotationType.CUSTOM), "Unnamed final column did not reopen");
        check(reopened.getColumnHeaders(AnnotationType.CUSTOM).length == 4
            && "edited".equals(reopened.getGeneAnnotations("geneA", AnnotationType.CUSTOM).get(0).getDescription())
            && "future".equals(reopened.getGeneAnnotations("geneA", AnnotationType.CUSTOM).get(0).getColumnValue(3)),
            "Editing a named field lost the unnamed extension column");
    }

    private static void mappedAnnotationsRoundTrip(Path temporary) throws Exception {
        Path mapping = write(temporary, "mapping.gff3", "##gff-version 3\n"
            + "chr1\tfixture\tgene\t1\t100\t.\t+\t.\tID=geneMapped\n"
            + "chr1\tfixture\tmRNA\t1\t100\t.\t+\t.\tID=transcriptOne;Parent=geneMapped\n"
            + "chr1\tfixture\tmRNA\t1\t100\t.\t+\t.\tID=transcriptTwo;Parent=geneMapped\n");
        for (AnnotationType type : new AnnotationType[]{AnnotationType.PFAM, AnnotationType.CUSTOM}) {
            Path source = write(temporary, "mapped-" + type + ".tsv", "# original source\n#Gene_ID\tAnnotation_ID\tDescription\tFuture\n"
                + "transcriptOne\tA1\tOne\tkeep1\ntranscriptTwo\tA2\tTwo\tkeep2\n");
            byte[] original = Files.readAllBytes(source);
            GeneAnnotationData annotations = data();
            check(annotations.loadCustomAnnotations(source.toFile(), type), "Load transcript annotations " + type);
            check(annotations.convertTranscriptToGeneAnnotations(mapping.toFile()), "Transcript mapping did not run " + type);
            Path target = temporary.resolve("mapped-saved-" + type + ".tsv");
            annotations.saveToFileChecked(target.toFile(), type);
            GeneAnnotationData reopened = data();
            check(reopened.loadCustomAnnotations(target.toFile(), type), "Reopen mapped annotations " + type);
            check(reopened.getAnnotatedGenes(type).equals(java.util.Collections.singleton("geneMapped"))
                && reopened.getGeneAnnotations("geneMapped", type).size() == 2, "Saved transcript IDs were not mapped " + type);
            for (GeneAnnotation row : reopened.getGeneAnnotations("geneMapped", type))
                check("geneMapped".equals(row.getColumnValue(0)) && row.getColumnValue(3).startsWith("keep"),
                    "Mapped dynamic row lost its true gene ID or extension columns " + type);
            check(Arrays.equals(original, Files.readAllBytes(source)), "Mapping rewrote original source " + type);
        }
    }

    private static void isolate(Path root) throws IOException {
        System.setProperty("user.home", Files.createDirectories(root.resolve("isolated-home")).toString());
        System.setProperty("java.io.tmpdir", Files.createDirectories(root.resolve("isolated-tmp")).toString());
    }
    private static void speciesSaveFailure(Path root, boolean baseline) throws Exception {
        Path dataRoot = Files.createDirectories(root.resolve("species-data"));
        simplegenomehub.model.SpeciesInfo species = new simplegenomehub.model.SpeciesInfo("Regression", "v1");
        species.initializeFileStructure(dataRoot.toFile());
        GeneAnnotationData mixed = data();
        mixed.addAnnotation(new GeneAnnotation("gPfam", "PF0001", "domain", AnnotationType.PFAM));
        mixed.addAnnotation(new GeneAnnotation("gGo", "GO:0000001", "go description", AnnotationType.GO));
        species.setFunctionalAnnotations(mixed);
        Path blocked = species.getAnnotationTypeDirectory(AnnotationType.PFAM).toPath().resolve(AnnotationType.PFAM.getShortName() + ".tsv");
        // A directory cannot be atomically replaced by a data file on either Windows or Unix.
        Files.createDirectories(blocked);
        Path sentinel = write(blocked, "keep.txt", "existing protected target");
        boolean saved = species.saveFunctionalAnnotations();
        if (baseline) check(saved, "Baseline no longer reproduces mixed-type save success masking failure");
        else check(!saved, "One successful annotation type masked failure of another type");
        Path good = species.getAnnotationTypeDirectory(AnnotationType.GO).toPath().resolve(AnnotationType.GO.getShortName() + ".tsv");
        check(Files.isRegularFile(good), "Mixed-type fixture never exercised a successful independent type save");
        check("existing protected target".equals(Files.readString(sentinel)), "Failed save damaged the protected destination");
    }
    private static void speciesClearRoundTrip(Path output) throws Exception {
        Path root = Files.createDirectories(output.resolve("clear-data"));
        simplegenomehub.model.SpeciesInfo species = new simplegenomehub.model.SpeciesInfo("ClearFixture", "v1");
        species.initializeFileStructure(root.toFile());
        GeneAnnotationData annotations = data();
        annotations.addAnnotation(new GeneAnnotation("gClear", "PF0001", "retained until committed", AnnotationType.PFAM));
        species.setFunctionalAnnotations(annotations);
        species.saveFunctionalAnnotationsChecked();
        Path target = species.getAnnotationTypeDirectory(AnnotationType.PFAM).toPath().resolve(AnnotationType.PFAM.getShortName() + ".tsv");
        byte[] original = Files.readAllBytes(target);
        simplegenomehub.model.SpeciesInfo lazy = new simplegenomehub.model.SpeciesInfo("ClearFixture", "v1");
        lazy.initializeFileStructure(root.toFile());
        check(lazy.loadFunctionalAnnotations(), "Lazy annotation metadata did not load");
        lazy.saveFunctionalAnnotationsChecked();
        check(Arrays.equals(original, Files.readAllBytes(target)), "Unloaded zero-count types erased existing annotation data");
        Path backup = target.resolveSibling("saved-before-clear.tsv");
        Files.move(target, backup);
        Files.createDirectory(target);
        Path sentinel = write(target, "keep.txt", "protected clear destination");
        try {
            species.clearFunctionalAnnotations(AnnotationType.PFAM);
            throw new AssertionError("Clearing into a nonempty directory unexpectedly succeeded");
        } catch (IOException expected) { }
        check(annotations.getAnnotationCounts().get(AnnotationType.PFAM) == 1,
            "A failed clear removed live annotations before storage committed");
        check("protected clear destination".equals(Files.readString(sentinel)), "Failed clear damaged the protected directory");
        Files.delete(sentinel);
        Files.delete(target);
        Files.move(backup, target);
        check(species.clearFunctionalAnnotations(AnnotationType.PFAM) == 1, "Successful clear returned an incorrect removed count");
        check(annotations.getAnnotationCounts().get(AnnotationType.PFAM) == 0, "Successful clear retained live annotations");
        assertEmptyOnReopen(root, target);
        annotations.addAnnotation(new GeneAnnotation("gAgain", "PF0002", "second round", AnnotationType.PFAM));
        species.saveFunctionalAnnotationsChecked();
        annotations.clearAnnotationsOfType(AnnotationType.PFAM);
        species.saveFunctionalAnnotationsChecked();
        assertEmptyOnReopen(root, target);
        GeneAnnotation nullTermFirst = new GeneAnnotation("gSame", "PFfirst", null, AnnotationType.PFAM);
        GeneAnnotation nullTermOther = new GeneAnnotation("gSame", "PFother", null, AnnotationType.PFAM);
        annotations.addAnnotation(nullTermFirst);
        annotations.addAnnotation(nullTermOther);
        check(annotations.removeAnnotation(nullTermFirst), "Matching null-term annotation was not removed");
        check(annotations.getAnnotationCounts().get(AnnotationType.PFAM) == 1
            && annotations.getGeneAnnotations("gSame", AnnotationType.PFAM).size() == 1
            && "PFother".equals(annotations.getGeneAnnotations("gSame", AnnotationType.PFAM).get(0).getAnnotationId()),
            "Removing a null-term annotation also removed a different annotation ID");
        species.saveFunctionalAnnotationsChecked();
        check(annotations.removeGeneAnnotations("gSame", AnnotationType.PFAM), "Last gene annotation was not removed");
        species.saveFunctionalAnnotationsChecked();
        assertEmptyOnReopen(root, target);
        GeneAnnotation finalRow = new GeneAnnotation("gFinal", "PFfinal", "final", AnnotationType.PFAM);
        annotations.addAnnotation(finalRow);
        species.saveFunctionalAnnotationsChecked();
        check(annotations.removeAnnotation(finalRow), "Last individual annotation was not removed");
        species.saveFunctionalAnnotationsChecked();
        assertEmptyOnReopen(root, target);
    }
    private static void assertEmptyOnReopen(Path root, Path target) throws Exception {
        GeneAnnotationData rows = data();
        check(!rows.loadCustomAnnotations(target.toFile(), AnnotationType.PFAM)
            && rows.getAnnotationCounts().get(AnnotationType.PFAM) == 0, "Cleared persisted table revived annotations");
        simplegenomehub.model.SpeciesInfo reopened = new simplegenomehub.model.SpeciesInfo("ClearFixture", "v1");
        reopened.initializeFileStructure(root.toFile());
        check(reopened.loadFunctionalAnnotations(), "Cleared table metadata could not reopen");
        simplegenomehub.model.AnnotationMetadata metadata = reopened.getAnnotationMetadata(AnnotationType.PFAM);
        check(metadata != null && metadata.getDataLines() == 0 && metadata.getTotalAnnotations() == 0,
            "Cleared annotation metadata retained stale row counts");
        simplegenomehub.gui.MetadataDrivenTableModel table = new simplegenomehub.gui.MetadataDrivenTableModel(metadata, AnnotationType.PFAM, target.toFile());
        table.loadData();
        check(table.getRowCount() == 0, "Reopening cleared data restored an old annotation row");
    }
    private static void tablePresentationRoundTrip(Path output) throws Exception {
        Path source = write(output, "table-columns.tsv", "# ordinary\tcomment\n#GeneID\tDescription\tFuture\tBlank\n"
            + "geneA\talpha\t中文 extension\t\n# explanation between rows\ngeneA\tbeta\tsecond extension\t\n"
            + "geneB\tdelta\tthird extension\t\n");
        simplegenomehub.model.AnnotationMetadata metadata = simplegenomehub.model.AnnotationMetadata.analyzeFile(source.toFile(), AnnotationType.CUSTOM);
        check(Arrays.equals(metadata.getColumnHeaders(), new String[]{"GeneID", "Description", "Future", "Blank"}),
            "Ordinary comment replaced metadata column headers");
        check(metadata.getDataLines() == 3 && metadata.getTotalAnnotations() == 3 && metadata.getUniqueGenes() == 2,
            "Metadata skipped first data row or collapsed multiple annotations for one gene");
        simplegenomehub.gui.MetadataDrivenTableModel table = new simplegenomehub.gui.MetadataDrivenTableModel(metadata, AnnotationType.CUSTOM, source.toFile());
        table.loadData();
        check(table.isLoaded() && table.getRowCount() == 3 && table.getColumnCount() == 4, "Table omitted rows or dynamic columns");
        check("geneA".equals(table.getValueAt(0, 0)) && "alpha".equals(table.getValueAt(0, 1))
            && "中文 extension".equals(table.getValueAt(0, 2)) && "".equals(table.getValueAt(0, 3)),
            "Table changed the first gene identifier, nonnumeric value, unknown column or trailing empty field");
        table.filterByGenes(java.util.Collections.singleton("geneA"));
        check(table.getRowCount() == 2, "Exact gene filter dropped multiple annotations for the same gene");
        table.filterByGenes(java.util.Collections.singleton("geneA"), true);
        check(table.getRowCount() == 2, "Batch partial gene filter dropped multiple annotations for the same gene");
        table.filterByPartialGeneId("geneA");
        check(table.getRowCount() == 2, "Partial gene filter dropped multiple annotations for the same gene");
        metadata.setLargeDataset(true);
        simplegenomehub.gui.MetadataDrivenTableModel pages = new simplegenomehub.gui.MetadataDrivenTableModel(metadata, AnnotationType.CUSTOM, source.toFile());
        pages.setPageSize(1);
        pages.loadData();
        check(pages.isPaginated() && pages.getTotalPages() == 3 && pages.getRowCount() == 1
            && "alpha".equals(pages.getValueAt(0, 1)), "First annotation page omitted geneA/alpha");
        pages.loadPage(1);
        check(pages.getRowCount() == 1 && "geneA".equals(pages.getValueAt(0, 0)) && "beta".equals(pages.getValueAt(0, 1)),
            "Second annotation page changed logical row offsets around comments");
        pages.loadPage(2);
        check(pages.getRowCount() == 1 && "geneB".equals(pages.getValueAt(0, 0)), "Final annotation page lost its data row");
        Path noHeader = write(output, "table-headerless.tsv", "geneA\talpha\tunknown first\ngeneB\tbeta\tunknown second\n");
        simplegenomehub.gui.MetadataDrivenTableModel headerless = new simplegenomehub.gui.MetadataDrivenTableModel(AnnotationType.CUSTOM, noHeader.toFile());
        headerless.loadData();
        check(headerless.getRowCount() == 2 && "geneA".equals(headerless.getValueAt(0, 0))
            && "alpha".equals(headerless.getValueAt(0, 1)), "Headerless geneA/alpha row was mistaken for column labels");
        Path late = write(output, "table-preamble.tsv", "# first preamble\n# ordinary\tcomment\nGene_ID\tDescription\tFuture\ngeneA\talpha\tkeep\n");
        simplegenomehub.gui.MetadataDrivenTableModel preamble = new simplegenomehub.gui.MetadataDrivenTableModel(AnnotationType.CUSTOM, late.toFile());
        preamble.loadData();
        check(preamble.getRowCount() == 1 && preamble.getColumnCount() == 3 && "Future".equals(preamble.getColumnName(2))
            && "alpha".equals(preamble.getValueAt(0, 1)), "A valid header after ordinary preamble lines was not recognized");
    }

    /** Uses only APIs present at cf8fd620, so compiled tests can run against baseline classes. */
    private static void baselineDefects(Path root) throws Exception {
        Path first = write(root, "baseline-first.tsv", "geneA\tPF0001\tfirst term\ngeneB\tPF0002\tsecond term\n");
        GeneAnnotationData initial = data();
        check(initial.loadCustomAnnotations(first.toFile(), AnnotationType.PFAM), "Baseline fixture could not load");
        check(!initial.getAnnotatedGenes(AnnotationType.PFAM).contains("geneA"), "Baseline no longer loses first geneA data row");
        System.out.println("REPRODUCED baseline: first geneA data row mistaken for a header");
        Path comment = write(root, "baseline-comment.tsv", "#Gene_ID\tPfam_ID\tTerm\tDescription\n"
            + "g1\tPF0001\tAlpha\tDomain description\n# ordinary comment between rows\ng2\tPF0002\tBeta\tOther description\n");
        GeneAnnotationData comments = data();
        check(comments.loadCustomAnnotations(comment.toFile(), AnnotationType.PFAM), "Baseline comment fixture could not load");
        check(comments.getColumnHeaders(AnnotationType.PFAM).length != 4, "Baseline no longer replaces schema with ordinary comments");
        Path commentSave = root.resolve("baseline-comment-saved.tsv");
        check(comments.saveToFile(commentSave.toFile(), AnnotationType.PFAM), "Baseline comment save failed unexpectedly");
        check(!Files.readString(commentSave).contains("\tAlpha\t"), "Baseline no longer loses annotation values after comment schema replacement");
        System.out.println("REPRODUCED baseline: ordinary # comment replaces schema and loses annotation content on save");
        // Exercise dynamic model columns directly without depending on baseline importer heuristics.
        GeneAnnotationData edited = data();
        String[] editHeaders = {"Gene_ID", "Pfam_ID", "Term", "Description", "Future"};
        GeneAnnotation edit = new GeneAnnotation("gEdit", AnnotationType.PFAM, editHeaders,
            new String[]{"gEdit", "PF0001", "Old term", "Old description", "keep unknown"});
        edited.setColumnHeaders(AnnotationType.PFAM, editHeaders);
        edited.addAnnotation(edit);
        edit.setDescription("New description");
        Path save = root.resolve("baseline-edit-saved.tsv");
        check(edited.saveToFile(save.toFile(), AnnotationType.PFAM), "Baseline edit save failed unexpectedly");
        check(!Files.readString(save).contains("New description"), "Baseline no longer loses standard setter edits");
        System.out.println("REPRODUCED baseline: model edits ignored by dynamic-column saving");
        speciesSaveFailure(root, true);
        System.out.println("REPRODUCED baseline: mixed-type save reports success when PFAM fails");
    }

    public static void main(String[] args) throws Exception {
        Path temporary = args.length > 0 && !args[0].equals("--baseline")
            ? Files.createDirectories(Path.of(args[0]).toAbsolutePath()) : Files.createTempDirectory("sgh-annotation-regression-");
        isolate(temporary);
        if (java.util.Arrays.asList(args).contains("--baseline")) { baselineDefects(temporary); return; }
        String input = "# generated by pfam importer\n#Gene_ID\tPfam_ID\tTerm\tDescription\tEvidence\tFuture_Field\tBlank\n"
            + "g1\tPF0001\tAlpha\tDomain description\tIEA\tuntouched ; value 中文\t\n"
            + "# ordinary comment between rows\n#Gene_ID\tPfam_ID\tTerm\tDescription\tEvidence\tFuture_Field\tBlank\n"
            + "g2\tPF0002\tBeta\tSecond description\tEXP\tsecond-value\t\n";
        Path source = write(temporary, "pfam.tsv", input);
        byte[] original = Files.readAllBytes(source);
        GeneAnnotationData pfam = data();
        check(pfam.loadCustomAnnotations(source.toFile(), AnnotationType.PFAM), "PFAM import");
        check(pfam.getAnnotationCounts().get(AnnotationType.PFAM) == 2, "Comment or repeated header became data");
        check(pfam.getColumnHeaders(AnnotationType.PFAM).length == 7, "Ordinary comment replaced schema");
        check("untouched ; value 中文".equals(pfam.getGeneAnnotations("g1", AnnotationType.PFAM).get(0).getColumnValue(5)),
            "Unknown annotation column changed");
        Path saved = temporary.resolve("pfam-saved.tsv");
        check(pfam.saveToFile(saved.toFile(), AnnotationType.PFAM), "PFAM save");
        GeneAnnotationData reopened = data();
        check(reopened.loadCustomAnnotations(saved.toFile(), AnnotationType.PFAM), "PFAM reopen");
        check(Arrays.equals(pfam.getColumnHeaders(AnnotationType.PFAM), reopened.getColumnHeaders(AnnotationType.PFAM)),
            "Unknown headers lost");
        check(Arrays.equals(pfam.getGeneAnnotations("g1", AnnotationType.PFAM).get(0).getColumnValues(),
            reopened.getGeneAnnotations("g1", AnnotationType.PFAM).get(0).getColumnValues()), "Unknown values or trailing empty column lost");
        check(Arrays.equals(original, Files.readAllBytes(source)), "Import rewrote original annotation source");

        for (String geneHeader : new String[]{"Gene", "Gene_ID", "GeneID"}) {
            Path alias = write(temporary, geneHeader + ".tsv", "# generated\n#" + geneHeader + "\tDescription\ng1\talpha\n");
            GeneAnnotationData custom = data();
            check(custom.loadCustomAnnotations(alias.toFile(), AnnotationType.CUSTOM), "Header alias " + geneHeader);
            check(custom.getAnnotatedGenes(AnnotationType.CUSTOM).contains("g1"), "First custom data row missing");
            Path copy = temporary.resolve(geneHeader + "-copy.tsv");
            custom.saveToFileChecked(copy.toFile(), AnnotationType.CUSTOM);
            check(Arrays.equals(Files.readAllBytes(alias), Files.readAllBytes(copy)), "Unedited CUSTOM source not preserved");
        }
        Path geneFirst = write(temporary, "first-geneA.tsv", "geneA\tPF0001\tFirst\ngeneB\tPF0002\tSecond\n");
        GeneAnnotationData firstRows = data();
        check(firstRows.loadCustomAnnotations(geneFirst.toFile(), AnnotationType.PFAM), "Headerless geneA import");
        check(firstRows.getAnnotatedGenes(AnnotationType.PFAM).containsAll(java.util.Arrays.asList("geneA", "geneB")),
            "A first data identifier containing gene was mistaken for a header");
        Path legacy = write(temporary, "legacy.tsv", "# generated\n#Gene\n"
            + "gLegacy\tPF0003\tTerm\tDescription\tEXP\textra\n");
        GeneAnnotationData headerless = data();
        check(headerless.loadCustomAnnotations(legacy.toFile(), AnnotationType.PFAM), "Legacy headerless file");
        check("Description".equals(headerless.getGeneAnnotations("gLegacy", AnnotationType.PFAM).get(0).getTerm()),
            "Legacy description precedence changed");
        Path legacySave = temporary.resolve("legacy-save.tsv");
        headerless.saveToFileChecked(legacySave.toFile(), AnnotationType.PFAM);
        check(Files.readString(legacySave).contains("\textra\n") || Files.readString(legacySave).contains("\textra\r\n"),
            "Legacy unknown extra column was discarded");

        byte[] stableTarget = Files.readAllBytes(saved);
        String[] stableHeaders = pfam.getColumnHeaders(AnnotationType.PFAM);
        Path conflict = write(temporary, "conflict.tsv", "#Gene_ID\tPfam_ID\ng3\tPF0003\n#Gene_ID\tOther\ng4\tPF0004\n");
        try {
            pfam.loadCustomAnnotations(conflict.toFile(), AnnotationType.PFAM, "\t", true);
            throw new AssertionError("Conflicting later header accepted");
        } catch (IOException expected) {
            check(expected.getMessage().contains("conflict.tsv:3"), "Missing source line in parse error");
        }
        check(Arrays.equals(stableHeaders, pfam.getColumnHeaders(AnnotationType.PFAM))
            && pfam.getAnnotationCounts().get(AnnotationType.PFAM) == 2
            && !pfam.getAnnotatedGenes(AnnotationType.PFAM).contains("g3"), "Parse failure partially replaced model");
        check(Arrays.equals(stableTarget, Files.readAllBytes(saved)), "Parse failure changed persisted target");
        Path lateHeader = write(temporary, "late-header.tsv",
            "gEarly\tPF0007\tTerm\n#Gene_ID\tChanged\tUnknown\ngLate\tPF0008\tOther\n");
        try {
            pfam.loadCustomAnnotations(lateHeader.toFile(), AnnotationType.PFAM, "\t", true);
            throw new AssertionError("A first header after data changed the established legacy columns");
        } catch (IOException expected) {
            check(expected.getMessage().contains("late-header.tsv:2"), "Late header error lost its source line");
        }
        check(Arrays.equals(stableHeaders, pfam.getColumnHeaders(AnnotationType.PFAM))
            && pfam.getAnnotationCounts().get(AnnotationType.PFAM) == 2,
            "Late conflicting header changed the live annotation dataset");
        Path matchingLateHeader = write(temporary, "matching-late-header.tsv",
            "gEarly\tPF0007\tTerm\tDescription\n#Gene_ID\tPfam_ID\tTerm\tDescription\n"
            + "gLate\tPF0008\tOtherTerm\tOtherDescription\n");
        GeneAnnotationData matchingLate = data();
        check(matchingLate.loadCustomAnnotations(matchingLateHeader.toFile(), AnnotationType.PFAM),
            "Repeated matching legacy header was rejected");
        check("Description".equals(matchingLate.getGeneAnnotations("gEarly", AnnotationType.PFAM).get(0).getTerm())
            && "OtherDescription".equals(matchingLate.getGeneAnnotations("gLate", AnnotationType.PFAM).get(0).getTerm()),
            "A matching late header changed interpretation of headerless rows");
        Path malformed = write(temporary, "malformed.tsv", "#Gene_ID\tPfam_ID\ngNew\tPF1\nnot-a-tsv-row\n");
        check(!pfam.loadCustomAnnotations(malformed.toFile(), AnnotationType.PFAM), "Malformed row accepted");
        check(pfam.getAnnotationCounts().get(AnnotationType.PFAM) == 2, "Compatibility loader was not transactional");

        GeneAnnotation invalid = new GeneAnnotation("zBad", AnnotationType.PFAM, stableHeaders,
            new String[]{"zBad", "PFbad", "term", "desc", "EXP", "future", "", "overflow"});
        pfam.addAnnotation(invalid);
        try {
            pfam.saveToFileChecked(saved.toFile(), AnnotationType.PFAM);
            throw new AssertionError("Invalid output schema unexpectedly saved");
        } catch (IOException expected) { }
        check(Arrays.equals(stableTarget, Files.readAllBytes(saved)), "Partial save truncated the existing annotation file");

        Path customSource = write(temporary, "custom.tsv", "# original comment\n#GeneID\tDescription\tFuture\ng1\tAlpha\tkeep\ng2\tBeta\tkeep2\n");
        GeneAnnotationData custom = data();
        check(custom.loadCustomAnnotations(customSource.toFile(), AnnotationType.CUSTOM), "Custom source load");
        Path badCustom = write(temporary, "bad-custom.tsv", "#GeneID\tA\ngLost\tx\n#GeneID\tB\n");
        check(!custom.loadCustomAnnotations(badCustom.toFile(), AnnotationType.CUSTOM), "Conflicting CUSTOM header accepted");
        Path customSaved = temporary.resolve("custom-saved.tsv");
        custom.saveToFileChecked(customSaved.toFile(), AnnotationType.CUSTOM);
        check(Arrays.equals(Files.readAllBytes(customSource), Files.readAllBytes(customSaved)), "Failed load replaced CUSTOM source snapshot");
        custom.removeGeneAnnotations("g2", AnnotationType.CUSTOM);
        custom.saveToFileChecked(customSaved.toFile(), AnnotationType.CUSTOM);
        check(!Files.readString(customSaved).contains("g2\t"), "Save copied stale source after model edit");
        check(Files.readString(customSource).contains("g2\t"), "Save edited original import source");
        Files.writeString(customSource, "#GeneID\tDescription\ngExternal\tchanged\n");
        custom.saveToFileChecked(customSaved.toFile(), AnnotationType.CUSTOM);
        check(!Files.readString(customSaved).contains("gExternal"), "Save silently imported external source changes");
        Path csv = write(temporary, "annotations.csv", "# generated\nGeneID,Description,Future\ngCsv,alpha,retain\n");
        check(custom.loadCustomAnnotations(csv.toFile(), AnnotationType.CUSTOM, ",", true), "Literal delimiter / replacement import");
        custom.saveToFileChecked(customSaved.toFile(), AnnotationType.CUSTOM);
        check(Files.readString(customSaved).contains("gCsv\talpha\tretain"), "Non-TSV source copied into TSV storage");
        check(custom.getAnnotatedGenes(AnnotationType.CUSTOM).size() == 1, "Replacement import appended old data");
        Path compressed = temporary.resolve("annotations.tsv.gz");
        try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(Files.newOutputStream(compressed))) {
            gzip.write("#GeneID\tDescription\ngZip\tcompressed 中文\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        check(custom.loadCustomAnnotations(compressed.toFile(), AnnotationType.CUSTOM, "\t", true), "Legacy gzip input");
        custom.saveToFileChecked(customSaved.toFile(), AnnotationType.CUSTOM);
        check(Files.readString(customSaved).contains("gZip\tcompressed 中文"), "Gzip payload copied into plain TSV storage");
        java.nio.charset.Charset legacyCharset = java.nio.charset.Charset.defaultCharset();
        if (!legacyCharset.equals(java.nio.charset.StandardCharsets.UTF_8)) {
            String legacyText = "#GeneID\tDescription\ngNative\tcafé\n";
            Path nativeText = temporary.resolve("native.tsv");
            Files.write(nativeText, legacyText.getBytes(legacyCharset));
            check(custom.loadCustomAnnotations(nativeText.toFile(), AnnotationType.CUSTOM, "\t", true), "Legacy native charset input");
            check("café".equals(custom.getGeneAnnotations("gNative", AnnotationType.CUSTOM).get(0).getColumnValue(1)),
                "Legacy native charset value changed");
        }
        speciesSaveFailure(temporary, false);
        speciesClearRoundTrip(temporary);
        tablePresentationRoundTrip(temporary);
        editedFieldsRoundTrip(temporary);
        mappedAnnotationsRoundTrip(temporary);
        unnamedColumnRoundTrip(temporary);
        ontologyEncodingRoundTrip(temporary);
        System.out.println("PASS annotations: strict headers/comments, edited PFAM/InterPro/CUSTOM fields and unknown columns, transcript mapping, transactional load/save, mixed-type failure, durable deletion, lazy preservation and full/filtered/paged metadata tables");
    }
}
