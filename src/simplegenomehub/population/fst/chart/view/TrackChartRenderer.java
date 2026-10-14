package simplegenomehub.population.fst.chart.view;

import simplegenomehub.population.fst.chart.FSTDisplayCache;
import simplegenomehub.population.fst.chart.FSTTrackChartPanel;
import simplegenomehub.population.fst.chart.FSTWindow;
import simplegenomehub.population.fst.chart.model.DataSet;
import simplegenomehub.population.fst.chart.model.GenomeContext;
import simplegenomehub.population.fst.chart.model.PlotItem;
import simplegenomehub.population.fst.chart.model.TrackModel;
import simplegenomehub.population.fst.chart.render.AxisRenderer;
import simplegenomehub.population.fst.chart.render.FstRenderer;
import simplegenomehub.population.fst.chart.render.PiRenderer;
import simplegenomehub.population.fst.chart.render.TajimaDRenderer;
import simplegenomehub.population.fst.chart.render.LogisticRenderer;
import simplegenomehub.population.fst.chart.render.PlotContext;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 轨道图表绘制器：负责所有绘制相关的操作
public class TrackChartRenderer {
    private final FSTTrackChartPanel mainPanel;
    private final AxisRenderer axisRenderer;
    private final Map<String, FstRenderer> rendererMap;
    private final Map<String, PiRenderer> piRendererMap;
    private final Map<String, TajimaDRenderer> tajimaDRendererMap;
    private final Map<String, LogisticRenderer> logisticRendererMap;
    
    public TrackChartRenderer(FSTTrackChartPanel mainPanel, AxisRenderer axisRenderer, 
                             Map<String, FstRenderer> rendererMap) {
        this.mainPanel = mainPanel;
        this.axisRenderer = axisRenderer;
        this.rendererMap = rendererMap;
        this.piRendererMap = new HashMap<>();
        this.tajimaDRendererMap = new HashMap<>();
        this.logisticRendererMap = new HashMap<>();
    }
    
    public TrackChartRenderer(FSTTrackChartPanel mainPanel, AxisRenderer axisRenderer, 
                             Map<String, FstRenderer> rendererMap,
                             Map<String, PiRenderer> piRendererMap) {
        this.mainPanel = mainPanel;
        this.axisRenderer = axisRenderer;
        this.rendererMap = rendererMap;
        this.piRendererMap = piRendererMap != null ? piRendererMap : new HashMap<>();
        this.tajimaDRendererMap = new HashMap<>();
        this.logisticRendererMap = new HashMap<>();
    }
    
    public TrackChartRenderer(FSTTrackChartPanel mainPanel, AxisRenderer axisRenderer, 
                             Map<String, FstRenderer> rendererMap,
                             Map<String, PiRenderer> piRendererMap,
                             Map<String, TajimaDRenderer> tajimaDRendererMap) {
        this(mainPanel, axisRenderer, rendererMap, piRendererMap, tajimaDRendererMap, new HashMap<>());
    }
    
    public TrackChartRenderer(FSTTrackChartPanel mainPanel, AxisRenderer axisRenderer, 
                             Map<String, FstRenderer> rendererMap,
                             Map<String, PiRenderer> piRendererMap,
                             Map<String, TajimaDRenderer> tajimaDRendererMap,
                             Map<String, LogisticRenderer> logisticRendererMap) {
        this.mainPanel = mainPanel;
        this.axisRenderer = axisRenderer;
        this.rendererMap = rendererMap;
        this.piRendererMap = piRendererMap != null ? piRendererMap : new HashMap<>();
        this.tajimaDRendererMap = tajimaDRendererMap != null ? tajimaDRendererMap : new HashMap<>();
        this.logisticRendererMap = logisticRendererMap != null ? logisticRendererMap : new HashMap<>();
    }
    
