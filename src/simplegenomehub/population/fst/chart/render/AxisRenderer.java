package simplegenomehub.population.fst.chart.render;

import simplegenomehub.population.fst.chart.FSTChartStyle;
import simplegenomehub.population.fst.chart.FSTChromosomeRange;
import simplegenomehub.population.fst.chart.FSTDisplayCache;
import simplegenomehub.population.fst.chart.FSTPositionMapper;
import simplegenomehub.population.fst.chart.FSTWindow;
import simplegenomehub.population.fst.chart.model.GenomeContext;
import simplegenomehub.population.fst.chart.model.GenomeContext.PositionLine;
import java.awt.*;
import java.util.List;

/**
 * 坐标系渲染器：绘制坐标轴、刻度、分隔线、位置线等
 */
public class AxisRenderer {
    private final Color firstColor;
    private final Color secondColor;
    
    public AxisRenderer(Color firstColor, Color secondColor) {
        this.firstColor = firstColor;
        this.secondColor = secondColor;
    }
    
    public void render(Graphics2D g, PlotContext ctx, GenomeContext genome, 
                      List<FSTWindow> windows, FSTDisplayCache displayCache) {
        render(g, ctx, genome, windows, displayCache, null, false, false);
    }
    
    public void render(Graphics2D g, PlotContext ctx, GenomeContext genome, 
                      List<FSTWindow> windows, FSTDisplayCache displayCache, Double piMaxValue) {
        render(g, ctx, genome, windows, displayCache, piMaxValue, false, false);
    }
    
    public void render(Graphics2D g, PlotContext ctx, GenomeContext genome, 
                      List<FSTWindow> windows, FSTDisplayCache displayCache, Double piMaxValue,
                      boolean hasFST, boolean hasPI) {
        render(g, ctx, genome, windows, displayCache, piMaxValue, hasFST, hasPI, false, null);
    }
    
    public void render(Graphics2D g, PlotContext ctx, GenomeContext genome, 
                      List<FSTWindow> windows, FSTDisplayCache displayCache, Double piMaxValue,
                      boolean hasFST, boolean hasPI, boolean hasTajimaD, Double tajimaDMaxValue) {
        render(g, ctx, genome, windows, displayCache, piMaxValue, hasFST, hasPI, hasTajimaD, tajimaDMaxValue, false, null);
    }
    
    public void render(Graphics2D g, PlotContext ctx, GenomeContext genome, 
                      List<FSTWindow> windows, FSTDisplayCache displayCache, Double piMaxValue,
                      boolean hasFST, boolean hasPI, boolean hasTajimaD, Double tajimaDMaxValue,
                      boolean hasLogistic, Double logisticMaxValue) {
        render(g, ctx, genome, windows, displayCache, piMaxValue, hasFST, hasPI, hasTajimaD,
               tajimaDMaxValue, hasLogistic, logisticMaxValue, 1.0);
    }
    
    /**
     * 完整版：额外带 FST 的 Y 轴上限（折叠时按数据最大值收紧，默认 1.0）
     */
    public void render(Graphics2D g, PlotContext ctx, GenomeContext genome, 
                      List<FSTWindow> windows, FSTDisplayCache displayCache, Double piMaxValue,
                      boolean hasFST, boolean hasPI, boolean hasTajimaD, Double tajimaDMaxValue,
                      boolean hasLogistic, Double logisticMaxValue, Double fstAxisMax) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int plotX = ctx.plotX;
        int plotY = ctx.plotY;
        int plotW = ctx.plotWidth;
        int plotH = ctx.plotHeight; // 固定为230
        
        // 绘制坐标轴
        g.setColor(simplegenomehub.population.fst.FstUiStyle.AXIS_LINE);
        g.setStroke(FSTChartStyle.STROKE_AXIS);
        
