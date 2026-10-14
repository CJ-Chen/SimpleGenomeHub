package simplegenomehub.population.fst;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * FST1JieMian3 - VS对比面板管理器
 * 负责管理VS面板中的群体拖拽对比功能
 */
public class FST1JieMian3 {
    private JPanel vsPanel;
    private JPanel vsGroupListPanel;
    private JPanel vsComparisonContainerPanel;
    private GroupRectangle draggingRect;
    private Point dragOffset;
    private JPanel leftBox;
    private JPanel rightBox;
    private List<JPanel> dynamicComparisonPanels;
    private Supplier<List<String>> groupNamesSupplier;
    private JPanel existingStrategiesPanel;        // "已有策略"内容面板（只读）
    private JScrollPane existingStrategiesScroll;  // "已有策略"滚动容器（可整体隐藏）
    private Runnable comparisonsChangedListener;   // 比较策略发生变化时的回调
    
    public FST1JieMian3(Supplier<List<String>> groupNamesSupplier) {
        this.groupNamesSupplier = groupNamesSupplier;
        this.dynamicComparisonPanels = new ArrayList<>();
        createVSPanel();
    }
    
    /**
     * 获取VS面板
     */
    public JPanel getPanel() {
        return vsPanel;
    }
    
    /**
     * 更新VS面板
     */
    public void updatePanel() {
        updateVSPanel();
    }
    
    /**
     * 更新VS面板的显示/隐藏状态
     */
    public void updateVisibility(int totalGroups) {
        vsPanel.setVisible(totalGroups >= 3);
        if (vsPanel.getParent() != null) {
            vsPanel.getParent().revalidate();
            vsPanel.getParent().repaint();
        }
        if (totalGroups >= 3) {
            updateVSPanel();
        }
    }

    /** 加载已有结果时始终显示 VS 面板（即使只有 2 个群体）。 */
    public void forceVisible() {
        if (vsPanel == null) return;
        vsPanel.setVisible(true);
        if (vsPanel.getParent() != null) {
            vsPanel.getParent().revalidate();
            vsPanel.getParent().repaint();
        }
        updateVSPanel();
    }

    /** 注册"比较策略发生变化"的回调（新增/删除比较、拖入/拖出类群等）。 */
    public void setOnComparisonsChanged(Runnable listener) {
        this.comparisonsChangedListener = listener;
    }

    private void notifyComparisonsChanged() {
        if (comparisonsChangedListener != null) {
            comparisonsChangedListener.run();
        }
    }
    
    /**
     * Comparison任务信息
     */
    public static class ComparisonTask {
        public String name;
        public List<String> leftGroups;
        public List<String> rightGroups;
        
        public ComparisonTask(String name, List<String> leftGroups, List<String> rightGroups) {
            this.name = name;
            this.leftGroups = leftGroups;
            this.rightGroups = rightGroups;
        }
    }
    
    /**
     * 获取所有有效的Comparison任务信息
     */
    public List<ComparisonTask> getValidComparisonTasks() {
        List<ComparisonTask> tasks = new ArrayList<>();
        if (vsComparisonContainerPanel == null) return tasks;
        
        for (Component comp : vsComparisonContainerPanel.getComponents()) {
            if (comp instanceof JPanel) {
                JPanel comparisonPanel = (JPanel) comp;
                
                String comparisonName = getComparisonName(comparisonPanel);
                if (comparisonName == null) continue;
                
                JPanel leftBoxPanel = findBoxPanel(comparisonPanel, "leftBox");
                JPanel rightBoxPanel = findBoxPanel(comparisonPanel, "rightBox");
                
                if (leftBoxPanel != null && rightBoxPanel != null) {
                    List<String> leftGroups = getGroupNamesFromBox(leftBoxPanel);
                    List<String> rightGroups = getGroupNamesFromBox(rightBoxPanel);
                    
                    if (!leftGroups.isEmpty() && !rightGroups.isEmpty()) {
                        tasks.add(new ComparisonTask(comparisonName, leftGroups, rightGroups));
                    }
                }
            }
        }
        
        return tasks;
    }
    
    /**
     * 从比对框中提取Group名称列表
     */
    private List<String> getGroupNamesFromBox(JPanel box) {
        List<String> groupNames = new ArrayList<>();
        for (Component comp : box.getComponents()) {
            if (comp instanceof GroupRectangle) {
                groupNames.add(((GroupRectangle) comp).getGroupName());
            }
        }
        return groupNames;
    }
    
