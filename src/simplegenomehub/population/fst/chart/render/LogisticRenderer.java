package simplegenomehub.population.fst.chart.render;

import simplegenomehub.population.fst.chart.FSTChartStyle;
import simplegenomehub.population.fst.chart.FSTDisplayCache;
import simplegenomehub.population.fst.chart.FSTWindow;
import simplegenomehub.population.fst.chart.model.DataSet;
import simplegenomehub.population.fst.chart.model.GenomeContext;
import simplegenomehub.population.fst.chart.model.PlotItem;
import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * Logistic数据渲染器
 * 绘制GWAS分析结果，y轴为-log10(P值)
 */
public class LogisticRenderer implements DataRenderer {
    private Color logisticColor;  // Logistic数据的颜色
    
    public LogisticRenderer(Color logisticColor) {
        this.logisticColor = logisticColor;
    }
    
    /**
     * 设置Logistic颜色
     */
    public void setColor(Color color) {
        this.logisticColor = color;
    }
    
    /**
     * 获取Logistic颜色
     */
    public Color getColor() {
        return logisticColor;
    }
    
    @Override
    public void render(Graphics2D g, PlotContext ctx, GenomeContext genome, PlotItem item,
                      Map<String, Object> renderState) {
        // 启用抗锯齿，确保线条和圆点平滑
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        
        // 从renderState获取数据
        DataSet dataSet = (DataSet) renderState.get("dataSet");
        FSTDisplayCache displayCache = (FSTDisplayCache) renderState.get("displayCache");
        Double logisticMaxValue = (Double) renderState.get("logisticMaxValue");
        
        if (dataSet == null || displayCache == null) return;
        
        List<FSTWindow> windows = dataSet.getWindows();
        if (windows == null || windows.isEmpty()) return;
        
        displayCache.ensure(genome.getSelectedChromosome());
        int n = displayCache.getDisplayCount(genome.getSelectedChromosome());
        if (n <= 0) return;
        
        double scale = ctx.getScalePerIndex(n);
        if (scale <= 0) return;
        
        int plotX = ctx.plotX;
        int plotY = ctx.plotY;
        int plotH = ctx.plotHeight;
        
        // 如果没有logisticMaxValue，使用默认的最大值
        if (logisticMaxValue == null || logisticMaxValue <= 0) {
            logisticMaxValue = 10.0; // 默认最大值为10（对应P=1e-10）
        }
        
        // 绘制连线
        if (item.getStyle() == PlotItem.PlotStyle.LINE) {
            g.setStroke(FSTChartStyle.STROKE_LINE);
            g.setColor(logisticColor);
            int prevX = -1, prevY = -1;
            String lastChrom = null;
            
            for (int di = 0; di < n; di++) {
                int oi = displayCache.displayIndexToOriginalIndex(genome.getSelectedChromosome(), di);
                if (oi < 0 || oi >= windows.size()) continue;
                
                FSTWindow w = windows.get(oi);
                String chrom = w.getChrom();
                
                if (lastChrom != null && !lastChrom.equals(chrom)) {
                    prevX = prevY = -1;
                }
                lastChrom = chrom;
                
                double negLog10P = w.getAvgFst();  // -log10(P值)存储在avgFst字段中
                if (!Double.isFinite(negLog10P)) continue;
                
                int x = ctx.xForIndex(di, scale);
                // 使用logisticMaxValue作为上限计算y坐标
                // y轴从底部开始，值越大（-log10(P)越大）越靠上
                int y = ctx.yForValue(negLog10P, logisticMaxValue);
                
                if (prevX >= 0) {
                    g.drawLine(prevX, prevY, x, y);
                }
                prevX = x;
                prevY = y;
            }
        }
        
        // 绘制点（窗口化数据，每个窗口一个点）
        for (int di = 0; di < n; di++) {
            int oi = displayCache.displayIndexToOriginalIndex(genome.getSelectedChromosome(), di);
            if (oi < 0 || oi >= windows.size()) continue;
            
            FSTWindow w = windows.get(oi);
            double negLog10P = w.getAvgFst();  // NEG_LOG10_MIN_P值存储在avgFst字段中
            if (!Double.isFinite(negLog10P)) continue;
            
            int x = ctx.xForIndex(di, scale);
            // 使用logisticMaxValue作为上限计算y坐标
            int y = ctx.yForValue(negLog10P, logisticMaxValue);
            
            if (item.getStyle() == PlotItem.PlotStyle.POINT) {
                g.setColor(logisticColor);
                g.fillOval(x - FSTChartStyle.POINT_SIZE / 2, y - FSTChartStyle.POINT_SIZE / 2,
                          FSTChartStyle.POINT_SIZE, FSTChartStyle.POINT_SIZE);
            }
        }
    }
    
    @Override
    public HitResult hitTest(Point p, PlotContext ctx, GenomeContext genome, PlotItem item) {
        // 实现命中测试逻辑
        return HitResult.none();
    }
}
