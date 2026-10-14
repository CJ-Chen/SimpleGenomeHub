package simplegenomehub.population.fst.chart.model;

/**
 * 绘图项：表示某个数据集在轨道上的绘制配置
 */
public class PlotItem {
    private final String dataSetId;
    private PlotStyle style;
    private boolean visible;
    private boolean topEnabled; // 是否显示top位点虚线
    private double topPercent; // top百分比阈值
    
    public PlotItem(String dataSetId, PlotStyle style) {
        this.dataSetId = dataSetId;
        this.style = style;
        this.visible = true;
        this.topEnabled = false;
        this.topPercent = 0.01; // 默认0.01
    }
    
    public String getDataSetId() { return dataSetId; }
    public PlotStyle getStyle() { return style; }
    public void setStyle(PlotStyle style) { this.style = style; }
    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public boolean isTopEnabled() { return topEnabled; }
    public void setTopEnabled(boolean topEnabled) { this.topEnabled = topEnabled; }
    public double getTopPercent() { return topPercent; }
    public void setTopPercent(double topPercent) { this.topPercent = topPercent; }
    
    public enum PlotStyle {
        POINT, LINE
    }
}