    /**
     * 创建VS功能面板
     */
    private void createVSPanel() {
        vsPanel = new JPanel(new BorderLayout(5, 5)) {
            @Override
            public void paint(Graphics g) {
                super.paint(g);
                if (draggingRect != null && g != null) {
                    try {
                        Graphics2D g2 = (Graphics2D) g.create();
                        if (g2 != null) {
                            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
                            Point loc = draggingRect.getLocation();
                            if (loc != null) {
                                g2.translate(loc.x, loc.y);
                                drawGroupRectangle(g2, draggingRect);
                            }
                            g2.dispose();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        vsPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "VS",
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));
        vsPanel.setPreferredSize(new Dimension(500, 500));
        
        vsGroupListPanel = new JPanel();
        vsGroupListPanel.setLayout(new GridBagLayout());
        vsGroupListPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "Groups",
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));
        JScrollPane groupListScroll = new JScrollPane(vsGroupListPanel);
        groupListScroll.setPreferredSize(new Dimension(430, 120));
        
        vsComparisonContainerPanel = new JPanel();
        vsComparisonContainerPanel.setLayout(new BoxLayout(vsComparisonContainerPanel, BoxLayout.Y_AXIS));
        dynamicComparisonPanels = new ArrayList<>();
        
        JPanel firstComparisonPanel = createComparisonPanel(1);
        vsComparisonContainerPanel.add(firstComparisonPanel);
        
        JScrollPane comparisonScrollPane = new JScrollPane(vsComparisonContainerPanel);
        comparisonScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        comparisonScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        comparisonScrollPane.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "New comparisons",
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));
        