    // 绘制拖拽的幽灵卡牌
    public void drawGhostCard(Graphics2D g2, DataSet dataSet, Point location) {
        if (g2 == null || dataSet == null || location == null) return;
        
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            
            int cardWidth = 120;
            int cardHeight = 150;
            int x = location.x;
            int y = location.y;
            
            // 绘制卡牌背景（使用CardView2，半透明）
            Graphics2D bgG2 = (Graphics2D) g2.create();
            bgG2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
            CardView2.drawCardBackground(bgG2, x, y, cardWidth, cardHeight, true);
            bgG2.dispose();
            
            // 绘制数字标识（带圆形背景）- 在右上角（使用CardView2）
            CardView2.drawCardNumber(g2, dataSet, x, y, cardWidth, true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // 绘制从投放框拖拽的卡牌（小卡牌样式）
    public void drawDropZoneCard(Graphics2D g2, DataSet dataSet, PlotItem item, Point location) {
        if (g2 == null || dataSet == null || item == null || location == null) return;
        
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            
            int cardWidth = 120;
            int cardHeight = 70;
            int x = location.x;
            int y = location.y;
            
            // 绘制卡牌背景（使用CardView2，半透明）
            Graphics2D bgG2 = (Graphics2D) g2.create();
            bgG2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
            CardView2.drawCardBackground(bgG2, x, y, cardWidth, cardHeight, true);
            bgG2.dispose();
            
            // 绘制数字标识（带圆形背景）- 在右上角（使用CardView2）
            CardView2.drawCardNumber(g2, dataSet, x, y, cardWidth, true);
            
            // 定义buttonSize变量（用于切换按钮）
            int buttonSize = 16;
            
            // 绘制Top按钮和文本框（仅对FST类型显示）
            dataSet = mainPanel.findDataSet(item.getDataSetId());
            if (dataSet != null && dataSet.getType() == DataSet.DataType.FST) {
                int switchButtonY = y + cardHeight - buttonSize - 5;
                int topButtonY = switchButtonY;
                int topButtonSize = 14;
                int topButtonX = x + 5;
                
                // Top按钮背景
                if (item.isTopEnabled()) {
                    g2.setColor(simplegenomehub.population.fst.FstUiStyle.SELECTION);
                } else {
                    g2.setColor(simplegenomehub.population.fst.FstUiStyle.PANEL_BACKGROUND);
                }
                g2.fillRect(topButtonX, topButtonY, topButtonSize, topButtonSize);
                g2.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_SECONDARY);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRect(topButtonX, topButtonY, topButtonSize, topButtonSize);
                
                // 绘制"T"文字
                g2.setColor(item.isTopEnabled() ? simplegenomehub.population.fst.FstUiStyle.TOP_MARKER : simplegenomehub.population.fst.FstUiStyle.TEXT_PRIMARY);
                g2.setFont(simplegenomehub.population.fst.FstUiStyle.arial(9, Font.BOLD));
                FontMetrics topFm = g2.getFontMetrics();
                int tX = topButtonX + (topButtonSize - topFm.stringWidth("T")) / 2;
                int tY = topButtonY + topButtonSize / 2 + topFm.getAscent() / 2 - 1;
                g2.drawString("T", tX, tY);
                
                // 绘制文本框背景
                int textFieldX = topButtonX + topButtonSize + 5;
                int textFieldY = topButtonY;
                int textFieldWidth = 55;
                int textFieldHeight = topButtonSize;
                
                g2.setColor(new Color(255, 255, 255, 200));
                g2.fillRect(textFieldX, textFieldY, textFieldWidth, textFieldHeight);
                g2.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_SECONDARY);
                g2.drawRect(textFieldX, textFieldY, textFieldWidth, textFieldHeight);
                
                // 绘制文本框内容
                g2.setColor(new Color(0, 0, 0, 200));
                g2.setFont(simplegenomehub.population.fst.FstUiStyle.arial(9, Font.PLAIN));
                FontMetrics textFieldFm = g2.getFontMetrics();
                String topPercentText = formatTopPercent(item.getTopPercent());
                int textFieldTextX = textFieldX + 2;
                int textFieldTextY = textFieldY + textFieldHeight / 2 + textFieldFm.getAscent() / 2 - 1;
                g2.drawString(topPercentText, textFieldTextX, textFieldTextY);
            }
            
            // 对于PI和TajimaD类型，绘制颜色选择按钮
            if (dataSet != null && (dataSet.getType() == DataSet.DataType.PI || dataSet.getType() == DataSet.DataType.TAJIMAD)) {
                int colorButtonY = y + cardHeight - buttonSize - 5;
                int colorButtonSize = 14;
                int colorButtonX = x + 5;
                
                Color cardColor;
                if (dataSet.getType() == DataSet.DataType.PI) {
                    PiRenderer piRenderer = piRendererMap.get(dataSet.getId());
                    cardColor = piRenderer != null ? piRenderer.getColor() : simplegenomehub.population.fst.FstUiStyle.SERIES_PI;
                } else {
                    TajimaDRenderer tajimaDRenderer = tajimaDRendererMap.get(dataSet.getId());
                    cardColor = tajimaDRenderer != null ? tajimaDRenderer.getColor() : simplegenomehub.population.fst.FstUiStyle.SERIES_TAJIMA_D;
                }
                
                g2.setColor(cardColor);
                g2.fillRect(colorButtonX, colorButtonY, colorButtonSize, colorButtonSize);
                g2.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_SECONDARY);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRect(colorButtonX, colorButtonY, colorButtonSize, colorButtonSize);
            }
            
            // 绘制删除按钮（右上角）
            int deleteButtonSize = 14;
            int deleteButtonX = x + cardWidth - deleteButtonSize - 3;
            int deleteButtonY = y + 3;
            
            g2.setColor(new Color(255, 200, 200, 200));
            g2.fillOval(deleteButtonX, deleteButtonY, deleteButtonSize, deleteButtonSize);
            g2.setColor(new Color(200, 100, 100, 200));
            g2.setStroke(new BasicStroke(1f));
            g2.drawOval(deleteButtonX, deleteButtonY, deleteButtonSize, deleteButtonSize);
            
            // 绘制X符号
            g2.setColor(new Color(255, 0, 0, 200));
            g2.setStroke(new BasicStroke(1.5f));
            int delCenterX = deleteButtonX + deleteButtonSize / 2;
            int delCenterY = deleteButtonY + deleteButtonSize / 2;
            int delOffset = 3;
            g2.drawLine(delCenterX - delOffset, delCenterY - delOffset, 
                      delCenterX + delOffset, delCenterY + delOffset);
            g2.drawLine(delCenterX - delOffset, delCenterY + delOffset, 
                      delCenterX + delOffset, delCenterY - delOffset);
            
            // 绘制切换按钮（右下角）
            int switchButtonX = x + cardWidth - buttonSize - 5;
            int switchButtonY = y + cardHeight - buttonSize - 5;
            
