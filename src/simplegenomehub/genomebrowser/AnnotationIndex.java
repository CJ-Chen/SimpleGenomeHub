package simplegenomehub.genomebrowser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Streaming GFF3/GTF annotation model for the genome browser.
 * Features are grouped into loci (gene or standalone transcript) and stored per
 * chromosome with a coarse bin index so a viewport only pulls the visible ones.
 */
public final class AnnotationIndex {

    private static final int BIN_SIZE = 1 << 20;
    private static final int MAX_BINS_PER_LOCUS = 64;

    /** A half-open [start, end) interval in 0-based sequence coordinates. */
    public static final class Part {
        public final int start;
        public final int end;

        Part(int start, int end) {
            this.start = Math.min(start, end);
            this.end = Math.max(start, end);
        }

        public int length() {
            return end - start;
        }
    }

    /** One transcript (mRNA/CDS/tRNA/...) with its exon and CDS segments. */
    public static final class Transcript {
        public final String id;
        public final String name;
        public final String product;
        public final char strand;
        public final List<Part> exons;
        public final List<Part> cds;
        /** GFF3 codon_start 的 0 基形式（0..2）；-1 表示这一行没有标注。 */
        public final int codonStart;

        Transcript(String id, String name, String product, char strand,
                   List<Part> exons, List<Part> cds, int codonStart) {
            this.id = id;
            this.name = name;
            this.product = product;
            this.strand = strand;
            this.exons = exons;
            this.cds = cds;
            this.codonStart = codonStart;
        }

        public int start() {
            int min = Integer.MAX_VALUE;
            for (Part p : exons) min = Math.min(min, p.start);
            for (Part p : cds) min = Math.min(min, p.start);
            return min == Integer.MAX_VALUE ? 0 : min;
        }

        public int end() {
            int max = 0;
            for (Part p : exons) max = Math.max(max, p.end);
            for (Part p : cds) max = Math.max(max, p.end);
            return max;
        }
    }

    /** A gene, or a standalone transcript when no gene is present. */
    public static final class Locus {
        public final String chromosome;
        public final String id;
        public final String name;
        public final char strand;
        public final List<Transcript> transcripts;
        public final int start;
        public final int end;
        final String searchText;
        public int lane;

        Locus(String chromosome, String id, String name, char strand, List<Transcript> transcripts) {
            this.chromosome = chromosome;
            this.id = id;
            this.name = name;
            this.strand = strand;
            this.transcripts = transcripts;
            int min = Integer.MAX_VALUE, max = 0;
            StringBuilder text = new StringBuilder();
            text.append(id == null ? "" : id).append(' ').append(name == null ? "" : name);
            for (Transcript t : transcripts) {
                min = Math.min(min, t.start());
                max = Math.max(max, t.end());
                text.append(' ').append(t.id == null ? "" : t.id)
                    .append(' ').append(t.name == null ? "" : t.name)
                    .append(' ').append(t.product == null ? "" : t.product);
            }
            this.start = min == Integer.MAX_VALUE ? 0 : min;
            this.end = max;
            this.searchText = text.toString().toLowerCase(Locale.ROOT);
        }

        public String displayName() {
            if (name != null && !name.isEmpty()) return name;
            if (id != null && !id.isEmpty()) return id;
            return "feature";
        }
    }

    private static final class Binlist {
        final List<List<Locus>> bins = new ArrayList<>();
        final List<Locus> longLoci = new ArrayList<>();
    }

    private static final class Row {
        String seqid, type, id, parent, name, product, geneId, transcriptId, geneName;
        int start, end;
        char strand;
        int codonStart = -1;
        boolean consumed;
    }

