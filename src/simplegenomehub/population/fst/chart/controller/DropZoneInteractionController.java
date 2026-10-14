package simplegenomehub.population.fst.chart.controller;

import simplegenomehub.population.fst.chart.FSTTrackChartPanel;
import simplegenomehub.population.fst.chart.model.DataSet;
import simplegenomehub.population.fst.chart.model.PlotItem;
import simplegenomehub.population.fst.chart.model.TrackModel;
import simplegenomehub.population.fst.chart.view.CardBarPanel;
import simplegenomehub.population.fst.chart.view.TrackBoardPanel;
import simplegenomehub.population.fst.chart.view.TrackView;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * 投放框交互控制器：处理投放框内卡牌的点击、按钮切换和拖拽
 */
public class DropZoneInteractionController {
    private final TrackBoardPanel trackBoardPanel;
    private final FSTTrackChartPanel mainPanel;
    private final CardDragController cardDragController;
    private final CardBarPanel cardBarPanel;
    
    private PlotItem draggingItem = null;
    private TrackView sourceTrackView = null;
    private Point dragOffset = null;
    
    public DropZoneInteractionController(TrackBoardPanel trackBoardPanel, 
                                       FSTTrackChartPanel mainPanel,
                                       CardDragController cardDragController) {
        this.trackBoardPanel = trackBoardPanel;
        this.mainPanel = mainPanel;
        this.cardDragController = cardDragController;
        this.cardBarPanel = mainPanel.getCardBarPanel();
        
        setupListeners();
    }
    