        // 根据数据类型决定X轴和Y轴的位置
        if (hasTajimaD) {
            // TajimaD：X轴在y坐标0点处（中间），Y轴在左侧
            int zeroY = plotY + plotH / 2;
            // X轴在中间
            g.drawLine(plotX, zeroY, plotX + plotW, zeroY);
            // Y轴
            g.drawLine(plotX, plotY, plotX, plotY + plotH);
            // 绘制TajimaD坐标轴
            if (tajimaDMaxValue != null && tajimaDMaxValue > 0) {
                renderTajimaDAxis(g, ctx, tajimaDMaxValue);
            }
        } else if (hasLogistic) {
            // Logistic：X轴在底部，Y轴在左侧，绘制-log10(P值)坐标
            // X轴
            g.drawLine(plotX, plotY + plotH, plotX + plotW, plotY + plotH);
            // Y轴
            g.drawLine(plotX, plotY, plotX, plotY + plotH);
            // 绘制Logistic坐标轴
            if (logisticMaxValue != null && logisticMaxValue > 0) {
                renderLogisticAxis(g, ctx, logisticMaxValue);
            }
        } else {
            // FST/PI：X轴在底部，Y轴在左侧
            // X轴
            g.drawLine(plotX, plotY + plotH, plotX + plotW, plotY + plotH);
            // Y轴（只在有数据时绘制）
            if (hasFST || hasPI) {
                g.drawLine(plotX, plotY, plotX, plotY + plotH);
            }
            
            // 根据数据类型绘制左侧Y轴刻度
            if (hasFST && !hasPI) {
                // 只有FST数据：绘制FST的0到1纵坐标
                renderFstAxis(g, ctx, fstAxisMax);
            } else if (hasPI && !hasFST) {
                // 只有PI数据：绘制PI的最大值纵坐标
                if (piMaxValue != null && piMaxValue > 0) {
                    renderPiAxisOnLeft(g, ctx, piMaxValue);
                }
            } else if (hasFST && hasPI) {
                // 同时有FST和PI：左边绘制FST的0到1坐标，右边绘制PI的最大值坐标
                renderFstAxis(g, ctx, fstAxisMax);
                if (piMaxValue != null && piMaxValue > 0) {
                    renderPiAxis(g, ctx, piMaxValue);
                }
            }
        }
        
        // 染色体分隔线（始终绘制所有染色体的分隔线）
        if (windows != null && !windows.isEmpty()) {
            String selectedChrom = genome.getSelectedChromosome();
            if (selectedChrom != null) {
                // 如果选中了特定染色体，只绘制该染色体内的分隔（如果有多个区域）
                displayCache.ensure(selectedChrom);
                int n = displayCache.getDisplayCount(selectedChrom);
                if (n > 0) {
                    double scale = ctx.getScalePerIndex(n);
                    String prevChrom = null;
                    g.setColor(simplegenomehub.population.fst.FstUiStyle.GRID);
                    g.setStroke(FSTChartStyle.STROKE_THIN);
                    
                    for (int di = 0; di < n; di++) {
                        int oi = displayCache.displayIndexToOriginalIndex(selectedChrom, di);
                        if (oi >= 0 && oi < windows.size()) {
                            String curChrom = windows.get(oi).getChrom();
                            if (prevChrom != null && !prevChrom.equals(curChrom)) {
                                int x = plotX + (int) (di * scale);
                                g.drawLine(x, plotY, x, plotY + plotH);
                            }
                            prevChrom = curChrom;
                        }
                    }
                }
            } else {
                // 如果没有选中特定染色体，绘制所有染色体之间的分隔线
                List<FSTChromosomeRange.ChromosomeRange> chromRanges = genome.getChromRanges();
                if (chromRanges != null && !chromRanges.isEmpty()) {
                    g.setColor(simplegenomehub.population.fst.FstUiStyle.GRID);
                    g.setStroke(FSTChartStyle.STROKE_THIN);
                    
                    // 计算总显示数量
                    int totalDisplayCount = 0;
                    for (FSTChromosomeRange.ChromosomeRange range : chromRanges) {
                        totalDisplayCount += range.endIdx - range.startIdx + 1;
                    }
                    
                    if (totalDisplayCount > 0) {
                        double scale = ctx.getScalePerIndex(totalDisplayCount);
                        int currentDisplayIdx = 0;
                        
                        for (FSTChromosomeRange.ChromosomeRange range : chromRanges) {
                            // 在每个染色体开始处绘制分隔线（除了第一个）
                            if (currentDisplayIdx > 0) {
                                int x = plotX + (int) (currentDisplayIdx * scale);
                                g.drawLine(x, plotY, x, plotY + plotH);
                            }
                            currentDisplayIdx += range.endIdx - range.startIdx + 1;
                        }
                    }
                }
            }
        }
        
