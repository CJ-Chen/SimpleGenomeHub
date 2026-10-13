package simplegenomehub.visualization.regionset;

/**
 * A chromosome interval (1-based, inclusive) that can be turned into a Region Set entry.
 * Browser agnostic: any viewer (FST, GWAS, ...) can build one from a hit point.
 */
public final class GenomicInterval {

    private final String chromosome;
    private final long start;
    private final long end;

    public GenomicInterval(String chromosome, long start, long end) {
        this.chromosome = chromosome;
        this.start = start;
        this.end = end;
    }

    public String getChromosome() {
        return chromosome;
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
    }
}
