package simplegenomehub.population.fst.chart.render;

import simplegenomehub.population.fst.chart.FSTChartStyle;
import simplegenomehub.population.fst.chart.FSTDisplayCache;
import simplegenomehub.population.fst.chart.FSTWindow;
import simplegenomehub.population.fst.chart.model.DataSet;
import simplegenomehub.population.fst.chart.model.GenomeContext;
import simplegenomehub.population.fst.chart.model.PlotItem;
import java.awt.*;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

/**
 * FST数据渲染器
 */
public class FstRenderer implements DataRenderer {
    private static final int HOVER_DISTANCE = 20;
    
    private final Color firstColor;
    private final Color secondColor;
    private final Color topColor;
    private final double threshold;
    private final double topPercent;
    
    public FstRenderer(Color firstColor, Color secondColor, Color topColor, 
                       double threshold, double topPercent) {
        this.firstColor = firstColor;
        this.secondColor = secondColor;
        this.topColor = topColor;
        this.threshold = threshold;
        this.topPercent = topPercent;
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
        BitSet topBits = (BitSet) renderState.get("topBits");
        Integer hoveredIndex = (Integer) renderState.get("hoveredIndex");
        Integer selectedIndex = (Integer) renderState.get("selectedIndex");
        // Y轴上限（折叠时由轨道按数据最大值收紧；默认1.0）
        Object axisMaxObj = renderState.get("fstAxisMax");
        double fstAxisMax = (axisMaxObj instanceof Double && (Double) axisMaxObj > 0) ? (Double) axisMaxObj : 1.0;
        
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
        
        // 绘制连线
        if (item.getStyle() == PlotItem.PlotStyle.LINE) {
            g.setStroke(FSTChartStyle.STROKE_LINE);
            int prevX = -1, prevY = -1;
            String lastChrom = null;
            int color = 0;
            
            for (int di = 0; di < n; di++) {
                int oi = displayCache.displayIndexToOriginalIndex(genome.getSelectedChromosome(), di);
                if (oi < 0 || oi >= windows.size()) continue;
                
                FSTWindow w = windows.get(oi);
                String chrom = w.getChrom();
                
                if (lastChrom != null && !lastChrom.equals(chrom)) {
                    color ^= 1;
                    prevX = prevY = -1;
                }
                lastChrom = chrom;
                
                double fst = w.getAvgFst();
                if (!Double.isFinite(fst)) continue;
                
                int x = ctx.xForIndex(di, scale);
                int y = ctx.yForValue(fst, fstAxisMax);
                
                if (prevX >= 0) {
                    g.setColor(color == 0 ? firstColor : secondColor);
                    g.drawLine(prevX, prevY, x, y);
                }
                prevX = x;
                prevY = y;
            }
        }
        
        // 绘制点
        String lastChrom = null;
        int color = 0;
        for (int di = 0; di < n; di++) {
            int oi = displayCache.displayIndexToOriginalIndex(genome.getSelectedChromosome(), di);
            if (oi < 0 || oi >= windows.size()) continue;
            
            FSTWindow w = windows.get(oi);
            String chrom = w.getChrom();
            
            if (lastChrom != null && !lastChrom.equals(chrom)) {
                color ^= 1;
            }
            lastChrom = chrom;
            
            double fst = w.getAvgFst();
            if (!Double.isFinite(fst)) continue;
            
            // 只有当PlotItem的topEnabled为true时，才使用topBits判断是否为top位点
            boolean isTop = item.isTopEnabled() && (topBits != null && topBits.get(oi));
            int x = ctx.xForIndex(di, scale);
            int y = ctx.yForValue(fst, fstAxisMax);
            
            if (item.getStyle() == PlotItem.PlotStyle.POINT || isTop) {
                g.setColor(isTop ? topColor : (color == 0 ? firstColor : secondColor));
                g.fillOval(x - FSTChartStyle.POINT_SIZE / 2, y - FSTChartStyle.POINT_SIZE / 2,
                          FSTChartStyle.POINT_SIZE, FSTChartStyle.POINT_SIZE);
            }
            
            // 高亮显示
            if (isTop) {
                boolean hovered = (hoveredIndex != null && hoveredIndex == oi);
                boolean selected = (selectedIndex != null && selectedIndex == oi);
                
                if (hovered || selected) {
                    g.setColor(topColor);
                    if (selected) {
                        g.setStroke(FSTChartStyle.STROKE_SELECTED);
                        g.drawOval(x - FSTChartStyle.POINT_SIZE / 2 - 4, 
                                  y - FSTChartStyle.POINT_SIZE / 2 - 4,
                                  FSTChartStyle.POINT_SIZE + 8, FSTChartStyle.POINT_SIZE + 8);
                    } else {
                        g.setStroke(FSTChartStyle.STROKE_HOVER);
                        g.drawOval(x - FSTChartStyle.POINT_SIZE / 2 - 3,
                                  y - FSTChartStyle.POINT_SIZE / 2 - 3,
                                  FSTChartStyle.POINT_SIZE + 6, FSTChartStyle.POINT_SIZE + 6);
                    }
                    g.setStroke(FSTChartStyle.STROKE_THIN);
                }
            }
        }
        
        // 绘制阈值线（只有当PlotItem的topEnabled为true时才绘制）
        if (item.isTopEnabled()) {
            // 使用PlotItem的topPercent计算阈值
            double itemTopPercent = item.getTopPercent();
            double itemThreshold = calculateTopThreshold(windows, itemTopPercent);
            
            if (Double.isFinite(itemThreshold)) {
                g.setColor(topColor);
                g.setStroke(FSTChartStyle.STROKE_DASH);
                int ty = ctx.yForValue(itemThreshold, fstAxisMax);
                g.drawLine(plotX, ty, plotX + ctx.plotWidth, ty);
                
                g.setFont(FSTChartStyle.thresholdFont());
                FontMetrics fm = g.getFontMetrics();
                int lx = plotX + ctx.plotWidth + 10;
                int ly = ty - fm.getHeight() / 2;
                g.drawString("Top " + formatPercent(itemTopPercent) + "%", lx, ly);
                g.drawString(String.format("%.4f", itemThreshold), lx, ly + fm.getHeight());
            }
        }
    }
    
