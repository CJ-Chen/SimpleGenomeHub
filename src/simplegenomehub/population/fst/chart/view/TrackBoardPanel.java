package simplegenomehub.population.fst.chart.view;

import simplegenomehub.population.fst.chart.FSTTrackChartPanel;
import simplegenomehub.population.fst.chart.model.DataSet;
import simplegenomehub.population.fst.chart.model.PlotItem;
import simplegenomehub.population.fst.chart.model.TrackBoardModel;
import simplegenomehub.population.fst.chart.model.TrackModel;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/**
 * 轨道板面板：纵向显示多个轨道
 */
public class TrackBoardPanel extends JPanel {
    private final TrackBoardModel boardModel;
    private final List<TrackView> trackViews;
    private FSTTrackChartPanel mainPanel; // 用于查数据集（悬停提示）
    
    public TrackBoardPanel(TrackBoardModel boardModel) {
        this.boardModel = boardModel;
        this.trackViews = new ArrayList<>();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Color.WHITE);
        // 悬停提示必须在“接收鼠标事件的面板”上注册：
        // TrackView 不能注册（否则会拦截鼠标事件，导致拖拽/删除卡牌失效），
        // 所以这里在 TrackBoardPanel 上注册，再按坐标换算到对应的小卡牌。
        ToolTipManager.sharedInstance().registerComponent(this);
    }
    
    /**
     * 投放框内小卡牌的悬停提示（Compare/类群名 + 明细）。
     * TrackView 不再注册 ToolTipManager，鼠标事件统一由本面板接收。
     */
    @Override
    public String getToolTipText(MouseEvent event) {
        if (event == null || mainPanel == null) return null;
        TrackView trackView = getTrackViewAt(event.getPoint());
        if (trackView == null) return null;
        Point trackPoint = SwingUtilities.convertPoint(this, event.getPoint(), trackView);
        Rectangle dropZoneBounds = trackView.getDropZoneBounds();
        if (!dropZoneBounds.contains(trackPoint)) return null;
        PlotItem item = trackView.findDropZoneItemAt(trackPoint, dropZoneBounds);
        if (item == null) return null;
        DataSet dataSet = mainPanel.findDataSet(item.getDataSetId());
        if (dataSet == null) return null;
        return CardLoad.getCardToolTip(dataSet);
    }
    
    public void rebuildViews() {
        rebuildViews(null);
    }
    
    public void rebuildViews(FSTTrackChartPanel mainPanel) {
        this.mainPanel = mainPanel;
        removeAll();
        trackViews.clear();
        
        for (TrackModel trackModel : boardModel.getTracks()) {
            TrackView trackView = new TrackView(trackModel);
            if (mainPanel != null) {
                trackView.setMainPanel(mainPanel);
                // 设置TrackModel监听器：items 或高度改变时刷新尺寸并重绘
                trackModel.setListener(() -> {
                    trackView.refreshHeight();
                    if (mainPanel != null) {
                        mainPanel.repaint();
                    }
                    revalidate();
                    repaint();
                });
            }
            trackViews.add(trackView);
            add(trackView);
            add(Box.createVerticalStrut(20)); // 轨道间距
        }
        
        revalidate();
        repaint();
    }
    
    public List<TrackView> getTrackViews() {
        return new ArrayList<>(trackViews);
    }
    
    public TrackView getTrackView(String trackId) {
        for (TrackView trackView : trackViews) {
            if (trackView.getTrackModel().getId().equals(trackId)) {
                return trackView;
            }
        }
        return null;
    }
    
    public TrackView getTrackViewAt(Point p) {
        // 直接检查所有TrackView的边界，确保覆盖整个标签纸（包括上半部分和下半部分）
        for (TrackView trackView : trackViews) {
            Rectangle bounds = trackView.getBounds();
            // 检查点是否在TrackView的完整边界内（包括整个高度）
            // 使用精确的边界检查，确保包括标签纸的下半部分和投放框区域
            if (bounds != null && !bounds.isEmpty()) {
                // 检查点是否在边界内（包括边界本身）
                if (p.x >= bounds.x && p.x < bounds.x + bounds.width &&
                    p.y >= bounds.y && p.y < bounds.y + bounds.height) {
                    return trackView;
                }
            }
        }
        return null;
    }
    
    public int getTrackIndexAtY(int y) {
        int currentY = 0;
        for (int i = 0; i < trackViews.size(); i++) {
            TrackView trackView = trackViews.get(i);
            int trackHeight = trackView.getPreferredSize().height;
            if (y >= currentY && y < currentY + trackHeight) {
                return i;
            }
            currentY += trackHeight + 20; // 加上间距
        }
        return -1;
    }
}
