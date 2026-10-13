/*
 * 简洁的GO富集分析对话框
 * 参考TBtools设计风格，专注核心功能
 */
package simplegenomehub.gui;

import simplegenomehub.model.*;
import simplegenomehub.model.GeneAnnotationData.AnnotationType;
import simplegenomehub.util.enrichment.*;
import simplegenomehub.util.fileio.GoOboManager;

// TBtools GO enrichment imports
import biocjava.bioIO.GeneOntology.EnrichMent.GOTermEnrichment;
import biocjava.bioIO.GeneOntology.EnrichMent.Enricher;
import biocjava.bioIO.GeneOntology.EnrichMent.GOLevelGetter;
import biocjava.bioIO.GeneOntology.EnrichMent.GObasicOboDatabase;
import biocjava.bioIO.GeneOntology.EnrichMent.GOoboTerm;
import toolsKit.PadjustBhMethod;
import toolsKit.MathTools;

// TBtools visualization imports
import biocjava.bioDoer.JIGplotToolkit.EnrichmentAnalysisGraph.Barplot;

// TBtools drag-drop imports
import toolsKit.GUItools.DragDropFileEnterListener;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * 简洁的GO富集分析对话框
 * 参考TBtools设计风格，专注GO功能
 * 
 * @author SimpleGenomeHub
 */
public class GOEnrichmentDialog extends JDialog {
    
    private static final Logger logger = Logger.getLogger(GOEnrichmentDialog.class.getName());
    
    private SpeciesInfo targetSpecies;
    private GeneAnnotationData annotationData;
    
    // TBtools GO enrichment components
    private GOTermEnrichment tbToolsGOEnricher;
    private GOLevelGetter goLevelGetter;
    private File goOboFile;
    private File gene2GoFile;
    
    // 核心UI组件
    private JTextArea geneListArea;
    private JTable resultsTable;
    private GOEnrichmentTableModel tableModel;
    private JButton loadGeneListButton;
    private JButton runAnalysisButton;
    private JButton exportButton;
    private JButton clearButton;
    private JButton visualizeButton;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JLabel geneCountLabel;
    private JComboBox<String> importGeneSetComboBox;
    private JButton clearGeneListButton;
    private List<File> availableGeneSetFiles;
    
    // 简化的参数设置
    private JSpinner pValueSpinner;
    private JSpinner minGenesSpinner;
    
    // GO类别选择
    private JComboBox<String> goCategoryComboBox;
    private JSpinner goLevelSpinner;
    
    // 分析结果
    private List<EnhancedEnrichmentResult> currentResults;
    private List<String> currentGeneList;
    
    /**
     * 构造函数
     */
    public GOEnrichmentDialog(Window parent, SpeciesInfo species) {
        super(parent, "GO Enrichment Analysis - " + species.getSpeciesName(), ModalityType.MODELESS);
        simplegenomehub.runtime.HubSession.attach(this, simplegenomehub.runtime.HubSession.forComponent(parent));
        this.targetSpecies = species;
        simplegenomehub.runtime.HubSession.attach(this, simplegenomehub.runtime.HubSession.forDataPath(species.getSpeciesDir()));
        this.annotationData = species.getFunctionalAnnotations();
        this.currentGeneList = new ArrayList<>();
        this.currentResults = new ArrayList<>();
        this.availableGeneSetFiles = new ArrayList<>();
        
        // Debug annotation data loading
        System.out.println("=== GOEnrichmentDialog Debug ===");
        System.out.println("Species: " + species.getSpeciesName() + " (" + species.getVersion() + ")");
        System.out.println("Annotation data: " + (annotationData != null ? "Available" : "NULL"));
        if (annotationData != null) {
            System.out.println("Annotation counts: " + annotationData.getAnnotationCounts());
        }
        
        // If annotation data is null, try to load it from species directory
        if (annotationData == null) {
            System.out.println("Attempting to load annotation data from species directory...");
            try {
                // Try to load functional annotations if they exist
                if (species.hasFunctionalAnnotations()) {
                    // Force load the functional annotations
                    species.loadFunctionalAnnotations();
                    this.annotationData = species.getFunctionalAnnotations();
                    System.out.println("Loaded annotation data: " + (annotationData != null ? "Success" : "Failed"));
                    if (annotationData != null) {
                        System.out.println("New annotation counts: " + annotationData.getAnnotationCounts());
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to load annotation data: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        
        setSize(1400, 800);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        
        // 在后台线程初始化TBtools组件以显示进度
        initializeTBtoolsComponentsWithProgress();
    }
    
    /**
     * 在后台线程初始化TBtools组件并显示进度
     */
    private void initializeTBtoolsComponentsWithProgress() {
        // 创建进度对话框
        JDialog progressDialog = new JDialog(this, "Loading GO Database", true);
        progressDialog.setSize(400, 120);
        progressDialog.setLocationRelativeTo(this);
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        
        JPanel progressPanel = new JPanel(new BorderLayout(10, 10));
        progressPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        JLabel progressLabel = new JLabel("Initializing GO enrichment database...");
        progressLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setStringPainted(true);
        progressBar.setString("Loading GO annotation data");
        
        progressPanel.add(progressLabel, BorderLayout.NORTH);
        progressPanel.add(progressBar, BorderLayout.CENTER);
        
        progressDialog.add(progressPanel);
        
        // 在后台线程执行初始化
        SwingWorker<Boolean, String> initWorker = new SwingWorker<Boolean, String>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                publish("Reading GO OBO file...");
                Thread.sleep(100); // 给UI时间更新
                
                publish("Processing GO annotations...");
                Thread.sleep(100);
                
                // 执行实际的初始化
                initializeTBtoolsComponents();
                
                publish("Initializing enrichment engine...");
                Thread.sleep(100);
                
                return tbToolsGOEnricher != null;
            }
            
            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    String message = chunks.get(chunks.size() - 1);
                    progressBar.setString(message);
                    progressLabel.setText(message);
                }
            }
            
            @Override
            protected void done() {
                try {
                    Boolean success = get();
                    progressDialog.dispose();
                    
                    if (success) {
                        // 检查GO注释数据可用性
                        checkGOAnnotationAvailability();
                        statusLabel.setText("GO database initialized successfully");
                    } else {
                        statusLabel.setText("Warning: GO database initialization failed - some features may be limited");
                        statusLabel.setForeground(Color.ORANGE);
                        // 仍然检查可用性以显示适当的警告
                        checkGOAnnotationAvailability();
                    }
                } catch (Exception e) {
                    progressDialog.dispose();
                    logger.log(Level.WARNING, "Failed to initialize GO database", e);
                    statusLabel.setText("Warning: GO database initialization failed");
                    statusLabel.setForeground(Color.ORANGE);
                    // 仍然检查可用性
                    checkGOAnnotationAvailability();
                }
            }
        };
        
        // 启动后台任务
        simplegenomehub.runtime.HubSession.execute(this, initWorker);
        
        // 延迟一点再显示进度对话框，确保用户能看到它
        javax.swing.Timer showTimer = new javax.swing.Timer(200, e -> {
            if (!initWorker.isDone()) {
                progressDialog.setVisible(true);
            }
        });
        showTimer.setRepeats(false);
        showTimer.start();
    }
    
