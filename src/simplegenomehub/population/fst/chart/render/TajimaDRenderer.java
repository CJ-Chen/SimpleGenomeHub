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
 * TajimaD数据渲染器
 */
public class TajimaDRenderer implements DataRenderer {
    private Color tajimaDColor;  // TajimaD数据的颜色
    
    public TajimaDRenderer(Color tajimaDColor) {
        this.tajimaDColor = tajimaDColor;
    }
    
    /**
     * 设置TajimaD颜色
     */
    public void setColor(Color color) {
        this.tajimaDColor = color;
    }
    
    /**
     * 获取TajimaD颜色
     */
    public Color getColor() {
        return tajimaDColor;
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
        Double tajimaDMaxValue = (Double) renderState.get("tajimaDMaxValue");
        
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
        
        // 如果没有tajimaDMaxValue，使用默认值
        if (tajimaDMaxValue == null || tajimaDMaxValue <= 0) {
            tajimaDMaxValue = 1.0;
        }
        
        // 计算y轴0点的位置（在中间）
        int zeroY = plotY + plotH / 2;
        
        // 绘制连线
        if (item.getStyle() == PlotItem.PlotStyle.LINE) {
            g.setStroke(FSTChartStyle.STROKE_LINE);
            g.setColor(tajimaDColor);
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
                
                double tajimaDValue = w.getAvgFst();  // TajimaD值存储在avgFst字段中
                if (!Double.isFinite(tajimaDValue)) continue;
                
                int x = ctx.xForIndex(di, scale);
                // 计算y坐标：0点在中间，正负值对称
                // 正值在上方，负值在下方
                int y = ctx.yForSignedValue(tajimaDValue, tajimaDMaxValue);
                
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
            double tajimaDValue = w.getAvgFst();  // TajimaD值存储在avgFst字段中
            if (!Double.isFinite(tajimaDValue)) continue;
            
            int x = ctx.xForIndex(di, scale);
            // 计算y坐标：0点在中间，正负值对称
            int y = ctx.yForSignedValue(tajimaDValue, tajimaDMaxValue);
            
            if (item.getStyle() == PlotItem.PlotStyle.POINT) {
                g.setColor(tajimaDColor);
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