            g2.setColor(simplegenomehub.population.fst.FstUiStyle.PANEL_BACKGROUND);
            g2.fillOval(switchButtonX, switchButtonY, buttonSize, buttonSize);
            g2.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_SECONDARY);
            g2.setStroke(new BasicStroke(1f));
            g2.drawOval(switchButtonX, switchButtonY, buttonSize, buttonSize);
            
            // 绘制点/线图标
            g2.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_PRIMARY);
            int centerX = switchButtonX + buttonSize / 2;
            int centerY = switchButtonY + buttonSize / 2;
            if (item.getStyle() == PlotItem.PlotStyle.POINT) {
                g2.fillOval(centerX - 2, centerY - 2, 4, 4);
            } else {
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawLine(centerX - 4, centerY, centerX + 4, centerY);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // 绘制拖拽中的轨道（半透明投影）
    public void drawDraggingTrack(Graphics2D g2, TrackView trackView, Point location, Point offset) {
        if (g2 == null || trackView == null || location == null || offset == null) return;
        
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            
            int w = trackView.getWidth();
            int h = trackView.getHeight();
            int x = location.x - offset.x;
            int y = location.y - offset.y;
            
            // 绘制阴影
            int shadowOffset = 3;
            g2.setColor(new Color(0, 0, 0, 30));
            RoundRectangle2D shadowRect = new RoundRectangle2D.Float(
                x + shadowOffset, y + shadowOffset, w - 1, h - 1, 8, 8
            );
            g2.fill(shadowRect);
            
            // 绘制标签纸背景
            RoundRectangle2D paperRect = new RoundRectangle2D.Float(x, y, w - 1, h - 1, 8, 8);
            g2.setColor(simplegenomehub.population.fst.FstUiStyle.PLOT_BACKGROUND);
            g2.fill(paperRect);
            
            // 绘制边框
            g2.setColor(simplegenomehub.population.fst.FstUiStyle.CARD_BORDER);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(paperRect);
            
            // 绘制左侧小把手
            int handleWidth = 20;
            int handleHeight = 40;
            Rectangle handleBounds = new Rectangle(x, y, handleWidth, handleHeight);
            g2.setColor(simplegenomehub.population.fst.FstUiStyle.GRID);
            g2.fillRect(handleBounds.x, handleBounds.y, handleBounds.width, handleBounds.height);
            g2.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_SECONDARY);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRect(handleBounds.x, handleBounds.y, handleBounds.width, handleBounds.height);
            
            // 绘制把手上的横线
            g2.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_PRIMARY);
            int centerX = handleBounds.x + handleBounds.width / 2;
            for (int i = 0; i < 3; i++) {
                int lineY = handleBounds.y + handleBounds.height / 4 + i * handleBounds.height / 4;
                g2.drawLine(centerX - 4, lineY, centerX + 4, lineY);
            }
            
            // 绘制删除按钮
            int deleteButtonSize = 16;
            int deleteX = x + handleWidth / 2 - deleteButtonSize / 2;
            int deleteY = y + handleHeight + 2;
            Rectangle deleteBounds = new Rectangle(deleteX, deleteY, deleteButtonSize, deleteButtonSize);
            g2.setColor(new Color(255, 150, 150, 200));
            g2.fillOval(deleteBounds.x, deleteBounds.y, deleteBounds.width, deleteBounds.height);
            g2.setColor(new Color(255, 0, 0, 200));
            g2.setStroke(new BasicStroke(2f));
            int delCenterX = deleteBounds.x + deleteBounds.width / 2;
            int delCenterY = deleteBounds.y + deleteBounds.height / 2;
            g2.drawLine(delCenterX - 4, delCenterY, delCenterX + 4, delCenterY);
            
            // 绘制右侧投放框
            int dropZoneWidth = 120;
            Rectangle dropZoneBounds = new Rectangle(x + w - dropZoneWidth, y, dropZoneWidth, h);
            g2.setColor(simplegenomehub.population.fst.FstUiStyle.PLOT_BACKGROUND);
            g2.fillRect(dropZoneBounds.x, dropZoneBounds.y, dropZoneBounds.width, dropZoneBounds.height);
            
            // 绘制投放框虚线边框
            g2.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_SECONDARY);
            Stroke dashedStroke = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10.0f, new float[]{5.0f}, 0.0f);
            g2.setStroke(dashedStroke);
            g2.drawRect(dropZoneBounds.x, dropZoneBounds.y, dropZoneBounds.width, dropZoneBounds.height);
            
            // 显示投放框提示文字
            TrackModel trackModel = trackView.getTrackModel();
            if (trackModel.getItems().isEmpty()) {
                g2.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_SECONDARY);
                g2.setFont(simplegenomehub.population.fst.FstUiStyle.arial(10, Font.ITALIC));
                FontMetrics fm = g2.getFontMetrics();
                String text = "Drop cards here";
                int textX = dropZoneBounds.x + (dropZoneBounds.width - fm.stringWidth(text)) / 2;
                int textY = dropZoneBounds.y + dropZoneBounds.height / 2;
                g2.drawString(text, textX, textY);
            } else {
                g2.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_PRIMARY);
                g2.setFont(simplegenomehub.population.fst.FstUiStyle.arial(9, Font.PLAIN));
                FontMetrics fm = g2.getFontMetrics();
                String text = trackModel.getItems().size() + " cards";
                int textX = dropZoneBounds.x + (dropZoneBounds.width - fm.stringWidth(text)) / 2;
                int textY = dropZoneBounds.y + dropZoneBounds.height / 2;
                g2.drawString(text, textX, textY);
            }
            
            // 绘制坐标系和数据（简化版）
            Rectangle plotBounds = trackView.getPlotAreaBounds();
            if (plotBounds.width > 0 && plotBounds.height > 0) {
                int plotX = x + plotBounds.x;
                int plotY = y + plotBounds.y;
                g2.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_PRIMARY);
                g2.setStroke(new BasicStroke(1f));
                g2.drawLine(plotX, plotY + plotBounds.height, plotX + plotBounds.width, plotY + plotBounds.height);
                g2.drawLine(plotX, plotY, plotX, plotY + plotBounds.height);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // 绘制坐标系（屏幕轨道视图）
    public void paintAxis(Graphics2D g, TrackView trackView, Rectangle plotBounds, 
                         GenomeContext genomeContext) {
        paintAxis(g, trackView.getTrackModel(), plotBounds, genomeContext);
    }
    
    // 绘制坐标系（按轨道模型 + 绘图区域；导出纯图时也走这里）
    public void paintAxis(Graphics2D g, TrackModel trackModel, Rectangle plotBounds, 
                         GenomeContext genomeContext) {
        if (plotBounds.width <= 0 || plotBounds.height <= 0) return;
        
        PlotContext ctx = PlotContext.of(plotBounds);
        
        FSTDisplayCache displayCache = new FSTDisplayCache(genomeContext.getReferenceWindows());
        
        boolean collapsed = trackModel != null && trackModel.isCollapsed();
        
        // 检测当前轨道中的数据类型
        boolean hasFST = hasDataType(trackModel, DataSet.DataType.FST);
        boolean hasPI = hasDataType(trackModel, DataSet.DataType.PI);
        boolean hasTajimaD = hasDataType(trackModel, DataSet.DataType.TAJIMAD);
        boolean hasLogistic = hasDataType(trackModel, DataSet.DataType.LOGISTIC);
        
        // 轴上限：折叠时按该轨道数据最大值收紧要“比最大值大的刻度”
        Double fstAxisMax = hasFST ? axisMaxFST(trackModel, collapsed) : null;
        Double piMaxValue = hasPI ? axisMaxPI(trackModel, collapsed) : null;
        Double tajimaDMaxValue = hasTajimaD ? axisMaxTajimaD(trackModel, collapsed) : null;
        Double logisticMaxValue = hasLogistic ? axisMaxLogistic(trackModel, collapsed) : null;
        
        axisRenderer.render(g, ctx, genomeContext, genomeContext.getReferenceWindows(), displayCache, 
                            piMaxValue, hasFST, hasPI, hasTajimaD, tajimaDMaxValue, hasLogistic, logisticMaxValue,
                            fstAxisMax);
    }
    
    // 检测轨道中是否有指定类型的数据
    private boolean hasDataType(TrackModel trackModel, DataSet.DataType type) {
        if (trackModel == null) return false;
        
        for (PlotItem item : trackModel.getItems()) {
            if (!item.isVisible()) continue;
            
            DataSet dataSet = mainPanel.findDataSet(item.getDataSetId());
            if (dataSet == null) continue;
            if (dataSet.getType() == type) {
                return true;
            }
        }
        
        return false;
    }
    
    // 计算轨道内所有PI数据的最大值（原始值）
    private Double calculatePiMaxValue(TrackModel trackModel) {
        if (trackModel == null) return null;
        
        double maxValue = Double.NEGATIVE_INFINITY;
        boolean hasValue = false;
        
        // 收集所有可见的PI数据，找出最大值
        for (PlotItem item : trackModel.getItems()) {
            if (!item.isVisible()) continue;
            
            DataSet dataSet = mainPanel.findDataSet(item.getDataSetId());
            if (dataSet == null) continue;
            if (dataSet.getType() != DataSet.DataType.PI) continue;
            
            List<FSTWindow> windows = dataSet.getWindows();
            if (windows == null) continue;
            
            for (FSTWindow w : windows) {
                double piValue = w.getAvgFst();  // PI值存储在avgFst字段中
                if (Double.isFinite(piValue)) {
                    hasValue = true;
                    if (piValue > maxValue) {
                        maxValue = piValue;
                    }
                }
            }
        }
        
        if (!hasValue) return null;
        
        return maxValue;
    }
    
    // 计算轨道内所有TajimaD数据的最大绝对值（原始值）
    private Double calculateTajimaDMaxValue(TrackModel trackModel) {
        if (trackModel == null) return null;
        
        double maxAbsValue = 0.0;
        boolean hasValue = false;
        
        // 收集所有可见的TajimaD数据，找出最大绝对值
        for (PlotItem item : trackModel.getItems()) {
            if (!item.isVisible()) continue;
            
            DataSet dataSet = mainPanel.findDataSet(item.getDataSetId());
            if (dataSet == null) continue;
            if (dataSet.getType() != DataSet.DataType.TAJIMAD) continue;
            
            List<FSTWindow> windows = dataSet.getWindows();
            if (windows == null) continue;
            
            for (FSTWindow w : windows) {
                double tajimaDValue = w.getAvgFst();  // TajimaD值存储在avgFst字段中
                if (Double.isFinite(tajimaDValue)) {
                    hasValue = true;
                    double absValue = Math.abs(tajimaDValue);
                    if (absValue > maxAbsValue) {
                        maxAbsValue = absValue;
                    }
                }
            }
        }
        
        if (!hasValue) return null;
        
        return maxAbsValue;
    }
    
    // 绘制轨道上的数据（屏幕轨道视图）
    public void paintTrackData(Graphics2D g, TrackView trackView, GenomeContext genomeContext,
                              Integer hoveredIndex, Integer selectedIndex) {
        paintTrackData(g, trackView.getTrackModel(), trackView.getPlotAreaBounds(), genomeContext,
                       hoveredIndex, selectedIndex);
    }
    
    // 绘制轨道上的数据（按轨道模型 + 绘图区域；导出纯图时也走这里）
    public void paintTrackData(Graphics2D g, TrackModel trackModel, Rectangle plotBounds,
                              GenomeContext genomeContext, Integer hoveredIndex, Integer selectedIndex) {
        if (trackModel == null) return;
        if (plotBounds.width <= 0 || plotBounds.height <= 0) return;
        
        PlotContext ctx = PlotContext.of(plotBounds);
        
        boolean collapsed = trackModel.isCollapsed();
        
        // 轴上限（与坐标轴保持一致）
        Double fstAxisMax = axisMaxFST(trackModel, collapsed);
        Double piMaxValue = axisMaxPI(trackModel, collapsed);
        Double tajimaDMaxValue = axisMaxTajimaD(trackModel, collapsed);
        Double logisticMaxValue = axisMaxLogistic(trackModel, collapsed);
        
        for (PlotItem item : trackModel.getItems()) {
            if (!item.isVisible()) continue;
            
            DataSet dataSet = mainPanel.findDataSet(item.getDataSetId());
            if (dataSet == null) continue;
            
            FSTDisplayCache itemCache = new FSTDisplayCache(dataSet.getWindows());
            
            Map<String, Object> renderState = new HashMap<>();
            renderState.put("dataSet", dataSet);
            renderState.put("displayCache", itemCache);
            renderState.put("hoveredIndex", hoveredIndex);
            renderState.put("selectedIndex", selectedIndex);
            
            // 根据数据类型选择renderer
            if (dataSet.getType() == DataSet.DataType.FST) {
                FstRenderer renderer = rendererMap.get(dataSet.getId());
                if (renderer == null) continue;
                
                BitSet itemTopBits = null;
                if (item.isTopEnabled()) {
                    itemTopBits = calculateTopBitsForItem(dataSet.getWindows(), item.getTopPercent(), genomeContext);
                }
                renderState.put("topBits", itemTopBits);
                renderState.put("fstAxisMax", fstAxisMax);
                
                renderer.render(g, ctx, genomeContext, item, renderState);
            } else if (dataSet.getType() == DataSet.DataType.PI) {
                PiRenderer piRenderer = piRendererMap.get(dataSet.getId());
                if (piRenderer == null) continue;
                
                // 传递PI最大值给renderer
                if (piMaxValue != null) {
                    renderState.put("piMaxValue", piMaxValue);
                }
                
                piRenderer.render(g, ctx, genomeContext, item, renderState);
            } else if (dataSet.getType() == DataSet.DataType.TAJIMAD) {
                TajimaDRenderer tajimaDRenderer = tajimaDRendererMap.get(dataSet.getId());
                if (tajimaDRenderer == null) continue;
                
                // 传递TajimaD最大绝对值给renderer
                if (tajimaDMaxValue != null) {
                    renderState.put("tajimaDMaxValue", tajimaDMaxValue);
                }
                
                tajimaDRenderer.render(g, ctx, genomeContext, item, renderState);
            } else if (dataSet.getType() == DataSet.DataType.LOGISTIC) {
                LogisticRenderer logisticRenderer = logisticRendererMap.get(dataSet.getId());
                if (logisticRenderer == null) continue;
                
                // 传递Logistic最大值给renderer
                if (logisticMaxValue != null) {
                    renderState.put("logisticMaxValue", logisticMaxValue);
                }
                
                logisticRenderer.render(g, ctx, genomeContext, item, renderState);
            }
        }
    }
    
    // 计算轨道内所有Logistic数据的最大值（原始 -log10(P值)）
    private Double calculateLogisticMaxValue(TrackModel trackModel) {
        if (trackModel == null) return null;
        double maxValue = 0.0;
        boolean hasValue = false;
        
        for (PlotItem item : trackModel.getItems()) {
            if (!item.isVisible()) continue;
            
            DataSet dataSet = mainPanel.findDataSet(item.getDataSetId());
            if (dataSet == null || dataSet.getType() != DataSet.DataType.LOGISTIC) continue;
            
            List<FSTWindow> windows = dataSet.getWindows();
            if (windows == null) continue;
            
            for (FSTWindow w : windows) {
                double negLog10P = w.getAvgFst(); // -log10(P值)存储在avgFst字段中
                if (Double.isFinite(negLog10P) && negLog10P > maxValue) {
                    maxValue = negLog10P;
                    hasValue = true;
                }
            }
        }
        
        if (!hasValue) return null;
        return maxValue;
    }
    
    // ===== 轴上限：折叠时按该轨道数据最大值收紧要“比最大值大的刻度” =====

    /**
     * 轨道上某类数据的 Y 轴上限（与绘制和坐标轴用的是同一个值）。
     * 鼠标命中测试必须用它来换算像素位置，否则折叠 Y 轴 / 改变轨道高度后点位就对不上了。
     */
    public double axisMaxFor(TrackModel trackModel, DataSet.DataType type) {
        if (trackModel == null || type == null) return 1.0;
        boolean collapsed = trackModel.isCollapsed();
        Double max;
        switch (type) {
            case FST: max = axisMaxFST(trackModel, collapsed); break;
            case PI: max = axisMaxPI(trackModel, collapsed); break;
            case TAJIMAD: max = axisMaxTajimaD(trackModel, collapsed); break;
            case LOGISTIC: max = axisMaxLogistic(trackModel, collapsed); break;
            default: max = null;
        }
        return (max != null && Double.isFinite(max) && max > 0) ? max : 1.0;
    }

    private Double axisMaxFST(TrackModel trackModel, boolean collapsed) {
        if (!collapsed) return 1.0;
        double max = 0.0;
        boolean has = false;
        for (PlotItem item : trackModel.getItems()) {
            if (!item.isVisible()) continue;
            DataSet ds = mainPanel.findDataSet(item.getDataSetId());
            if (ds == null || ds.getType() != DataSet.DataType.FST) continue;
            List<FSTWindow> windows = ds.getWindows();
            if (windows == null) continue;
            for (FSTWindow w : windows) {
                double v = w.getAvgFst();
                if (Double.isFinite(v)) { has = true; if (v > max) max = v; }
            }
        }
        return has && max > 0 ? niceCeil(max) : 1.0;
    }
    
    private Double axisMaxPI(TrackModel trackModel, boolean collapsed) {
        Double raw = calculatePiMaxValue(trackModel);
        if (raw == null || raw <= 0) return null;
        return collapsed ? niceCeil(raw) : raw;
    }
    
    private Double axisMaxTajimaD(TrackModel trackModel, boolean collapsed) {
        Double raw = calculateTajimaDMaxValue(trackModel);
        if (raw == null || raw <= 0) return null;
        return collapsed ? niceCeil(raw) : raw;
    }
    
    private Double axisMaxLogistic(TrackModel trackModel, boolean collapsed) {
        Double raw = calculateLogisticMaxValue(trackModel);
        if (raw == null || raw <= 0) return null;
        if (collapsed) return niceCeil(raw);
        return Math.max(10.0, Math.ceil(raw));
    }
    
    /**
     * 把数值向上取到"整数刻度"：0.17→0.2、0.33→0.4（按量级选步长）。
     */
    static double niceCeil(double v) {
        if (!Double.isFinite(v) || v <= 0) return 1.0;
        double step;
        if (v >= 10) {
            step = Math.pow(10, Math.floor(Math.log10(v)));
        } else if (v >= 1) {
            step = 1.0;
        } else if (v >= 0.1) {
            step = 0.1;
        } else if (v >= 0.01) {
            step = 0.01;
        } else if (v >= 0.001) {
            step = 0.001;
        } else {
            step = Math.pow(10, Math.floor(Math.log10(v)));
        }
        double top = Math.ceil(v / step - 1e-9) * step;
        return roundTo(top, step);
    }
    
    /** 刻度步长：让 0~axisMax 之间有 3~10 个刻度且刻度值好看 */
    static double niceStep(double axisMax) {
        if (!Double.isFinite(axisMax) || axisMax <= 0) return 0.1;
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
    
    private static double roundTo(double value, double step) {
        if (step <= 0) return value;
        int decimals = Math.max(0, (int) Math.ceil(-Math.log10(step)));
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
    
    // 根据top百分比计算top bits
    public BitSet calculateTopBitsForItem(List<FSTWindow> windows, double topPercent, GenomeContext genomeContext) {
        if (windows == null || windows.isEmpty() || topPercent <= 0 || topPercent > 1.0) {
            return new BitSet();
        }
        
        // 第一步：使用全部染色体的数据计算top阈值（不论当前选中哪个染色体）
        List<Double> allFstValues = new ArrayList<>();
        for (FSTWindow w : windows) {
            double fst = w.getAvgFst();
            if (Double.isFinite(fst)) {
                allFstValues.add(fst);
            }
        }
        
        if (allFstValues.isEmpty()) return new BitSet();
        
        // 按FST值降序排序
        allFstValues.sort((a, b) -> Double.compare(b, a));
        
        // 计算top百分比对应的数量
        int topCount = Math.max(1, (int) Math.ceil(allFstValues.size() * topPercent));
        if (topCount > allFstValues.size()) topCount = allFstValues.size();
        
        // 获取阈值（第topCount个值）- 基于全部染色体的数据
        double threshold = allFstValues.get(topCount - 1);
        
        // 第二步：标记所有达到阈值的位点（包括所有染色体）
        BitSet bits = new BitSet(windows.size());
        for (int i = 0; i < windows.size(); i++) {
            FSTWindow w = windows.get(i);
            double fst = w.getAvgFst();
            if (Double.isFinite(fst) && fst >= threshold) {
                bits.set(i);
            }
        }
        return bits;
    }
    
    // FST值和索引的辅助类
    private static class FSTValueWithIndex {
        final double fst;
        final int index;
        
        FSTValueWithIndex(double fst, int index) {
            this.fst = fst;
            this.index = index;
        }
    }
    
    // 绘制投放框内的卡牌
    public void paintDropZoneCards(Graphics2D g, TrackView trackView, Rectangle dropZoneBounds,
                                   PlotItem draggingItem, TrackView draggingSourceTrack) {
        TrackModel trackModel = trackView.getTrackModel();
        List<PlotItem> items = trackModel.getItems();
        
        boolean isDraggingFromThisTrack = (draggingItem != null && draggingSourceTrack == trackView);
        
        int cardWidth = 120;
        int cardHeight = 70;
        int cardMargin = 5;
        int scrollOffset = trackView.getDropZoneScrollOffset();
        int x = dropZoneBounds.x + (dropZoneBounds.width - cardWidth) / 2;
        
        Graphics2D clippedG = (Graphics2D) g.create();
        clippedG.setClip(dropZoneBounds);
        
        // 绘制轨道分隔线（每个卡牌位置一个轨道，包括正在拖拽的卡牌位置）
        if (!items.isEmpty()) {
            g.setColor(simplegenomehub.population.fst.FstUiStyle.GRID);
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10.0f, new float[]{3.0f, 3.0f}, 0.0f));
            
            // 包括正在拖拽的卡牌位置，所以总数不变
            int totalCards = items.size();
            
            // 从上往下绘制分隔线
            for (int i = 0; i <= totalCards; i++) {
                int y = dropZoneBounds.y + 10 + i * (cardHeight + cardMargin) - scrollOffset;
                if (y >= dropZoneBounds.y && y <= dropZoneBounds.y + dropZoneBounds.height) {
                    g.drawLine(dropZoneBounds.x, y, dropZoneBounds.x + dropZoneBounds.width, y);
                }
            }
        }
        
        if (items.isEmpty()) return;
        
        // 从上往下绘制
        int displayIndex = 0;
        for (int i = 0; i < items.size(); i++) {
            PlotItem item = items.get(i);
            
            // 从顶部开始绘制：计算从顶部向下的位置
            int y = dropZoneBounds.y + 10 + displayIndex * (cardHeight + cardMargin) - scrollOffset;
            if (y + cardHeight < dropZoneBounds.y) {
                displayIndex++;
                // 如果正在拖拽这个卡牌，仍然要保留位置
                continue;
            }
            if (y > dropZoneBounds.y + dropZoneBounds.height) break;
            
            // 如果正在拖拽这个卡牌，保留原位置空白，不绘制内容
            if (isDraggingFromThisTrack && item == draggingItem) {
                displayIndex++;
                continue;
            }
            
            DataSet dataSet = mainPanel.findDataSet(item.getDataSetId());
            if (dataSet == null) {
                displayIndex++;
                continue;
            }
            
            displayIndex++;
            
            // 绘制卡牌背景（使用CardView2）
            Graphics2D g2 = (Graphics2D) g.create();
            CardView2.drawCardBackground(g2, x, y, cardWidth, cardHeight, false);
            
            // 绘制数字标识（带扩展名，使用CardView2）
            CardView2.drawCardNumber(g2, dataSet, x, y, cardWidth, false);
            
            // 绘制卡牌名称（在圆形左侧多行显示，使用CardView2）
            CardView2.drawCardName(g2, dataSet, x, y, cardWidth);
            g2.dispose();
            
            int buttonSize = 16;
            int switchButtonY = y + cardHeight - buttonSize - 5;
            
            // 根据数据类型绘制不同的控件
            if (dataSet.getType() == DataSet.DataType.FST) {
                // FST类型：绘制Top按钮和文本框
                int topButtonY = switchButtonY;
                int topButtonSize = 14;
                int topButtonX = x + 5;
                
                if (item.isTopEnabled()) {
                    g.setColor(simplegenomehub.population.fst.FstUiStyle.SELECTION);
                } else {
                    g.setColor(simplegenomehub.population.fst.FstUiStyle.PANEL_BACKGROUND);
                }
                g.fillRect(topButtonX, topButtonY, topButtonSize, topButtonSize);
                g.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_SECONDARY);
                g.setStroke(new BasicStroke(1f));
                g.drawRect(topButtonX, topButtonY, topButtonSize, topButtonSize);
                
                g.setColor(item.isTopEnabled() ? simplegenomehub.population.fst.FstUiStyle.TOP_MARKER : simplegenomehub.population.fst.FstUiStyle.TEXT_SECONDARY);
                g.setFont(simplegenomehub.population.fst.FstUiStyle.arial(9, Font.BOLD));
                FontMetrics topFm = g.getFontMetrics();
                int tX = topButtonX + (topButtonSize - topFm.stringWidth("T")) / 2;
                int tY = topButtonY + topButtonSize / 2 + topFm.getAscent() / 2 - 1;
                g.drawString("T", tX, tY);
                
                int textFieldX = topButtonX + topButtonSize + 5;
                int textFieldY = topButtonY;
                int textFieldWidth = 55;
                int textFieldHeight = topButtonSize;
                
                g.setColor(Color.WHITE);
                g.fillRect(textFieldX, textFieldY, textFieldWidth, textFieldHeight);
                g.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_SECONDARY);
                g.drawRect(textFieldX, textFieldY, textFieldWidth, textFieldHeight);
                
                if (!mainPanel.getInlineTopPercentController().isEditingItem(item, trackView)) {
                    g.setColor(Color.BLACK);
                    g.setFont(simplegenomehub.population.fst.FstUiStyle.arial(9, Font.PLAIN));
                    FontMetrics textFieldFm = g.getFontMetrics();
                    String topPercentText = formatTopPercent(item.getTopPercent());
                    int textFieldTextX = textFieldX + 2;
                    int textFieldTextY = textFieldY + textFieldHeight / 2 + textFieldFm.getAscent() / 2 - 1;
                    g.drawString(topPercentText, textFieldTextX, textFieldTextY);
                }
            } else if (dataSet.getType() == DataSet.DataType.PI || dataSet.getType() == DataSet.DataType.TAJIMAD || 
                      dataSet.getType() == DataSet.DataType.LOGISTIC) {
                // PI、TajimaD和Logistic类型：只绘制颜色选择按钮
                int colorButtonY = switchButtonY;
                int colorButtonSize = 14;
                int colorButtonX = x + 5;
                
                Color cardColor;
                if (dataSet.getType() == DataSet.DataType.PI) {
                    PiRenderer piRenderer = piRendererMap.get(dataSet.getId());
                    cardColor = piRenderer != null ? piRenderer.getColor() : simplegenomehub.population.fst.FstUiStyle.SERIES_PI;
                } else if (dataSet.getType() == DataSet.DataType.TAJIMAD) {
                    TajimaDRenderer tajimaDRenderer = tajimaDRendererMap.get(dataSet.getId());
                    cardColor = tajimaDRenderer != null ? tajimaDRenderer.getColor() : simplegenomehub.population.fst.FstUiStyle.SERIES_TAJIMA_D;
                } else {
                    LogisticRenderer logisticRenderer = logisticRendererMap.get(dataSet.getId());
                    cardColor = logisticRenderer != null ? logisticRenderer.getColor() : simplegenomehub.population.fst.FstUiStyle.PRIMARY_ACTION;
                }
                
                g.setColor(cardColor);
                g.fillRect(colorButtonX, colorButtonY, colorButtonSize, colorButtonSize);
                g.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_SECONDARY);
                g.setStroke(new BasicStroke(1f));
                g.drawRect(colorButtonX, colorButtonY, colorButtonSize, colorButtonSize);
            }
            
            // 绘制删除按钮
            int deleteButtonSize = 14;
            int deleteButtonX = x + cardWidth - deleteButtonSize - 3;
            int deleteButtonY = y + 3;
            
            g.setColor(new Color(255, 200, 200));
            g.fillOval(deleteButtonX, deleteButtonY, deleteButtonSize, deleteButtonSize);
            g.setColor(new Color(200, 100, 100));
            g.setStroke(new BasicStroke(1f));
            g.drawOval(deleteButtonX, deleteButtonY, deleteButtonSize, deleteButtonSize);
            
            g.setColor(Color.RED);
            g.setStroke(new BasicStroke(1.5f));
            int delCenterX = deleteButtonX + deleteButtonSize / 2;
            int delCenterY = deleteButtonY + deleteButtonSize / 2;
            int delOffset = 3;
            g.drawLine(delCenterX - delOffset, delCenterY - delOffset, 
                      delCenterX + delOffset, delCenterY + delOffset);
            g.drawLine(delCenterX - delOffset, delCenterY + delOffset, 
                      delCenterX + delOffset, delCenterY - delOffset);
            
            // 绘制切换按钮
            int switchButtonX = x + cardWidth - buttonSize - 5;
            switchButtonY = y + cardHeight - buttonSize - 5;
            
            g.setColor(simplegenomehub.population.fst.FstUiStyle.PANEL_BACKGROUND);
            g.fillOval(switchButtonX, switchButtonY, buttonSize, buttonSize);
            g.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_SECONDARY);
            g.setStroke(new BasicStroke(1f));
            g.drawOval(switchButtonX, switchButtonY, buttonSize, buttonSize);
            
            g.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_PRIMARY);
            int centerX = switchButtonX + buttonSize / 2;
            int centerY = switchButtonY + buttonSize / 2;
            if (item.getStyle() == PlotItem.PlotStyle.POINT) {
                g.fillOval(centerX - 2, centerY - 2, 4, 4);
            } else {
                g.setStroke(new BasicStroke(1.5f));
                g.drawLine(centerX - 4, centerY, centerX + 4, centerY);
            }
        }
        
        clippedG.dispose();
        
        int totalHeight = items.size() * (cardHeight + cardMargin) + 20;
        if (totalHeight > dropZoneBounds.height) {
            drawDropZoneScrollBar(g, trackView, dropZoneBounds, totalHeight);
        }
    }
    
    // 绘制投放框滚动条
    private void drawDropZoneScrollBar(Graphics2D g, TrackView trackView, Rectangle dropZoneBounds, int totalHeight) {
        int scrollBarWidth = 8;
        int scrollBarX = dropZoneBounds.x + dropZoneBounds.width - scrollBarWidth - 2;
        int scrollBarY = dropZoneBounds.y + 2;
        int scrollBarHeight = dropZoneBounds.height - 4;
        
        g.setColor(simplegenomehub.population.fst.FstUiStyle.GRID);
        g.fillRect(scrollBarX, scrollBarY, scrollBarWidth, scrollBarHeight);
        g.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_SECONDARY);
        g.drawRect(scrollBarX, scrollBarY, scrollBarWidth, scrollBarHeight);
        
        int scrollOffset = trackView.getDropZoneScrollOffset();
        int maxScroll = Math.max(0, totalHeight - dropZoneBounds.height);
        if (maxScroll <= 0) return;
        
        double scrollRatio = (double) scrollOffset / maxScroll;
        int thumbHeight = Math.max(20, (int) (scrollBarHeight * dropZoneBounds.height / totalHeight));
        int thumbY = scrollBarY + (int) (scrollRatio * (scrollBarHeight - thumbHeight));
        
        g.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_SECONDARY);
        g.fillRect(scrollBarX + 1, thumbY, scrollBarWidth - 2, thumbHeight);
        g.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_PRIMARY);
        g.drawRect(scrollBarX + 1, thumbY, scrollBarWidth - 2, thumbHeight);
    }
    
    // 格式化Top百分比显示（使用CardLoad类的方法）
    private String formatTopPercent(double percent) {
        return CardLoad.formatTopPercentText(percent);
    }
}
