package simplegenomehub.population.fst.chart.view;

import simplegenomehub.population.fst.chart.FSTChartStyle;
import simplegenomehub.population.fst.chart.FSTTrackChartPanel;
import simplegenomehub.population.fst.chart.model.PlotItem;
import simplegenomehub.population.fst.chart.model.TrackModel;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import javax.swing.JComponent;

/**
 * 轨道视图：标签纸样式的轨道
 */
public class TrackView extends JComponent {
    private FSTTrackChartPanel mainPanel;
    private static final int HANDLE_WIDTH = 20;
    private static final int HANDLE_HEIGHT = 40;
    private static final int DELETE_BUTTON_SIZE = 16;
    private static final int DELETE_BUTTON_MARGIN = 2;
    private static final int DROP_ZONE_WIDTH = 120;
    private static final int CORNER_RADIUS = 8;
    private static final int SHADOW_OFFSET = 3;
    
    private final TrackModel trackModel;
    private boolean hovered = false;
    private boolean handleHovered = false;
    private boolean deleteHovered = false;
    private boolean dropZoneHovered = false;
    private String hoveredCardId = null; // 悬停的卡牌ID
    private int hoveredCardIndex = -1; // 悬停的卡牌索引
    private int dropZoneScrollOffset = 0; // 投放框滚动偏移量
    
    public TrackView(TrackModel trackModel) {
        this.trackModel = trackModel;
        setBackground(Color.WHITE);
        setLayout(null); // 使用null布局，以便绝对定位内联文本框
        refreshHeight();
        // 注意：这里不能注册 ToolTipManager。注册后会给本组件装上鼠标监听器（MouseListener/MouseMotionListener），
        // 鼠标事件便不再上抛给父面板 TrackBoardPanel，导致投放框内卡牌的拖拽、删除、悬停等全部失效。
        // 悬停提示改由 TrackBoardPanel 统一处理（见 TrackBoardPanel.getToolTipText）。
    }
    
    /** 轨道高度由模型决定；拖拽上下边界改变高度后调用本方法刷新尺寸 */
    public void refreshHeight() {
        int h = trackModel != null ? trackModel.getHeight() : TrackModel.DEFAULT_HEIGHT;
        // 宽度不写死：随窗口宽度自适应；高度由模型决定
        setPreferredSize(new Dimension(100, h));
        setMinimumSize(new Dimension(0, h));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        revalidate();
        repaint();
    }
    
    // 根据投放框内坐标找到对应的小卡牌（与绘制时的排版保持一致）
    PlotItem findDropZoneItemAt(Point p, Rectangle dropZoneBounds) {
        List<PlotItem> items = trackModel.getItems();
        if (items == null || items.isEmpty()) return null;
        final int cardHeight = 70;
        final int cardMargin = 5;
        int scrollOffset = getDropZoneScrollOffset();
        int row = (p.y - (dropZoneBounds.y + 10) + scrollOffset) / (cardHeight + cardMargin);
        if (row < 0 || row >= items.size()) return null;
        return items.get(row);
    }
    
    @Override
    public Dimension getMinimumSize() {
        int h = trackModel != null ? trackModel.getHeight() : TrackModel.DEFAULT_HEIGHT;
        return new Dimension(0, h);
    }
    
    @Override
    public Dimension getMaximumSize() {
        int h = trackModel != null ? trackModel.getHeight() : TrackModel.DEFAULT_HEIGHT;
        return new Dimension(Integer.MAX_VALUE, h);
    }
    
    public void setMainPanel(FSTTrackChartPanel mainPanel) {
        this.mainPanel = mainPanel;
    }
    
    public TrackModel getTrackModel() {
        return trackModel;
    }
    
    public void setHovered(boolean hovered) {
        this.hovered = hovered;
        repaint();
    }
    
    public void setHandleHovered(boolean hovered) {
        this.handleHovered = hovered;
        repaint();
    }
    
    public void setDeleteHovered(boolean hovered) {
        this.deleteHovered = hovered;
        repaint();
    }
    
    public void setDropZoneHovered(boolean hovered) {
        this.dropZoneHovered = hovered;
        repaint();
    }
    
    public void setHoveredCardIndex(int index) {
        this.hoveredCardIndex = index;
        repaint();
    }
    
    public int getHoveredCardIndex() {
        return hoveredCardIndex;
    }
    
    public Rectangle getHandleBounds() {
        return new Rectangle(0, 0, HANDLE_WIDTH, HANDLE_HEIGHT);
    }
    
    public Rectangle getDeleteBounds() {
        int x = HANDLE_WIDTH / 2 - DELETE_BUTTON_SIZE / 2;
        int y = HANDLE_HEIGHT + DELETE_BUTTON_MARGIN;
        return new Rectangle(x, y, DELETE_BUTTON_SIZE, DELETE_BUTTON_SIZE);
    }
    
