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
 * PI数据渲染器
 */
public class PiRenderer implements DataRenderer {
    private Color piColor;  // PI数据的颜色（从settings获取）
    
    public PiRenderer(Color piColor) {
        this.piColor = piColor;
    }
    
    /**
     * 设置PI颜色
     */
    public void setColor(Color color) {
        this.piColor = color;
    }
    
    /**
     * 获取PI颜色
     */
    public Color getColor() {
        return piColor;
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
        Double piMaxValue = (Double) renderState.get("piMaxValue");
        
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
        
        // 如果没有piMaxValue，使用默认的0-1范围
        if (piMaxValue == null || piMaxValue <= 0) {
            piMaxValue = 1.0;
        }
        
        // 绘制连线
        if (item.getStyle() == PlotItem.PlotStyle.LINE) {
            g.setStroke(FSTChartStyle.STROKE_LINE);
            g.setColor(piColor);
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
                
                double piValue = w.getAvgFst();  // PI值存储在avgFst字段中
                if (!Double.isFinite(piValue)) continue;
                
                int x = ctx.xForIndex(di, scale);
                // 使用piMaxValue作为上限计算y坐标
                int y = ctx.yForValue(piValue, piMaxValue);
                
                if (prevX >= 0) {
                    g.drawLine(prevX, prevY, x, y);
                }
                prevX = x;
                prevY = y;
            }
        }
        
        // 绘制点
        for (int di = 0; di < n; di++) {
            int oi = displayCache.displayIndexToOriginalIndex(genome.getSelectedChromosome(), di);
            if (oi < 0 || oi >= windows.size()) continue;
            
            FSTWindow w = windows.get(oi);
            double piValue = w.getAvgFst();  // PI值存储在avgFst字段中
            if (!Double.isFinite(piValue)) continue;
            
            int x = ctx.xForIndex(di, scale);
            // 使用piMaxValue作为上限计算y坐标
            int y = ctx.yForValue(piValue, piMaxValue);
            
            if (item.getStyle() == PlotItem.PlotStyle.POINT) {
                g.setColor(piColor);
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
