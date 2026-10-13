package simplegenomehub.population.fst;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * FST1JieMian1 - 主界面窗口
 * 样本分组对话框，用于选择群体样本
 * <p>
 * Ported from FSTdraw; SimpleGenomeHub hosts it as a modeless dialog and receives finished run
 * folders through {@link FstResultHandler} instead of the original chart window.
 */
public class FST1JieMian1 extends JDialog {

    /** Notified when results have been written; null falls back to a plain message dialog. */
    private FstResultHandler resultHandler;

    /** Optional run label (species and version) recorded in run.info.txt. */
    private String runSpeciesName;
    private String runSpeciesVersion;

    public void setResultHandler(FstResultHandler resultHandler) {
        this.resultHandler = resultHandler;
    }

    /** Records which SimpleGenomeHub genome unit this run belongs to. */
    public void setRunSpeciesIdentity(String speciesName, String speciesVersion) {
        this.runSpeciesName = speciesName;
        this.runSpeciesVersion = speciesVersion;
    }

    /** True when results should be displayed ordered by the number in the chromosome name. */
    public boolean isSortChromosomesByNumber() {
        if (loadMode) {
            // 加载模式已移除排序勾选框，沿用原运行记录里的设置
            return loadSortChromosomes;
        }
        return sortChromosomesCheckBox != null && sortChromosomesCheckBox.isSelected();
    }

    /** Copies the owning genome identity into a run info record when known. */
    private void applySpeciesIdentity(FSTRunInfo.Info info) {
        if (info == null) return;
        if (runSpeciesName != null) info.speciesName = runSpeciesName;
        if (runSpeciesVersion != null) info.speciesVersion = runSpeciesVersion;
    }

    /** Reports a finished run folder, falling back to a message when no handler is installed. */
    private void notifyResultsReady(File parameterDirectory) {
        if (resultHandler != null) {
            resultHandler.onResultsReady(parameterDirectory);
            return;
        }
        JOptionPane.showMessageDialog(this,
            "FST results were written to:\n" + parameterDirectory.getAbsolutePath(),
            "Calculation Finished", JOptionPane.INFORMATION_MESSAGE);
    }

    private JList<String> sampleList;
    private DefaultListModel<String> listModel;
    private JTextArea pop1Area;
    private JTextArea pop2Area;
    private JButton startButton;
    private JButton runNewComparisonsButton;  // 加载模式：运行新增的比较策略
    private JTextField windowSizeField;
    private JTextField windowStepField;
    private JCheckBox sortChromosomesCheckBox;  // 展示时按染色体编号排序
    private JLabel compareCaptionLabel; // 底部"Compare:"说明文字
    private JLabel twoGroupCompareLabel; // 只有两个类群时的Compare名称标签（双击改名）
    private boolean twoGroupCompareCustomized = false; // Compare名是否已被手动自定义
    private String twoGroupCompareName = "";           // 手动自定义的Compare名
    
    // 存储动态添加的样本框面板
    private List<JPanel> dynamicPopPanels;
    private JPanel rightPanel;  // 右侧面板，用于动态添加组件
    private List<JTextArea> allPopAreas;  // 存储所有群体文本框的引用
    private FST1JieMian3 vsPanelManager;  // VS功能面板管理器
    private JPanel leftButtonPanel;  // 左侧按钮面板，用于动态更新按钮
    
    private List<String> allSamples;
    // 本次运行要忽略的样本（在"样本不可用"弹窗中确认忽略后填充）
    private Set<String> unavailableSamples = new LinkedHashSet<>();
    private String vcfFilePath;
    private String genomeFilePath;  // 基因组文件路径（可能为null）
    private String gff3FilePath;  // GFF3文件路径（可能为null）
    private int windowSize;  // 窗口大小
    private int stepSize;    // 步长
    
    // ---- 加载结果目录模式（从 run.info.txt 还原群体/比较策略）----
    private boolean loadMode = false;
    private File loadOutputFolder;                      // 结果写回的目标文件夹
    private List<FSTRunInfo.Group> loadGroups;          // 已有群体（含成员，只读）
    private List<FSTRunInfo.Comparison> loadComparisons; // 已有比较策略（只读）
    private boolean loadSortChromosomes = false;        // 已有运行的染色体排序设置
    
    public FST1JieMian1(Window parent, List<String> samples, String vcfFilePath, String genomeFilePath, String gff3FilePath, int windowSize, int stepSize) {
        // Modeless: the SimpleGenomeHub main window stays usable while groups are configured.
        super(parent, "Sample Grouping", ModalityType.MODELESS);
        this.allSamples = samples;
        this.vcfFilePath = vcfFilePath;
        this.genomeFilePath = genomeFilePath;
        this.gff3FilePath = gff3FilePath;
        this.windowSize = windowSize;
        this.stepSize = stepSize;
        
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        initializeUI();
    }
    