    private void setupListeners() {
        trackBoardPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                TrackView trackView = trackBoardPanel.getTrackViewAt(e.getPoint());
                if (trackView == null) return;
                
                Point trackPoint = SwingUtilities.convertPoint(trackBoardPanel, e.getPoint(), trackView);
                Rectangle dropZoneBounds = trackView.getDropZoneBounds();
                
                if (!dropZoneBounds.contains(trackPoint)) return;
                
                // 检查是否点击了卡牌（考虑滚动偏移）
                PlotItem clickedItem = getItemAtPoint(trackView, dropZoneBounds, trackPoint);
                if (clickedItem != null) {
                    // 计算卡牌的实际显示位置（考虑滚动偏移）
                    int cardWidth = 120;
                    int cardHeight = 70; // 增加高度以容纳Top按钮和文本框
                    int cardMargin = 5;
                    int scrollOffset = trackView.getDropZoneScrollOffset();
                    int x = dropZoneBounds.x + (dropZoneBounds.width - cardWidth) / 2;
                    
                    // 计算卡牌在列表中的实际显示索引（从上往下，包括正在拖拽的卡牌位置）
                    List<PlotItem> items = trackView.getTrackModel().getItems();
                    
                    // 直接使用items中的索引，不跳过正在拖拽的卡牌
                    int displayIndex = items.indexOf(clickedItem);
                    if (displayIndex < 0) return;
                    
                    int cardY = dropZoneBounds.y + 10 + displayIndex * (cardHeight + cardMargin) - scrollOffset;
                    
                    // 检查是否点击了删除按钮（右上角）
                    int deleteButtonSize = 14;
                    int deleteButtonX = x + cardWidth - deleteButtonSize - 3;
                    int deleteButtonY = cardY + 3;
                    Rectangle deleteButtonBounds = new Rectangle(deleteButtonX, deleteButtonY, deleteButtonSize, deleteButtonSize);
                    
                    if (deleteButtonBounds.contains(trackPoint)) {
                        // 删除卡牌
                        String trackId = trackView.getTrackModel().getId();
                        trackView.getTrackModel().removeItem(clickedItem);
                        // 如果卡牌数为0，删除该标签纸
                        if (trackView.getTrackModel().isEmpty()) {
                            mainPanel.getBoardModel().removeTrack(trackId);
                            trackBoardPanel.rebuildViews(mainPanel);
                        }
                        mainPanel.repaint();
                        return;
                    }
                    
                    // 根据数据类型检查不同的按钮
                    simplegenomehub.population.fst.chart.model.DataSet dataSet = mainPanel.findDataSet(clickedItem.getDataSetId());
                    int buttonSize = 16;
                    int switchButtonY = cardY + cardHeight - buttonSize - 5;
                    
                    if (dataSet != null && dataSet.getType() == simplegenomehub.population.fst.chart.model.DataSet.DataType.FST) {
                        // FST类型：检查Top按钮和文本框
                        int topButtonY = switchButtonY; // 和切换按钮齐平
                        int topButtonSize = 14;
                        int topButtonX = x + 5;
                        Rectangle topButtonBounds = new Rectangle(topButtonX, topButtonY, topButtonSize, topButtonSize);
                        
                        if (topButtonBounds.contains(trackPoint)) {
                            // 切换Top显示状态
                            clickedItem.setTopEnabled(!clickedItem.isTopEnabled());
                            if (clickedItem.isTopEnabled()) {
                                // 如果启用，重新计算top bits
                                mainPanel.recalculateTopBitsForItem(clickedItem);
                            }
                            mainPanel.repaint();
                            return;
                        }
                        
                        // 检查是否点击了Top文本框
                        int textFieldX = topButtonX + topButtonSize + 5;
                        int textFieldY = topButtonY;
                        int textFieldWidth = 55; // 增加文本框长度以容纳更多小数位
                        int textFieldHeight = topButtonSize;
                        Rectangle textFieldBounds = new Rectangle(textFieldX, textFieldY, textFieldWidth, textFieldHeight);
                        
                        if (textFieldBounds.contains(trackPoint)) {
                            // 创建内联文本框进行编辑
                            mainPanel.getInlineTopPercentController().startInlineTopPercentEditing(clickedItem, trackView, textFieldBounds);
                            return;
                        }
                    } else if (dataSet != null && (dataSet.getType() == simplegenomehub.population.fst.chart.model.DataSet.DataType.PI || 
                                                   dataSet.getType() == simplegenomehub.population.fst.chart.model.DataSet.DataType.TAJIMAD)) {
                        // PI和TajimaD类型：检查颜色选择按钮
                        int colorButtonY = switchButtonY;
                        int colorButtonSize = 14;
                        int colorButtonX = x + 5;
                        Rectangle colorButtonBounds = new Rectangle(colorButtonX, colorButtonY, colorButtonSize, colorButtonSize);
                        
                        if (colorButtonBounds.contains(trackPoint)) {
                            String dialogTitle = dataSet.getType() == simplegenomehub.population.fst.chart.model.DataSet.DataType.PI ? "Choose the PI color" : "Choose the Tajima's D color";
                            Color currentColor = null;
                            boolean colorChanged = false;
                            
                            if (dataSet.getType() == simplegenomehub.population.fst.chart.model.DataSet.DataType.PI) {
                                simplegenomehub.population.fst.chart.render.PiRenderer piRenderer = mainPanel.getPiRenderer(dataSet.getId());
                                if (piRenderer != null) {
                                    currentColor = piRenderer.getColor();
                                    Color newColor = javax.swing.JColorChooser.showDialog(mainPanel, dialogTitle, currentColor);
                                    if (newColor != null) {
                                        piRenderer.setColor(newColor);
                                        colorChanged = true;
                                    }
                                }
                            } else {
                                simplegenomehub.population.fst.chart.render.TajimaDRenderer tajimaDRenderer = mainPanel.getTajimaDRenderer(dataSet.getId());
                                if (tajimaDRenderer != null) {
                                    currentColor = tajimaDRenderer.getColor();
                                    Color newColor = javax.swing.JColorChooser.showDialog(mainPanel, dialogTitle, currentColor);
                                    if (newColor != null) {
                                        tajimaDRenderer.setColor(newColor);
                                        colorChanged = true;
                                    }
                                }
                            }
                            if (colorChanged) {
                                mainPanel.repaint();
                            }
                            return;
                        }
                    }
                    
                    // 检查是否点击了切换按钮（右下角）
                    int switchButtonX = x + cardWidth - buttonSize - 5;
                    switchButtonY = cardY + cardHeight - buttonSize - 5;
                    Rectangle buttonBounds = new Rectangle(switchButtonX, switchButtonY, buttonSize, buttonSize);
                    
                    if (buttonBounds.contains(trackPoint)) {
                        // 切换样式
                        cardDragController.toggleCardStyleInTrack(clickedItem.getDataSetId(), trackView.getTrackModel().getId());
                        return;
                    }
                    
                    // 如果正在编辑文本框，先结束编辑
                    if (mainPanel.getInlineTopPercentController().isEditingTopPercent()) {
                        mainPanel.getInlineTopPercentController().finishInlineTopPercentEditing();
                    }
                    
                    // 开始拖拽投放框内的卡牌（移动，不是复制）
                    draggingItem = clickedItem;
                    sourceTrackView = trackView;
                    Point mouseLocation = SwingUtilities.convertPoint(trackBoardPanel, e.getPoint(), mainPanel);
                    Point cardLocation = SwingUtilities.convertPoint(trackView, 
                        new Point(x, cardY), mainPanel);
                    dragOffset = new Point(mouseLocation.x - cardLocation.x, mouseLocation.y - cardLocation.y);
                    
                    // 设置幽灵卡牌
                    mainPanel.setDraggingItemFromDropZone(clickedItem, sourceTrackView, dragOffset);
                    mainPanel.repaint();
                }
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                if (draggingItem == null) return;
                
                // 检查是否拖放回卡牌栏
                Point cardBarLocation = SwingUtilities.convertPoint(trackBoardPanel, e.getPoint(), cardBarPanel);
                Rectangle cardBarBounds = new Rectangle(0, 0, cardBarPanel.getWidth(), cardBarPanel.getHeight());
                if (cardBarBounds.contains(cardBarLocation) || 
                    cardBarPanel.getCardViewAt(cardBarLocation) != null) {
                    // 拖放回卡牌栏，从原轨道移除卡牌，不创建新轨道
                    String trackId = sourceTrackView.getTrackModel().getId();
                    sourceTrackView.getTrackModel().removeItem(draggingItem);
                    // 如果卡牌数为0，删除该标签纸
                    if (sourceTrackView.getTrackModel().isEmpty()) {
                        mainPanel.getBoardModel().removeTrack(trackId);
                        trackBoardPanel.rebuildViews(mainPanel);
                    }
                    draggingItem = null;
                    sourceTrackView = null;
                    dragOffset = null;
                    mainPanel.clearDraggingItemFromDropZone();
                    mainPanel.repaint();
                    return;
                }
                
                // 检查是否拖放到轨道（事件来自trackBoardPanel，直接使用e.getPoint()）
                Point trackLocation = e.getPoint();
                TrackView targetTrack = trackBoardPanel.getTrackViewAt(trackLocation);
                
                if (targetTrack != null) {
                    // 检查是否在标签纸的拖放区域内（排除把手和删除按钮区域）
                    Point trackPoint = SwingUtilities.convertPoint(trackBoardPanel, trackLocation, targetTrack);
                    Rectangle dropZoneBounds = targetTrack.getDropZoneBounds();
                    
                    // 排除左侧把手区域（前20像素）和删除按钮区域
                    Rectangle handleBounds = targetTrack.getHandleBounds();
                    Rectangle deleteBounds = targetTrack.getDeleteBounds();
                    
                    // 如果不在把手区域和删除按钮区域，且在拖放区域内
                    if (dropZoneBounds.contains(trackPoint) && 
                        !handleBounds.contains(trackPoint) &&
                        !deleteBounds.contains(trackPoint)) {
                        
                        if (targetTrack == sourceTrackView) {
                            // 在同一轨道内移动：计算目标轨道位置
                            int targetSlotIndex = getSlotIndexAtPoint(targetTrack, dropZoneBounds, trackPoint);
                            if (targetSlotIndex >= 0) {
                                // 在同一轨道内重新排序
                                List<PlotItem> items = sourceTrackView.getTrackModel().getItems();
                                int currentIndex = items.indexOf(draggingItem);
                                if (currentIndex >= 0) {
                                    // 计算目标索引（从上往下）
                                    int targetIndex;
                                    
                                    // targetSlotIndex 的范围是 0 到 items.size()
                                    // 如果 targetSlotIndex == items.size()，表示要移动到最后一个位置
                                    if (targetSlotIndex >= items.size()) {
                                        // 移动到最后一个位置
                                        targetIndex = items.size() - 1;
                                    } else {
                                        targetIndex = targetSlotIndex;
                                        // 如果向下移动，目标索引需要减1（因为原位置还在列表中）
                                        if (currentIndex < targetIndex) {
                                            targetIndex--;
                                        }
                                    }
                                    
                                    if (targetIndex >= 0 && targetIndex < items.size() && targetIndex != currentIndex) {
                                        sourceTrackView.getTrackModel().moveItem(currentIndex, targetIndex);
                                    }
                                }
                            }
                        } else {
                            // 拖放到另一个标签纸的拖放区域：移动到该轨道
                            int targetSlotIndex = getSlotIndexAtPoint(targetTrack, dropZoneBounds, trackPoint);
                            moveItemToTrackAtSlot(draggingItem, sourceTrackView.getTrackModel().getId(), 
                                                 targetTrack.getTrackModel().getId(), targetSlotIndex);
                        }
                    } else {
                        // 拖拽到轨道外部区域，创建新标签纸
                        String sourceTrackId = sourceTrackView.getTrackModel().getId();
                        createNewTrackWithItem(draggingItem);
                        // 从原轨道移除
                        sourceTrackView.getTrackModel().removeItem(draggingItem);
                        // 如果原轨道卡牌数为0，删除该标签纸
                        if (sourceTrackView.getTrackModel().isEmpty()) {
                            mainPanel.getBoardModel().removeTrack(sourceTrackId);
                            trackBoardPanel.rebuildViews(mainPanel);
                        }
                    }
                } else {
                    // 拖拽到轨道外部区域，创建新标签纸
                    String sourceTrackId = sourceTrackView.getTrackModel().getId();
                    createNewTrackWithItem(draggingItem);
                    // 从原轨道移除
                    sourceTrackView.getTrackModel().removeItem(draggingItem);
                    // 如果原轨道卡牌数为0，删除该标签纸
                    if (sourceTrackView.getTrackModel().isEmpty()) {
                        mainPanel.getBoardModel().removeTrack(sourceTrackId);
                        trackBoardPanel.rebuildViews(mainPanel);
                    }
                }
                
                // 清理拖拽状态
                draggingItem = null;
                sourceTrackView = null;
                dragOffset = null;
                mainPanel.clearDraggingItemFromDropZone();
                mainPanel.repaint();
            }
        });
        
        trackBoardPanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (draggingItem == null) return;
                
                Point mouseLocation = SwingUtilities.convertPoint(trackBoardPanel, e.getPoint(), mainPanel);
                Point ghostLocation = new Point(mouseLocation.x - dragOffset.x, mouseLocation.y - dragOffset.y);
                mainPanel.updateDraggingItemLocation(ghostLocation);
                mainPanel.repaint();
            }
            
            @Override
            public void mouseMoved(MouseEvent e) {
                TrackView trackView = trackBoardPanel.getTrackViewAt(e.getPoint());
                if (trackView == null) return;
                
                Point trackPoint = SwingUtilities.convertPoint(trackBoardPanel, e.getPoint(), trackView);
                Rectangle dropZoneBounds = trackView.getDropZoneBounds();
                
                if (dropZoneBounds.contains(trackPoint)) {
                    // 更新悬停状态（考虑滚动偏移）
                    int cardIndex = getCardIndexAtPoint(trackView, dropZoneBounds, trackPoint);
                    if (cardIndex >= 0 && cardIndex < trackView.getTrackModel().getItems().size()) {
                        trackView.setHoveredCardIndex(cardIndex);
                    } else {
                        trackView.setHoveredCardIndex(-1);
                    }
                } else {
                    trackView.setHoveredCardIndex(-1);
                }
                trackView.repaint();
            }
        });
        
        // 添加鼠标滚轮事件处理滚动
        trackBoardPanel.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
            @Override
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                TrackView trackView = trackBoardPanel.getTrackViewAt(e.getPoint());
                if (trackView == null) return;
                
                Point trackPoint = SwingUtilities.convertPoint(trackBoardPanel, e.getPoint(), trackView);
                Rectangle dropZoneBounds = trackView.getDropZoneBounds();
                
                // 如果鼠标在投放框内，处理滚动
                if (dropZoneBounds.contains(trackPoint)) {
                    int scrollAmount = e.getWheelRotation() * 20; // 每次滚动20像素
                    trackView.scrollDropZone(scrollAmount);
                    e.consume();
                }
            }
        });
    }
    
    private int getCardIndexAtPoint(TrackView trackView, Rectangle dropZoneBounds, Point p) {
        int cardWidth = 120;
        int cardHeight = 70;
        int cardMargin = 5;
        int scrollOffset = trackView.getDropZoneScrollOffset();
        
        // 从上往下计算：从顶部开始
        int relYFromTop = p.y - dropZoneBounds.y - 10 + scrollOffset;
        if (relYFromTop < 0) return -1;
        
        // 计算卡牌索引（从顶部开始，包括正在拖拽的卡牌位置）
        int cardIndex = relYFromTop / (cardHeight + cardMargin);
        
        // 验证点击位置是否在卡牌范围内
        int cardY = dropZoneBounds.y + 10 + cardIndex * (cardHeight + cardMargin) - scrollOffset;
        if (p.y >= cardY && p.y <= cardY + cardHeight) {
            return cardIndex;
        }
        
        return -1;
    }
    
    private PlotItem getItemAtPoint(TrackView trackView, Rectangle dropZoneBounds, Point p) {
        // 计算实际显示的卡牌索引（考虑滚动偏移，从上往下，包括正在拖拽的卡牌位置）
        int displayIndex = getCardIndexAtPoint(trackView, dropZoneBounds, p);
        if (displayIndex < 0) return null;
        
        List<PlotItem> items = trackView.getTrackModel().getItems();
        
        // 直接返回对应索引的卡牌，不跳过正在拖拽的卡牌（因为原位置保留空白）
        if (displayIndex >= 0 && displayIndex < items.size()) {
            PlotItem item = items.get(displayIndex);
            // 如果点击的是正在拖拽的卡牌位置，返回null（不允许点击自己）
            if (draggingItem == item && sourceTrackView == trackView) {
                return null;
            }
            return item;
        }
        
        return null;
    }
    
    
    private void moveItemToTrack(PlotItem item, String sourceTrackId, String targetTrackId) {
        TrackModel sourceTrack = mainPanel.getBoardModel().getTrack(sourceTrackId);
        TrackModel targetTrack = mainPanel.getBoardModel().getTrack(targetTrackId);
        if (sourceTrack == null || targetTrack == null) return;
        
        // 从源轨道移除
        sourceTrack.removeItem(item);
        // 如果源轨道卡牌数为0，删除该标签纸
        if (sourceTrack.isEmpty()) {
            mainPanel.getBoardModel().removeTrack(sourceTrackId);
            trackBoardPanel.rebuildViews(mainPanel);
        }
        // 添加到目标轨道（添加到末尾，即最上方）
        targetTrack.addItem(item);
        
        mainPanel.repaint();
    }
    
    private void moveItemToTrackAtSlot(PlotItem item, String sourceTrackId, String targetTrackId, int targetSlotIndex) {
        TrackModel sourceTrack = mainPanel.getBoardModel().getTrack(sourceTrackId);
        TrackModel targetTrack = mainPanel.getBoardModel().getTrack(targetTrackId);
        if (sourceTrack == null || targetTrack == null) return;
        
        // 获取要移动的数据集类型
        DataSet dataSet = mainPanel.findDataSet(item.getDataSetId());
        if (dataSet == null) return;
        DataSet.DataType dataSetType = dataSet.getType();
        
        // 检查目标轨道中已有的数据类型
        DataSet.DataType trackType = null;
        for (PlotItem targetItem : targetTrack.getItems()) {
            DataSet targetDataSet = mainPanel.findDataSet(targetItem.getDataSetId());
            if (targetDataSet != null) {
                trackType = targetDataSet.getType();
                break;
            }
        }
        
        // 类型限制：TajimaD只能和TajimaD一起，FST/PI不能和TajimaD一起
        if (dataSetType == DataSet.DataType.TAJIMAD) {
            // TajimaD只能移动到TajimaD轨道
            if (trackType != null && trackType != DataSet.DataType.TAJIMAD) {
                // 类型不匹配，不允许移动
                return;
            }
        } else {
            // FST/PI不能移动到TajimaD轨道
            if (trackType == DataSet.DataType.TAJIMAD) {
                // 类型不匹配，不允许移动
                return;
            }
        }
        
        // 从源轨道移除
        sourceTrack.removeItem(item);
        // 如果源轨道卡牌数为0，删除该标签纸
        if (sourceTrack.isEmpty()) {
            mainPanel.getBoardModel().removeTrack(sourceTrackId);
            trackBoardPanel.rebuildViews(mainPanel);
        }
        
        // 添加到目标轨道的指定位置
        List<PlotItem> targetItems = targetTrack.getItems();
        // 计算目标索引（从上往下）
        int targetIndex = targetSlotIndex;
        if (targetIndex < 0) targetIndex = 0;
        if (targetIndex > targetItems.size()) targetIndex = targetItems.size();
        
        // 先添加到末尾，然后移动到目标位置
        targetTrack.addItem(item);
        targetTrack.moveItemToPosition(item, targetIndex);
        
        mainPanel.repaint();
    }
    
    private int getSlotIndexAtPoint(TrackView trackView, Rectangle dropZoneBounds, Point p) {
        int cardHeight = 70;
        int cardMargin = 5;
        int scrollOffset = trackView.getDropZoneScrollOffset();
        
        // 从上往下计算：从顶部开始
        int relYFromTop = p.y - dropZoneBounds.y - 10 + scrollOffset;
        if (relYFromTop < 0) return 0;
        
        // 计算轨道槽位索引（从顶部开始，包括正在拖拽的卡牌位置）
        int slotIndex = relYFromTop / (cardHeight + cardMargin);
        
        // 确保索引在有效范围内（包括正在拖拽的卡牌位置）
        List<PlotItem> items = trackView.getTrackModel().getItems();
        int maxSlots = items.size() + 1; // +1是为了允许插入到末尾
        if (slotIndex < 0) slotIndex = 0;
        if (slotIndex >= maxSlots) slotIndex = maxSlots - 1;
        
        return slotIndex;
    }
    
    private void createNewTrackWithItem(PlotItem item) {
        // 创建新轨道
        TrackModel newTrack = mainPanel.getBoardModel().createTrack();
        // 添加卡牌到新轨道
        newTrack.addItem(item);
        // 重建视图
        trackBoardPanel.rebuildViews(mainPanel);
        mainPanel.repaint();
    }
}