    /**
     * 解析 GFF3 第 7 列（strand）。GFF3 规范里只有一个字符：{@code +}、{@code -}、{@code .} 或 {@code ?}。
     * 这里做防御性解析：取第一个非空白字符，并把 {@code -} 之外的所有值都当作 {@code +}。
     */
    private static char parseStrand(String field) {
        if (field == null) return '+';
        for (int i = 0; i < field.length(); i++) {
            char c = field.charAt(i);
            if (Character.isWhitespace(c)) continue;
            return c;
        }
        return '+';
    }

    /** 基因座与转录本的链一定要取一致：某些 GFF3 只在 exon/CDS 行写链。 */
    private static char strandOf(char primary, List<Transcript> transcripts) {
        if (primary == '-') return '-';
        for (Transcript transcript : transcripts) {
            if (transcript.strand == '-') return '-';
        }
        return '+';
    }

    private final Map<String, List<Locus>> lociByChromosome = new LinkedHashMap<>();
    private final Map<String, Binlist> binsByChromosome = new LinkedHashMap<>();
    private final List<Locus> allLoci = new ArrayList<>();

    private AnnotationIndex() {
    }

    public static AnnotationIndex load(File gffFile, Map<String, Integer> chromosomeLengths) throws IOException {
        AnnotationIndex index = new AnnotationIndex();
        if (gffFile == null || !gffFile.isFile()) {
            return index;
        }
        Map<String, List<Row>> rowsByChromosome = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(gffFile), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                String[] fields = line.split("\t", -1);
                if (fields.length < 9) continue;
                Row row = new Row();
                try {
                    row.seqid = fields[0].trim();
                    row.type = fields[2].trim();
                    row.start = Integer.parseInt(fields[3].trim()) - 1;
                    row.end = Integer.parseInt(fields[4].trim());
                } catch (NumberFormatException ex) {
                    continue;
                }
                if (row.start < 0) row.start = 0;
                if (row.end < row.start) row.end = row.start;
                row.strand = parseStrand(fields[6]);
                Map<String, String> attrs = parseAttributes(fields[8]);
                row.id = firstNonEmpty(attrs.get("ID"), attrs.get("id"));
                row.parent = firstNonEmpty(attrs.get("Parent"), attrs.get("parent"));
                row.geneId = firstNonEmpty(attrs.get("gene_id"), attrs.get("geneID"), attrs.get("gene"));
                row.transcriptId = firstNonEmpty(attrs.get("transcript_id"), attrs.get("transcriptId"));
                row.name = firstNonEmpty(attrs.get("Name"), attrs.get("name"), row.geneId, row.id);
                row.product = firstNonEmpty(attrs.get("product"), attrs.get("Product"), attrs.get("description"));
                row.geneName = firstNonEmpty(attrs.get("gene"), attrs.get("gene_name"), attrs.get("locus_tag"));
                String codonStart = attrs.get("codon_start");
                if (codonStart != null) {
                    try { row.codonStart = Math.max(0, Math.min(2, Integer.parseInt(codonStart.trim()) - 1)); }
                    catch (NumberFormatException ignored) { }
                }
                rowsByChromosome.computeIfAbsent(row.seqid, k -> new ArrayList<>()).add(row);
            }
        }
        for (Map.Entry<String, List<Row>> entry : rowsByChromosome.entrySet()) {
            index.buildChromosome(entry.getKey(), entry.getValue());
        }
        return index;
    }

    private void buildChromosome(String chromosome, List<Row> rows) {
        Map<String, Row> byId = new HashMap<>();
        for (Row row : rows) {
            if (row.id != null && !row.id.isEmpty() && !byId.containsKey(row.id)) {
                byId.put(row.id, row);
            }
        }
        Map<String, List<Row>> children = new LinkedHashMap<>();
        for (Row row : rows) {
            if (row.parent == null) continue;
            for (String parent : row.parent.split(",")) {
                String key = parent.trim();
                if (!key.isEmpty()) children.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
            }
        }

        List<Locus> loci = new ArrayList<>();
        for (Row row : rows) {
            if (!isGene(row.type)) continue;
            String geneKey = row.id != null ? row.id : row.name;
            List<Transcript> transcripts = new ArrayList<>();
            List<Row> geneChildren = children.getOrDefault(geneKey, Collections.emptyList());
            for (Row child : geneChildren) {
                if (isTranscriptLike(child.type)) {
                    child.consumed = true;
                    transcripts.add(buildTranscript(child, children, geneChildren));
                }
            }
            if (transcripts.isEmpty()) {
                transcripts.add(buildTranscriptFromGene(row, geneChildren));
            }
            loci.add(new Locus(chromosome, geneKey, row.name, strandOf(row.strand, transcripts), transcripts));
        }
        for (Row row : rows) {
            if (!isTranscriptLike(row.type) || row.consumed) continue;
            if (row.parent != null && byId.containsKey(row.parent.trim()) && isGene(byId.get(row.parent.trim()).type)) {
                continue;
            }
            String id = row.id != null ? row.id : row.name;
            List<Transcript> transcripts = new ArrayList<>();
            transcripts.add(buildTranscript(row, children, Collections.emptyList()));
            loci.add(new Locus(chromosome, id, row.name, strandOf(row.strand, transcripts), transcripts));
        }
        loci.sort((a, b) -> Integer.compare(a.start, b.start));
        lociByChromosome.put(chromosome, loci);
        allLoci.addAll(loci);
        binsByChromosome.put(chromosome, buildBins(loci));
    }

    private static Transcript buildTranscript(Row tx, Map<String, List<Row>> children, List<Row> geneChildren) {
        String key = tx.id != null ? tx.id : tx.transcriptId;
        List<Row> own = key == null ? Collections.emptyList() : children.getOrDefault(key, Collections.emptyList());
        List<Part> exons = partsOf(own, "exon");
        List<Part> cds = partsOf(own, "CDS");
        int codonStart = codonStartOf(own);
        if (exons.isEmpty() && cds.isEmpty() && geneChildren != null) {
            exons = partsOf(geneChildren, "exon");
            cds = partsOf(geneChildren, "CDS");
            codonStart = codonStartOf(geneChildren);
        }
        if (exons.isEmpty()) {
            exons = cds.isEmpty() ? Collections.singletonList(new Part(tx.start, tx.end)) : cds;
        }
        return new Transcript(key, firstNonEmpty(tx.name, tx.geneName), tx.product, tx.strand,
            sortParts(exons), sortParts(cds), codonStart);
    }

    private static Transcript buildTranscriptFromGene(Row gene, List<Row> geneChildren) {
        List<Part> exons = partsOf(geneChildren, "exon");
        List<Part> cds = partsOf(geneChildren, "CDS");
        if (exons.isEmpty()) {
            exons = cds.isEmpty() ? Collections.singletonList(new Part(gene.start, gene.end)) : cds;
        }
        String id = gene.id != null ? gene.id : gene.name;
        return new Transcript(id, firstNonEmpty(gene.name, gene.geneName), gene.product, gene.strand,
            sortParts(exons), sortParts(cds), codonStartOf(geneChildren));
    }

    /** 这批行里第一个标注了 codon_start 的 CDS 值；都没有则 -1。 */
    private static int codonStartOf(List<Row> rows) {
        for (Row row : rows) {
            if (row.type != null && row.type.equalsIgnoreCase("CDS") && row.codonStart >= 0) {
                return row.codonStart;
            }
        }
        return -1;
    }

    private static List<Part> partsOf(List<Row> rows, String type) {
        List<Part> parts = new ArrayList<>();
        for (Row row : rows) {
            if (type.equalsIgnoreCase(row.type)) {
                row.consumed = true;
                parts.add(new Part(row.start, row.end));
            }
        }
        return parts;
    }

    private static List<Part> sortParts(List<Part> parts) {
        if (parts.size() > 1) {
            parts.sort((a, b) -> Integer.compare(a.start, b.start));
        }
        return parts;
    }

    private static Binlist buildBins(List<Locus> loci) {
        Binlist binlist = new Binlist();
        for (Locus locus : loci) {
            int first = locus.start / BIN_SIZE;
            int last = Math.max(first, (Math.max(locus.start, locus.end - 1)) / BIN_SIZE);
            if (last - first > MAX_BINS_PER_LOCUS) {
                binlist.longLoci.add(locus);
                continue;
            }
            while (binlist.bins.size() <= last) {
                binlist.bins.add(new ArrayList<>());
            }
            for (int bin = first; bin <= last; bin++) {
                binlist.bins.get(bin).add(locus);
            }
        }
        return binlist;
    }

    public List<Locus> loci(String chromosome, int from, int to) {
        if (chromosome == null) return Collections.emptyList();
        Binlist binlist = binsByChromosome.get(chromosome);
        List<Locus> loci = lociByChromosome.get(chromosome);
        if (binlist == null || loci == null || loci.isEmpty()) return Collections.emptyList();
        List<Locus> result = new ArrayList<>();
        int first = Math.max(0, from / BIN_SIZE);
        int last = Math.max(first, to / BIN_SIZE);
        for (int bin = first; bin <= last && bin < binlist.bins.size(); bin++) {
            for (Locus locus : binlist.bins.get(bin)) {
                if (locus.end > from && locus.start < to) result.add(locus);
            }
        }
        for (Locus locus : binlist.longLoci) {
            if (locus.end > from && locus.start < to) result.add(locus);
        }
        result.sort((a, b) -> Integer.compare(a.start, b.start));
        return result;
    }

    public List<Locus> search(String query) {
        List<Locus> result = new ArrayList<>();
        if (query == null) return result;
        String needle = query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return result;
        for (Locus locus : allLoci) {
            if (locus.searchText.contains(needle)) result.add(locus);
        }
        result.sort((a, b) -> Integer.compare(a.start, b.start));
        return result;
    }

    static Map<String, String> parseAttributes(String attributes) {
        Map<String, String> map = new LinkedHashMap<>();
        if (attributes == null || attributes.isEmpty()) return map;
        for (String pair : attributes.split(";")) {
            String trimmed = pair.trim();
            if (trimmed.isEmpty()) continue;
            int equals = trimmed.indexOf('=');
            if (equals > 0) {
                putAttribute(map, trimmed.substring(0, equals).trim(), decode(trimmed.substring(equals + 1).trim()));
                continue;
            }
            int space = firstWhitespace(trimmed);
            if (space > 0) {
                putAttribute(map, trimmed.substring(0, space).trim(), stripQuotes(trimmed.substring(space + 1).trim()));
            }
        }
        return map;
    }

    private static void putAttribute(Map<String, String> map, String key, String value) {
        if (!key.isEmpty() && value != null) map.putIfAbsent(key, value);
    }

    private static int firstWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) return i;
        }
        return -1;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String decode(String value) {
        if (value.indexOf('%') < 0) return value;
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%' && i + 2 < value.length()) {
                try {
                    builder.append((char) Integer.parseInt(value.substring(i + 1, i + 3), 16));
                    i += 2;
                    continue;
                } catch (NumberFormatException ignored) { }
            }
            builder.append(c);
        }
        return builder.toString();
    }

    static boolean isGene(String type) {
        if (type == null) return false;
        String lower = type.toLowerCase(Locale.ROOT);
        return lower.equals("gene") || lower.equals("pseudogene");
    }

    static boolean isTranscriptLike(String type) {
        if (type == null) return false;
        String lower = type.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "mrna": case "transcript": case "cds": case "trna": case "rrna":
            case "ncrna": case "mirna": case "snrna": case "snorna": case "rrna_5s":
            case "misc_rna": case "misc_feature": case "gap":
                return true;
            default:
                return lower.endsWith("_rna") || lower.startsWith("rrna") || lower.startsWith("trna");
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return null;
    }
}