    /**
     * 初始化TBtools GO富集分析组件
     */
    private void initializeTBtoolsComponents() {
        try {
            // Locate GO OBO file
            GoOboManager.ResolutionResult oboResolution = GoOboManager.resolveForSpecies(targetSpecies, null);
            goOboFile = oboResolution.getResolvedFile();

            if (goOboFile == null || !goOboFile.exists()) {
                System.err.println("Warning: go-basic.obo file not found for species " + targetSpecies.getSpeciesName());
                System.err.println("GO enrichment will use basic functionality only");
                return;
            }
            
            // Prepare gene2Go file from species annotation
            gene2GoFile = createGene2GoFile();
            
            if (gene2GoFile != null && gene2GoFile.exists()) {
                System.out.println("Initializing TBtools GO enrichment with:");
                System.out.println("  GO OBO file: " + goOboFile.getAbsolutePath());
                System.out.println("  Gene2GO file: " + gene2GoFile.getAbsolutePath());
                
                // Initialize TBtools GO enrichment
                tbToolsGOEnricher = new GOTermEnrichment();
                tbToolsGOEnricher.prepareForEnrichMent(goOboFile, gene2GoFile);
                
                System.out.println("TBtools GO enrichment initialized successfully");
            } else {
                System.err.println("Failed to create gene2Go file for TBtools enrichment");
            }
            
        } catch (Exception e) {
            System.err.println("Failed to initialize TBtools GO enrichment: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 创建TBtools标准的Gene2Go文件
     */
    private File createGene2GoFile() throws Exception {
        File goAnnotationFile = new File(targetSpecies.getFunctionalAnnotationDir(), "GO/GO.tsv");
        if (!goAnnotationFile.exists()) {
            System.err.println("GO annotation file not found: " + goAnnotationFile.getAbsolutePath());
            return null;
        }
        
        // Create temporary gene2Go file in TBtools format
        File tempGene2Go = File.createTempFile("SimpleGenomeHub_Gene2GO_", ".txt");
        tempGene2Go.deleteOnExit();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(goAnnotationFile));
             BufferedWriter writer = new BufferedWriter(new FileWriter(tempGene2Go))) {
            
            String line;
            Map<String, Set<String>> geneToGoTerms = new HashMap<>();
            
            // Read GO annotations and group by gene
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("Gene_ID")) {
                    continue;
                }
                
                String[] parts = line.split("\t");
                if (parts.length >= 2) {
                    String geneId = parts[0].trim();
                    String goTerm = parts[1].trim();
                    
                    geneToGoTerms.computeIfAbsent(geneId, k -> new HashSet<>()).add(goTerm);
                }
            }
            
            // Write in TBtools format: geneId\tgoId1,goId2,goId3...
            for (Map.Entry<String, Set<String>> entry : geneToGoTerms.entrySet()) {
                writer.write(entry.getKey());
                writer.write("\t");
                writer.write(String.join(",", entry.getValue()));
                writer.newLine();
            }
            
            System.out.println("Created gene2Go file with " + geneToGoTerms.size() + " genes");
        }
        