        // 位置线
        PositionLine posLine = genome.getPositionLine();
        if (posLine != null) {
            g.setColor(Color.RED);
            g.setStroke(FSTChartStyle.STROKE_POSITION_LINE);
            
            double x1 = calculatePositionX(posLine.chromosome, posLine.position1, 
                                          windows, genome, displayCache, ctx);
            if (x1 >= 0) {
                int x = plotX + (int) x1;
                if (x >= plotX && x <= plotX + plotW) {
                    g.drawLine(x, plotY, x, plotY + plotH);
                }
            }
            
            if (posLine.position2 > 0) {
                double x2 = calculatePositionX(posLine.chromosome, posLine.position2,
                                              windows, genome, displayCache, ctx);
                if (x2 >= 0) {
                    int x = plotX + (int) x2;
                    if (x >= plotX && x <= plotX + plotW) {
                        g.drawLine(x, plotY, x, plotY + plotH);
                    }
                }
            }
        }
        
    }
    
    // 绘制左侧FST坐标轴（0~axisMax；未折叠时 axisMax=1.0，维持原来的 0.0~1.0）
    private void renderFstAxis(Graphics2D g, PlotContext ctx, Double fstAxisMax) {
        int plotX = ctx.plotX;
        int plotY = ctx.plotY;
        int plotH = ctx.plotHeight;
        
        double axisMax = (fstAxisMax != null && fstAxisMax > 0) ? fstAxisMax : 1.0;
        boolean defaultAxis = Math.abs(axisMax - 1.0) < 1e-9;
        double step = defaultAxis ? 0.1 : niceStep(axisMax);
        
        g.setFont(FSTChartStyle.axisFont());
        g.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_PRIMARY);
        FontMetrics fm = g.getFontMetrics();
        
        int ticks = (int) Math.round(axisMax / step);
        for (int i = 0; i <= ticks; i++) {
            double v = i * step;
            if (v > axisMax + 1e-9) break;
            int y = plotY + (int) (plotH * (1.0 - v / axisMax));
            // 绘制刻度线（横线）
            g.setStroke(FSTChartStyle.STROKE_THIN);
            g.drawLine(plotX - 5, y, plotX, y);
            g.setStroke(FSTChartStyle.STROKE_AXIS);
            // 绘制刻度值（标签垂直居中对齐刻度线）
            String label = formatTick(v, step);
            int labelWidth = fm.stringWidth(label);
            int labelX = plotX - labelWidth - 10;
            int labelY = y + fm.getAscent() / 2; // 基线位置，使标签垂直居中
            g.drawString(label, labelX, labelY);
        }
    }
    
    private static String formatTick(double v, double step) {
        int decimals = Math.max(0, (int) Math.ceil(-Math.log10(step)));
        return String.format("%." + decimals + "f", v);
    }
    
    /** 刻度步长：让 0~axisMax 之间有 3~10 个刻度 */
    private static double niceStep(double axisMax) {
        double[] steps = {0.001, 0.002, 0.005, 0.01, 0.02, 0.05, 0.1, 0.2, 0.25, 0.5,
                          1.0, 2.0, 2.5, 5.0, 10.0, 20.0, 25.0, 50.0, 100.0, 200.0, 500.0};
        for (int i = steps.length - 1; i >= 0; i--) {
            double step = steps[i];
            double ticks = axisMax / step;
            if (ticks >= 3 - 1e-9 && ticks <= 10 + 1e-9) {
                return step;
            }
        }
        return axisMax / 5.0;
    }
    
    // 绘制左侧PI坐标轴（当只有PI数据时使用）
    private void renderPiAxisOnLeft(Graphics2D g, PlotContext ctx, double piMaxValue) {
        int plotX = ctx.plotX;
        int plotY = ctx.plotY;
        int plotH = ctx.plotHeight;
        
        g.setFont(FSTChartStyle.axisFont());
        g.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_PRIMARY);
        FontMetrics fm = g.getFontMetrics();
        
        for (int i = 0; i <= 10; i++) {
            double v = i / 10.0;  // 0到1的比例
            double piValue = v * piMaxValue;  // 对应的PI值
            int y = plotY + (int) (plotH * (1.0 - v));
            
            // 绘制刻度线（横线）
            g.setStroke(FSTChartStyle.STROKE_THIN);
            g.drawLine(plotX - 5, y, plotX, y);
            g.setStroke(FSTChartStyle.STROKE_AXIS);
            
            // 绘制刻度值（标签垂直居中对齐刻度线）
            String label = formatPiValue(piValue);
            int labelWidth = fm.stringWidth(label);
            int labelX = plotX - labelWidth - 10;
            int labelY = y + fm.getAscent() / 2; // 基线位置，使标签垂直居中
            g.drawString(label, labelX, labelY);
        }
    }
    
    // 绘制右侧PI坐标轴（当同时有FST和PI数据时使用）
    private void renderPiAxis(Graphics2D g, PlotContext ctx, double piMaxValue) {
        int plotX = ctx.plotX;
        int plotY = ctx.plotY;
        int plotW = ctx.plotWidth;
        int plotH = ctx.plotHeight;
        
        // 右侧Y轴位置
        int rightAxisX = plotX + plotW;
        
        // 绘制右侧Y轴
        g.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_PRIMARY);
        g.setStroke(FSTChartStyle.STROKE_AXIS);
        g.drawLine(rightAxisX, plotY, rightAxisX, plotY + plotH);
        
        // 绘制10个刻度
        g.setFont(FSTChartStyle.axisFont());
        g.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_PRIMARY);
        FontMetrics fm = g.getFontMetrics();
        
        for (int i = 0; i <= 10; i++) {
            double v = i / 10.0;  // 0到1的比例
            double piValue = v * piMaxValue;  // 对应的PI值
            int y = plotY + (int) (plotH * (1.0 - v));
            
            // 绘制刻度线（横线）
            g.setStroke(FSTChartStyle.STROKE_THIN);
            g.drawLine(rightAxisX, y, rightAxisX + 5, y);
            g.setStroke(FSTChartStyle.STROKE_AXIS);
            
            // 绘制刻度值（标签垂直居中对齐刻度线）
            String label = formatPiValue(piValue);
            int labelWidth = fm.stringWidth(label);
            int labelX = rightAxisX + 10;
            int labelY = y + fm.getAscent() / 2; // 基线位置，使标签垂直居中
            g.drawString(label, labelX, labelY);
        }
    }
    
    // 绘制TajimaD坐标轴（y轴0点在中间，正负对称）
    private void renderTajimaDAxis(Graphics2D g, PlotContext ctx, double tajimaDMaxValue) {
        int plotX = ctx.plotX;
        int plotY = ctx.plotY;
        int plotH = ctx.plotHeight;
        int zeroY = plotY + plotH / 2;  // y轴0点位置（中间）
        
        g.setFont(FSTChartStyle.axisFont());
        g.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_PRIMARY);
        FontMetrics fm = g.getFontMetrics();
        
        // 绘制10个刻度（从-1到1，包括0）
        for (int i = 0; i <= 10; i++) {
            double v = (i - 5) / 5.0;  // -1到1的比例
            double tajimaDValue = v * tajimaDMaxValue;  // 对应的TajimaD值
            int y = zeroY - (int) (plotH / 2 * v);  // 从中间开始，向上为正，向下为负
            
            // 绘制刻度线（横线）
            g.setStroke(FSTChartStyle.STROKE_THIN);
            g.drawLine(plotX - 5, y, plotX, y);
            g.setStroke(FSTChartStyle.STROKE_AXIS);
            
            // 绘制刻度值（标签垂直居中对齐刻度线）
            String label = formatTajimaDValue(tajimaDValue);
            int labelWidth = fm.stringWidth(label);
            int labelX = plotX - labelWidth - 10;
            int labelY = y + fm.getAscent() / 2; // 基线位置，使标签垂直居中
            g.drawString(label, labelX, labelY);
        }
    }
    
    // 格式化TajimaD值显示
    private String formatTajimaDValue(double value) {
        if (Math.abs(value) >= 100) {
            return String.format("%.0f", value);
        } else if (Math.abs(value) >= 10) {
            return String.format("%.1f", value);
        } else if (Math.abs(value) >= 1) {
            return String.format("%.2f", value);
        } else {
            return String.format("%.3f", value);
        }
    }
    
    // 绘制Logistic坐标轴（-log10(P值)）
    private void renderLogisticAxis(Graphics2D g, PlotContext ctx, double logisticMaxValue) {
        int plotX = ctx.plotX;
        int plotY = ctx.plotY;
        int plotH = ctx.plotHeight;
        
        g.setFont(FSTChartStyle.axisFont());
        g.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_PRIMARY);
        FontMetrics fm = g.getFontMetrics();
        
        // 绘制10个刻度（从0到logisticMaxValue）
        for (int i = 0; i <= 10; i++) {
            double v = i / 10.0;  // 0到1的比例
            double negLog10P = v * logisticMaxValue;  // 对应的-log10(P值)
            int y = plotY + (int) (plotH * (1.0 - v));
            
            // 绘制刻度线（横线）
            g.setStroke(FSTChartStyle.STROKE_THIN);
            g.drawLine(plotX - 5, y, plotX, y);
            g.setStroke(FSTChartStyle.STROKE_AXIS);
            
            // 绘制刻度值（标签垂直居中对齐刻度线）
            String label = formatLogisticValue(negLog10P);
            int labelWidth = fm.stringWidth(label);
            int labelX = plotX - labelWidth - 10;
            int labelY = y + fm.getAscent() / 2; // 基线位置，使标签垂直居中
            g.drawString(label, labelX, labelY);
        }
    }
    
    // 格式化Logistic值显示（-log10(P值)）
    private String formatLogisticValue(double value) {
        if (value >= 100) {
            return String.format("%.0f", value);
        } else if (value >= 10) {
            return String.format("%.1f", value);
        } else if (value >= 1) {
            return String.format("%.2f", value);
        } else {
            return String.format("%.3f", value);
        }
    }
    
    // 格式化PI值显示
    private String formatPiValue(double value) {
        if (value >= 1000) {
            return String.format("%.0f", value);
        } else if (value >= 100) {
            return String.format("%.1f", value);
        } else if (value >= 10) {
            return String.format("%.2f", value);
        } else if (value >= 1) {
            return String.format("%.3f", value);
        } else {
            return String.format("%.4f", value);
        }
    }
    
    private double calculatePositionX(String chromosome, int position,
                                     List<FSTWindow> windows, GenomeContext genome,
                                     FSTDisplayCache displayCache, PlotContext ctx) {
        return FSTPositionMapper.calculateXFromPosition(
            windows, genome.getSelectedChromosome(), genome.getChromRanges(), displayCache,
            ctx.plotX + ctx.plotWidth, ctx.plotX + ctx.plotWidth, chromosome, position
        );
    }
}
