package simplegenomehub.population.fst.chart.render;

import simplegenomehub.population.fst.chart.FSTChartStyle;

import java.awt.Rectangle;

/**
 * 绘图上下文：包含绘图区域的几何信息，以及“数值 → 像素”的映射。
 * <p>
 * 映射集中放在这里，是因为绘制（{@code FstRenderer} 等）和鼠标命中测试
 * （{@code PlotInteractionController}）必须使用完全相同的一套算法：
 * 一旦两边各算各的，缩放轨道高度或切换 Y 轴范围后，画出来的标注点和能被点中的位置就会错开。
 */
public class PlotContext {
    public final int plotX;
    public final int plotY;
    public final int plotWidth;
    public final int plotHeight;
    public final int padding;
    
    public PlotContext(int plotX, int plotY, int plotWidth, int plotHeight, int padding) {
        this.plotX = plotX;
        this.plotY = plotY;
        this.plotWidth = plotWidth;
        this.plotHeight = plotHeight;
        this.padding = padding;
    }
    
    /** 由绘图区域创建上下文；绘制与命中测试都用它，保证两侧几何一致。 */
    public static PlotContext of(Rectangle plotBounds) {
        return new PlotContext(plotBounds.x, plotBounds.y, plotBounds.width, plotBounds.height, 200);
    }
    
    public Rectangle getBounds() {
        return new Rectangle(plotX, plotY, plotWidth, plotHeight);
    }
    
    public double getScalePerIndex(int displayCount) {
        if (plotWidth <= 0 || displayCount <= 0) return 0.0;
        return (double) plotWidth / Math.max(10, displayCount);
    }
    
    /** display 序号 → 像素 X。 */
    public int xForIndex(int displayIndex, double scale) {
        return plotX + (int) (displayIndex * scale);
    }
    
    /**
     * 数值 → 像素 Y（0 在底部，{@code axisMax} 在顶部）。
     * 与 FST / PI / Logistic 的绘制方式一致。
     *
     * @param value   数值
     * @param axisMax Y 轴上限（折叠轨道时按数据最大值收窄，默认 1.0）
     */
    public int yForValue(double value, double axisMax) {
        double max = (Double.isFinite(axisMax) && axisMax > 0) ? axisMax : 1.0;
        return plotY + (int) (plotHeight * (1.0 - FSTChartStyle.clamp01(value / max)));
    }
    
    /**
     * 有正负的数值（Tajima's D）→ 像素 Y：0 在中间，正值在上、负值在下。
     *
     * @param value   数值
     * @param axisMax Y 轴上限（绝对值）
     */
    public int yForSignedValue(double value, double axisMax) {
        double max = (Double.isFinite(axisMax) && axisMax > 0) ? axisMax : 1.0;
        double normalized = Math.max(-1.0, Math.min(1.0, value / max));
        return plotY + plotHeight / 2 - (int) (plotHeight / 2 * normalized);
    }
}