    public Rectangle getDropZoneBounds() {
        int w = getWidth();
        int h = getHeight();
        int x = w - DROP_ZONE_WIDTH;
        int y = 0;
        return new Rectangle(x, y, DROP_ZONE_WIDTH, h);
    }
    
    public int getDropZoneScrollOffset() {
        return dropZoneScrollOffset;
    }
    
    public void setDropZoneScrollOffset(int offset) {
        if (offset < 0) offset = 0;
        // 计算最大滚动偏移
        if (mainPanel != null) {
            int cardHeight = 70;
            int cardMargin = 5;
            int totalHeight = trackModel.getItems().size() * (cardHeight + cardMargin) + 20;
            int maxScroll = Math.max(0, totalHeight - getHeight());
            if (offset > maxScroll) offset = maxScroll;
        }
        this.dropZoneScrollOffset = offset;
        repaint();
    }
    
    public void scrollDropZone(int delta) {
        setDropZoneScrollOffset(dropZoneScrollOffset + delta);
    }
    
    public Rectangle getPlotAreaBounds() {
        int w = getWidth();
        int h = getHeight();
        // 绘制区域要左右各从屏幕区域减去200
        int leftMargin = 200;
        int rightMargin = 200;
        int x = leftMargin;
        int y = 10; // 顶部留少量空间
        // X轴长度应该和染色体右端对齐（与GenomeRulerPanel的绘图宽度一致）
        int plotW = w - leftMargin - rightMargin;
        int plotH = Math.max(24, h - 20); // 高度随大卡牌高度自适应（上下各留10）
        return new Rectangle(x, y, plotW, plotH);
    }
    
    /** Y轴左上角的折叠按钮区域：放在刻度数字左侧，避免与刻度值重叠 */
    public Rectangle getCollapseButtonBounds() {
        Rectangle plot = getPlotAreaBounds();
        return new Rectangle(plot.x - 62, plot.y + 1, 12, 12);
    }
    
    /** 是否为折叠状态 */
    public boolean isCollapsed() {
        return trackModel != null && trackModel.isCollapsed();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        // 如果正在被拖拽，不绘制（隐藏原位置）
        if (mainPanel != null && mainPanel.getDraggingTrack() == this) {
            return;
        }
        
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int w = getWidth();
        int h = getHeight();
        
        // 绘制阴影
        g2.setColor(new Color(0, 0, 0, 30));
        RoundRectangle2D shadowRect = new RoundRectangle2D.Float(
            SHADOW_OFFSET, SHADOW_OFFSET, w - 1, h - 1, CORNER_RADIUS, CORNER_RADIUS
        );
        g2.fill(shadowRect);
        
        // 绘制标签纸背景（圆角矩形）
        RoundRectangle2D paperRect = new RoundRectangle2D.Float(0, 0, w - 1, h - 1, CORNER_RADIUS, CORNER_RADIUS);
        g2.setColor(simplegenomehub.population.fst.FstUiStyle.TRACK_BACKGROUND);
        g2.fill(paperRect);
        
        // 绘制边框
        g2.setColor(simplegenomehub.population.fst.FstUiStyle.TRACK_BORDER);
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(paperRect);
        
        // 绘制左侧小把手
        Rectangle handleBounds = getHandleBounds();
        if (handleHovered) {
            g2.setColor(simplegenomehub.population.fst.FstUiStyle.CARD_BORDER);
        } else {
            g2.setColor(simplegenomehub.population.fst.FstUiStyle.GRID);
        }
        g2.fillRect(handleBounds.x, handleBounds.y, handleBounds.width, handleBounds.height);
        g2.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_SECONDARY);
        g2.setStroke(new BasicStroke(1f));
        g2.drawRect(handleBounds.x, handleBounds.y, handleBounds.width, handleBounds.height);
        