        // 已有策略（只读展示）：加载结果目录时用于显示 run.info.txt 中已记录的比较策略
        existingStrategiesPanel = new JPanel();
        existingStrategiesPanel.setLayout(new BoxLayout(existingStrategiesPanel, BoxLayout.Y_AXIS));
        existingStrategiesPanel.setBackground(simplegenomehub.population.fst.FstUiStyle.PANEL_BACKGROUND);
        JScrollPane existingScroll = new JScrollPane(existingStrategiesPanel);
        existingScroll.setPreferredSize(new Dimension(430, 90));
        existingScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        existingScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        existingScroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "Existing strategy",
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));
        existingScroll.setVisible(false);
        this.existingStrategiesScroll = existingScroll;
        
        JPanel comparisonAreaPanel = new JPanel(new BorderLayout(5, 5));
        comparisonAreaPanel.add(existingScroll, BorderLayout.NORTH);
        comparisonAreaPanel.add(comparisonScrollPane, BorderLayout.CENTER);
        
        // 铺满整栏宽度，与左侧/右侧的添加按钮保持同一形态
        JButton addComparisonButton = new JButton("Add a Comparison");
        addComparisonButton.addActionListener(e -> addComparisonPanel());
        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.add(addComparisonButton, BorderLayout.CENTER);
        
        vsPanel.add(groupListScroll, BorderLayout.NORTH);
        vsPanel.add(comparisonAreaPanel, BorderLayout.CENTER);
        vsPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        setupDropTargets(leftBox, rightBox);
    }
    
    /**
     * 设置"已有策略"（只读展示）。传入 null 或空列表时隐藏该区块。
     */
    public void setExistingComparisons(List<ComparisonTask> tasks) {
        if (existingStrategiesPanel == null || existingStrategiesScroll == null) return;
        existingStrategiesPanel.removeAll();
        if (tasks != null && !tasks.isEmpty()) {
            for (ComparisonTask task : tasks) {
                if (task == null) continue;
                String text = "<html><b>" + escapeHtml(task.name) + "</b>&nbsp;:&nbsp;"
                        + escapeHtml(joinGroups(task.leftGroups)) + "&nbsp;&rarr;&nbsp;"
                        + escapeHtml(joinGroups(task.rightGroups)) + "</html>";
                JLabel label = new JLabel(text);
                label.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
                existingStrategiesPanel.add(label);
            }
            existingStrategiesScroll.setVisible(true);
        } else {
            existingStrategiesScroll.setVisible(false);
        }
        existingStrategiesPanel.revalidate();
        existingStrategiesPanel.repaint();
        if (vsPanel != null) {
            vsPanel.revalidate();
            vsPanel.repaint();
        }
    }
    
    private static String joinGroups(List<String> groups) {
        if (groups == null || groups.isEmpty()) return "(none)";
        return String.join(" + ", groups);
    }
    
    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
    
    /**
     * 创建Comparison面板
     */
    private JPanel createComparisonPanel(int comparisonNumber) {
        JPanel comparisonPanel = new JPanel(new BorderLayout(10, 5));
        comparisonPanel.setBorder(BorderFactory.createLineBorder(simplegenomehub.population.fst.FstUiStyle.CARD_BORDER, 1, true));
        
        // 顶部：可双击改名的比较名称（身份与显示来源）
        String baseName = "Comparison" + comparisonNumber;
        comparisonPanel.putClientProperty("__baseName__", baseName);
        comparisonPanel.putClientProperty("__customName__", Boolean.FALSE);
        JLabel nameHeader = new JLabel(baseName);
        nameHeader.putClientProperty("__compareNameHeader__", Boolean.TRUE);
        nameHeader.setFont(simplegenomehub.population.fst.FstUiStyle.font(0, Font.BOLD));
        nameHeader.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        nameHeader.setToolTipText("Comparison name: generated from the left/right groups until you double-click to rename it");
        FST1JieMian2.installLabelRenameOnDoubleClick(nameHeader,
            newName -> {
                String err = FST1JieMian2.validateDisplayName(newName);
                if (err != null) return err;
                // 不能与其它比较同名
                for (Component comp : vsComparisonContainerPanel.getComponents()) {
                    if (!(comp instanceof JPanel)) continue;
                    JPanel other = (JPanel) comp;
                    if (other == comparisonPanel) continue;
                    if (newName.equals(getComparisonName(other))) {
                        return "Comparison names must be unique.";
                    }
                }
                return null;
            },
            (oldName, newName) -> {
                comparisonPanel.putClientProperty("__customName__", Boolean.TRUE);
                vsComparisonContainerPanel.revalidate();
                vsComparisonContainerPanel.repaint();
            });
        comparisonPanel.add(nameHeader, BorderLayout.NORTH);
        
        JPanel leftBoxPanel = new JPanel(new BorderLayout());
        leftBoxPanel.setPreferredSize(new Dimension(120, 70));
        leftBoxPanel.setBackground(Color.WHITE);
        leftBoxPanel.setName("leftBox");
        
        JPanel rightBoxPanel = new JPanel(new BorderLayout());
        rightBoxPanel.setPreferredSize(new Dimension(120, 70));
        rightBoxPanel.setBackground(Color.WHITE);
        rightBoxPanel.setName("rightBox");
        
        JScrollPane leftScrollPane = new JScrollPane(leftBoxPanel);
        leftScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        leftScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        leftScrollPane.setBorder(null);
        leftScrollPane.setPreferredSize(new Dimension(120, 70));
        
        JScrollPane rightScrollPane = new JScrollPane(rightBoxPanel);
        rightScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        rightScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        rightScrollPane.setBorder(null);
        rightScrollPane.setPreferredSize(new Dimension(120, 70));
        
        JPanel vsContainerPanel = new JPanel(new BorderLayout());
        JLabel vsLabel = new JLabel("VS", JLabel.CENTER);
        vsLabel.setFont(simplegenomehub.population.fst.FstUiStyle.font(8, Font.BOLD));
        vsContainerPanel.add(vsLabel, BorderLayout.CENTER);
        
        if (comparisonNumber > 1) {
            // 与其它按钮同一形态（去掉硬编码尺寸，交给统一按钮样式）
            JButton removeButton = new JButton("-");
            removeButton.setToolTipText("Remove this comparison");
            removeButton.setAlignmentX(Component.LEFT_ALIGNMENT);
            Dimension preferred = removeButton.getPreferredSize();
            removeButton.setPreferredSize(new Dimension(40, Math.max(30, preferred.height)));
            removeButton.addActionListener(e -> removeComparisonPanel(comparisonPanel));
            vsContainerPanel.add(removeButton, BorderLayout.SOUTH);
        }
        
        comparisonPanel.add(leftScrollPane, BorderLayout.WEST);
        comparisonPanel.add(vsContainerPanel, BorderLayout.CENTER);
        comparisonPanel.add(rightScrollPane, BorderLayout.EAST);
        
        if (comparisonNumber == 1) {
            leftBox = leftBoxPanel;
            rightBox = rightBoxPanel;
        }
        
        setupDropTargets(leftBoxPanel, rightBoxPanel);
        
        return comparisonPanel;
    }
    
    /**
     * 查找比较面板顶部的名称标签
     */
    private JLabel findCompareNameHeader(JPanel comparisonPanel) {
        if (comparisonPanel == null) return null;
        for (Component comp : comparisonPanel.getComponents()) {
            if (comp instanceof JLabel && Boolean.TRUE.equals(((JLabel) comp).getClientProperty("__compareNameHeader__"))) {
                return (JLabel) comp;
            }
        }
        return null;
    }
    
    /**
     * 读取比较面板的名称
     */
    private String getComparisonName(JPanel comparisonPanel) {
        JLabel header = findCompareNameHeader(comparisonPanel);
        return header != null ? header.getText() : null;
    }
    
    /**
     * 设置比较面板的名称（显示在顶部标签）
     */
    private void setComparisonTitle(JPanel comparisonPanel, String title) {
        JLabel header = findCompareNameHeader(comparisonPanel);
        if (header != null && title != null && !title.isEmpty()) {
            header.setText(title);
            comparisonPanel.revalidate();
            comparisonPanel.repaint();
        }
    }
    
    /**
     * 刷新所有比较面板的自动名称（未被手动自定义过的面板，
     * 按左、右框内的类群名生成 "A_B_vs_C_D" 样式名称）
     */
    public void refreshAutoCompareTitles() {
        if (vsComparisonContainerPanel == null) return;
        for (Component comp : vsComparisonContainerPanel.getComponents()) {
            if (!(comp instanceof JPanel)) continue;
            JPanel panel = (JPanel) comp;
            if (Boolean.TRUE.equals(panel.getClientProperty("__customName__"))) continue;
            
            JPanel leftBox = findBoxPanel(panel, "leftBox");
            JPanel rightBox = findBoxPanel(panel, "rightBox");
            List<String> left = getGroupNamesFromBox(leftBox);
            List<String> right = getGroupNamesFromBox(rightBox);
            
            String base = (String) panel.getClientProperty("__baseName__");
            String title;
            if (!left.isEmpty() && !right.isEmpty()) {
                title = String.join("_", left) + "_vs_" + String.join("_", right);
            } else {
                title = base != null ? base : "Comparison";
            }
            setComparisonTitle(panel, title);
        }
        notifyComparisonsChanged();
    }
    
    /**
     * 在比较面板内查找指定名称的类群框
     */
    private JPanel findBoxPanel(JPanel comparisonPanel, String boxName) {
        if (comparisonPanel == null) return null;
        for (Component subComp : comparisonPanel.getComponents()) {
            if (subComp instanceof JScrollPane) {
                Component view = ((JScrollPane) subComp).getViewport().getView();
                if (view instanceof JPanel) {
                    JPanel boxPanel = (JPanel) view;
                    if (boxName.equals(boxPanel.getName())) {
                        return boxPanel;
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * 类群改名后同步：更新所有比较框内对应类群矩形的名称，
     * 并刷新列表与未自定义的比较名称
     *
     * @param oldName 旧类群名
     * @param newName 新类群名
     */
    public void renameGroup(String oldName, String newName) {
        if (oldName == null || newName == null) return;
        if (oldName.equals(newName)) return;
        if (vsComparisonContainerPanel == null) return;
        
        for (Component comp : vsComparisonContainerPanel.getComponents()) {
            if (!(comp instanceof JPanel)) continue;
            JPanel panel = (JPanel) comp;
            renameGroupInBox(findBoxPanel(panel, "leftBox"), oldName, newName);
            renameGroupInBox(findBoxPanel(panel, "rightBox"), oldName, newName);
        }
        updateVSPanel();
        refreshAutoCompareTitles();
    }
    
    /**
     * 在单个类群框内把旧名矩形改名为新名（若新名已存在则移除旧矩形）
     */
    private void renameGroupInBox(JPanel box, String oldName, String newName) {
        if (box == null) return;
        List<Component> toRemove = new ArrayList<>();
        for (Component comp : box.getComponents()) {
            if (!(comp instanceof GroupRectangle)) continue;
            GroupRectangle rect = (GroupRectangle) comp;
            if (!oldName.equals(rect.getGroupName())) continue;
            boolean alreadyExists = false;
            for (Component other : box.getComponents()) {
                if (other instanceof GroupRectangle && other != rect
                        && newName.equals(((GroupRectangle) other).getGroupName())) {
                    alreadyExists = true;
                    break;
                }
            }
            if (alreadyExists) {
                toRemove.add(rect);
                int i = -1;
                for (int j = 0; j < box.getComponents().length; j++) {
                    if (box.getComponent(j) == rect) { i = j; break; }
                }
                if (i >= 0 && i + 1 < box.getComponentCount() && box.getComponent(i + 1) instanceof Box.Filler) {
                    toRemove.add(box.getComponent(i + 1));
                }
            } else {
                rect.setGroupName(newName);
            }
        }
        for (Component compToRemove : toRemove) {
            box.remove(compToRemove);
        }
        if (!toRemove.isEmpty()) {
            box.revalidate();
            box.repaint();
        }
    }
    
    /**
     * 添加新的Comparison面板
     */
    private void addComparisonPanel() {
        // 编号避免与现存比较名冲突（含自动名与自定义名）
        int comparisonNumber = dynamicComparisonPanels.size() + 2;
        List<String> usedNames = new ArrayList<>();
        if (vsComparisonContainerPanel != null) {
            for (Component comp : vsComparisonContainerPanel.getComponents()) {
                if (comp instanceof JPanel) {
                    String name = getComparisonName((JPanel) comp);
                    if (name != null) usedNames.add(name);
                }
            }
        }
        while (usedNames.contains("Comparison" + comparisonNumber)) {
            comparisonNumber++;
        }
        
        JPanel newComparisonPanel = createComparisonPanel(comparisonNumber);
        dynamicComparisonPanels.add(newComparisonPanel);
        
        int addButtonIndex = findButtonIndex(vsComparisonContainerPanel, "Add a Comparison");
        if (addButtonIndex >= 0) {
            vsComparisonContainerPanel.add(Box.createVerticalStrut(5), addButtonIndex);
            vsComparisonContainerPanel.add(newComparisonPanel, addButtonIndex + 1);
        } else {
            vsComparisonContainerPanel.add(Box.createVerticalStrut(5));
            vsComparisonContainerPanel.add(newComparisonPanel);
        }
        
        vsComparisonContainerPanel.revalidate();
        vsComparisonContainerPanel.repaint();
        notifyComparisonsChanged();
    }
    
    /**
     * 查找按钮索引
     */
    private int findButtonIndex(Container container, String buttonText) {
        Component[] components = container.getComponents();
        for (int i = 0; i < components.length; i++) {
            if (components[i] instanceof JButton) {
                JButton btn = (JButton) components[i];
                if (buttonText.equals(btn.getText())) {
                    return i;
                }
            }
        }
        return -1;
    }
    
    /**
     * 移除面板及其前面的间距组件
     */
    private void removePanelWithSpacing(Container container, Component panelToRemove) {
        Component[] components = container.getComponents();
        int index = -1;
        for (int i = 0; i < components.length; i++) {
            if (components[i] == panelToRemove) {
                index = i;
                break;
            }
        }
        if (index >= 0) {
            container.remove(index);
            if (index > 0 && container.getComponent(index - 1) instanceof Box.Filler) {
                container.remove(index - 1);
            }
        }
    }
    
    /**
     * 删除Comparison面板
     */
    private void removeComparisonPanel(JPanel panelToRemove) {
        if (dynamicComparisonPanels.remove(panelToRemove)) {
            removePanelWithSpacing(vsComparisonContainerPanel, panelToRemove);
            vsComparisonContainerPanel.revalidate();
            vsComparisonContainerPanel.repaint();
        }
        notifyComparisonsChanged();
    }
    
    /**
     * 更新VS面板中的群体列表
     */
    private void updateVSPanel() {
        if (vsGroupListPanel == null) return;
        
        List<String> existingGroupNames = groupNamesSupplier.get();
        
        vsGroupListPanel.removeAll();
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        
        for (int i = 0; i < existingGroupNames.size(); i++) {
            String groupName = existingGroupNames.get(i);
            
            GroupRectangle groupRect = new GroupRectangle(groupName);
            groupRect.setPreferredSize(new Dimension(100, 30));
            groupRect.setMinimumSize(new Dimension(100, 30));
            groupRect.setMaximumSize(new Dimension(100, 30));
            
            setupDragSource(groupRect);
            
            gbc.gridx = i % 4;
            gbc.gridy = i / 4;
            gbc.gridwidth = 1;
            gbc.weightx = 0;
            gbc.weighty = 0;
            
            vsGroupListPanel.add(groupRect, gbc);
        }
        
        vsGroupListPanel.revalidate();
        vsGroupListPanel.repaint();
        
        cleanupComparisonBoxes(existingGroupNames);
        notifyComparisonsChanged();
    }
    
    /**
     * 清理比对框内不存在的Group矩形
     */
    private void findAndCleanupBoxes(Container container, List<String> existingGroupNames) {
        for (Component comp : container.getComponents()) {
            if (comp instanceof JPanel) {
                JPanel comparisonPanel = (JPanel) comp;
                for (Component subComp : comparisonPanel.getComponents()) {
                    if (subComp instanceof JScrollPane) {
                        Component view = ((JScrollPane) subComp).getViewport().getView();
                        if (view instanceof JPanel) {
                            JPanel boxPanel = (JPanel) view;
                            String boxName = boxPanel.getName();
                            if ("leftBox".equals(boxName) || "rightBox".equals(boxName)) {
                                cleanupBoxPanel(boxPanel, existingGroupNames);
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 清理单个比对框面板
     */
    private void cleanupBoxPanel(JPanel boxPanel, List<String> existingGroupNames) {
        Component[] boxComponents = boxPanel.getComponents();
        List<Component> toRemove = new ArrayList<>();
        
        for (int i = 0; i < boxComponents.length; i++) {
            Component boxComp = boxComponents[i];
            if (boxComp instanceof GroupRectangle) {
                GroupRectangle rect = (GroupRectangle) boxComp;
                if (!existingGroupNames.contains(rect.getGroupName())) {
                    toRemove.add(boxComp);
                    if (i + 1 < boxComponents.length && boxComponents[i + 1] instanceof Box.Filler) {
                        toRemove.add(boxComponents[i + 1]);
                    }
                }
            }
        }
        
        for (Component compToRemove : toRemove) {
            boxPanel.remove(compToRemove);
        }
        
        if (!toRemove.isEmpty()) {
            boxPanel.revalidate();
            boxPanel.repaint();
        }
    }
    
    /**
     * 清理比对框内不存在的Group矩形
     */
    private void cleanupComparisonBoxes(List<String> existingGroupNames) {
        if (vsComparisonContainerPanel == null) return;
        findAndCleanupBoxes(vsComparisonContainerPanel, existingGroupNames);
    }
    
    /**
     * 设置拖拽源
     */
    private void setupDragSource(GroupRectangle groupRect) {
        groupRect.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                draggingRect = new GroupRectangle(groupRect.getGroupName());
                draggingRect.setSize(groupRect.getWidth(), groupRect.getHeight());
                draggingRect.setPreferredSize(new Dimension(groupRect.getWidth(), groupRect.getHeight()));
                draggingRect.setOpaque(true);
                Color bgColor = groupRect.getBackground();
                draggingRect.setBackground(new Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), 180));
                
                Point rectLocation = SwingUtilities.convertPoint(groupRect, 0, 0, vsPanel);
                Point mouseLocation = SwingUtilities.convertPoint((Component)e.getSource(), e.getPoint(), vsPanel);
                dragOffset = new Point(mouseLocation.x - rectLocation.x, mouseLocation.y - rectLocation.y);
                
                draggingRect.setLocation(mouseLocation.x - dragOffset.x, mouseLocation.y - dragOffset.y);
                
                groupRect.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                vsPanel.repaint();
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                if (draggingRect != null) {
                    Point mouseLocation = SwingUtilities.convertPoint((Component)e.getSource(), e.getPoint(), vsPanel);
                    Component target = SwingUtilities.getDeepestComponentAt(vsPanel, mouseLocation.x, mouseLocation.y);
                    
                    JPanel targetBox = findTargetBox(target);
                    if (targetBox != null) {
                        if (groupRect.getParent() == vsGroupListPanel) {
                            addGroupToBox(targetBox, draggingRect.getGroupName());
                        } 
                        else if (groupRect.getParent() != targetBox) {
                            removeGroupFromBox((JPanel)groupRect.getParent());
                            addGroupToBox(targetBox, draggingRect.getGroupName());
                        }
                    } else {
                        if (groupRect.getParent() != vsGroupListPanel) {
                            removeGroupFromBox((JPanel)groupRect.getParent());
                        }
                    }
                    
                    draggingRect = null;
                    dragOffset = null;
                    groupRect.setCursor(Cursor.getDefaultCursor());
                    vsPanel.repaint();
                }
            }
        });
        
        groupRect.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (draggingRect != null) {
                    Point mouseLocation = SwingUtilities.convertPoint((Component)e.getSource(), e.getPoint(), vsPanel);
                    draggingRect.setLocation(mouseLocation.x - dragOffset.x, mouseLocation.y - dragOffset.y);
                    vsPanel.repaint();
                }
            }
        });
    }
    
    /**
     * 查找目标比对框
     */
    private JPanel findTargetBox(Component comp) {
        while (comp != null && comp != vsPanel) {
            if (comp instanceof JPanel) {
                String name = ((JPanel) comp).getName();
                if ("leftBox".equals(name) || "rightBox".equals(name)) {
                    return (JPanel) comp;
                }
            }
            comp = comp.getParent();
        }
        return null;
    }
    
    /**
     * 创建拖放目标监听器
     */
    private DropTargetListener createDropTargetListener(JPanel targetBox) {
        return new DropTargetListener() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                if (dtde.isDataFlavorSupported(GroupTransferable.GROUP_FLAVOR)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                } else {
                    dtde.rejectDrag();
                }
            }
            
            @Override
            public void dragOver(DropTargetDragEvent dtde) {
                if (dtde.isDataFlavorSupported(GroupTransferable.GROUP_FLAVOR)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                } else {
                    dtde.rejectDrag();
                }
            }
            
            @Override
            public void dropActionChanged(DropTargetDragEvent dtde) {}
            
            @Override
            public void dragExit(DropTargetEvent dte) {}
            
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    Transferable transferable = dtde.getTransferable();
                    if (transferable.isDataFlavorSupported(GroupTransferable.GROUP_FLAVOR)) {
                        String groupName = (String) transferable.getTransferData(GroupTransferable.GROUP_FLAVOR);
                        addGroupToBox(targetBox, groupName);
                        dtde.dropComplete(true);
                    } else {
                        dtde.dropComplete(false);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    dtde.dropComplete(false);
                }
            }
        };
    }
    
    /**
     * 设置拖放目标
     */
    private void setupDropTargets(JPanel leftBox, JPanel rightBox) {
        new DropTarget(leftBox, createDropTargetListener(leftBox));
        new DropTarget(rightBox, createDropTargetListener(rightBox));
    }
    
    /**
     * 在比对框内添加群体矩形
     */
    private void addGroupToBox(JPanel box, String groupName) {
        for (Component comp : box.getComponents()) {
            if (comp instanceof GroupRectangle && ((GroupRectangle) comp).getGroupName().equals(groupName)) {
                return;
            }
        }
        
        if (box.getLayout() instanceof BorderLayout) {
            box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        }
        
        GroupRectangle groupRect = new GroupRectangle(groupName);
        groupRect.setPreferredSize(new Dimension(100, 30));
        groupRect.setMaximumSize(new Dimension(100, 30));
        setupDragSource(groupRect);
        
        box.add(groupRect);
        box.add(Box.createVerticalStrut(5));
        box.revalidate();
        box.repaint();
        
        // 拖入类群后刷新未自定义的比较名称
        refreshAutoCompareTitles();
    }
    
    /**
     * 从比对框内移除群体矩形
     */
    private void removeGroupFromBox(JPanel box) {
        Component[] components = box.getComponents();
        for (int i = components.length - 1; i >= 0; i--) {
            if (components[i] instanceof GroupRectangle) {
                box.remove(i);
                if (i > 0 && components[i - 1] instanceof Box.Filler) {
                    box.remove(i - 1);
                }
                box.revalidate();
                box.repaint();
                // 拖出类群后刷新未自定义的比较名称
                refreshAutoCompareTitles();
                return;
            }
        }
    }
    
    /**
     * 绘制群体矩形（用于拖拽时绘制）
     */
    private void drawGroupRectangle(Graphics2D g2, GroupRectangle rect) {
        if (g2 == null || rect == null) return;
        
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            
            Color bgColor = rect.getBackground();
            if (bgColor != null) {
                g2.setColor(bgColor);
                g2.fillRoundRect(0, 0, rect.getWidth(), rect.getHeight(), 4, 4);
            }
            
            g2.setColor(simplegenomehub.population.fst.FstUiStyle.CARD_BORDER);
            g2.setStroke(new BasicStroke(1.0f));
            g2.drawRoundRect(0, 0, rect.getWidth() - 1, rect.getHeight() - 1, 4, 4);
            
            Color fgColor = rect.getForeground();
            if (fgColor != null) {
                g2.setColor(fgColor);
                Font font = rect.getFont();
                if (font != null) {
                    g2.setFont(font);
                    FontMetrics fm = g2.getFontMetrics();
                    String groupName = rect.getGroupName();
                    if (groupName != null) {
                        int textWidth = fm.stringWidth(groupName);
                        int textHeight = fm.getHeight();
                        int x = (rect.getWidth() - textWidth) / 2;
                        int y = (rect.getHeight() + textHeight) / 2 - fm.getDescent();
                        g2.drawString(groupName, x, y);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 群体矩形组件
     */
    private static class GroupRectangle extends JPanel {
        /** 卡牌底色：不依赖组件背景，避免被统一样式覆盖。 */
        private static final Color CHIP_BACKGROUND = simplegenomehub.population.fst.FstUiStyle.TAB_BACKGROUND;
        private static final Color CHIP_BORDER = simplegenomehub.population.fst.FstUiStyle.AXIS_LINE;
        private String groupName;
        
        public GroupRectangle(String groupName) {
            this.groupName = groupName;
            // 自绘底色：设为非不透明，组件背景色变化不会影响观感
            setOpaque(false);
            setForeground(simplegenomehub.population.fst.FstUiStyle.TITLE_BLUE);
            setBorder(null);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Drag me into the left or right box to build a comparison");
            setFont(simplegenomehub.population.fst.FstUiStyle.font(0, Font.BOLD));
            setBackground(CHIP_BACKGROUND);
        }
        
        public String getGroupName() {
            return groupName;
        }
        
        /**
         * 类群改名时同步更新矩形内显示的名称
         */
        public void setGroupName(String groupName) {
            this.groupName = groupName;
            repaint();
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            if (g == null) return;
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // 卡牌底色 + 圆角描边：让用户一眼看出这是可以拖拽的类群卡牌
            g2.setColor(CHIP_BACKGROUND);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
            g2.setColor(CHIP_BORDER);
            g2.setStroke(new BasicStroke(1.0f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);

            // 左侧握把，提示可拖拽
            g2.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_SECONDARY);
            int gripX = 7;
            int gripTop = getHeight() / 2 - 4;
            for (int i = 0; i < 3; i++) {
                g2.fillRect(gripX, gripTop + i * 4, 6, 2);
            }

            g2.setColor(getForeground());
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int textWidth = fm.stringWidth(groupName);
            int textHeight = fm.getHeight();
            int x = Math.max(gripX + 12, (getWidth() - textWidth) / 2);
            int y = (getHeight() + textHeight) / 2 - fm.getDescent();
            g2.drawString(groupName, x, y);
        }
    }
    
    /**
     * 群体传输对象
     */
    private static class GroupTransferable implements Transferable {
        public static final DataFlavor GROUP_FLAVOR = new DataFlavor(String.class, "Group Name");
        private String groupName;
        
        public GroupTransferable(String groupName) {
            this.groupName = groupName;
        }
        
        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{GROUP_FLAVOR};
        }
        
        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return flavor.equals(GROUP_FLAVOR);
        }
        
        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            if (flavor.equals(GROUP_FLAVOR)) {
                return groupName;
            }
            throw new UnsupportedFlavorException(flavor);
        }
    }
}