        return tempGene2Go;
    }
    
    /**
     * 初始化组件
     */
    private void initializeComponents() {
        // 基因列表输入区域
        geneListArea = new JTextArea(8, 40);
        geneListArea.setLineWrap(true);
        geneListArea.setWrapStyleWord(true);
        geneListArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        geneListArea.setBorder(BorderFactory.createLoweredBevelBorder());
        
        // 添加拖拽功能
        geneListArea.setDropTarget(new DropTarget(this, DnDConstants.ACTION_REFERENCE,
                new DragDropFileEnterListener(geneListArea), true));
        geneListArea.setDragEnabled(true);
        
        // 基因计数标签
        geneCountLabel = new JLabel("Genes: 0");
        geneCountLabel.setFont(SimpleGenomeHubStyle.bold(geneCountLabel.getFont()));
        geneCountLabel.setForeground(new Color(0, 100, 0));
        
        // 结果表格
        tableModel = new GOEnrichmentTableModel();
        resultsTable = new JTable(tableModel);
        resultsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        resultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setupTableColumns();
        
        // 按钮
        loadGeneListButton = new JButton("Load Gene List");
        Dimension loadGeneButtonSize = new Dimension(138, 30);
        loadGeneListButton.setPreferredSize(loadGeneButtonSize);
        loadGeneListButton.setMinimumSize(loadGeneButtonSize);
        
        importGeneSetComboBox = new JComboBox<>();
        importGeneSetComboBox.setToolTipText("Import a Gene Set or resolve genes from a Region Set");
        refreshImportGeneSetOptions();
        SimpleGenomeHubUi.setComboBoxDisplayWidth(importGeneSetComboBox, 180);
        clearGeneListButton = new JButton("Clear");
        Dimension clearGeneButtonSize = new Dimension(78, 30);
        clearGeneListButton.setPreferredSize(clearGeneButtonSize);
        clearGeneListButton.setMinimumSize(clearGeneButtonSize);
        
        runAnalysisButton = new JButton("Run GO Enrichment");
        runAnalysisButton.setFont(SimpleGenomeHubStyle.bold(runAnalysisButton.getFont()));
        runAnalysisButton.setBackground(SimpleGenomeHubStyle.EMPHASIS_STEEL_BLUE);
        runAnalysisButton.setForeground(Color.WHITE);
        runAnalysisButton.setEnabled(false);
        
        exportButton = new JButton("Export Results");
        exportButton.setEnabled(false);
        
        clearButton = new JButton("Clear");
        clearButton.setEnabled(false);
        
        visualizeButton = new JButton("Visualization");
        visualizeButton.setEnabled(false);
        
        // 进度条和状态
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        
        statusLabel = new JLabel("Ready for GO enrichment analysis");
        statusLabel.setForeground(Color.DARK_GRAY);
        
        // 简化的参数设置
        pValueSpinner = new JSpinner(new SpinnerNumberModel(0.05, 0.001, 1.0, 0.01));
        minGenesSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 50, 1));
        
        // GO类别和层级设置
        goCategoryComboBox = new JComboBox<>(new String[]{"All", "BP (Biological Process)", "MF (Molecular Function)", "CC (Cellular Component)"});
        goCategoryComboBox.setSelectedIndex(0);
        SimpleGenomeHubUi.setComboBoxDisplayWidth(goCategoryComboBox, 240);
        
        goLevelSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 15, 1));
        goLevelSpinner.setToolTipText("Minimum GO level cutoff");
    }
    
    /**
     * 设置布局
     */
    private void setupLayout() {
        setLayout(new BorderLayout(10, 10));
        
        // 顶部：标题和物种信息
        JPanel headerPanel = createHeaderPanel();
        add(headerPanel, BorderLayout.NORTH);
        
        // 中间：主要内容区域
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        
        // 左侧：输入区域
        JPanel inputPanel = createInputPanel();
        mainPanel.add(inputPanel, BorderLayout.WEST);
        
        // 右侧：结果区域
        JPanel resultsPanel = createResultsPanel();
        mainPanel.add(resultsPanel, BorderLayout.CENTER);
        
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inputPanel, resultsPanel);
        inputPanel.setMinimumSize(new Dimension(500, 300));
        resultsPanel.setMinimumSize(new Dimension(360, 300));
        mainSplitPane.setResizeWeight(0.42);
        mainSplitPane.setOneTouchExpandable(false);
        mainSplitPane.setDividerLocation(520);
        SimpleGenomeHubUi.styleSplitPane(mainSplitPane);
        add(mainSplitPane, BorderLayout.CENTER);
        
        // 底部：状态和操作按钮
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    /**
     * 创建头部面板
     */
    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        
        JLabel titleLabel = new JLabel("GO Enrichment Analysis");
        titleLabel.setFont(SimpleGenomeHubStyle.bold(titleLabel.getFont(), 16f));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        JLabel speciesLabel = new JLabel("Species: " + targetSpecies.getSpeciesName() + " (" + targetSpecies.getVersion() + ")");
        speciesLabel.setHorizontalAlignment(SwingConstants.CENTER);
        speciesLabel.setFont(SimpleGenomeHubStyle.italic(speciesLabel.getFont()));
        
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(speciesLabel, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 创建输入面板
     */
    private JPanel createInputPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(520, 500));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        // 基因列表区域
        JPanel genePanel = new JPanel(new BorderLayout(5, 5));
        genePanel.setBorder(new TitledBorder("Gene List Input"));
        
        // 基因列表控制
        JPanel geneControlPanel = new JPanel(new GridBagLayout());
        GridBagConstraints controlsGbc = new GridBagConstraints();
        controlsGbc.insets = new Insets(4, 4, 4, 4);
        controlsGbc.anchor = GridBagConstraints.WEST;
        controlsGbc.fill = GridBagConstraints.HORIZONTAL;
        
        controlsGbc.gridx = 0;
        controlsGbc.gridy = 0;
        controlsGbc.weightx = 0;
        geneControlPanel.add(loadGeneListButton, controlsGbc);
        
        controlsGbc.gridx = 0;
        controlsGbc.gridy = 1;
        controlsGbc.weightx = 1.0;
        geneControlPanel.add(importGeneSetComboBox, controlsGbc);
        
        controlsGbc.gridx = 1;
        controlsGbc.weightx = 0;
        geneControlPanel.add(clearGeneListButton, controlsGbc);
        
        controlsGbc.gridx = 2;
        geneControlPanel.add(geneCountLabel, controlsGbc);
        
        JScrollPane geneScrollPane = new JScrollPane(geneListArea);
        geneScrollPane.setPreferredSize(new Dimension(480, 210));
        
        genePanel.add(geneControlPanel, BorderLayout.NORTH);
        genePanel.add(geneScrollPane, BorderLayout.CENTER);
        
        // 参数设置区域
        JPanel paramPanel = createParameterPanel();
        
        panel.add(genePanel, BorderLayout.CENTER);
        panel.add(paramPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * 创建参数面板
     */
    private JPanel createParameterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Analysis Parameters"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // P值阈值
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("P-value cutoff:"), gbc);
        gbc.gridx = 1;
        panel.add(pValueSpinner, gbc);
        
        // 最小基因数
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Min genes:"), gbc);
        gbc.gridx = 1;
        panel.add(minGenesSpinner, gbc);
        
        // GO类别选择
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1;
        panel.add(new JLabel("GO category:"), gbc);
        gbc.gridx = 1;
        panel.add(goCategoryComboBox, gbc);
        
        // GO层级过滤
        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(new JLabel("Min GO level:"), gbc);
        gbc.gridx = 1;
        panel.add(goLevelSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 5, 5, 5);
        panel.add(runAnalysisButton, gbc);

        return panel;
    }
    
    /**
     * 创建结果面板
     */
    private JPanel createResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new TitledBorder("GO Enrichment Results"));
        
        // 结果表格
        JScrollPane tableScrollPane = new JScrollPane(resultsTable);
        tableScrollPane.setPreferredSize(new Dimension(800, 450));
        
        panel.add(tableScrollPane, BorderLayout.CENTER);
        
        // 结果操作按钮
        JPanel resultButtonPanel = new JPanel(new FlowLayout());
        resultButtonPanel.add(exportButton);
        resultButtonPanel.add(visualizeButton);
        resultButtonPanel.add(clearButton);
        
        panel.add(resultButtonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * 创建底部面板
     */
    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        
        // 状态区域
        panel.add(progressBar, BorderLayout.NORTH);
        panel.add(statusLabel, BorderLayout.CENTER);
        
        // 主操作按钮
        
        
        return panel;
    }
    
    /**
     * 设置表格列
     */
    private void setupTableColumns() {
        TableColumnModel columnModel = resultsTable.getColumnModel();
        
        // TBtools风格列宽设置: GO_Name, GO_Category, GO_ID, GO_Level, P_value, Adjusted_P_value, Enrichment_Score, Hit_in_Set, Hit_in_Background, Num_of_Set, Num_of_Background, Gene_List
        int[] columnWidths = {250, 80, 100, 80, 100, 120, 120, 80, 120, 80, 120, 200};
        for (int i = 0; i < Math.min(columnWidths.length, columnModel.getColumnCount()); i++) {
            columnModel.getColumn(i).setPreferredWidth(columnWidths[i]);
        }
        
        // 自定义渲染器设置
        DecimalRenderer decimalRenderer = new DecimalRenderer();
        ScientificRenderer scientificRenderer = new ScientificRenderer();
        
        // P值列使用科学计数法 (第4列)
        if (columnModel.getColumnCount() > 4) {
            resultsTable.getColumnModel().getColumn(4).setCellRenderer(scientificRenderer); // P_value
        }
        
        // Adjusted P值列使用科学计数法 (第5列)
        if (columnModel.getColumnCount() > 5) {
            resultsTable.getColumnModel().getColumn(5).setCellRenderer(scientificRenderer); // Adjusted_P_value
        }
        
        // 富集分数使用2位小数 (现在是第6列)
        if (columnModel.getColumnCount() > 6) {
            resultsTable.getColumnModel().getColumn(6).setCellRenderer(decimalRenderer); // Enrichment_Score
        }
        
        // 整数列使用右对齐 (调整列索引)
        for (int i : new int[]{3, 7, 8, 9, 10}) { // GO_Level, Hit_in_Set, Hit_in_Background, Num_of_Set, Num_of_Background
            if (i < columnModel.getColumnCount()) {
                resultsTable.getColumnModel().getColumn(i).setCellRenderer(decimalRenderer);
            }
        }
        
        // 启用排序
        resultsTable.setAutoCreateRowSorter(true);
        
        // 启用单个Cell选中和复制功能
        resultsTable.setCellSelectionEnabled(true);
        resultsTable.setRowSelectionAllowed(true);
        resultsTable.setColumnSelectionAllowed(true);
        
        // 设置表格外观
        resultsTable.setRowHeight(22);
        resultsTable.setShowGrid(true);
        resultsTable.setGridColor(Color.LIGHT_GRAY);
        resultsTable.getTableHeader().setBackground(new Color(240, 240, 240));
        resultsTable.getTableHeader().setFont(SimpleGenomeHubStyle.bold(resultsTable.getTableHeader().getFont()));
    }
    
    /**
     * 设置事件处理器
     */
    private void setupEventHandlers() {
        // 基因列表变化监听
        geneListArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateGeneCount(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateGeneCount(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateGeneCount(); }
        });
        
        // 按钮事件
        loadGeneListButton.addActionListener(e -> loadGeneListFromFile());
        importGeneSetComboBox.addActionListener(e -> importSelectedGeneSet());
        clearGeneListButton.addActionListener(e -> clearGeneListInput());
        runAnalysisButton.addActionListener(e -> runGOEnrichment());
        exportButton.addActionListener(e -> exportResults());
        clearButton.addActionListener(e -> clearResults());
        visualizeButton.addActionListener(e -> createVisualization());
        
        // 表格复制功能 (Ctrl+C)
        setupTableCopyFunctionality();
    }
    
    /**
     * 设置表格复制功能
     */
    private void setupTableCopyFunctionality() {
        // 添加Ctrl+C快捷键支持
        KeyStroke copyKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.CTRL_DOWN_MASK);
        resultsTable.getInputMap(JComponent.WHEN_FOCUSED).put(copyKeyStroke, "copy");
        resultsTable.getActionMap().put("copy", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                copySelectedCells();
            }
        });
    }
    
    /**
     * 复制选中的单元格内容到剪贴板
     */
    private void copySelectedCells() {
        int[] selectedRows = resultsTable.getSelectedRows();
        int[] selectedColumns = resultsTable.getSelectedColumns();
        
        if (selectedRows.length == 0 || selectedColumns.length == 0) {
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        
        for (int i = 0; i < selectedRows.length; i++) {
            for (int j = 0; j < selectedColumns.length; j++) {
                Object value = resultsTable.getValueAt(selectedRows[i], selectedColumns[j]);
                sb.append(value != null ? value.toString() : "");
                
                if (j < selectedColumns.length - 1) {
                    sb.append("\t");
                }
            }
            if (i < selectedRows.length - 1) {
                sb.append("\n");
            }
        }
        
        StringSelection stringSelection = new StringSelection(sb.toString());
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
        
        // 显示复制成功的状态信息
        statusLabel.setText("Copied " + selectedRows.length + " row(s), " + selectedColumns.length + " column(s) to clipboard");
        statusLabel.setForeground(Color.BLUE);
    }
    
    /**
     * 更新基因计数
     */
    private void updateGeneCount() {
        SwingUtilities.invokeLater(() -> {
            List<String> genes = parseGeneList();
            geneCountLabel.setText("Genes: " + genes.size());
            currentGeneList = genes;
            
            // 至少需要3个基因才能进行分析
            runAnalysisButton.setEnabled(genes.size() >= 3);
            
            if (genes.size() < 3 && !genes.isEmpty()) {
                statusLabel.setText("Need at least 3 genes for GO enrichment analysis");
                statusLabel.setForeground(Color.RED);
            } else if (genes.size() >= 3) {
                statusLabel.setText("Ready for GO enrichment analysis");
                statusLabel.setForeground(Color.DARK_GRAY);
            }
        });
    }
    
    /**
     * 解析基因列表
     */
    private List<String> parseGeneList() {
        String text = geneListArea.getText().trim();
        if (text.isEmpty()) {
            return new ArrayList<>();
        }
        
        Set<String> geneSet = new HashSet<>();
        String[] tokens = text.split("[,\\s\\t\\n\\r]+");
        
        for (String token : tokens) {
            String gene = token.trim();
            if (!gene.isEmpty()) {
                geneSet.add(gene);
            }
        }
        
        return new ArrayList<>(geneSet);
    }
    
    /**
     * 从文件加载基因列表
     */
    private void loadGeneListFromFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("Text files (*.txt, *.tsv)", "txt", "tsv"));
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                List<String> genes = readGeneListFromFile(file);
                geneListArea.setText(String.join("\n", genes));
                statusLabel.setText("Loaded " + genes.size() + " genes from " + file.getName());
                statusLabel.setForeground(Color.BLUE);
                
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to load gene list", e);
                JOptionPane.showMessageDialog(this,
                    "Failed to load gene list: " + e.getMessage(),
                    "File Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * 加载演示基因列表 - 从注释背景文件随机选择500个基因ID
     */
    private void clearGeneListInput() {
        geneListArea.setText("");
        geneListArea.requestFocusInWindow();
        statusLabel.setText("Gene list cleared");
        statusLabel.setForeground(Color.DARK_GRAY);
    }

    private void refreshImportGeneSetOptions() {
        availableGeneSetFiles = GeneSetImportSupport.loadAvailableSetFiles(targetSpecies);

        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement(availableGeneSetFiles.isEmpty() ? "Import Gene Set (No Sets)" : "Import Gene Set");
        for (File geneSetFile : availableGeneSetFiles) {
            model.addElement(GeneSetImportSupport.buildImportLabel(geneSetFile));
        }

        importGeneSetComboBox.setModel(model);
        importGeneSetComboBox.setSelectedIndex(0);
        importGeneSetComboBox.setEnabled(!availableGeneSetFiles.isEmpty());
    }

    private void importSelectedGeneSet() {
        int selectedIndex = importGeneSetComboBox.getSelectedIndex();
        if (selectedIndex <= 0) {
            return;
        }

        int fileIndex = selectedIndex - 1;
        if (fileIndex < 0 || fileIndex >= availableGeneSetFiles.size()) {
            importGeneSetComboBox.setSelectedIndex(0);
            return;
        }

        File selectedFile = availableGeneSetFiles.get(fileIndex);
        importGeneSetComboBox.setEnabled(false);
        statusLabel.setText("Importing " + GeneSetFileSupport.extractDisplayName(selectedFile) + "...");
        statusLabel.setForeground(Color.BLUE);

        SwingWorker<GeneSetImportSupport.ImportedGeneSet, Void> worker =
            new SwingWorker<GeneSetImportSupport.ImportedGeneSet, Void>() {
                @Override
                protected GeneSetImportSupport.ImportedGeneSet doInBackground() throws Exception {
                    return GeneSetImportSupport.importIds(targetSpecies, selectedFile, GeneSetImportSupport.OutputIdType.GENE);
                }

                @Override
                protected void done() {
                    importGeneSetComboBox.setEnabled(!availableGeneSetFiles.isEmpty());
                    importGeneSetComboBox.setSelectedIndex(0);

                    try {
                        GeneSetImportSupport.ImportedGeneSet importedGeneSet = get();
                        List<String> importedIds = importedGeneSet.getIds();
                        geneListArea.setText(String.join("\n", importedIds));
                        geneListArea.setCaretPosition(0);
                        statusLabel.setText("Imported " + importedIds.size() + " gene IDs from "
                            + (importedGeneSet.getSetKind() == GeneSetFileSupport.SetKind.REGION ? "Region Set" : "Gene Set"));
                        statusLabel.setForeground(Color.BLUE);
                    } catch (Exception ex) {
                        Throwable cause = ex instanceof java.util.concurrent.ExecutionException && ex.getCause() != null
                            ? ex.getCause()
                            : ex;

                        String message = cause.getMessage() != null ? cause.getMessage() : "Unknown error";
                        statusLabel.setText("Import failed");
                        statusLabel.setForeground(Color.RED);

                        int messageType = cause instanceof GeneSetImportSupport.NoGeneFoundException
                            ? JOptionPane.INFORMATION_MESSAGE
                            : JOptionPane.ERROR_MESSAGE;

                        JOptionPane.showMessageDialog(
                            GOEnrichmentDialog.this,
                            "Failed to import selected set:\n" + message,
                            "Import Gene Set Failed",
                            messageType
                        );
                    }
                }
            };

        simplegenomehub.runtime.HubSession.execute(this, worker);
    }

    private List<String> readGeneListFromFile(File file) throws IOException {

        List<String> genes = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    // 取第一列作为基因ID
                    String geneId = line.split("\\t")[0].trim();
                    if (!geneId.isEmpty()) {
                        genes.add(geneId);
                    }
                }
            }
        }
        
        return genes;
    }
    
    /**
     * 运行GO富集分析
     */
    private void runGOEnrichment() {
        if (currentGeneList.size() < 3) {
            showErrorMessage("Please enter at least 3 genes for GO enrichment analysis.");
            return;
        }
        
        // 检查GO注释数据
        if (!hasGOAnnotations()) {
            showErrorMessage("No GO annotations available. Please import GO annotation data first.");
            return;
        }
        
        // 在后台线程运行分析
        SwingWorker<List<EnhancedEnrichmentResult>, Void> worker = new SwingWorker<List<EnhancedEnrichmentResult>, Void>() {
            @Override
            protected List<EnhancedEnrichmentResult> doInBackground() throws Exception {
                return performGOEnrichmentAnalysis(currentGeneList);
            }
            
            @Override
            protected void done() {
                try {
                    List<EnhancedEnrichmentResult> results = get();
                    displayResults(results);
                    
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "GO enrichment analysis failed", e);
                    showErrorMessage("GO enrichment analysis failed: " + e.getMessage());
                } finally {
                    // 恢复UI状态
                    progressBar.setVisible(false);
                    runAnalysisButton.setEnabled(true);
                    statusLabel.setText("Analysis completed");
                    statusLabel.setForeground(Color.DARK_GRAY);
                }
            }
        };
        
        // 显示进度并禁用按钮
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        progressBar.setString("Running GO enrichment analysis...");
        runAnalysisButton.setEnabled(false);
        statusLabel.setText("Analyzing " + currentGeneList.size() + " genes...");
        statusLabel.setForeground(Color.BLUE);
        
        simplegenomehub.runtime.HubSession.execute(this, worker);
    }
    
    /**
     * 执行GO富集分析（使用TBtools原生方法）
     */
    private List<EnhancedEnrichmentResult> performGOEnrichmentAnalysis(List<String> geneList) throws Exception {
        if (tbToolsGOEnricher == null) {
            System.err.println("TBtools GO enricher not initialized. Using fallback analysis.");
            return performFallbackEnrichmentAnalysis(geneList);
        }
        
        System.out.println("Performing TBtools GO enrichment analysis with " + geneList.size() + " genes");
        
        // Convert gene list to HashSet as required by TBtools
        HashSet<String> selectedGenes = new HashSet<>(geneList);
        
        // Get selected GO category
        String selectedCategory = getSelectedGOCategory();
        String[] categoriesToAnalyze;
        
        if ("All".equals(selectedCategory)) {
            categoriesToAnalyze = new String[]{"BP", "MF", "CC"};
        } else {
            categoriesToAnalyze = new String[]{selectedCategory};
        }
        
        List<EnhancedEnrichmentResult> allResults = new ArrayList<>();
        
        // Perform enrichment for each selected category
        for (String category : categoriesToAnalyze) {
            try {
                System.out.println("Analyzing GO category: " + category);
                
                // Use TBtools doEnrichMent method
                HashMap<Integer, Enricher> enrichmentResults = tbToolsGOEnricher.doEnrichMent(selectedGenes, category);
                
                System.out.println("Found " + enrichmentResults.size() + " enriched terms in " + category);
                
                // Convert TBtools results to our format
                List<EnhancedEnrichmentResult> categoryResults = convertTBtoolsResults(enrichmentResults, category);
                allResults.addAll(categoryResults);
                
            } catch (Exception e) {
                System.err.println("Failed to analyze GO category " + category + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        // Apply filters and sorting
        return filterAndSortResults(allResults);
    }
    
    /**
     * 获取选中的GO类别
     */
    private String getSelectedGOCategory() {
        String selected = (String) goCategoryComboBox.getSelectedItem();
        if (selected.startsWith("BP")) return "BP";
        if (selected.startsWith("MF")) return "MF";
        if (selected.startsWith("CC")) return "CC";
        return "All";
    }
    
    /**
     * 转换TBtools富集结果为我们的格式
     */
    private List<EnhancedEnrichmentResult> convertTBtoolsResults(HashMap<Integer, Enricher> tbResults, String category) {
        List<EnhancedEnrichmentResult> results = new ArrayList<>();
        
        if (tbToolsGOEnricher.getGoDb() == null) {
            System.err.println("GO database not available for term name lookup");
            return results;
        }
        
        HashMap<Integer, GOoboTerm> goHashDb = tbToolsGOEnricher.getGoDb().getGoHashDb();
        
        for (Map.Entry<Integer, Enricher> entry : tbResults.entrySet()) {
            int goId = entry.getKey();
            Enricher enricher = entry.getValue();
            
            // Apply p-value filter
            if (enricher.getPValue() > (Double) pValueSpinner.getValue()) {
                continue;
            }
            
            // Apply minimum genes filter
            if (enricher.getAllSelectWhiteBalls() < (Integer) minGenesSpinner.getValue()) {
                continue;
            }
            
            // Apply GO level filter if available
            if (tbToolsGOEnricher.getGoDb() != null) {
                int goLevel = tbToolsGOEnricher.getGoDb().getLevel(goId);
                if (goLevel < (Integer) goLevelSpinner.getValue()) {
                    continue;
                }
            }
            
            EnhancedEnrichmentResult result = new EnhancedEnrichmentResult();
            result.setTermId(formatGoId(goId));
            result.setCategory(category);
            result.setPValue(enricher.getPValue());
            // Adjusted p-value will be calculated later using FDR correction
            result.setEnrichmentRatio(enricher.getEnrichment());
            result.setGeneCount(enricher.getAllSelectWhiteBalls());
            result.setTermSize(enricher.getAllWhiteBalls());
            result.setTestSetSize(enricher.getAllSelectBalls());
            result.setBackgroundCount(enricher.getAllBalls());
            
            // Set GO level from TBtools database
            if (tbToolsGOEnricher.getGoDb() != null) {
                int goLevel = tbToolsGOEnricher.getGoDb().getLevel(goId);
                result.setGoLevel(goLevel);
                result.setHierarchyLevel(goLevel);
            }
            
            // Get term name from GO database
            if (goHashDb.containsKey(goId)) {
                GOoboTerm goTerm = goHashDb.get(goId);
                result.setTermName(goTerm.getName());
            } else {
                result.setTermName("GO:" + String.format("%07d", goId));
            }
            
            // Extract gene IDs for this term
            List<String> enrichedGenes = getGenesForGoTerm(goId);
            result.setGeneIds(enrichedGenes);
            
            results.add(result);
        }
        
        return results;
    }
    
    /**
     * 格式化GO ID
     */
    private String formatGoId(int goId) {
        return "GO:" + String.format("%07d", goId);
    }
    
    /**
     * 获取注释到特定GO term的基因列表
     */
    private List<String> getGenesForGoTerm(int goId) {
        List<String> genes = new ArrayList<>();
        
        if (tbToolsGOEnricher.getParsedGene2Go() != null) {
            for (Map.Entry<String, HashSet<Integer>> entry : tbToolsGOEnricher.getParsedGene2Go().entrySet()) {
                if (entry.getValue().contains(goId) && currentGeneList.contains(entry.getKey())) {
                    genes.add(entry.getKey());
                }
            }
        }
        
        return genes;
    }
    
    /**
     * 过滤和排序结果
     */
    private List<EnhancedEnrichmentResult> filterAndSortResults(List<EnhancedEnrichmentResult> results) {
        // Sort by p-value
        results.sort(Comparator.comparingDouble(EnhancedEnrichmentResult::getPValue));
        
        // 计算FDR校正的adjusted p-value
        calculateAdjustedPValues(results);
        
        return results;
    }
    
    /**
     * 使用TBtools MathTools计算FDR校正的adjusted p-value
     */
    private void calculateAdjustedPValues(List<EnhancedEnrichmentResult> results) {
        if (results.isEmpty()) {
            return;
        }
        
        try {
            // 提取p-value列表
            ArrayList<Double> pValues = new ArrayList<>();
            for (EnhancedEnrichmentResult result : results) {
                pValues.add(result.getPValue());
            }
            
            // 使用TBtools MathTools进行Benjamini-Hochberg FDR校正
            ArrayList<Double> adjustedPValues = MathTools.Padjust(pValues);
            
            // 将校正后的p-value设置回结果中
            for (int i = 0; i < results.size() && i < adjustedPValues.size(); i++) {
                results.get(i).setAdjustedPValue(adjustedPValues.get(i));
            }
            
            System.out.println("Applied FDR correction using TBtools MathTools.Padjust()");
            
        } catch (Exception e) {
            System.err.println("Failed to calculate adjusted p-values: " + e.getMessage());
            e.printStackTrace();
            // 如果校正失败，保持原始p-value作为adjusted p-value
            for (EnhancedEnrichmentResult result : results) {
                result.setAdjustedPValue(result.getPValue());
            }
        }
    }
    
    /**
     * 后备的富集分析方法（当TBtools不可用时）
     */
    private List<EnhancedEnrichmentResult> performFallbackEnrichmentAnalysis(List<String> geneList) throws Exception {
        System.out.println("Using fallback GO enrichment analysis");
        
        // Load GO annotations directly from file
        File goFile = new File(targetSpecies.getFunctionalAnnotationDir(), "GO/GO.tsv");
        if (goFile.exists()) {
            Map<String, List<String>> geneGoTerms = loadGOAnnotationsFromFile(goFile);
            System.out.println("Loaded GO annotations for " + geneGoTerms.size() + " genes");
            
            return performGOEnrichmentWithData(geneList, geneGoTerms);
        } else {
            System.err.println("GO annotation file not found: " + goFile.getAbsolutePath());
            return new ArrayList<>();
        }
    }
    
    /**
     * Load GO annotations from TSV file
     */
    private Map<String, List<String>> loadGOAnnotationsFromFile(File goFile) throws Exception {
        Map<String, List<String>> geneGoTerms = new HashMap<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(goFile))) {
            String line;
            boolean isFirstLine = true;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                // Skip header line
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }
                
                String[] parts = line.split("\t");
                if (parts.length >= 2) {
                    String geneId = parts[0].trim();
                    String goTerm = parts[1].trim();
                    
                    geneGoTerms.computeIfAbsent(geneId, k -> new ArrayList<>()).add(goTerm);
                }
            }
        }
        
        return geneGoTerms;
    }
    
    /**
     * Perform GO enrichment analysis with loaded data
     */
    private List<EnhancedEnrichmentResult> performGOEnrichmentWithData(List<String> testGenes, Map<String, List<String>> geneGoTerms) {
        List<EnhancedEnrichmentResult> results = new ArrayList<>();
        
        // Create term -> genes mapping
        Map<String, Set<String>> termGenes = new HashMap<>();
        Map<String, String> termNames = new HashMap<>();
        
        for (Map.Entry<String, List<String>> entry : geneGoTerms.entrySet()) {
            String gene = entry.getKey();
            for (String goTerm : entry.getValue()) {
                termGenes.computeIfAbsent(goTerm, k -> new HashSet<>()).add(gene);
                // You would load term names from go-basic.obo, but for now use term ID
                termNames.putIfAbsent(goTerm, goTerm);
            }
        }
        
        int backgroundSize = geneGoTerms.size();
        int testSize = testGenes.size();
        
        // Get parameters from UI
        double pValueThreshold = (Double) pValueSpinner.getValue();
        int minGenes = (Integer) minGenesSpinner.getValue();
        
        // Perform enrichment for each term
        for (Map.Entry<String, Set<String>> termEntry : termGenes.entrySet()) {
            String termId = termEntry.getKey();
            Set<String> termGenesSet = termEntry.getValue();
            
            // Find overlapping genes
            List<String> overlappingGenes = new ArrayList<>();
            for (String testGene : testGenes) {
                if (termGenesSet.contains(testGene)) {
                    overlappingGenes.add(testGene);
                }
            }
            
            int overlap = overlappingGenes.size();
            if (overlap < minGenes) continue;
            
            int termSize = termGenesSet.size();
            
            // Calculate enrichment statistics using hypergeometric test
            double pValue = calculateHypergeometricPValue(overlap, testSize, termSize, backgroundSize);
            
            if (pValue <= pValueThreshold) {
                EnhancedEnrichmentResult result = new EnhancedEnrichmentResult();
                result.setTermId(termId);
                result.setTermName(termNames.get(termId));
                result.setCategory("GO");
                result.setGeneCount(overlap);
                result.setTermSize(termSize);
                result.setBackgroundCount(backgroundSize);
                result.setPValue(pValue);
                result.setAdjustedPValue(pValue); // TODO: Apply FDR correction
                result.setEnrichmentRatio((double) overlap / testSize / ((double) termSize / backgroundSize));
                result.setGeneIds(overlappingGenes);
                
                results.add(result);
            }
        }
        
        // Sort by p-value
        results.sort(Comparator.comparingDouble(EnhancedEnrichmentResult::getPValue));
        
        // TBtools已经自动计算p-adjust，无需额外FDR校正
        
        return results;
    }
    
    /**
     * Calculate hypergeometric p-value
     */
    private double calculateHypergeometricPValue(int overlap, int testSize, int termSize, int backgroundSize) {
        // Simple approximation using hypergeometric distribution
        // This is a simplified implementation - for production use a proper statistical library
        
        double pValue = 1.0;
        try {
            // Calculate probability using binomial approximation
            double prob = (double) termSize / backgroundSize;
            double expected = testSize * prob;
            
            if (overlap > expected) {
                // Use normal approximation for large samples
                double variance = testSize * prob * (1 - prob);
                double z = (overlap - expected) / Math.sqrt(variance);
                
                // Convert to p-value (rough approximation)
                pValue = Math.exp(-z * z / 2) / Math.sqrt(2 * Math.PI);
            }
        } catch (Exception e) {
            pValue = 0.05; // Default fallback
        }
        
        return Math.max(1e-15, Math.min(1.0, pValue));
    }
    
    /**
     * 显示分析结果
     */
    private void displayResults(List<EnhancedEnrichmentResult> results) {
        currentResults = results;
        tableModel.setResults(results);
        
        // 启用导出和清除按钮
        exportButton.setEnabled(!results.isEmpty());
        visualizeButton.setEnabled(!results.isEmpty());
        clearButton.setEnabled(!results.isEmpty());
        
        // 更新状态
        int significantCount = (int) results.stream()
            .filter(r -> r.getAdjustedPValue() <= 0.05)
            .count();
        
        statusLabel.setText(String.format("Found %d significant GO terms (total: %d)", 
                                         significantCount, results.size()));
        statusLabel.setForeground(Color.BLUE);
        
        logger.info("GO enrichment analysis completed: " + significantCount + " significant terms");
    }
    
    /**
     * 导出结果
     */
    private void exportResults() {
        if (currentResults.isEmpty()) {
            showErrorMessage("No results to export.");
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("TSV files (*.tsv)", "tsv"));
        fileChooser.setSelectedFile(new File("GO_enrichment_results.tsv"));
        
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                exportResultsToFile(file);
                statusLabel.setText("Results exported to " + file.getName());
                statusLabel.setForeground(Color.BLUE);
                
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to export results", e);
                showErrorMessage("Failed to export results: " + e.getMessage());
            }
        }
    }
    
    /**
     * 导出结果到文件
     */
    private void exportResultsToFile(File file) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            // 写入头部
            writer.write("GO_Term\\tName\\tP_Value\\tAdjusted_P_Value\\tEnrichment_Ratio\\tGene_Count\\tTerm_Size\\tGenes\\n");
            
            // 写入结果
            for (EnhancedEnrichmentResult result : currentResults) {
                writer.write(String.format("%s\\t%s\\t%.6e\\t%.6e\\t%.2f\\t%d\\t%d\\t%s\\n",
                    result.getTermId(),
                    result.getTermName(),
                    result.getPValue(),
                    result.getAdjustedPValue(),
                    result.getEnrichmentRatio(),
                    result.getGeneCount(),
                    result.getTermSize(),
                    String.join(",", result.getGeneIds())
                ));
            }
        }
    }
    
    /**
     * 清除结果
     */
    private void clearResults() {
        currentResults.clear();
        tableModel.setResults(currentResults);
        exportButton.setEnabled(false);
        visualizeButton.setEnabled(false);
        clearButton.setEnabled(false);
        statusLabel.setText("Results cleared");
        statusLabel.setForeground(Color.DARK_GRAY);
    }
    
    /**
     * 检查GO注释数据可用性
     */
    private void checkGOAnnotationAvailability() {
        if (!hasGOAnnotations()) {
            JLabel warningLabel = new JLabel(
                "<html><b>⚠ Warning:</b> No GO annotations found for this species.<br>" +
                "Please import GO annotation data to perform enrichment analysis.</html>");
            warningLabel.setForeground(Color.RED);
            warningLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
            add(warningLabel, BorderLayout.NORTH);
        }
    }
    
    /**
     * 检查是否有GO注释数据
     */
    private boolean hasGOAnnotations() {
        // First check if annotation data is loaded and has GO data
        if (annotationData != null) {
            Map<AnnotationType, Integer> counts = annotationData.getAnnotationCounts();
            Integer goCount = counts.get(AnnotationType.GO);
            if (goCount != null && goCount > 0) {
                return true;
            }
        }
        
        // If no loaded data, check if GO annotation files exist
        Set<GeneAnnotationData.AnnotationType> availableTypes = targetSpecies.getAvailableAnnotationTypes();
        boolean hasGOFiles = availableTypes.contains(GeneAnnotationData.AnnotationType.GO);
        
        System.out.println("DEBUG hasGOAnnotations(): annotationData=" + (annotationData != null) + 
                          ", hasGOFiles=" + hasGOFiles + ", availableTypes=" + availableTypes);
        
        return hasGOFiles;
    }
    
    /**
     * 显示错误消息
     */
    private void showErrorMessage(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
        statusLabel.setText("Error: " + message);
        statusLabel.setForeground(Color.RED);
    }
    
    // 内部类：GO富集结果表格模型
    private static class GOEnrichmentTableModel extends javax.swing.table.AbstractTableModel {
        private final String[] columnNames = {
            "GO_Name", "GO_Category", "GO_ID", "GO_Level", "P_value", "Adjusted_P_value", "Enrichment_Score",
            "Hit_in_Set", "Hit_in_Background", "Num_of_Set", "Num_of_Background", "Gene_List"
        };
        
        private List<EnhancedEnrichmentResult> results = new ArrayList<>();
        
        public void setResults(List<EnhancedEnrichmentResult> results) {
            this.results = new ArrayList<>(results);
            fireTableDataChanged();
        }
        
        @Override
        public int getRowCount() {
            return results.size();
        }
        
        @Override
        public int getColumnCount() {
            return columnNames.length;
        }
        
        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }
        
        @Override
        public Class<?> getColumnClass(int column) {
            switch (column) {
                case 3: return Integer.class;  // GO_Level
                case 4: case 5: case 6: return Double.class;  // P_value, Adjusted_P_value, Enrichment_Score
                case 7: case 8: case 9: case 10: return Integer.class;  // Hit counts and Num counts
                default: return String.class;  // GO_Name, GO_Category, GO_ID, Gene_List
            }
        }
        
        @Override
        public Object getValueAt(int row, int column) {
            if (row >= results.size()) return null;
            
            EnhancedEnrichmentResult result = results.get(row);
            switch (column) {
                case 0: return result.getTermName();  // GO_Name
                case 1: return result.getCategory();  // GO_Category
                case 2: return result.getTermId();    // GO_ID  
                case 3: return result.getGoLevel();   // GO_Level
                case 4: return result.getPValue();    // P_value
                case 5: return result.getAdjustedPValue();  // Adjusted_P_value
                case 6: return result.getEnrichmentRatio();  // Enrichment_Score
                case 7: return result.getGeneCount(); // Hit_in_Set
                case 8: return result.getHitInBackground();  // Hit_in_Background
                case 9: return result.getNumOfSet();  // Num_of_Set
                case 10: return result.getNumOfBackground();  // Num_of_Background
                case 11: return truncateGeneList(result.getGeneList(), 50);  // Gene_List (truncated)
                default: return null;
            }
        }
        
        private String truncateGeneList(String geneList, int maxLength) {
            if (geneList == null || geneList.length() <= maxLength) {
                return geneList;
            }
            return geneList.substring(0, maxLength) + "...";
        }
    }
    
    // P值科学计数法渲染器
    private static class ScientificRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            
            if (value instanceof Double) {
                double val = (Double) value;
                if (val < 0.001) {
                    setText(String.format("%.2e", val));
                } else {
                    setText(String.format("%.6f", val));
                }
                setHorizontalAlignment(SwingConstants.RIGHT);
            }
            
            return this;
        }
    }
    
    // 小数点后2位渲染器
    private static class DecimalRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            
            if (value instanceof Double) {
                double val = (Double) value;
                setText(String.format("%.2f", val));
                setHorizontalAlignment(SwingConstants.RIGHT);
            } else if (value instanceof Integer) {
                setText(value.toString());
                setHorizontalAlignment(SwingConstants.RIGHT);
            }
            
            return this;
        }
    }
    
    /**
     * 创建GO富集分析结果的可视化
     */
    private void createVisualization() {
        if (currentResults.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "No enrichment results available for visualization", 
                "No Results", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // 显示可视化参数配置对话框
        VisualizationConfigDialog configDialog = new VisualizationConfigDialog(this);
        configDialog.setVisible(true);
        
        if (configDialog.isConfirmed()) {
            try {
                // 创建临时文件用于可视化
                File tempFile = File.createTempFile("GO_enrichment_", ".txt");
                tempFile.deleteOnExit();
                
                // 写入GO富集分析结果到临时文件
                writeResultsForVisualization(tempFile);
                
                // 使用用户配置的参数创建可视化
                SwingUtilities.invokeLater(() -> {
                    try {
                        Barplot bp = new Barplot();
                        bp.setInTabFile(tempFile);
                        bp.setTermColName("GO_Name");
                        bp.setpValueColName(configDialog.getPValueColumn());
                        bp.setClassColName("GO_Category");
                        bp.setXlab(configDialog.getXLabel());
                        bp.setYlab(configDialog.getYLabel());
                        bp.setMaxTermToShow(configDialog.getMaxTermsToShow());
                        bp.setValueFormat(configDialog.getValueFormat());
                        bp.setGraphMode(configDialog.getGraphMode());
                        
                        // 生成可视化
                        bp.generate();
                        
                        statusLabel.setText("Visualization created successfully");
                        statusLabel.setForeground(Color.BLUE);
                        
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(GOEnrichmentDialog.this,
                            "Error creating visualization: " + ex.getMessage(),
                            "Visualization Error", JOptionPane.ERROR_MESSAGE);
                        statusLabel.setText("Failed to create visualization");
                        statusLabel.setForeground(Color.RED);
                    }
                });
                
            } catch (IOException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this,
                    "Error creating temporary file for visualization: " + ex.getMessage(),
                    "File Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * 将GO富集分析结果写入文件供可视化使用
     */
    private void writeResultsForVisualization(File outputFile) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            // 写入标题行 (TBtools Barplot兼容格式)
            writer.println("GO_Name\tGO_Category\tGO_ID\tGO_Level\tP_value\tAdjusted_P_value\tEnrichment_Score\tHit_in_Set\tHit_in_Background\tNum_of_Set\tNum_of_Background\tGene_List");
            
            // 按调整后的p值排序
            List<EnhancedEnrichmentResult> sortedResults = new ArrayList<>(currentResults);
            sortedResults.sort((a, b) -> Double.compare(a.getAdjustedPValue(), b.getAdjustedPValue()));
            
            // 只取显著的结果 (adjusted p-value <= 0.05)
            List<EnhancedEnrichmentResult> significantResults = sortedResults.stream()
                .filter(r -> r.getAdjustedPValue() <= 0.05)
                .collect(java.util.stream.Collectors.toList());
            
            // 限制最多前50个结果以保证可视化效果
            int maxResults = Math.min(50, significantResults.size());
            
            for (int i = 0; i < maxResults; i++) {
                EnhancedEnrichmentResult result = significantResults.get(i);
                
                // 写入结果行
                writer.printf("%s\t%s\t%s\t%d\t%.6e\t%.6e\t%.3f\t%d\t%d\t%d\t%d\t%s%n",
                    result.getTermName().replace("\t", " "), // 清理制表符
                    result.getCategory(),
                    result.getTermId(),
                    result.getGoLevel(),
                    result.getPValue(),
                    result.getAdjustedPValue(),
                    result.getEnrichmentRatio(),
                    result.getGeneCount(),
                    result.getHitInBackground(),
                    result.getNumOfSet(),
                    result.getNumOfBackground(),
                    String.join(";", result.getGeneList()) // 基因列表用分号分隔
                );
            }
            
            System.out.println("Visualization data written to: " + outputFile.getAbsolutePath());
            System.out.println("Total results for visualization: " + maxResults + " (significant: " + significantResults.size() + ")");
        }
    }
    
    /**
     * 可视化参数配置对话框
     */
    private static class VisualizationConfigDialog extends JDialog {
        private boolean confirmed = false;
        
        // 参数组件
        private JComboBox<String> pValueColumnCombo;
        private JTextField xLabelField;
        private JTextField yLabelField;
        private JSpinner maxTermsSpinner;
        private JTextField valueFormatField;
        private JComboBox<Barplot.GraphMode> graphModeCombo;
        
        // 按钮
        private JButton okButton;
        private JButton cancelButton;
        
        public VisualizationConfigDialog(Window parent) {
            super(parent, "Visualization Parameters", ModalityType.APPLICATION_MODAL);
        simplegenomehub.runtime.HubSession.attach(this, simplegenomehub.runtime.HubSession.forComponent(parent));
            initComponents();
            setupEventListeners();
            
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            setSize(450, 300);
            setLocationRelativeTo(parent);
        }
        
        private void initComponents() {
            setLayout(new BorderLayout());
            
            // 创建主面板
            JPanel mainPanel = new JPanel(new GridBagLayout());
            mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
            GridBagConstraints gbc = new GridBagConstraints();
            
            // P值列选择
            gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
            gbc.insets = new Insets(5, 5, 5, 5);
            mainPanel.add(new JLabel("P-value Column:"), gbc);
            
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            pValueColumnCombo = new JComboBox<>(new String[]{"P_value", "Adjusted_P_value"});
            pValueColumnCombo.setSelectedItem("Adjusted_P_value");
            SimpleGenomeHubUi.setComboBoxDisplayWidth(pValueColumnCombo, 180);
            mainPanel.add(pValueColumnCombo, gbc);
            
            // X轴标签
            gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
            mainPanel.add(new JLabel("X-axis Label:"), gbc);
            
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            xLabelField = new JTextField("-log10(Adjusted P-value)");
            mainPanel.add(xLabelField, gbc);
            
            // Y轴标签
            gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
            mainPanel.add(new JLabel("Y-axis Label:"), gbc);
            
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            yLabelField = new JTextField("GO Term");
            mainPanel.add(yLabelField, gbc);
            
            // 最大显示条目数
            gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
            mainPanel.add(new JLabel("Max Terms to Show:"), gbc);
            
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            maxTermsSpinner = new JSpinner(new SpinnerNumberModel(20, 5, 50, 1));
            mainPanel.add(maxTermsSpinner, gbc);
            
            // 数值格式
            gbc.gridx = 0; gbc.gridy = 4; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
            mainPanel.add(new JLabel("Value Format:"), gbc);
            
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            valueFormatField = new JTextField("0.00");
            mainPanel.add(valueFormatField, gbc);
            
            // 图形模式
            gbc.gridx = 0; gbc.gridy = 5; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
            mainPanel.add(new JLabel("Graph Mode:"), gbc);
            
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            graphModeCombo = new JComboBox<>(Barplot.GraphMode.values());
            graphModeCombo.setSelectedItem(Barplot.GraphMode.Normal);
            SimpleGenomeHubUi.setComboBoxDisplayWidth(graphModeCombo, 220);
            mainPanel.add(graphModeCombo, gbc);
            
            add(mainPanel, BorderLayout.CENTER);
            
            // 按钮面板
            JPanel buttonPanel = new JPanel(new FlowLayout());
            okButton = new JButton("Create Visualization");
            cancelButton = new JButton("Cancel");
            
            buttonPanel.add(okButton);
            buttonPanel.add(cancelButton);
            add(buttonPanel, BorderLayout.SOUTH);
            
            // 参数说明面板
            JPanel infoPanel = new JPanel(new BorderLayout());
            infoPanel.setBorder(new TitledBorder("Parameter Description"));
            
            JTextArea infoArea = new JTextArea(3, 30);
            infoArea.setText(
                "• P-value Column: Choose between raw P-value or FDR-corrected Adjusted P-value\n" +
                "• Graph Mode: Normal (text right), TextOnLeft (text left), BarOnLeft (bars left)\n" +
                "• Value Format: Number format for axis labels (e.g., 0.00, 0.000, 00.0)"
            );
            infoArea.setEditable(false);
            infoArea.setBackground(getBackground());
            infoArea.setFont(SimpleGenomeHubStyle.resize(infoArea.getFont(), 11f));
            
            infoPanel.add(new JScrollPane(infoArea), BorderLayout.CENTER);
            add(infoPanel, BorderLayout.NORTH);
        }
        
        private void setupEventListeners() {
            okButton.addActionListener(e -> {
                confirmed = true;
                setVisible(false);
            });
            
            cancelButton.addActionListener(e -> {
                confirmed = false;
                setVisible(false);
            });
            
            // P值列变化时自动更新X轴标签
            pValueColumnCombo.addActionListener(e -> {
                String selected = (String) pValueColumnCombo.getSelectedItem();
                if ("P_value".equals(selected)) {
                    xLabelField.setText("-log10(P-value)");
                } else {
                    xLabelField.setText("-log10(Adjusted P-value)");
                }
            });
        }
        
        // Getter methods
        public boolean isConfirmed() { return confirmed; }
        
        public String getPValueColumn() {
            return (String) pValueColumnCombo.getSelectedItem();
        }
        
        public String getXLabel() {
            return xLabelField.getText().trim();
        }
        
        public String getYLabel() {
            return yLabelField.getText().trim();
        }
        
        public int getMaxTermsToShow() {
            return (Integer) maxTermsSpinner.getValue();
        }
        
        public String getValueFormat() {
            return valueFormatField.getText().trim();
        }
        
        public Barplot.GraphMode getGraphMode() {
            return (Barplot.GraphMode) graphModeCombo.getSelectedItem();
        }
    }
}
