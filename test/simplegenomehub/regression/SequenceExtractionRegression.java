package simplegenomehub.regression;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import simplegenomehub.util.fileio.SequenceExtractor;

/** Fixed expected residues and genomic coordinates; no native tools or UI required. */
public final class SequenceExtractionRegression {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static Map<String, String> fasta(Path path) throws Exception {
        return SequenceExtractor.loadGenomeSequences(path.toFile());
    }
    private static String transcript(Map<String, String> sequences, String id) {
        for (String key : sequences.keySet())
            if (key.equals(id) || key.equals(id + "_CDS") || key.equals(id + "_cds") || key.equals(id + "_protein"))
                return sequences.get(key);
        return null;
    }
    public static void main(String[] args) throws Exception {
        boolean baseline = Arrays.asList(args).contains("--baseline");
        Path output = args.length > 0 && !args[0].equals("--baseline")
            ? Files.createDirectories(Path.of(args[0]).toAbsolutePath()) : Files.createTempDirectory("sgh-sequence-regression-");
        System.setProperty("user.home", Files.createDirectories(output.resolve("isolated-home")).toString());
        System.setProperty("java.io.tmpdir", Files.createDirectories(output.resolve("isolated-tmp")).toString());
        Path fixture = Path.of(System.getProperty("sgh.fixtures", "test/fixtures/science")).toAbsolutePath();
        File genome = fixture.resolve("genome.fa").toFile();
        File annotation = fixture.resolve("annotation.gff3").toFile();
        Path allCds = output.resolve("all-cds.fa");
        check(SequenceExtractor.extractCDS(genome, annotation, allCds.toFile()).isSuccess(), "CDS extraction failed");
        Map<String, String> cds = fasta(allCds);
        Path promoters = output.resolve("promoters.fa");
        SequenceExtractor.ExtractionResult promoterResult = SequenceExtractor.extractPromoters(
            fixture.resolve("promoters.fa").toFile(), fixture.resolve("promoters.gff3").toFile(), promoters.toFile(), 5);
        check(promoterResult.isSuccess(), "Promoter extraction failed");
        Map<String, String> promoter = fasta(promoters);
        if (baseline) {
            check(!"CTAAAA".equals(transcript(cds, "txB")), "Baseline no longer reproduces minus CDS segment-order defect");
            check(!"TCCAC".equals(promoter.get("minusNested")), "Baseline no longer reproduces nested minus promoter-coordinate defect");
            System.out.println("REPRODUCED baseline: minus multi-CDS residues=" + transcript(cds, "txB")
                + "; expected CTAAAA (high-to-low segments)");
            System.out.println("REPRODUCED baseline: nested minus promoter=" + promoter.get("minusNested")
                + "; expected TCCAC from genomic 51..55 (maximum CDS end 50)");
            return;
        }
        check(cds.size() == 3, "Expected all three transcript CDS records");
        check("ATGGGG".equals(transcript(cds, "txAshort")), "Plus multi-CDS segment order or inclusive endpoints changed");
        check("ATGAAACCC".equals(transcript(cds, "txAlong")), "Plus single CDS differs");
        check("CTAAAA".equals(transcript(cds, "txB")), "Minus multi-CDS must concatenate high-to-low reverse-complemented segments");
        Path proteins = output.resolve("all-proteins.fa");
        check(SequenceExtractor.extractProteins(genome, annotation, proteins.toFile()).isSuccess(), "Protein extraction failed");
        Map<String, String> protein = fasta(proteins);
        check("MG".equals(transcript(protein, "txAshort")), "Plus multi-CDS translation differs");
        check("MKP".equals(transcript(protein, "txAlong")), "Plus protein translation differs");
        check("LK".equals(transcript(protein, "txB")), "Minus protein translation differs");
        Path representativeCds = output.resolve("representative-cds.fa");
        check(SequenceExtractor.extractRepresentativeCDS(genome, annotation, representativeCds.toFile(), "LONGEST_CDS").isSuccess(),
            "Representative CDS extraction failed");
        Map<String, String> representatives = fasta(representativeCds);
        check(representatives.size() == 2 && "ATGAAACCC".equals(transcript(representatives, "txAlong"))
            && "CTAAAA".equals(transcript(representatives, "txB")), "Representative transcript selection or minus segment order changed");
        Path representativeProteins = output.resolve("representative-proteins.fa");
        check(SequenceExtractor.extractRepresentativeProteins(genome, annotation, representativeProteins.toFile(), "LONGEST_CDS").isSuccess(),
            "Representative protein extraction failed");
        Map<String, String> representativeProtein = fasta(representativeProteins);
        check(representativeProtein.size() == 2 && "MKP".equals(transcript(representativeProtein, "txAlong"))
            && "LK".equals(transcript(representativeProtein, "txB")), "Representative protein residues changed");

        check(promoterResult.getSequencesExtracted() == 6, "Only genes with no upstream bases should be omitted");
        check("TGCAA".equals(promoter.get("plus")), "Plus promoter must precede the minimum CDS start");
        check("GAGCT".equals(promoter.get("minus")), "Minus promoter must follow the maximum CDS end");
        check("AC".equals(promoter.get("plusEdge")), "Plus promoter must clip at the chromosome start");
        check("TG".equals(promoter.get("minusEdge")), "Minus promoter clipping or reverse complement differs");
        check("TCCAC".equals(promoter.get("minusGiven")), "Minus CDS 10..20/40..50 must produce genomic 51..55 once reverse complemented");
        check("TCCAC".equals(promoter.get("minusNested")), "A nested CDS must not move the upstream genomic boundary inside the outer CDS");
        check(!promoter.containsKey("plusNoUpstream") && !promoter.containsKey("minusNoUpstream"), "Empty upstream must not include coding bases");
        String promoterText = Files.readString(promoters);
        check(promoterText.contains("strand=- atg=44 region=45-49"), "Minus promoter coordinate metadata differs");
        check(promoterText.contains(">minusNested promoter upstream=5 chr=chrP strand=- atg=50 region=51-55"),
            "Nested minus promoter metadata does not describe the actual maximum CDS endpoint");

        Map<String, String> chromosome = SequenceExtractor.loadGenomeSequences(genome);
        int[][] bounds = {{1, 1}, {24, 24}, {1, 1}, {24, 24}, {22, 24}};
        String[] strands = {"+", "+", "-", "-", "-"};
        String[] expected = {"A", "G", "T", "C", "CTA"};
        for (int i = 0; i < bounds.length; i++)
            check(expected[i].equals(SequenceExtractor.extractFeatureSequence(chromosome,
                new SequenceExtractor.FeatureInfo("chr1", "region", bounds[i][0], bounds[i][1], strands[i]))),
                "Inclusive single-base or terminal interval differs at case " + i);
        check(SequenceExtractor.extractFeatureSequence(chromosome,
            new SequenceExtractor.FeatureInfo("chr1", "region", 0, 1, "+")) == null, "Zero-based coordinate incorrectly accepted");
        check(SequenceExtractor.extractFeatureSequence(chromosome,
            new SequenceExtractor.FeatureInfo("chr1", "region", 24, 25, "+")) == null, "Beyond-end coordinate incorrectly accepted");
        System.out.println("PASS sequence extraction: both strands, multiple CDS, translation, representative transcripts, nonuniform promoter coordinates and chromosome edges");
    }
}