    /**
     * 加载模式构造：从已有结果目录的 run.info.txt 还原"群体 + 比较策略"界面。
     * 群体与成员只读；可在下方追加新的比较策略，点"确定"后只运行新增策略并写回同一文件夹。
     *
     * @param runInfo      run.info.txt 读出的信息
     * @param outputFolder 结果写回的目标文件夹（即加载进来的那个文件夹）
     */
    public FST1JieMian1(Window parent, FSTRunInfo.Info runInfo, File outputFolder) {
        super(parent, "Groups & Comparison Strategy", ModalityType.MODELESS);
        this.loadMode = true;
        this.loadOutputFolder = outputFolder;
        this.loadGroups = (runInfo != null && runInfo.groups != null) ? runInfo.groups : new ArrayList<>();
        this.loadComparisons = (runInfo != null && runInfo.comparisons != null) ? runInfo.comparisons : new ArrayList<>();
        this.allSamples = new ArrayList<>();
        this.vcfFilePath = runInfo != null ? runInfo.vcf : null;
        this.genomeFilePath = runInfo != null ? runInfo.genome : null;
        this.gff3FilePath = runInfo != null ? runInfo.gff3 : null;
        this.windowSize = (runInfo != null && runInfo.windowSize > 0) ? runInfo.windowSize : 1000000;
        this.stepSize = (runInfo != null && runInfo.stepSize > 0) ? runInfo.stepSize : 100000;
        this.loadSortChromosomes = runInfo != null && runInfo.sortChromosomes;
        this.runSpeciesName = runInfo != null ? runInfo.speciesName : null;
        this.runSpeciesVersion = runInfo != null ? runInfo.speciesVersion : null;
        
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        initializeUI();
    }
    
    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));

        // 加载已有结果模式使用独立的界面布局
        if (loadMode) {
            initializeLoadModeUI();
            return;
        }
        
        // 顶部：窗口大小和步长设置面板
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        topPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "Settings",
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));
        
        topPanel.add(new JLabel("Window Size:"));
        windowSizeField = new JTextField(String.valueOf(windowSize), 12);
        windowSizeField.setPreferredSize(new Dimension(120, 25));
        final int[] windowSizeRef = {windowSize};
        windowSizeField.getDocument().addDocumentListener(createWindowSizeListener(windowSizeRef));
        topPanel.add(windowSizeField);
        
        topPanel.add(new JLabel("Window Step:"));
        windowStepField = new JTextField(String.valueOf(stepSize), 12);
        windowStepField.setPreferredSize(new Dimension(120, 25));
        final int[] stepSizeRef = {stepSize};
        windowStepField.getDocument().addDocumentListener(createStepSizeListener(stepSizeRef));
        topPanel.add(windowStepField);

        // 染色体按编号排序选项（默认不勾选：展示时按数据文件内的出场顺序）
        sortChromosomesCheckBox = new JCheckBox("Sort chromosomes by number");
        sortChromosomesCheckBox.setToolTipText("<html>When checked, results are displayed ordered by the number at the end of each chromosome name (Chr1, Chr2, ..., Chr10; Chr01 equals Chr1).<br>Unchecked: the order of the data file (VCF) is kept.</html>");
        sortChromosomesCheckBox.setSelected(false);
        topPanel.add(sortChromosomesCheckBox);
        
        // 加载模式：只开放 window/step 编辑（排序沿用原设置，只读展示）
        if (loadMode) {
            sortChromosomesCheckBox.setEnabled(false);
            sortChromosomesCheckBox.setSelected(loadSortChromosomes);
        }
        
        // 创建样品面板
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // 左侧：样本列表（加载模式下隐藏：群体成员只读，无需样本列表）
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "All Samples",
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));
        
        leftButtonPanel = new JPanel();
        leftButtonPanel.setLayout(new BoxLayout(leftButtonPanel, BoxLayout.Y_AXIS));
        
        if (!loadMode) {
            // 搜索框面板
            JPanel searchPanel = new JPanel(new BorderLayout(5, 5));
            JLabel searchLabel = new JLabel("Search:");
            JTextField searchField = new JTextField();
            searchField.setToolTipText("Type a sample name to filter the list");
            searchPanel.add(searchLabel, BorderLayout.WEST);
            searchPanel.add(searchField, BorderLayout.CENTER);
            searchPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            
            listModel = new DefaultListModel<>();
            for (String sample : allSamples) {
                listModel.addElement(sample);
            }
            sampleList = new JList<>(listModel);
            sampleList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            JScrollPane listScroll = new JScrollPane(sampleList);
            listScroll.setPreferredSize(new Dimension(200, 300));
            
            // 添加搜索功能
            FST1JieMian2.setupSearchFilter(searchField, listModel, allSamples);
            
            leftPanel.add(searchPanel, BorderLayout.NORTH);
            leftPanel.add(listScroll, BorderLayout.CENTER);
            leftPanel.add(leftButtonPanel, BorderLayout.SOUTH);
        } else {
            leftPanel.setVisible(false);
        }
        
        // 右侧：分组文本框
        rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        dynamicPopPanels = new ArrayList<>();
        allPopAreas = new ArrayList<>();
        
        if (loadMode) {
            // 加载模式：按 run.info 的 [Group] 还原群体，成员只读
            for (int i = 0; i < loadGroups.size(); i++) {
                FSTRunInfo.Group group = loadGroups.get(i);
                String groupName = (group != null && group.name != null && !group.name.isEmpty())
                        ? group.name : ("Group" + (i + 1));
                JPanel groupPanel = createGroupPanel(groupName, true);
                JTextArea area = allPopAreas.get(allPopAreas.size() - 1);
                if (group != null && group.samples != null) {
                    area.setText(String.join("\n", group.samples));
                    area.setCaretPosition(0);
                }
                rightPanel.add(groupPanel);
                rightPanel.add(groupColumnStrut());
            }
        } else {
            // 群体1（名称可双击修改，身份与显示都以名称标签为准）
            JPanel pop1Panel = createGroupPanel("Group1", false);
            pop1Area = allPopAreas.get(0);
            rightPanel.add(pop1Panel);
            rightPanel.add(groupColumnStrut());  // 添加间距
            // 群体2（名称可双击修改）
            JPanel pop2Panel = createGroupPanel("Group2", false);
            pop2Area = allPopAreas.get(1);
            rightPanel.add(pop2Panel);
            // 添加"Add a group"按钮（铺满整栏宽度）
            JButton addGroupButton = new JButton("Add a group");
            addGroupButton.setAlignmentX(Component.LEFT_ALIGNMENT);
            makeFullWidth(addGroupButton);
            addGroupButton.addActionListener(e -> addDynamicPopulationPanel());
            rightPanel.add(groupColumnStrut());  // 添加间距
            rightPanel.add(addGroupButton);
        }
        
        // 将右侧面板放入滚动面板
        JScrollPane rightScrollPane = new JScrollPane(rightPanel);
        rightScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        rightScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        rightScrollPane.setPreferredSize(new Dimension(400, 400));
        
        // 创建VS功能面板管理器
        vsPanelManager = new FST1JieMian3(() -> getAllGroupNames());
        
        // 加载模式：在"新增比较策略"上方展示已有的比较策略（只读）
        if (loadMode) {
            List<FST1JieMian3.ComparisonTask> existingTasks = new ArrayList<>();
            for (FSTRunInfo.Comparison comparison : loadComparisons) {
                if (comparison == null) continue;
                existingTasks.add(new FST1JieMian3.ComparisonTask(
                        comparison.name, comparison.leftGroups, comparison.rightGroups));
            }
            vsPanelManager.setExistingComparisons(existingTasks);
        }
        
        // 添加到主面板
        mainPanel.add(leftPanel, BorderLayout.WEST);
        mainPanel.add(rightScrollPane, BorderLayout.CENTER);
        // VS面板初始隐藏，只有当群体数量>=3时才显示
        mainPanel.add(vsPanelManager.getPanel(), BorderLayout.EAST);
        vsPanelManager.updateVisibility(allPopAreas.size());  // 初始化时检查是否需要显示VS面板
        
        // 初始化左侧按钮（必须在所有Group面板创建完成后）
        if (!loadMode) {
            updateLeftButtons();
        }
        
        // 底部：Compare名称（两组时显示，双击改名）+ Start/确定按钮
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JLabel compareCaption = new JLabel("Compare:");
        compareCaption.setFont(simplegenomehub.population.fst.FstUiStyle.font(0, Font.PLAIN));
        compareCaptionLabel = compareCaption;
        bottomPanel.add(compareCaption);
        String compareDisplay = currentTwoGroupCompareDisplay();
        if (loadMode && !loadComparisons.isEmpty()
                && loadComparisons.get(0) != null
                && loadComparisons.get(0).name != null
                && !loadComparisons.get(0).name.isEmpty()) {
            compareDisplay = loadComparisons.get(0).name;  // 已有策略名（只读展示）
        }
        twoGroupCompareLabel = new JLabel(compareDisplay);
        twoGroupCompareLabel.setFont(simplegenomehub.population.fst.FstUiStyle.font(0, Font.BOLD));
        twoGroupCompareLabel.setForeground(new Color(70, 130, 180));
        if (!loadMode) {
            installTwoGroupCompareRename();
        } else {
            twoGroupCompareLabel.setToolTipText("Existing comparison (read-only)");
        }
        bottomPanel.add(twoGroupCompareLabel);
        bottomPanel.add(Box.createHorizontalStrut(30));
        startButton = new JButton(loadMode ? "OK" : "Start");
        startButton.setPreferredSize(new Dimension(100, 30));
        startButton.addActionListener(e -> handleStart());
        bottomPanel.add(startButton);
        
        add(topPanel, BorderLayout.NORTH);
        add(mainPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
        
        // Compare名称入口只在两个类群时可见
        updateTwoGroupCompareVisibility();
        
        setSize(1000, 550);
        setLocationRelativeTo(null);
    }

    /**
     * 加载已有结果模式的界面：
     * 顶部 Group Info（已有群体成员，自动折行文本）；
     * 左侧一整栏 Existing strategy（卡牌：名称 / 组别 / 窗口与步长）；
     * 右侧 Window Settings + Groups + New comparisons + Add a Comparison；
     * 底部 Run New Comparisons + Close。
     */
    private void initializeLoadModeUI() {
        // ---- 顶部：Group Info ----
        JPanel groupInfoPanel = new JPanel(new GridLayout(1, Math.max(1, loadGroups.size()), 8, 0));
        groupInfoPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Group Info", TitledBorder.LEFT, TitledBorder.TOP));
        groupInfoPanel.setBackground(Color.WHITE);

        // rightPanel 仍作为“群体名称 -> 成员文本框”的查找容器
        rightPanel = groupInfoPanel;
        dynamicPopPanels = new ArrayList<>();
        allPopAreas = new ArrayList<>();

        for (int i = 0; i < loadGroups.size(); i++) {
            FSTRunInfo.Group group = loadGroups.get(i);
            String groupName = (group != null && group.name != null && !group.name.isEmpty())
                    ? group.name : ("Group" + (i + 1));
            groupInfoPanel.add(createGroupInfoBlock(groupName, group));
        }

        // ---- 右侧：Window Settings + VS 面板（Groups / New comparisons / Add a Comparison）----
        windowSizeField = new JTextField(String.valueOf(windowSize), 12);
        windowSizeField.setPreferredSize(new Dimension(120, 25));
        final int[] windowSizeRef = {windowSize};
        windowSizeField.getDocument().addDocumentListener(createWindowSizeListener(windowSizeRef));

        windowStepField = new JTextField(String.valueOf(stepSize), 12);
        windowStepField.setPreferredSize(new Dimension(120, 25));
        final int[] stepSizeRef = {stepSize};
        windowStepField.getDocument().addDocumentListener(createStepSizeListener(stepSizeRef));

        JPanel windowSettingsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        windowSettingsPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Window Settings", TitledBorder.LEFT, TitledBorder.TOP));
        windowSettingsPanel.add(new JLabel("Window Size:"));
        windowSettingsPanel.add(windowSizeField);
        windowSettingsPanel.add(new JLabel("Window Step:"));
        windowSettingsPanel.add(windowStepField);

        vsPanelManager = new FST1JieMian3(this::getAllGroupNames);
        vsPanelManager.setOnComparisonsChanged(this::updateRunNewComparisonsEnabled);
        vsPanelManager.forceVisible();

        JPanel editorColumn = new JPanel(new BorderLayout(0, 6));
        editorColumn.setOpaque(false);
        editorColumn.add(windowSettingsPanel, BorderLayout.NORTH);
        editorColumn.add(vsPanelManager.getPanel(), BorderLayout.CENTER);

        // ---- 左侧：Existing strategy 卡牌栏 ----
        JPanel existingColumn = createExistingStrategyColumn();

        JPanel centerPanel = new JPanel(new BorderLayout(10, 0));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        centerPanel.setOpaque(false);
        centerPanel.add(existingColumn, BorderLayout.WEST);
        centerPanel.add(editorColumn, BorderLayout.CENTER);

        // ---- 底部：Run New Comparisons + Close ----
        runNewComparisonsButton = new JButton("Run New Comparisons");
        runNewComparisonsButton.setPreferredSize(new Dimension(190, 30));
        runNewComparisonsButton.setEnabled(false);
        runNewComparisonsButton.addActionListener(e -> handleLoadModeConfirm());
        startButton = runNewComparisonsButton;

        JButton closeButton = new JButton("Close");
        closeButton.setPreferredSize(new Dimension(100, 30));
        closeButton.addActionListener(e -> dispose());

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8));
        bottomPanel.add(runNewComparisonsButton);
        bottomPanel.add(closeButton);

        add(groupInfoPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        setSize(1250, 700);
        setLocationRelativeTo(null);
    }

    /** 顶部 Group Info 里的一个群体块：只读、自动折行的成员文本。 */
    private JPanel createGroupInfoBlock(String groupName, FSTRunInfo.Group group) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createLineBorder(FstUiStyle.CARD_BORDER, 1, true));

        JLabel header = createGroupNameHeader(groupName);
        header.setToolTipText("Existing group (read-only)");
        header.setCursor(Cursor.getDefaultCursor());
        panel.add(header, BorderLayout.NORTH);

        JTextArea area = new JTextArea(4, 20);
        area.setFont(FstUiStyle.monospaced(12, Font.PLAIN));
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBackground(new Color(245, 245, 245));
        if (group != null && group.samples != null) {
            area.setText(String.join("\n", group.samples));
            area.setCaretPosition(0);
        }
        allPopAreas.add(area);

        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    /** 左侧一整栏 Existing strategy：每个已有策略一张卡牌。 */
    private JPanel createExistingStrategyColumn() {
        JPanel cards = new JPanel();
        cards.setLayout(new BoxLayout(cards, BoxLayout.Y_AXIS));
        cards.setBackground(Color.WHITE);

        if (loadComparisons == null || loadComparisons.isEmpty()) {
            JLabel empty = new JLabel("No existing strategy");
            empty.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            cards.add(empty);
        } else {
            for (FSTRunInfo.Comparison comparison : loadComparisons) {
                if (comparison == null) continue;
                cards.add(createExistingStrategyCard(comparison));
                cards.add(Box.createVerticalStrut(8));
            }
        }
        cards.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(cards);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Existing strategy", TitledBorder.LEFT, TitledBorder.TOP));
        scroll.setPreferredSize(new Dimension(280, 560));
        scroll.getViewport().setBackground(Color.WHITE);

        JPanel column = new JPanel(new BorderLayout());
        column.setOpaque(false);
        column.add(scroll, BorderLayout.CENTER);
        return column;
    }

    /** 单张已有策略卡牌：名称 / 组别 / 窗口与步长。 */
    private JPanel createExistingStrategyCard(FSTRunInfo.Comparison comparison) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(new Color(245, 247, 250));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(FstUiStyle.CARD_BORDER, 1, true),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        String name = (comparison.name == null || comparison.name.trim().isEmpty())
                ? "Comparison" : comparison.name;
        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(FstUiStyle.font(0, Font.BOLD));
        nameLabel.setForeground(new Color(60, 110, 160));

        JLabel groupsLabel = new JLabel("<html>"
            + escapeHtml(joinGroupNamesForCard(comparison.leftGroups))
            + "&nbsp;&rarr;&nbsp;"
            + escapeHtml(joinGroupNamesForCard(comparison.rightGroups)) + "</html>");
        groupsLabel.setFont(FstUiStyle.font(0, Font.PLAIN));

        JLabel sizeLabel = new JLabel("<html>Window Size: " + windowSize
            + "<br>Window Step: " + stepSize + "</html>");
        sizeLabel.setFont(FstUiStyle.font(0, Font.PLAIN));
        sizeLabel.setForeground(new Color(110, 118, 130));

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(groupsLabel);
        body.add(Box.createVerticalStrut(4));
        body.add(sizeLabel);

        card.add(nameLabel, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height + 4));
        return card;
    }

    private static String joinGroupNamesForCard(List<String> groups) {
        if (groups == null || groups.isEmpty()) return "(none)";
        return String.join(" + ", groups);
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** "Run New Comparisons" 按钮只在构建出新的比较策略后才可点击。 */
    private void updateRunNewComparisonsEnabled() {
        if (!loadMode || runNewComparisonsButton == null) {
            return;
        }
        boolean hasNew = vsPanelManager != null
            && !filterNewComparisons(vsPanelManager.getValidComparisonTasks()).isEmpty();
        runNewComparisonsButton.setEnabled(hasNew);
    }
    
    /**
     * 创建单个群体面板（名称标签 + 成员文本框），并把成员文本框登记到 allPopAreas。
     *
     * @param groupName 群体名称
     * @param readOnly  true=成员只读、不可改名/拖入文件（加载模式）；false=可编辑
     */
    /** 群体栏里的间距：与按钮、面板统一左对齐，保证整栏左右连通。 */
    private static Component groupColumnStrut() {
        Component strut = Box.createVerticalStrut(5);
        if (strut instanceof javax.swing.JComponent) {
            ((javax.swing.JComponent) strut).setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        return strut;
    }

    /**
     * 让按钮在 BoxLayout 的纵向栏里横向铺满（左右连通成一条）。
     */    private static void makeFullWidth(JButton button) {
        Dimension preferred = button.getPreferredSize();
        int height = Math.max(30, preferred.height);
        button.setPreferredSize(new Dimension(preferred.width, height));
        button.setMinimumSize(new Dimension(80, height));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
    }

    private JPanel createGroupPanel(String groupName, boolean readOnly) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createLineBorder(simplegenomehub.population.fst.FstUiStyle.CARD_BORDER, 1, true));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JLabel header = createGroupNameHeader(groupName);
        if (readOnly) {
            header.setToolTipText("Existing group (read-only)");
            header.setCursor(Cursor.getDefaultCursor());
        } else {
            installGroupNameRename(header);
        }
        panel.add(header, BorderLayout.NORTH);
        
        JTextArea area = new JTextArea(10, 20);
        area.setFont(simplegenomehub.population.fst.FstUiStyle.monospaced(12, Font.PLAIN));
        area.setEditable(!readOnly);
        if (readOnly) {
            area.setBackground(new Color(245, 245, 245));
        }
        if (!readOnly) {
            // 为成员文本框添加文件拖拽功能与文本变化监听（更新VS面板）
            FST1JieMian2.setupTextAreaDropTarget(area);
            area.getDocument().addDocumentListener(createVSPanelUpdateListener());
        }
        allPopAreas.add(area);
        
        JScrollPane scroll = new JScrollPane(area);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    /**
     * 处理Start按钮点击
     */
    private void handleStart() {
        // 加载模式：点"确定"只运行新增的比较策略，结果写回加载进来的文件夹，然后绘图
        if (loadMode) {
            handleLoadModeConfirm();
            return;
        }
        
        // 检查Group数量
        int totalGroups = allPopAreas.size();
        
        if (totalGroups < 2) {
            JOptionPane.showMessageDialog(this,
                "Please specify at least two groups.",
                "Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // 运行前校验样本：列出不可用样本，由用户决定忽略后继续还是中止
        if (!confirmUnavailableSamples()) {
            return;
        }
        
        // 每次点击Start都开一个全新的输出批次目录，避免与上一次运行的结果文件冲突。
        // 注意：必须在样本校验（确认/取消）通过后才创建目录；取消时不产生任何文件夹。
        try {
            // 批次目录：FSTool-时间戳
            FSTOutputDirectory.createNewRunDirectory();
            // 结果目录：FSTool-时间戳/w<windowSize>_s<stepSize>/（所有结果都写到这里）
            int[] wRef = {windowSize};
            int[] sRef = {stepSize};
            FST1JieMian2.updateWindowSize(windowSizeField, wRef);
            FST1JieMian2.updateStepSize(windowStepField, sRef);
            FSTOutputDirectory.getOrCreateParamDirectory(wRef[0], sRef[0]);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Cannot create output folder: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // 目录创建成功后，立即记录本次运行的输入与分组/比较策略（run.info.txt）
        writeRunInfo();
        
        if (totalGroups == 2) {
            // 只有Group1和Group2，使用原有逻辑
            handleTwoGroupsCalculation();
        } else {
            // 超过2个Group，使用VS面板的Comparison任务
            handleMultipleGroupsCalculation();
        }
    }
    
    /**
     * 加载模式确认：
     * 目标目录 = 批次目录（FSTool-时间戳）/ w&lt;windowSize&gt;_s&lt;stepSize&gt; /。
     * - 参数未变且已有对应结果 → 只运行新增策略（以及缺结果的已有策略）；
     * - 参数改变或缺少对应结果 → 对"已有策略 + 新增策略"全部按新 window/step 跑一轮；
     * 结果全部写回该参数文件夹，最后打开可视化。
     */
    private void handleLoadModeConfirm() {
        if (loadOutputFolder == null) {
            dispose();
            return;
        }
        
        int[] wRef = {windowSize};
        int[] sRef = {stepSize};
        FST1JieMian2.updateWindowSize(windowSizeField, wRef);
        FST1JieMian2.updateStepSize(windowStepField, sRef);
        final int reqWindow = wRef[0];
        final int reqStep = sRef[0];
        
        // 批次目录 = 加载进来的结果文件夹的父目录（FSTool-时间戳）
        File batchDir = loadOutputFolder.getParentFile();
        FSTOutputDirectory.setBatchDirectory(batchDir != null ? batchDir : loadOutputFolder);
        
        File targetDir;
        try {
            targetDir = FSTOutputDirectory.getOrCreateParamDirectory(reqWindow, reqStep);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Cannot create result folder: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // 新增策略
        List<FST1JieMian3.ComparisonTask> newTasks = new ArrayList<>();
        if (vsPanelManager != null) {
            newTasks.addAll(vsPanelManager.getValidComparisonTasks());
        }
        newTasks = filterNewComparisons(newTasks);
        
        // 已有策略
        List<FST1JieMian3.ComparisonTask> existingTasks = new ArrayList<>();
        if (loadComparisons != null) {
            for (FSTRunInfo.Comparison comparison : loadComparisons) {
                if (comparison == null) continue;
                existingTasks.add(new FST1JieMian3.ComparisonTask(
                        comparison.name, comparison.leftGroups, comparison.rightGroups));
            }
        }
        
        boolean paramsChanged = !targetDir.equals(loadOutputFolder);
        Set<String> seen = new LinkedHashSet<>();
        List<FST1JieMian3.ComparisonTask> tasksToRun = new ArrayList<>();
        
        if (paramsChanged) {
            // 参数改变：新参数文件夹，全部策略重跑一轮
            for (FST1JieMian3.ComparisonTask t : existingTasks) {
                if (seen.add(comparisonKey(t.leftGroups, t.rightGroups))) tasksToRun.add(t);
            }
            for (FST1JieMian3.ComparisonTask t : newTasks) {
                if (seen.add(comparisonKey(t.leftGroups, t.rightGroups))) tasksToRun.add(t);
            }
        } else {
            // 参数未变：只跑"缺对应结果"的已有策略 + 新增策略
            for (FST1JieMian3.ComparisonTask t : existingTasks) {
                if (!taskResultExists(targetDir, t)
                        && seen.add(comparisonKey(t.leftGroups, t.rightGroups))) {
                    tasksToRun.add(t);
                }
            }
            for (FST1JieMian3.ComparisonTask t : newTasks) {
                if (seen.add(comparisonKey(t.leftGroups, t.rightGroups))) tasksToRun.add(t);
            }
        }
        
        if (tasksToRun.isEmpty()) {
            openVisualizationAt(targetDir);
            dispose();
            return;
        }
        
        if (vcfFilePath == null || !new File(vcfFilePath).isFile()) {
            JOptionPane.showMessageDialog(this,
                "The original VCF file was not found, so the strategy cannot be re-run with the current window/step:\n"
                + (vcfFilePath == null || vcfFilePath.isEmpty() ? "(not recorded in run.info.txt)" : vcfFilePath)
                + "\n\nOnly the results already stored in this folder will be shown.",
                "Cannot run the strategy", JOptionPane.WARNING_MESSAGE);
            openVisualizationAt(loadOutputFolder);
            dispose();
            return;
        }
        
        // 结果目录 = targetDir；先写入 run.info.txt（含参数、群体、全部策略）
        FSTOutputDirectory.setOutputDirectory(targetDir);
        writeLoadRunInfo(targetDir, existingTasks, newTasks);
        
        executeMultipleFSTCalculations(tasksToRun);
    }
    
    /** 判断某策略在目录内是否已有 FST 结果文件 */
    private boolean taskResultExists(File dir, FST1JieMian3.ComparisonTask task) {
        if (dir == null || task == null) return false;
        try {
            String baseName = getVcfBaseName();
            String suffix = generateFileNameFromGroups(task.leftGroups, task.rightGroups);
            return new File(dir, baseName + "." + suffix + ".windowed.weir.fst").isFile();
        } catch (Exception e) {
            return false;
        }
    }
    
    /** 加载模式：把参数 + 群体 + 全部策略写入目标目录的 run.info.txt */
    private void writeLoadRunInfo(File dir, List<FST1JieMian3.ComparisonTask> existingTasks,
                                  List<FST1JieMian3.ComparisonTask> newTasks) {
        try {
            int[] wRef = {windowSize};
            int[] sRef = {stepSize};
            FST1JieMian2.updateWindowSize(windowSizeField, wRef);
            FST1JieMian2.updateStepSize(windowStepField, sRef);
            FSTRunInfo.Info info = FSTRunInfo.create(vcfFilePath, genomeFilePath, gff3FilePath,
                wRef[0], sRef[0], isSortChromosomesByNumber());
            applySpeciesIdentity(info);
            for (String groupName : getAllGroupNames()) {
                FSTRunInfo.Group group = new FSTRunInfo.Group(groupName);
                group.samples.addAll(getSamplesByGroupName(groupName));
                info.groups.add(group);
            }
            Set<String> seen = new LinkedHashSet<>();
            for (FST1JieMian3.ComparisonTask t : existingTasks) addComparisonToInfo(info, t, seen);
            for (FST1JieMian3.ComparisonTask t : newTasks) addComparisonToInfo(info, t, seen);
            FSTRunInfo.write(dir, info);
        } catch (Exception e) {
            System.err.println("Failed to write run info: " + e.getMessage());
        }
    }
    
    private void addComparisonToInfo(FSTRunInfo.Info info, FST1JieMian3.ComparisonTask task, Set<String> seen) {
        if (info == null || task == null) return;
        if (!seen.add(comparisonKey(task.leftGroups, task.rightGroups))) return;
        FSTRunInfo.Comparison comparison = new FSTRunInfo.Comparison();
        comparison.name = task.name;
        comparison.leftGroups.addAll(task.leftGroups);
        comparison.rightGroups.addAll(task.rightGroups);
        info.comparisons.add(comparison);
    }
    
    /**
     * 过滤掉与"已有策略"左右类群完全相同的任务（直接跳过，不重复运行）。
     */
    private List<FST1JieMian3.ComparisonTask> filterNewComparisons(List<FST1JieMian3.ComparisonTask> tasks) {
        Set<String> seenKeys = new LinkedHashSet<>();
        if (loadComparisons != null) {
            for (FSTRunInfo.Comparison existing : loadComparisons) {
                if (existing != null) {
                    seenKeys.add(comparisonKey(existing.leftGroups, existing.rightGroups));
                }
            }
        }
        List<FST1JieMian3.ComparisonTask> result = new ArrayList<>();
        for (FST1JieMian3.ComparisonTask task : tasks) {
            if (task == null) continue;
            String key = comparisonKey(task.leftGroups, task.rightGroups);
            if (seenKeys.add(key)) {   // 已有、或本次重复的，都跳过
                result.add(task);
            }
        }
        return result;
    }
    
    private String comparisonKey(List<String> left, List<String> right) {
        return joinSortedGroupNames(left) + ">" + joinSortedGroupNames(right);
    }
    
    private String joinSortedGroupNames(List<String> names) {
        if (names == null || names.isEmpty()) return "";
        List<String> copy = new ArrayList<>(names);
        Collections.sort(copy);
        return String.join(",", copy);
    }
    
    /**
     * 直接展示加载进来的文件夹（不运行任何新策略）
     */
    private void openLoadedFolderVisualization() {
        openVisualizationAt(loadOutputFolder);
    }
    
    /** 展示指定结果文件夹 */
    private void openVisualizationAt(File dir) {
        if (dir == null) return;
        notifyResultsReady(dir);
    }
    
    /**
     * 把新运行的比较策略追加到该文件夹的 run.info.txt（已存在相同左右类群则跳过）
     */
    private void appendNewComparisonsToRunInfo(List<FST1JieMian3.ComparisonTask> tasks) {
        try {
            File dir = FSTOutputDirectory.getOutputDirectory();
            if (dir == null || tasks == null || tasks.isEmpty()) return;
            List<FSTRunInfo.Comparison> additions = new ArrayList<>();
            for (FST1JieMian3.ComparisonTask task : tasks) {
                FSTRunInfo.Comparison comparison = new FSTRunInfo.Comparison();
                comparison.name = task.name;
                comparison.leftGroups.addAll(task.leftGroups);
                comparison.rightGroups.addAll(task.rightGroups);
                additions.add(comparison);
            }
            int added = FSTRunInfo.addComparisonsIfAbsent(dir, additions);
            System.out.println("Appended " + added + " new comparison(s) to run.info.txt");
        } catch (Exception e) {
            System.err.println("Failed to append run info: " + e.getMessage());
        }
    }
    
    /**
     * 把本次运行的输入文件、窗口设置以及群体与比较策略写入结果目录的 run.info.txt。
     * 仅记录实际参与运行的样本（已在确认弹窗中被忽略的样本不写入）。
     */
    private void writeRunInfo() {
        try {
            File outputDir = FSTOutputDirectory.getOutputDirectory();
            if (outputDir == null) return;
            
            int[] windowSizeRef = {windowSize};
            int[] stepSizeRef = {stepSize};
            FST1JieMian2.updateWindowSize(windowSizeField, windowSizeRef);
            FST1JieMian2.updateStepSize(windowStepField, stepSizeRef);
            
            FSTRunInfo.Info info = FSTRunInfo.create(
                vcfFilePath, genomeFilePath, gff3FilePath,
                windowSizeRef[0], stepSizeRef[0], isSortChromosomesByNumber());
            applySpeciesIdentity(info);
            
            List<String> groupNames = getAllGroupNames();
            for (String groupName : groupNames) {
                FSTRunInfo.Group group = new FSTRunInfo.Group(groupName);
                group.samples.addAll(getSamplesByGroupName(groupName));
                info.groups.add(group);
            }
            
            if (allPopAreas.size() == 2 && groupNames.size() >= 2) {
                FSTRunInfo.Comparison comparison = new FSTRunInfo.Comparison();
                comparison.name = currentTwoGroupCompareDisplay();
                comparison.leftGroups.add(groupNames.get(0));
                comparison.rightGroups.add(groupNames.get(1));
                info.comparisons.add(comparison);
            } else if (vsPanelManager != null) {
                for (FST1JieMian3.ComparisonTask task : vsPanelManager.getValidComparisonTasks()) {
                    FSTRunInfo.Comparison comparison = new FSTRunInfo.Comparison();
                    comparison.name = task.name;
                    comparison.leftGroups.addAll(task.leftGroups);
                    comparison.rightGroups.addAll(task.rightGroups);
                    info.comparisons.add(comparison);
                }
            }
            
            FSTRunInfo.write(outputDir, info);
        } catch (Exception e) {
            // 记录失败不影响计算
            System.err.println("Failed to write run info: " + e.getMessage());
        }
    }
    
    /**
     * 运行前校验各类群中的样本是否都存在于VCF样本列表中。
     * 若存在不可用样本：弹窗列出这些样本，
     * 点击"确认"则忽略它们继续运行；点击"取消"则中止运行。
     *
     * @return true 表示允许继续运行
     */
    private boolean confirmUnavailableSamples() {
        unavailableSamples.clear();
        Set<String> vcfSamples = new HashSet<>(allSamples);
        
        LinkedHashMap<String, List<String>> unavailableByGroup = new LinkedHashMap<>();
        for (String groupName : getAllGroupNames()) {
            List<String> unavailable = new ArrayList<>();
            Set<String> dedup = new LinkedHashSet<>();
            for (String sample : getRawSamplesByGroupName(groupName)) {
                if (!vcfSamples.contains(sample) && dedup.add(sample)) {
                    unavailable.add(sample);
                }
            }
            if (!unavailable.isEmpty()) {
                unavailableByGroup.put(groupName, unavailable);
            }
        }
        
        if (unavailableByGroup.isEmpty()) {
            return true;
        }
        
        StringBuilder message = new StringBuilder();
        message.append("These samples are not in the VCF and cannot take part:\n\n");
        for (Map.Entry<String, List<String>> entry : unavailableByGroup.entrySet()) {
            message.append("[").append(entry.getKey()).append("]\n");
            for (String sample : entry.getValue()) {
                message.append("    ").append(sample).append('\n');
            }
            message.append('\n');
        }
        
        // 提示忽略后完全没有样本的类群，避免后续出现难以理解的"没有有效计算"
        List<String> emptiedGroups = new ArrayList<>();
        for (String groupName : unavailableByGroup.keySet()) {
            boolean hasValid = false;
            for (String sample : getRawSamplesByGroupName(groupName)) {
                if (vcfSamples.contains(sample)) {
                    hasValid = true;
                    break;
                }
            }
            if (!hasValid) {
                emptiedGroups.add(groupName);
            }
        }
        if (!emptiedGroups.isEmpty()) {
            message.append("Note: after ignoring, [").append(String.join("], [", emptiedGroups))
                   .append("] will have no usable samples; comparisons involving them are skipped.\n\n");
        }
        
        message.append("OK: ignore these samples and continue.\n");
        message.append("Cancel: stop and check the samples.");
        
        if (!showUnavailableSamplesDialog(message.toString())) {
            return false;
        }
        
        for (List<String> samples : unavailableByGroup.values()) {
            unavailableSamples.addAll(samples);
        }
        return true;
    }
    
    /**
     * 显示"样本不可用"确认弹窗（下方为确认/取消按钮）
     *
     * @return true 表示用户点击了"确认"
     */
    private boolean showUnavailableSamplesDialog(String message) {
        JTextArea messageArea = new JTextArea(message);
        messageArea.setEditable(false);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        messageArea.setFont(simplegenomehub.population.fst.FstUiStyle.font(0, Font.PLAIN));
        messageArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        Color optionBg = UIManager.getColor("OptionPane.background");
        if (optionBg != null) {
            messageArea.setBackground(optionBg);
        }
        messageArea.setCaretPosition(0);
        
        JScrollPane scrollPane = new JScrollPane(messageArea);
        scrollPane.setPreferredSize(new Dimension(460, 320));
        scrollPane.setBorder(BorderFactory.createEtchedBorder());
        
        Object[] options = {"OK", "Cancel"};
        int choice = JOptionPane.showOptionDialog(
                this,
                scrollPane,
                "Samples unavailable",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[0]);
        return choice == JOptionPane.YES_OPTION;
    }
    
    /**
     * 处理两个Group的计算（原有逻辑）
     */
    private void handleTwoGroupsCalculation() {
        // 读取两个群体的样本名
        String pop1Text = pop1Area.getText().trim();
        String pop2Text = pop2Area.getText().trim();
        
        if (pop1Text.isEmpty() || pop2Text.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please specify samples for both populations.",
                "Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // 解析样本名（每行一个）
        List<String> pop1Samples = parseSamples(pop1Text);
        List<String> pop2Samples = parseSamples(pop2Text);
        
        if (pop1Samples.isEmpty() || pop2Samples.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Each population must have at least one sample.",
                "Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // 使用当前类群名称生成文件名后缀与Compare名（类群名可自定义）
        List<String> currentGroupNames = getAllGroupNames();
        if (currentGroupNames.size() < 2) {
            JOptionPane.showMessageDialog(this,
                "Please specify at least two groups.",
                "Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        List<String> leftGroups = new ArrayList<>();
        leftGroups.add(currentGroupNames.get(0));
        List<String> rightGroups = new ArrayList<>();
        rightGroups.add(currentGroupNames.get(1));
        String fileNameSuffix = generateFileNameFromGroups(leftGroups, rightGroups);
        
        // 执行单个FST计算
        executeFSTCalculation(pop1Samples, pop2Samples, fileNameSuffix, leftGroups, rightGroups, 0);
    }
    
    /**
     * 处理多个Group的计算（基于Comparison任务）
     */
    private void handleMultipleGroupsCalculation() {
        // 获取所有有效的Comparison任务
        List<FST1JieMian3.ComparisonTask> tasks = vsPanelManager.getValidComparisonTasks();
        
        if (tasks.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please configure at least one valid comparison in the VS panel.\n" +
                "Each comparison must have at least one group in both left and right boxes.",
                "Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // 依次执行每个Comparison任务
        executeMultipleFSTCalculations(tasks);
    }
    
    /**
     * 查找某个类群对应的样本文本框；找不到返回null
     */
    private JTextArea findGroupTextArea(String groupName) {
        // 注意：加载模式下 pop1Area/pop2Area 为 null，不能直接返回，
        // 否则名为 "Group1"/"Group2" 的已有群体找不到成员文本框。
        if (pop1Area != null && "Group1".equals(groupName)) {
            return pop1Area;
        } else if (pop2Area != null && "Group2".equals(groupName)) {
            return pop2Area;
        }
        
        // 查找动态添加的Group面板
        Component[] rightPanelComponents = rightPanel.getComponents();
        for (Component comp : rightPanelComponents) {
            if (comp instanceof JPanel) {
                JPanel panel = (JPanel) comp;
                if (groupName.equals(extractGroupNameFromPanel(panel))) {
                    return extractTextAreaFromPanel(panel);
                }
            }
        }
        return null;
    }
    
    /**
     * 根据Group名称获取原始样本列表（不做任何过滤）
     */
    List<String> getRawSamplesByGroupName(String groupName) {
        JTextArea textArea = findGroupTextArea(groupName);
        return textArea == null ? new ArrayList<>() : parseSamplesRaw(textArea.getText().trim());
    }
    
    /**
     * 根据Group名称获取样本列表（已剔除不可用样本）
     */
    List<String> getSamplesByGroupName(String groupName) {
        JTextArea textArea = findGroupTextArea(groupName);
        return textArea == null ? new ArrayList<>() : parseSamples(textArea.getText().trim());
    }
    
    /**
     * 执行多个FST计算任务
     */
    private void executeMultipleFSTCalculations(List<FST1JieMian3.ComparisonTask> tasks) {
        // 禁用按钮，显示进度
        startButton.setEnabled(false);
        startButton.setText("Calculating...");
        
        // 在后台线程中执行所有计算
        Thread calcThread = new Thread(() -> {
            List<File> resultFiles = new ArrayList<>();
            Map<String, FSTRunManifest.RunEntry> manifestEntries = new HashMap<>();
            List<FST1JieMian3.ComparisonTask> completedTasks = new ArrayList<>();
            
            try {
                // 依次执行每个任务
                for (int i = 0; i < tasks.size(); i++) {
                    FST1JieMian3.ComparisonTask task = tasks.get(i);
                    
                    // 合并左侧所有Group的样本
                    List<String> leftSamples = new ArrayList<>();
                    for (String groupName : task.leftGroups) {
                        List<String> groupSamples = getSamplesByGroupName(groupName);
                        leftSamples.addAll(groupSamples);
                    }
                    
                    // 合并右侧所有Group的样本
                    List<String> rightSamples = new ArrayList<>();
                    for (String groupName : task.rightGroups) {
                        List<String> groupSamples = getSamplesByGroupName(groupName);
                        rightSamples.addAll(groupSamples);
                    }
                    
                    if (leftSamples.isEmpty() || rightSamples.isEmpty()) {
                        continue;
                    }
                    
                    // 生成基于群体名称的文件名
                    String fileNameSuffix = generateFileNameFromGroups(task.leftGroups, task.rightGroups);
                    
                    // 执行FST计算
                    File resultFile = executeSingleFSTCalculation(leftSamples, rightSamples, fileNameSuffix, task.leftGroups, task.rightGroups, i);
                    if (resultFile != null && !resultFiles.contains(resultFile)) {
                        resultFiles.add(resultFile);
                    }
                    
                    String pop1Name = generateGroupNameString(task.leftGroups);
                    String pop2Name = generateGroupNameString(task.rightGroups);
                    collectPIFiles(resultFiles, pop1Name, pop2Name);
                    
                    // 登记命名清单（卡牌显示名 + 悬停明细）
                    registerComparisonEntries(manifestEntries, task.name, task.leftGroups, task.rightGroups);
                    completedTasks.add(task);
                }
                
                // 写入命名清单供结果展示页使用（加载模式下与已有清单合并，避免覆盖旧命名）
                writeRunManifest(manifestEntries);
                
                // 加载模式：把真正运行过的新比较策略追加到该文件夹的 run.info.txt
                if (loadMode) {
                    appendNewComparisonsToRunInfo(completedTasks);
                }
                
                final String startText = loadMode ? "Run New Comparisons" : "Start";
                if (!resultFiles.isEmpty()) {
                    // 使用新的简化方法，只传递工作目录路径
                    final File workDirectory = FSTOutputDirectory.getOutputDirectory();
                    SwingUtilities.invokeLater(() -> {
                        notifyResultsReady(workDirectory);
                        if (loadMode) {
                            dispose();
                        } else {
                            startButton.setEnabled(true);
                            startButton.setText(startText);
                        }
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this,
                            "No valid FST calculations completed.",
                            "Warning",
                            JOptionPane.WARNING_MESSAGE);
                        startButton.setText(startText);
                        if (loadMode) {
                            updateRunNewComparisonsEnabled();
                        } else {
                            startButton.setEnabled(true);
                        }
                    });
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                        "Error calculating FST: " + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                    startButton.setText(loadMode ? "Run New Comparisons" : "Start");
                    if (loadMode) {
                        updateRunNewComparisonsEnabled();
                    } else {
                        startButton.setEnabled(true);
                    }
                });
            }
        });
        calcThread.setDaemon(true);
        calcThread.start();
    }
    
    /**
     * 执行单个FST计算（返回结果文件）
     */
    File executeSingleFSTCalculation(List<String> pop1Samples, List<String> pop2Samples, 
                                             String fileNameSuffix, List<String> leftGroupNames, List<String> rightGroupNames, int taskIndex) {
        try {
            // 创建临时群体文件
            File pop1File = File.createTempFile("pop1_" + taskIndex + "_", ".table");
            File pop2File = File.createTempFile("pop2_" + taskIndex + "_", ".table");
            
            // 生成windowed FST输出文件路径
            File vcfFile = new File(vcfFilePath);
            String vcfBaseName = vcfFile.getName();
            String baseName = vcfBaseName;
            if (baseName.endsWith(".gz")) {
                baseName = baseName.substring(0, baseName.length() - 3);
            }
            if (baseName.endsWith(".vcf")) {
                baseName = baseName.substring(0, baseName.length() - 4);
            }
            
            // 使用输出目录生成文件路径
            File outputDir = FSTOutputDirectory.getOutputDirectory();
            File outFstFile = new File(outputDir, baseName + "." + fileNameSuffix + ".windowed.weir.fst");
            
            // 检查FST文件是否已存在
            if (outFstFile.exists()) {
                return outFstFile;
            }
            
            // 写入群体文件
            writeSampleFile(pop1File, pop1Samples);
            writeSampleFile(pop2File, pop2Samples);
            
            // 读取VCF数据和样本数据
            LinkedHashSet<String> pop1Set = new LinkedHashSet<>(pop1Samples);
            LinkedHashSet<String> pop2Set = new LinkedHashSet<>(pop2Samples);
            FST2DuQu.VCFData vcfData = FST2DuQu.readVCFData(vcfFilePath, pop1Set, pop2Set);
            FST2DuQu.SampleData sampleData = FST2DuQu.readSampleData(pop1File.getAbsolutePath(), pop2File.getAbsolutePath());
            
            // 读取窗口设置
            final int[] windowSizeRef = {windowSize};
            final int[] stepSizeRef = {stepSize};
            FST1JieMian2.updateWindowSize(windowSizeField, windowSizeRef);
            FST1JieMian2.updateStepSize(windowStepField, stepSizeRef);
            windowSize = windowSizeRef[0];
            stepSize = stepSizeRef[0];
            
            // 第一步：计算per-site FST
            File outFstSiteFile = File.createTempFile("fst_site_" + taskIndex + "_", ".fst");
            FST4JiSuanFST.calculateFSTParallel(vcfData, 
                vcfData.headerInfo.pop1Idx, 
                vcfData.headerInfo.pop2Idx,
                outFstSiteFile.getAbsolutePath());
            
            // 第二步：将per-site FST转换为windowed格式
            boolean includeEmpty = false;
            FST5ShuChu.convertFSTToWindowed(
                outFstSiteFile.getAbsolutePath(),
                windowSize,
                stepSize,
                outFstFile.getAbsolutePath(),
                includeEmpty
            );
            
            // 第三步：自动计算Pi（使用相同的VCF数据、群体文件和窗口/步长）
            try {
                // 生成Pi输出文件前缀（使用输出目录）
                outputDir = FSTOutputDirectory.getOutputDirectory();
                String piOutPrefix = new File(outputDir, baseName).getAbsolutePath();
                
                // 生成群体名称（用于PI文件名）
                String pop1Name = generateGroupNameString(leftGroupNames);
                String pop2Name = generateGroupNameString(rightGroupNames);
                
                // 检查PI文件是否已存在
                String piSiteFile1 = piOutPrefix + "." + pop1Name + ".sites.pi";
                String piSiteFile2 = piOutPrefix + "." + pop2Name + ".sites.pi";
                String piSiteFile12 = piOutPrefix + "." + pop1Name + "_" + pop2Name + ".sites.pi";
                String piWindowedFile1 = piOutPrefix + "." + pop1Name + ".windowed.pi";
                String piWindowedFile2 = piOutPrefix + "." + pop2Name + ".windowed.pi";
                String piWindowedFile12 = piOutPrefix + "." + pop1Name + "_" + pop2Name + ".windowed.pi";
                
                if (!(new File(piSiteFile1).exists() && new File(piSiteFile2).exists() &&
                    new File(piSiteFile12).exists() &&
                    new File(piWindowedFile1).exists() && new File(piWindowedFile2).exists() &&
                    new File(piWindowedFile12).exists())) {
                    
                    // 计算per-site Pi（内部会自动合并文件）
                    int[] nSamples = FST4JiSuanPi.calculatePiParallel(vcfData,
                        vcfData.headerInfo.pop1Idx,
                        vcfData.headerInfo.pop2Idx,
                        piOutPrefix, pop1Name, pop2Name, windowSize, stepSize);  // 传递windowSize和stepSize给TajimaD
                    
                    // 转换为windowed格式
                    FST5ShuChu.convertPiToWindowed(piSiteFile1, windowSize, stepSize, piWindowedFile1, nSamples[0]);
                    FST5ShuChu.convertPiToWindowed(piSiteFile2, windowSize, stepSize, piWindowedFile2, nSamples[1]);
                    FST5ShuChu.convertPiToWindowed(piSiteFile12, windowSize, stepSize, piWindowedFile12, nSamples[0] + nSamples[1]);
                }
            } catch (Exception e) {
                System.err.println("Error calculating Pi: " + e.getMessage());
                e.printStackTrace();
                // Pi计算失败不影响FST结果返回
            }
            
            return outFstFile;
        } catch (Exception e) {
            System.err.println("Error in FST calculation for " + fileNameSuffix + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 执行FST计算（原有方法，用于两个Group的情况）
     */
    private void executeFSTCalculation(List<String> pop1Samples, List<String> pop2Samples, 
                                       String fileNameSuffix, List<String> leftGroupNames, List<String> rightGroupNames, int taskIndex) {
        // 禁用按钮，显示进度
        startButton.setEnabled(false);
        startButton.setText("Calculating...");
        final String compareDisplayName = currentTwoGroupCompareDisplay();
        
        // 在后台线程中执行FST计算
        Thread calcThread = new Thread(() -> {
            List<File> resultFiles = new ArrayList<>();
            Map<String, FSTRunManifest.RunEntry> manifestEntries = new HashMap<>();
            
            try {
                File resultFile = executeSingleFSTCalculation(pop1Samples, pop2Samples, fileNameSuffix, leftGroupNames, rightGroupNames, taskIndex);
                
                final String pop1Name = generateGroupNameString(leftGroupNames);
                final String pop2Name = generateGroupNameString(rightGroupNames);
                
                if (resultFile != null && !resultFiles.contains(resultFile)) {
                    resultFiles.add(resultFile);
                    collectPIFiles(resultFiles, pop1Name, pop2Name);
                }
                
                // 登记命名清单（卡牌显示名 + 悬停明细）
                registerComparisonEntries(manifestEntries, compareDisplayName, leftGroupNames, rightGroupNames);
                writeRunManifest(manifestEntries);
                
                if (!resultFiles.isEmpty()) {
                    // 使用新的简化方法，只传递工作目录路径
                    final File workDirectory = FSTOutputDirectory.getOutputDirectory();
                    SwingUtilities.invokeLater(() -> {
                        notifyResultsReady(workDirectory);
                        startButton.setEnabled(true);
                        startButton.setText("Start");
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this,
                            "No valid FST calculations completed.",
                            "Warning",
                            JOptionPane.WARNING_MESSAGE);
                        startButton.setEnabled(true);
                        startButton.setText("Start");
                    });
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                        "Error calculating FST: " + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                    startButton.setEnabled(true);
                    startButton.setText("Start");
                });
            }
        });
        calcThread.setDaemon(true);
        calcThread.start();
    }
    
    /**
     * 收集PI文件路径（包括两个群体间比较的PI文件）
     */
    private void collectPIFiles(List<File> resultFiles, String pop1Name, String pop2Name) {
        File vcfFile = new File(vcfFilePath);
        String vcfBaseName = vcfFile.getName();
        String baseName = vcfBaseName;
        if (baseName.endsWith(".gz")) {
            baseName = baseName.substring(0, baseName.length() - 3);
        }
        if (baseName.endsWith(".vcf")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }
        // 使用输出目录生成PI文件路径
        File outputDir = FSTOutputDirectory.getOutputDirectory();
        String piOutPrefix = new File(outputDir, baseName).getAbsolutePath();
        
        String piWindowedFile1 = piOutPrefix + "." + pop1Name + ".windowed.pi";
        File piFile1 = new File(piWindowedFile1);
        if (piFile1.exists() && !resultFiles.contains(piFile1)) {
            resultFiles.add(piFile1);
        }
        
        String piWindowedFile2 = piOutPrefix + "." + pop2Name + ".windowed.pi";
        File piFile2 = new File(piWindowedFile2);
        if (piFile2.exists() && !resultFiles.contains(piFile2)) {
            resultFiles.add(piFile2);
        }
        
        String piWindowedFile12 = piOutPrefix + "." + pop1Name + "_" + pop2Name + ".windowed.pi";
        File piFile12 = new File(piWindowedFile12);
        if (piFile12.exists() && !resultFiles.contains(piFile12)) {
            resultFiles.add(piFile12);
        }
    }

    /**
     * VCF基名（去掉目录与 .vcf/.gz 后缀）
     */
    private String getVcfBaseName() {
        File vcfFile = new File(vcfFilePath);
        String baseName = vcfFile.getName();
        if (baseName.endsWith(".gz")) {
            baseName = baseName.substring(0, baseName.length() - 3);
        }
        if (baseName.endsWith(".vcf")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }
        return baseName;
    }

    /**
     * 登记一次比较产生的所有结果文件的命名信息（FST/PI/TajimaD，只登记已生成的文件），
     * 供结果展示页的卡牌显示自定义名称与悬停明细
     */
    private void registerComparisonEntries(Map<String, FSTRunManifest.RunEntry> entries,
                                           String compareDisplayName,
                                           List<String> leftGroups, List<String> rightGroups) {
        File outputDir = FSTOutputDirectory.getOutputDirectory();
        if (outputDir == null || compareDisplayName == null || compareDisplayName.isEmpty()) return;
        String baseName = getVcfBaseName();
        String leftNames = String.join(" + ", leftGroups);
        String rightNames = String.join(" + ", rightGroups);
        String pop1 = generateGroupNameString(leftGroups);
        String pop2 = generateGroupNameString(rightGroups);
        String compareNote = "Comparison: " + compareDisplayName;

        // FST窗口化文件：显示Compare名称
        String suffix = generateFileNameFromGroups(leftGroups, rightGroups);
        addManifestEntryIfExists(entries, outputDir,
            baseName + "." + suffix + ".windowed.weir.fst",
            compareDisplayName,
            "Group (left): " + leftNames + "  vs  Group (right): " + rightNames);

        // PI窗口化文件：左侧群体、右侧群体、合并群体
        addManifestEntryIfExists(entries, outputDir,
            baseName + "." + pop1 + ".windowed.pi",
            leftNames,
            "Group: " + leftNames + " (left)\n" + compareNote);
        addManifestEntryIfExists(entries, outputDir,
            baseName + "." + pop2 + ".windowed.pi",
            rightNames,
            "Group: " + rightNames + " (right)\n" + compareNote);
        addManifestEntryIfExists(entries, outputDir,
            baseName + "." + pop1 + "_" + pop2 + ".windowed.pi",
            leftNames + " + " + rightNames,
            "Combined: " + leftNames + " + " + rightNames + "\n" + compareNote);

        // TajimaD窗口化文件：每个群体一个
        addManifestEntryIfExists(entries, outputDir,
            baseName + "." + pop1 + ".Tajima.D",
            leftNames,
            "Group: " + leftNames + " (left)\n" + compareNote);
        addManifestEntryIfExists(entries, outputDir,
            baseName + "." + pop2 + ".Tajima.D",
            rightNames,
            "Group: " + rightNames + " (right)\n" + compareNote);
    }

    /**
     * 仅当文件真实存在时登记清单条目；同一文件（如被多次比较共享的PI文件）保留第一条
     */
    private void addManifestEntryIfExists(Map<String, FSTRunManifest.RunEntry> entries, File dir,
                                          String fileName, String displayName, String description) {
        if (fileName == null || fileName.isEmpty()) return;
        if (new File(dir, fileName).isFile() && !entries.containsKey(fileName)) {
            entries.put(fileName, new FSTRunManifest.RunEntry(displayName, description));
        }
    }

    /**
     * 把命名清单写入当前运行目录（供结果展示页读取）
     */
    private void writeRunManifest(Map<String, FSTRunManifest.RunEntry> entries) {
        try {
            File dir = FSTOutputDirectory.getOutputDirectory();
            if (loadMode) {
                // 加载模式：与已有 run.names.tsv 合并，保留旧卡牌的命名
                FSTRunManifest.merge(dir, entries);
            } else {
                FSTRunManifest.write(dir, entries);
            }
        } catch (IOException e) {
            System.err.println("Failed to write the name manifest: " + e.getMessage());
        }
    }

    /**
     * 解析样本文本（每行一个样本名），并剔除本次运行被忽略的不可用样本
     */
    private List<String> parseSamples(String text) {
        List<String> samples = parseSamplesRaw(text);
        if (unavailableSamples.isEmpty()) {
            return samples;
        }
        List<String> usable = new ArrayList<>(samples.size());
        for (String sample : samples) {
            if (!unavailableSamples.contains(sample)) {
                usable.add(sample);
            }
        }
        return usable;
    }
    
    /**
     * 解析样本文本（每行一个样本名），不做任何过滤
     */
    private List<String> parseSamplesRaw(String text) {
        List<String> samples = new ArrayList<>();
        String[] lines = text.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty()) {
                samples.add(line);
            }
        }
        return samples;
    }

    /**
     * 写入样本文件（每行一个样本名）
     */
    private void writeSampleFile(File file, List<String> samples) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            for (String sample : samples) {
                writer.println(sample);
            }
        }
    }
    
    /**
     * 获取所有存在的Group名称列表（供VS面板管理器使用）
     */
    private List<String> getAllGroupNames() {
        List<String> groupNames = new ArrayList<>();
        
        // 检查rightPanel是否已初始化
        if (rightPanel == null) {
            return groupNames;
        }
        
        // 获取Group1和Group2以及动态添加的Group
        Component[] rightPanelComponents = rightPanel.getComponents();
        for (Component comp : rightPanelComponents) {
            if (comp instanceof JPanel) {
                JPanel panel = (JPanel) comp;
                String groupName = extractGroupNameFromPanel(panel);
                if (groupName != null) {
                    groupNames.add(groupName);
                }
            }
        }
        
        return groupNames;
    }
    
    /**
     * 从标题中提取Group名称
     */
    private String extractGroupNameFromTitle(String title) {
        if (title != null && title.startsWith("Group")) {
            int spaceIndex = title.indexOf(" ");
            return spaceIndex > 0 ? title.substring(0, spaceIndex) : title;
        }
        return null;
    }
    
    /**
     * 从TitledBorder中提取Group名称
     */
    private String extractGroupNameFromBorder(TitledBorder border) {
        return border != null ? extractGroupNameFromTitle(border.getTitle()) : null;
    }
    
    /**
     * 从面板中提取Group名称（优先名称标签，其次兼容旧的边框标题）
     */
    private String extractGroupNameFromPanel(JPanel panel) {
        // 检查面板内的类群名称标签
        if (panel != null) {
            JLabel header = findGroupNameHeader(panel);
            if (header != null && header.getText() != null && !header.getText().trim().isEmpty()) {
                return header.getText().trim();
            }
        }
        
        // 检查面板本身的边框
        if (panel.getBorder() instanceof TitledBorder) {
            String groupName = extractGroupNameFromBorder((TitledBorder) panel.getBorder());
            if (groupName != null) return groupName;
        }
        
        // 检查子面板的边框
        for (Component comp : panel.getComponents()) {
            if (comp instanceof JPanel) {
                JPanel subPanel = (JPanel) comp;
                if (subPanel.getBorder() instanceof TitledBorder) {
                    String groupName = extractGroupNameFromBorder((TitledBorder) subPanel.getBorder());
                    if (groupName != null) return groupName;
                }
            }
        }
        return null;
    }
    
    /**
     * 创建类群名称标签（双击改名）
     */
    private JLabel createGroupNameHeader(String name) {
        JLabel label = new JLabel(name);
        label.putClientProperty("__groupNameHeader__", Boolean.TRUE);
        label.setFont(simplegenomehub.population.fst.FstUiStyle.font(0, Font.BOLD));
        label.setForeground(new Color(60, 110, 160));
        label.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        label.setToolTipText("Group name: double-click to rename");
        return label;
    }
    
    /**
     * 在面板树中查找类群名称标签
     */
    private JLabel findGroupNameHeader(Container container) {
        if (container == null) return null;
        for (Component comp : container.getComponents()) {
            if (comp instanceof JLabel && Boolean.TRUE.equals(((JLabel) comp).getClientProperty("__groupNameHeader__"))) {
                return (JLabel) comp;
            }
        }
        // 递归查找子面板（动态类群面板外面还有一层容器）
        for (Component comp : container.getComponents()) {
            if (comp instanceof Container) {
                JLabel header = findGroupNameHeader((Container) comp);
                if (header != null) return header;
            }
        }
        return null;
    }
    
    /**
     * 给类群名称标签安装双击改名功能
     */
    private void installGroupNameRename(final JLabel label) {
        FST1JieMian2.installLabelRenameOnDoubleClick(label,
            newName -> {
                String err = FST1JieMian2.validateDisplayName(newName);
                if (err != null) return err;
                // 不能与其它类群同名（当前标签自己的文本除外）
                for (String other : getAllGroupNames()) {
                    if (other == null) continue;
                    if (!other.equals(label.getText()) && other.equals(newName)) {
                        return "Group names must be unique.";
                    }
                }
                return null;
            },
            (oldName, newName) -> onGroupRenamed(oldName, newName));
    }
    
    /**
     * 类群改名成功的同步处理
     */
    private void onGroupRenamed(String oldName, String newName) {
        if (oldName == null || newName == null || oldName.equals(newName)) return;
        // 同步VS面板中的类群矩形与自动比较名
        if (vsPanelManager != null) {
            vsPanelManager.renameGroup(oldName, newName);
        }
        // 刷新左侧"Add to ..."按钮
        updateLeftButtons();
        // 两组时的Compare自动名跟随类群名
        syncTwoGroupCompareIfAuto();
    }
    
    /**
     * 只有两个类群时的Compare显示名：
     * 手动自定义过则用自定义名，否则按当前类群名自动生成 "A_vs_B"
     */
    private String currentTwoGroupCompareDisplay() {
        if (twoGroupCompareCustomized && !twoGroupCompareName.isEmpty()) {
            return twoGroupCompareName;
        }
        List<String> names = getAllGroupNames();
        if (names.size() >= 2) {
            return names.get(0) + "_vs_" + names.get(1);
        }
        return "Group1_vs_Group2";
    }
    
    /**
     * 两组Compare标签双击改名
     */
    private void installTwoGroupCompareRename() {
        FST1JieMian2.installLabelRenameOnDoubleClick(twoGroupCompareLabel,
            FST1JieMian2::validateDisplayName,
            (oldName, newName) -> {
                twoGroupCompareCustomized = true;
                twoGroupCompareName = newName;
                twoGroupCompareLabel.setText(newName);
            });
    }
    
    /**
     * Compare名未被手动自定义时，跟随类群改名自动刷新显示
     */
    private void syncTwoGroupCompareIfAuto() {
        if (twoGroupCompareCustomized) return;
        if (twoGroupCompareLabel == null) return;
        twoGroupCompareLabel.setText(currentTwoGroupCompareDisplay());
    }
    
    /**
     * Compare名称入口只在恰好两个类群时显示（>=3 个类群走VS面板配置比较）
     */
    private void updateTwoGroupCompareVisibility() {
        boolean twoGroups = allPopAreas != null && allPopAreas.size() == 2;
        if (compareCaptionLabel != null) compareCaptionLabel.setVisible(twoGroups);
        if (twoGroupCompareLabel != null) twoGroupCompareLabel.setVisible(twoGroups);
    }
    
    /**
     * 创建VS面板更新监听器
     */
    private DocumentListener createVSPanelUpdateListener() {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { 
                if (vsPanelManager != null) vsPanelManager.updatePanel(); 
            }
            @Override
            public void removeUpdate(DocumentEvent e) { 
                if (vsPanelManager != null) vsPanelManager.updatePanel(); 
            }
            @Override
            public void changedUpdate(DocumentEvent e) { 
                if (vsPanelManager != null) vsPanelManager.updatePanel(); 
            }
        };
    }
    
    /**
     * 创建窗口大小监听器
     */
    private DocumentListener createWindowSizeListener(final int[] windowSizeRef) {
        return new DocumentListener() {
            private void update() {
                FST1JieMian2.updateWindowSize(windowSizeField, windowSizeRef);
                windowSize = windowSizeRef[0];
            }
            @Override
            public void insertUpdate(DocumentEvent e) { update(); }
            @Override
            public void removeUpdate(DocumentEvent e) { update(); }
            @Override
            public void changedUpdate(DocumentEvent e) { update(); }
        };
    }
    
    /**
     * 创建步长监听器
     */
    private DocumentListener createStepSizeListener(final int[] stepSizeRef) {
        return new DocumentListener() {
            private void update() {
                FST1JieMian2.updateStepSize(windowStepField, stepSizeRef);
                stepSize = stepSizeRef[0];
            }
            @Override
            public void insertUpdate(DocumentEvent e) { update(); }
            @Override
            public void removeUpdate(DocumentEvent e) { update(); }
            @Override
            public void changedUpdate(DocumentEvent e) { update(); }
        };
    }
    
    /**
     * 添加样本到文本框
     */
    private void addSamplesToTextArea(JTextArea textArea) {
        List<String> selected = sampleList.getSelectedValuesList();
        for (String sample : selected) {
            String current = textArea.getText().trim();
            // 移除所有连续换行符，替换为单个换行符
            current = current.replaceAll("\n{2,}", "\n");
            // 如果当前文本不为空，添加换行符
            if (!current.isEmpty() && !current.endsWith("\n")) {
                current += "\n";
            }
            // 添加新样本
            current += sample + "\n";
            // 再次移除连续换行符（防止sample本身包含换行符）
            current = current.replaceAll("\n{2,}", "\n");
            textArea.setText(current);
        }
    }
    
    /**
     * 添加动态群体面板
     */
    private void addDynamicPopulationPanel() {
        // 从3开始编号（前两个是pop1和pop2）；若与现存类群名冲突则顺延，避免重名
        int popNumber = dynamicPopPanels.size() + 3;
        List<String> existingNames = getAllGroupNames();
        while (existingNames.contains("Group" + popNumber)) {
            popNumber++;
        }
        
        // 创建面板容器
        JPanel containerPanel = new JPanel(new BorderLayout(5, 0));
        
        // 创建群体面板（名称可双击修改）
        JPanel popPanel = new JPanel(new BorderLayout());
        popPanel.setBorder(BorderFactory.createLineBorder(simplegenomehub.population.fst.FstUiStyle.CARD_BORDER, 1, true));
        JLabel nameHeader = createGroupNameHeader("Group" + popNumber);
        installGroupNameRename(nameHeader);
        popPanel.add(nameHeader, BorderLayout.NORTH);
        JTextArea popArea = new JTextArea(10, 20);
        popArea.setFont(simplegenomehub.population.fst.FstUiStyle.monospaced(12, Font.PLAIN));
        // 为popArea添加文件拖拽功能
        FST1JieMian2.setupTextAreaDropTarget(popArea);
        // 添加文本变化监听，更新VS面板
        popArea.getDocument().addDocumentListener(createVSPanelUpdateListener());
        allPopAreas.add(popArea);
        JScrollPane popScroll = new JScrollPane(popArea);
        popPanel.add(popScroll, BorderLayout.CENTER);
        
        // 创建减号按钮
        JButton removeButton = new JButton("-");
        removeButton.setPreferredSize(new Dimension(40, 30));
        removeButton.setToolTipText("Remove this group");
        removeButton.addActionListener(e -> removeDynamicPopulationPanel(containerPanel));
        
        // 将群体面板和删除按钮组合
        containerPanel.add(popPanel, BorderLayout.CENTER);
        containerPanel.add(removeButton, BorderLayout.EAST);
        
        // 添加到列表和面板
        containerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        dynamicPopPanels.add(containerPanel);
        
        // 找到"Add a group"按钮的位置，在其前面插入新面板
        int addButtonIndex = findButtonIndex(rightPanel, "Add a group");
        if (addButtonIndex >= 0) {
            rightPanel.add(groupColumnStrut(), addButtonIndex);
            rightPanel.add(containerPanel, addButtonIndex + 1);
        } else {
            rightPanel.add(groupColumnStrut());
            rightPanel.add(containerPanel);
        }
        
        // 刷新界面
        rightPanel.revalidate();
        rightPanel.repaint();
        
        // 更新左侧按钮列表
        updateLeftButtons();
        
        // 更新VS面板显示状态
        if (vsPanelManager != null) {
            vsPanelManager.updateVisibility(allPopAreas.size());
        }
        // Compare名称入口只在两个类群时可见
        updateTwoGroupCompareVisibility();
    }
    
    /**
     * 删除动态群体面板
     */
    private void removeDynamicPopulationPanel(JPanel panelToRemove) {
        // 从列表中移除
        if (dynamicPopPanels.remove(panelToRemove)) {
            // 从panelToRemove中提取JTextArea并从allPopAreas中移除
            JTextArea popAreaToRemove = extractTextAreaFromPanel(panelToRemove);
            if (popAreaToRemove != null) {
                allPopAreas.remove(popAreaToRemove);
            }
            
            // 移除面板及其前面的间距
            removePanelWithSpacing(rightPanel, panelToRemove);
            
            // 刷新界面
            rightPanel.revalidate();
            rightPanel.repaint();
            
            // 更新左侧按钮列表
            updateLeftButtons();
            
            // 更新VS面板显示状态和群体列表
            if (vsPanelManager != null) {
                vsPanelManager.updateVisibility(allPopAreas.size());
            }
            // Compare名称入口只在两个类群时可见
            updateTwoGroupCompareVisibility();
        }
    }
    
    /**
     * 更新左侧按钮列表（根据Group数量动态创建）
     */
    private void updateLeftButtons() {
        if (leftButtonPanel == null) return;
        
        // 清除所有现有按钮
        leftButtonPanel.removeAll();
        
        // 获取所有Group名称
        List<String> groupNames = getAllGroupNames();
        
        // 为每个Group创建按钮：铺满整栏宽度，形成一条横向连通的按钮条
        for (int i = 0; i < groupNames.size(); i++) {
            String groupName = groupNames.get(i);
            JTextArea targetArea = allPopAreas.get(i);
            
            JButton button = new JButton("Add to " + groupName);
            button.addActionListener(e -> addSamplesToTextArea(targetArea));
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            makeFullWidth(button);
            
            leftButtonPanel.add(button);
            if (i < groupNames.size() - 1) {
                leftButtonPanel.add(Box.createVerticalStrut(5));  // 按钮间距
            }
        }
        
        // 刷新界面
        leftButtonPanel.revalidate();
        leftButtonPanel.repaint();
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
            // 如果前面有间距组件，也移除它
            if (index > 0 && container.getComponent(index - 1) instanceof Box.Filler) {
                container.remove(index - 1);
            }
        }
    }
    
    /**
     * 从面板中提取JTextArea（兼容直接包含文本框的面板与外面再包一层容器的面板）
     */
    private JTextArea extractTextAreaFromPanel(JPanel containerPanel) {
        for (Component comp : containerPanel.getComponents()) {
            if (comp instanceof JScrollPane) {
                Component view = ((JScrollPane) comp).getViewport().getView();
                if (view instanceof JTextArea) {
                    return (JTextArea) view;
                }
            }
        }
        for (Component comp : containerPanel.getComponents()) {
            if (comp instanceof JPanel) {
                JTextArea found = extractTextAreaFromPanel((JPanel) comp);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
    
    /**
     * 根据群体名称列表生成文件名后缀（用于FST文件）
     */
    String generateFileNameFromGroups(List<String> leftGroups, List<String> rightGroups) {
        StringBuilder sb = new StringBuilder();
        
        // 左侧群体名称，用下划线连接
        if (!leftGroups.isEmpty()) {
            sb.append(String.join("", leftGroups));
        }
        
        sb.append("_vs_");
        
        // 右侧群体名称，直接拼接
        if (!rightGroups.isEmpty()) {
            sb.append(String.join("", rightGroups));
        }
        
        return sb.toString();
    }
    
    /**
     * 根据群体名称列表生成单个群体名称字符串（用于PI文件）
     */
    String generateGroupNameString(List<String> groupNames) {
        if (groupNames == null || groupNames.isEmpty()) {
            return "pop";
        }
        // 如果有多个群体，直接拼接
        return String.join("", groupNames);
    }
}
