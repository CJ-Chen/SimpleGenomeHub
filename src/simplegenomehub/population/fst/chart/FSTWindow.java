package simplegenomehub.population.fst.chart;

/**
 * FST窗口类，表示一个滑动窗口的平均FST值
 */
public class FSTWindow {
    private final String chrom;
    private final int startPos;
    private final int endPos;
    private final double avgFst;
    
    public FSTWindow(String chrom, int startPos, int endPos, double avgFst) {
        this.chrom = chrom;
        this.startPos = startPos;
        this.endPos = endPos;
        this.avgFst = avgFst;
    }
    
    public String getChrom() {
        return chrom;
    }
    
    public int getStartPos() {
        return startPos;
    }
    
    public int getEndPos() {
        return endPos;
    }
    
    public double getAvgFst() {
        return avgFst;
    }
}