    @Override
    public HitResult hitTest(Point p, PlotContext ctx, GenomeContext genome, PlotItem item) {
        // 实现命中测试逻辑（类似原来的windowIndexAt）
        return HitResult.none();
    }
    
    private static String formatPercent(double p) {
        double v = p * 100;
        if (v < 0.01) return String.format("%.3f", v);
        if (v < 0.1) return String.format("%.2f", v);
        if (v < 1.0) return String.format("%.1f", v);
        return String.format("%.0f", v);
    }
    
    /**
     * 根据top百分比计算阈值
     */
    private double calculateTopThreshold(List<FSTWindow> windows, double topPercent) {
        if (windows == null || windows.isEmpty() || topPercent <= 0 || topPercent > 1.0) {
            return Double.NaN;
        }
        
        // 收集所有有效的FST值
        List<Double> fstValues = new java.util.ArrayList<>();
        for (FSTWindow w : windows) {
            double fst = w.getAvgFst();
            if (Double.isFinite(fst)) {
                fstValues.add(fst);
            }
        }
        
        if (fstValues.isEmpty()) return Double.NaN;
        
        // 排序
        fstValues.sort(java.util.Collections.reverseOrder());
        
        // 计算top百分比对应的索引
        int topIndex = (int) Math.max(0, Math.ceil(fstValues.size() * topPercent) - 1);
        if (topIndex >= fstValues.size()) topIndex = fstValues.size() - 1;
        
        return fstValues.get(topIndex);
    }
}
