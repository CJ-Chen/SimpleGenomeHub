package simplegenomehub.population.fst.chart.controller;

import simplegenomehub.population.fst.chart.FSTTrackChartPanel;
import simplegenomehub.population.fst.chart.model.DataSet;
import simplegenomehub.population.fst.chart.model.PlotItem;
import simplegenomehub.population.fst.chart.model.TrackBoardModel;
import simplegenomehub.population.fst.chart.view.CardBarPanel;
import simplegenomehub.population.fst.chart.view.CardView;
import simplegenomehub.population.fst.chart.view.TrackBoardPanel;
import simplegenomehub.population.fst.chart.view.TrackView;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * 卡牌拖拽控制器：处理卡牌的拖拽和投放
 */
public class CardDragController {
    private final CardBarPanel cardBarPanel;
    private final TrackBoardPanel trackBoardPanel;
    private final TrackBoardModel boardModel;
    private final List<DataSet> allDataSets;
    private final GhostCardOverlay ghostOverlay;
    private final FSTTrackChartPanel mainPanel;
    
    private CardView draggingCard = null;
    private Point dragOffset = null;
    private TrackView hoveredTrack = null;
    
    // 获取拖拽状态（供主面板绘制使用）
    public CardView getDraggingCard() {
        return draggingCard;
    }
    
    public Point getDragOffset() {
        return dragOffset;
    }
    
    public CardDragController(CardBarPanel cardBarPanel, TrackBoardPanel trackBoardPanel,
                             TrackBoardModel boardModel, List<DataSet> allDataSets,
                             GhostCardOverlay ghostOverlay,
                             FSTTrackChartPanel mainPanel) {
        this.cardBarPanel = cardBarPanel;
        this.trackBoardPanel = trackBoardPanel;
        this.boardModel = boardModel;
        this.allDataSets = allDataSets;
        this.ghostOverlay = ghostOverlay;
        this.mainPanel = mainPanel;
        
        setupCardBarListeners();
        
        // 监听卡牌栏的变化，为新添加的卡牌设置监听器
        cardBarPanel.addPropertyChangeListener("cardAdded", evt -> {
            CardView newCard = (CardView) evt.getNewValue();
            if (newCard != null) {
                setupCardListeners(newCard);
            }
        });
    }
    
    private void setupCardBarListeners() {
        for (CardView cardView : cardBarPanel.getCardViews()) {
            setupCardListeners(cardView);
        }
    }
    
