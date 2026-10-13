package simplegenomehub.population.fst.view;

/**
 * FST数据点类
 */
public class FSTDataPoint {
    private final String chrom;
    private final int pos;
    private final double fst;
    
    public FSTDataPoint(String chrom, int pos, double fst) {
        this.chrom = chrom;
        this.pos = pos;
        this.fst = fst;
    }
    
    public String getChrom() {
        return chrom;
    }
    
    public int getPos() {
        return pos;
    }
    
    public double getFst() {
        return fst;
    }
}
