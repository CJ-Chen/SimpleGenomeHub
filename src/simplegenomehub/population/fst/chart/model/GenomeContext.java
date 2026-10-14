package simplegenomehub.population.fst.chart.model;

import simplegenomehub.population.fst.chart.FSTChromosomeRange;
import simplegenomehub.population.fst.chart.FSTWindow;
import java.util.List;

/**
 * 基因组上下文：管理全局坐标系、染色体信息、选择状态等
 */
public class GenomeContext {
    private final List<FSTChromosomeRange.ChromosomeRange> chromRanges;
    private final List<FSTWindow> referenceWindows; // 参考窗口列表（用于X映射）
    private String selectedChromosome;
    private PositionLine positionLine;
    private List<String> chromosomeOrder; // 染色体显示顺序
    
    public GenomeContext(List<FSTWindow> referenceWindows) {
        this.referenceWindows = referenceWindows;
        this.chromRanges = FSTChromosomeRange.buildChromosomeRangesOnce(referenceWindows);
        this.selectedChromosome = null;
        this.positionLine = null;
        // 初始化染色体顺序
        this.chromosomeOrder = new java.util.ArrayList<>();
        for (FSTChromosomeRange.ChromosomeRange range : chromRanges) {
            if (!chromosomeOrder.contains(range.chrom)) {
                chromosomeOrder.add(range.chrom);
            }
        }
    }
    
    public List<FSTChromosomeRange.ChromosomeRange> getChromRanges() {
        return chromRanges;
    }
    
    public List<FSTWindow> getReferenceWindows() {
        return referenceWindows;
    }
    
    public String getSelectedChromosome() {
        return selectedChromosome;
    }
    
    public void setSelectedChromosome(String chromosome) {
        this.selectedChromosome = chromosome;
    }
    
    public PositionLine getPositionLine() {
        return positionLine;
    }
    
    public void setPositionLine(PositionLine positionLine) {
        this.positionLine = positionLine;
    }
    
    public List<String> getChromosomeOrder() {
        return new java.util.ArrayList<>(chromosomeOrder);
    }
    
    public void setChromosomeOrder(List<String> order) {
        this.chromosomeOrder = new java.util.ArrayList<>(order);
    }
    
    public void moveChromosome(int fromIndex, int toIndex) {
        if (fromIndex < 0 || fromIndex >= chromosomeOrder.size() ||
            toIndex < 0 || toIndex >= chromosomeOrder.size() ||
            fromIndex == toIndex) {
            return;
        }
        String chrom = chromosomeOrder.remove(fromIndex);
        chromosomeOrder.add(toIndex, chrom);
    }
    
    /**
     * 位置线信息
     */
    public static class PositionLine {
        public final String chromosome;
        public final int position1;
        public final int position2;
        
        public PositionLine(String chromosome, int position1) {
            this(chromosome, position1, -1);
        }
        
        public PositionLine(String chromosome, int position1, int position2) {
            this.chromosome = chromosome;
            this.position1 = position1;
            this.position2 = position2;
        }
    }
}
