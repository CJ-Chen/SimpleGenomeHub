package simplegenomehub.population.fst.chart.controller;

import simplegenomehub.population.fst.chart.FSTTrackChartPanel;
import simplegenomehub.population.fst.chart.model.PlotItem;
import simplegenomehub.population.fst.chart.view.TrackView;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * 内联Top百分比编辑控制器：处理投放框内卡牌的Top百分比内联编辑
 */
public class InlineTopPercentController {
    private final FSTTrackChartPanel mainPanel;
    
    // 内联文本框编辑状态
    private PlotItem editingTopPercentItem = null;
    private TrackView editingTopPercentTrack = null;
    private JTextField inlineTextField = null;
    
    public InlineTopPercentController(FSTTrackChartPanel mainPanel) {
        this.mainPanel = mainPanel;
    }
    
    /**
     * 检查是否正在编辑Top百分比
     */
    public boolean isEditingTopPercent() {
        return inlineTextField != null && editingTopPercentItem != null;
    }
    
    /**
     * 检查是否正在编辑指定的PlotItem
     */
    public boolean isEditingItem(PlotItem item, TrackView trackView) {
        return editingTopPercentItem == item && editingTopPercentTrack == trackView;
    }
    
    /**
     * 开始内联编辑Top百分比
     */
    public void startInlineTopPercentEditing(PlotItem item, TrackView trackView, Rectangle textFieldBounds) {
        // 如果已经有正在编辑的，先结束它
        if (inlineTextField != null) {
            finishInlineTopPercentEditing();
        }
        
        editingTopPercentItem = item;
        editingTopPercentTrack = trackView;
        
        // 创建内联文本框（使用原始值，不格式化，让用户看到完整输入）
        inlineTextField = new JTextField(String.valueOf(item.getTopPercent()));
        inlineTextField.setFont(simplegenomehub.population.fst.FstUiStyle.sans(11, Font.PLAIN));
        inlineTextField.setBorder(BorderFactory.createLineBorder(Color.BLUE, 1));
        inlineTextField.setOpaque(true);
        inlineTextField.setBackground(Color.WHITE);
        
        // 文本框位置相对于TrackView
        inlineTextField.setBounds(textFieldBounds.x, textFieldBounds.y, textFieldBounds.width, textFieldBounds.height);
        
        // 添加到TrackView（使用绝对定位）
        trackView.setLayout(null);
        trackView.add(inlineTextField);
        inlineTextField.setBounds(textFieldBounds.x, textFieldBounds.y, textFieldBounds.width, textFieldBounds.height);
        trackView.setLayout(null); // TrackView应该使用null布局
        
        SwingUtilities.invokeLater(() -> {
            inlineTextField.requestFocus();
            inlineTextField.selectAll();
        });
        
        // 添加焦点监听器，失去焦点时结束编辑
        inlineTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                // 延迟执行，避免与其他事件冲突
                // 检查焦点是否转移到了其他组件（而不是因为窗口失去焦点）
                Component opposite = e.getOppositeComponent();
                if (opposite == null || !SwingUtilities.isDescendingFrom(opposite, trackView)) {
                    SwingUtilities.invokeLater(() -> {
                        // 再次检查，避免重复执行
                        if (inlineTextField != null && inlineTextField == e.getComponent()) {
                            finishInlineTopPercentEditing();
                        }
                    });
                }
            }
        });
        
        // 添加回车键监听器
        inlineTextField.addActionListener(e -> finishInlineTopPercentEditing());
        
        // 添加ESC键监听器，取消编辑
        inlineTextField.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) {
                    // 取消编辑，恢复原值
                    finishInlineTopPercentEditing();
                }
            }
        });
        
        trackView.revalidate();
        trackView.repaint();
    }
    
    /**
     * 结束内联编辑Top百分比
     */
    public void finishInlineTopPercentEditing() {
        if (inlineTextField == null || editingTopPercentItem == null) return;
        
        // 保存编辑的TrackView引用
        TrackView trackView = editingTopPercentTrack;
        
        try {
            String text = inlineTextField.getText().trim();
            if (!text.isEmpty()) {
                double newPercent = Double.parseDouble(text);
                if (newPercent > 0 && newPercent <= 1.0) {
                    editingTopPercentItem.setTopPercent(newPercent);
                    if (editingTopPercentItem.isTopEnabled()) {
                        // 如果启用，重新计算top bits
                        mainPanel.recalculateTopBitsForItem(editingTopPercentItem);
                    }
                }
            }
        } catch (NumberFormatException e) {
            // 忽略无效输入，保持原值
        }
        
        // 移除文本框（从TrackView移除）
        if (trackView != null && inlineTextField.getParent() == trackView) {
            trackView.remove(inlineTextField);
            trackView.revalidate();
            trackView.repaint();
        } else if (inlineTextField.getParent() != null) {
            // 如果不在TrackView中，从当前父组件移除
            inlineTextField.getParent().remove(inlineTextField);
            if (inlineTextField.getParent() != null) {
                inlineTextField.getParent().revalidate();
                inlineTextField.getParent().repaint();
            }
        }
        
        inlineTextField = null;
        editingTopPercentItem = null;
        editingTopPercentTrack = null;
        
        // 重绘主面板
        mainPanel.repaint();
    }
}