    private void setupCardListeners(CardView cardView) {
        cardView.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // 开始拖拽 - 参考 FSTCalculatorJieMian3 的实现
                draggingCard = cardView;
                
                // 计算偏移量（鼠标相对于卡牌的位置）- 参考 FSTCalculatorJieMian3
                Point rectLocation = SwingUtilities.convertPoint(cardView, 0, 0, mainPanel);
                Point mouseLocation = SwingUtilities.convertPoint((Component)e.getSource(), e.getPoint(), mainPanel);
                dragOffset = new Point(e.getPoint().x, e.getPoint().y); // 鼠标在卡牌内的相对位置
                
                // 创建幽灵卡牌
                ghostOverlay.setDataSet(cardView.getDataSet());
                // 设置初始位置：卡牌左上角跟随鼠标（减去偏移量）
                Point ghostLocation = new Point(mouseLocation.x - dragOffset.x, mouseLocation.y - dragOffset.y);
                ghostOverlay.setLocation(ghostLocation);
                ghostOverlay.setVisible(true);
                
                // 重绘主面板（拖拽卡牌在主面板的paint方法中绘制）
                mainPanel.repaint();
                
                cardView.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                if (draggingCard == null) return;
                
                Point mouseLocation = SwingUtilities.convertPoint(cardView, e.getPoint(), mainPanel);
                
                // 检查是否拖放回卡牌栏（将鼠标位置转换为相对于卡牌栏的坐标）
                Point cardBarLocation = SwingUtilities.convertPoint(cardView, e.getPoint(), cardBarPanel);
                Rectangle cardBarBounds = new Rectangle(0, 0, cardBarPanel.getWidth(), cardBarPanel.getHeight());
                if (cardBarBounds.contains(cardBarLocation) || 
                    cardBarPanel.getCardViewAt(cardBarLocation) != null) {
                    // 拖放回卡牌栏，不做任何操作，只是取消拖拽
                    draggingCard = null;
                    dragOffset = null;
                    ghostOverlay.setVisible(false);
                    ghostOverlay.setDataSet(null);
                    cardView.setCursor(Cursor.getDefaultCursor());
                    mainPanel.repaint();
                    return;
                }
                
                // 检查是否拖放到轨道
                Point trackLocation = SwingUtilities.convertPoint(cardView, e.getPoint(), trackBoardPanel);
                TrackView targetTrack = trackBoardPanel.getTrackViewAt(trackLocation);
                
                if (targetTrack != null) {
                    // 检查是否在标签纸的拖放区域内（排除把手和删除按钮区域）
                    Point trackPoint = SwingUtilities.convertPoint(trackBoardPanel, trackLocation, targetTrack);
                    
                    // 排除左侧把手区域（前20像素）和删除按钮区域
                    Rectangle handleBounds = targetTrack.getHandleBounds();
                    Rectangle deleteBounds = targetTrack.getDeleteBounds();
                    
                    // 获取标签纸的完整边界（包括整个高度，确保包括上半部分和下半部分）
                    int trackWidth = targetTrack.getWidth();
                    int trackHeight = targetTrack.getHeight();
                    
                    // 检查点是否在标签纸范围内（包括整个高度，确保包括投放框的下半部分）
                    boolean inTrackBounds = trackPoint.x >= 0 && trackPoint.x < trackWidth &&
                                          trackPoint.y >= 0 && trackPoint.y < trackHeight;
                    
                    // 特别检查是否在投放框区域内（包括整个高度）
                    Rectangle dropZoneBounds = targetTrack.getDropZoneBounds();
                    boolean inDropZone = dropZoneBounds != null && 
                                        trackPoint.x >= dropZoneBounds.x && 
                                        trackPoint.x < dropZoneBounds.x + dropZoneBounds.width &&
                                        trackPoint.y >= dropZoneBounds.y && 
                                        trackPoint.y < dropZoneBounds.y + dropZoneBounds.height;
                    
                    // 如果不在把手区域和删除按钮区域，且在标签纸范围内（包括上半部分和下半部分），就添加到该轨道
                    // 特别确保投放框区域（包括下半部分）也能正确识别
                    if (inTrackBounds && 
                        !handleBounds.contains(trackPoint) &&
                        !deleteBounds.contains(trackPoint)) {
                        // 拖放到标签纸的任意位置（除了把手和删除按钮），都添加到该轨道
                        // 这包括投放框的上半部分和下半部分
                        assignDataSetToTrack(draggingCard.getDataSet().getId(), targetTrack.getTrackModel().getId());
                    } else {
                        // 拖拽到轨道外部区域，创建新标签纸
                        createNewTrackWithCard(draggingCard.getDataSet().getId());
                    }
                } else {
                    // 拖拽到轨道外部区域，创建新标签纸
                    createNewTrackWithCard(draggingCard.getDataSet().getId());
                }
                
                // 清理拖拽状态
                draggingCard = null;
                dragOffset = null;
                ghostOverlay.setVisible(false);
                ghostOverlay.setDataSet(null);
                cardView.setCursor(Cursor.getDefaultCursor());
                mainPanel.repaint();
            }
        });
        
        cardView.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (draggingCard == null) return;
                
                // 更新 ghostOverlay 位置 - 卡牌左上角跟随鼠标
                Point mouseLocation = SwingUtilities.convertPoint((Component)e.getSource(), e.getPoint(), mainPanel);
                Point ghostLocation = new Point(mouseLocation.x - dragOffset.x, mouseLocation.y - dragOffset.y);
                ghostOverlay.setLocation(ghostLocation);
                // 重绘主面板（会在paint方法中绘制拖拽卡牌）
                mainPanel.repaint();
                
                // 检查是否悬停在某个轨道的投放框上（包括整个高度，确保包括下半部分）
                Point trackLocation = SwingUtilities.convertPoint(cardView, e.getPoint(), trackBoardPanel);
                TrackView track = trackBoardPanel.getTrackViewAt(trackLocation);
                
                if (track != null) {
                    // 检查是否在投放框区域内（包括整个高度）
                    Point trackPoint = SwingUtilities.convertPoint(trackBoardPanel, trackLocation, track);
                    Rectangle dropZoneBounds = track.getDropZoneBounds();
                    boolean inDropZone = dropZoneBounds != null && 
                                        trackPoint.x >= dropZoneBounds.x && 
                                        trackPoint.x < dropZoneBounds.x + dropZoneBounds.width &&
                                        trackPoint.y >= dropZoneBounds.y && 
                                        trackPoint.y < dropZoneBounds.y + dropZoneBounds.height;
                    
                    if (inDropZone && track != hoveredTrack) {
                        if (hoveredTrack != null) {
                            hoveredTrack.setDropZoneHovered(false);
                        }
                        hoveredTrack = track;
                        track.setDropZoneHovered(true);
                        track.repaint();
                    } else if (!inDropZone && track == hoveredTrack) {
                        hoveredTrack.setDropZoneHovered(false);
                        hoveredTrack.repaint();
                        hoveredTrack = null;
                    }
                } else if (track == null && hoveredTrack != null) {
                    hoveredTrack.setDropZoneHovered(false);
                    hoveredTrack.repaint();
                    hoveredTrack = null;
                }
            }
        });
    }
    
    private void togglePlotStyle(String dataSetId) {
        // 找到所有包含该数据集的轨道，切换样式
        for (simplegenomehub.population.fst.chart.model.TrackModel trackModel : boardModel.getTracks()) {
            for (PlotItem item : trackModel.getItems()) {
                if (item.getDataSetId().equals(dataSetId)) {
                    PlotItem.PlotStyle newStyle = (item.getStyle() == PlotItem.PlotStyle.POINT) 
                        ? PlotItem.PlotStyle.LINE 
                        : PlotItem.PlotStyle.POINT;
                    item.setStyle(newStyle);
                    mainPanel.repaint();
                }
            }
        }
    }
    
    private void assignDataSetToTrack(String dataSetId, String trackId) {
        simplegenomehub.population.fst.chart.model.TrackModel trackModel = boardModel.getTrack(trackId);
        if (trackModel == null) return;
        
        // 获取要添加的数据集类型
        DataSet dataSet = mainPanel.findDataSet(dataSetId);
        if (dataSet == null) return;
        DataSet.DataType dataSetType = dataSet.getType();
        
        // 检查目标轨道中已有的数据类型
        DataSet.DataType trackType = null;
        for (PlotItem item : trackModel.getItems()) {
            DataSet itemDataSet = mainPanel.findDataSet(item.getDataSetId());
            if (itemDataSet != null) {
                trackType = itemDataSet.getType();
                break;
            }
        }
        
        // 类型限制：TajimaD只能和TajimaD一起，FST/PI不能和TajimaD一起
        if (dataSetType == DataSet.DataType.TAJIMAD) {
            // TajimaD只能添加到TajimaD轨道
            if (trackType != null && trackType != DataSet.DataType.TAJIMAD) {
                // 类型不匹配，不允许添加
                return;
            }
        } else {
            // FST/PI不能添加到TajimaD轨道
            if (trackType == DataSet.DataType.TAJIMAD) {
                // 类型不匹配，不允许添加
                return;
            }
        }
        
        // 检查是否已存在该数据集
        PlotItem existingItem = null;
        for (PlotItem item : trackModel.getItems()) {
            if (item.getDataSetId().equals(dataSetId)) {
                existingItem = item;
                break;
            }
        }
        
        if (existingItem != null) {
            // 如果已存在，保持原有样式，不重复添加
            return;
        }
        
        // 添加新的PlotItem（默认点图模式）
        PlotItem item = new PlotItem(dataSetId, PlotItem.PlotStyle.POINT);
        trackModel.addItem(item);
        
        // 触发重绘
        mainPanel.repaint();
    }
    
    // 切换卡牌样式（在投放框内点击按钮时调用）
    public void toggleCardStyleInTrack(String dataSetId, String trackId) {
        simplegenomehub.population.fst.chart.model.TrackModel trackModel = boardModel.getTrack(trackId);
        if (trackModel == null) return;
        
        for (PlotItem item : trackModel.getItems()) {
            if (item.getDataSetId().equals(dataSetId)) {
                PlotItem.PlotStyle newStyle = (item.getStyle() == PlotItem.PlotStyle.POINT) 
                    ? PlotItem.PlotStyle.LINE 
                    : PlotItem.PlotStyle.POINT;
                item.setStyle(newStyle);
                mainPanel.repaint();
                break;
            }
        }
    }
    
    // 创建新标签纸并添加卡牌
    private void createNewTrackWithCard(String dataSetId) {
        // 创建新轨道
        simplegenomehub.population.fst.chart.model.TrackModel newTrack = boardModel.createTrack();
        // 添加卡牌到新轨道
        PlotItem item = new PlotItem(dataSetId, PlotItem.PlotStyle.POINT);
        newTrack.addItem(item);
        // 重建视图
        trackBoardPanel.rebuildViews(mainPanel);
        mainPanel.repaint();
    }
}