        // 绘制把手上的横线（表示可拖拽）
        g2.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_PRIMARY);
        int centerX = handleBounds.x + handleBounds.width / 2;
        for (int i = 0; i < 3; i++) {
            int y = handleBounds.y + handleBounds.height / 4 + i * handleBounds.height / 4;
            g2.drawLine(centerX - 4, y, centerX + 4, y);
        }
        
        // 绘制删除按钮
        Rectangle deleteBounds = getDeleteBounds();
        if (deleteHovered) {
            g2.setColor(new Color(255, 200, 200));
        } else {
            g2.setColor(new Color(255, 150, 150));
        }
        g2.fillOval(deleteBounds.x, deleteBounds.y, deleteBounds.width, deleteBounds.height);
        g2.setColor(Color.RED);
        g2.setStroke(new BasicStroke(2f));
        int delCenterX = deleteBounds.x + deleteBounds.width / 2;
        int delCenterY = deleteBounds.y + deleteBounds.height / 2;
        g2.drawLine(delCenterX - 4, delCenterY, delCenterX + 4, delCenterY);
        
        // 绘制右侧投放框
        Rectangle dropZoneBounds = getDropZoneBounds();
        if (dropZoneHovered) {
            g2.setColor(simplegenomehub.population.fst.FstUiStyle.SELECTION);
        } else {
            g2.setColor(simplegenomehub.population.fst.FstUiStyle.PANEL_BACKGROUND);
        }
        g2.fillRect(dropZoneBounds.x, dropZoneBounds.y, dropZoneBounds.width, dropZoneBounds.height);
        
        // 绘制投放框虚线边框
        g2.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_SECONDARY);
        Stroke dashedStroke = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
            10.0f, new float[]{5.0f}, 0.0f);
        g2.setStroke(dashedStroke);
        g2.drawRect(dropZoneBounds.x, dropZoneBounds.y, dropZoneBounds.width, dropZoneBounds.height);
        
        // 绘制投放框内的卡牌（如果有的话）
        if (mainPanel != null) {
            mainPanel.paintDropZoneCards(g2, this, dropZoneBounds);
        }
        
        // 如果没有卡牌，显示提示文字
        if (trackModel.getItems().isEmpty()) {
            g2.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_SECONDARY);
            g2.setFont(simplegenomehub.population.fst.FstUiStyle.sans(11, Font.ITALIC));
            FontMetrics fm = g2.getFontMetrics();
            String text = "Drop cards here";
            int textX = dropZoneBounds.x + (dropZoneBounds.width - fm.stringWidth(text)) / 2;
            int textY = dropZoneBounds.y + dropZoneBounds.height / 2;
            g2.drawString(text, textX, textY);
        }
        
        g2.dispose();
        
        // 绘制坐标系和数据（由主面板负责，在标签纸背景之上）
        if (mainPanel != null) {
            Rectangle plotBounds = getPlotAreaBounds();
            
            // 获取当前组件的可见区域（考虑父组件的裁剪）
            Rectangle visibleRect = getVisibleRect();
            Rectangle componentBounds = new Rectangle(0, 0, getWidth(), getHeight());
            Rectangle intersection = visibleRect.intersection(componentBounds);
            
            // 如果组件完全不可见，不绘制
            if (intersection.isEmpty()) {
                return;
            }
            
            // 计算绘图区域与可见区域的交集
            Rectangle visiblePlotBounds = plotBounds.intersection(intersection);
            if (!visiblePlotBounds.isEmpty()) {
                // 先绘制坐标系（不裁剪，以便显示刻度标签，但需要考虑可见区域）
                Graphics2D axisG = (Graphics2D) g.create();
                // 设置裁剪区域为可见区域，但允许Y轴刻度标签显示在左侧
                Rectangle axisClip = new Rectangle(
                    Math.max(0, intersection.x - 60), // 为Y轴标签留出空间
                    intersection.y,
                    intersection.width + 60,
                    intersection.height
                );
                axisG.setClip(axisClip);
                mainPanel.paintAxis(axisG, this, plotBounds);
                axisG.dispose();
                
                // 再绘制数据（裁剪到绘图区域和可见区域的交集）
                Graphics2D dataG = (Graphics2D) g.create();
                dataG.setClip(visiblePlotBounds);
                // 启用抗锯齿，确保数据绘制平滑
                dataG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                dataG.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                mainPanel.paintTrackData(dataG, this);
                dataG.dispose();
            }
        }
        
        // 折叠按钮：Y轴左上角紧贴轴的黑色小三角（点击切换 Y 轴是否按数据最大值收紧）
        Rectangle collapseBounds = getCollapseButtonBounds();
        Graphics2D cg = (Graphics2D) g.create();
        cg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        cg.setColor(Color.BLACK);
        int cx = collapseBounds.x;
        int cy = collapseBounds.y;
        int cs = collapseBounds.width;
        if (isCollapsed()) {
            // 折叠：右向三角
            cg.fillPolygon(new int[]{cx + 2, cx + 2, cx + cs - 1},
                           new int[]{cy + 1, cy + cs - 1, cy + cs / 2}, 3);
        } else {
            // 展开：下向三角
            cg.fillPolygon(new int[]{cx + 1, cx + cs - 1, cx + cs / 2},
                           new int[]{cy + 2, cy + 2, cy + cs - 2}, 3);
        }
        cg.dispose();
    }
}
