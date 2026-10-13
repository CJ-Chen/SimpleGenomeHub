package simplegenomehub.population.fst.view;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import simplegenomehub.genomebrowser.GenomeBrowserPanel;
import simplegenomehub.genomebrowser.GenomeBrowserDock;
import simplegenomehub.population.fst.chart.FSTWindow;
import simplegenomehub.population.fst.chart.FSTTrackChartPanel;
import simplegenomehub.population.fst.chart.model.TrackBoardModel;
import simplegenomehub.population.fst.chart.model.TrackModel;
import simplegenomehub.population.fst.chart.model.PlotItem;
import simplegenomehub.population.fst.chart.view.CardLoad;
import simplegenomehub.population.fst.chart.view.FSTChartMain;
import simplegenomehub.population.fst.chart.view.PositionLineParser;
import simplegenomehub.population.fst.chart.view.FSTUIBuilder;
import simplegenomehub.population.fst.view.LogisticReader;

//交互式窗口，包含菜单栏、工具栏和分割栏绘制区域
public class FSTJieMian extends JFrame {
    /** 结果目录名形如 {@code w1000000_s100000}，即本次运行使用的窗口大小与步长。 */
    private static final java.util.regex.Pattern RUN_WINDOW_STEP =
        java.util.regex.Pattern.compile("^w(\\d+)_s(\\d+)(?:-\\d+)?$");

    private JMenuBar menuBar;
    private JSplitPane splitPane;
    // 下方分隔栏：新的基因组浏览器（可复用面板）
    private GenomeBrowserPanel genomeBrowser;
    private final JPanel genomeBrowserHolder = new JPanel(new BorderLayout());
    private GenomeBrowserDock genomeBrowserDock;
    private String genomeFilePath;
    private String workDirectory;   // 结果来源目录（用于 Refresh 重新扫描；从文件打开时为null）
    
    // 数据
    private List<FSTWindow> windows;
    private double threshold;
    private double topPercent = 0.01;  // Top百分比，默认0.01（1%）
    private String filename;  // FST文件名（单个文件时使用）
    private List<String> filenames;  // FST文件名列表（多个文件时使用）
    private List<List<FSTWindow>> multipleWindows;  // 多个FST文件的窗口列表
    private List<Double> multipleThresholds;  // 多个FST文件的阈值列表
    private String piFilename;  // PI文件名（单个文件时使用）
    private List<String> piFilenames;  // PI文件名列表（多个文件时使用）
    private List<List<FSTWindow>> multiplePIWindows;  // 多个PI文件的窗口列表
    private String tajimaDFilename;  // TajimaD文件名（单个文件时使用）
    private List<String> tajimaDFilenames;  // TajimaD文件名列表（多个文件时使用）
    private List<List<FSTWindow>> multipleTajimaDWindows;  // 多个TajimaD文件的窗口列表
    private String logisticFilename;  // Logistic文件名（单个文件时使用）
    private List<String> logisticFilenames;  // Logistic文件名列表（多个文件时使用）
    private List<List<FSTWindow>> multipleLogisticWindows;  // 多个Logistic文件的窗口列表
    private String gff3FilePath;  // GFF3文件路径
    
    // 颜色设置（从FSTSettingsDialog读取默认值）
    private Color firstColor;
    private Color secondColor;
    private Color topColor;
    private Color piColor;
    
    // 基因颜色设置（从FSTSettingsDialog读取默认值）
    private Color intronColor;
    private Color forwardExonColor;
    private Color reverseExonColor;
    
    // 初始化默认颜色（与FSTSettingsDialog保持一致）
    private void initializeDefaultColors() {
        // 使用与FSTSettingsDialog相同的默认值
        firstColor = simplegenomehub.population.fst.FstUiStyle.SERIES_FIRST;  // 蓝（跟随 SimpleGenomeHub 主色）
        secondColor = simplegenomehub.population.fst.FstUiStyle.SERIES_SECOND;  // 橙（跟随 SimpleGenomeHub 主色）
        topColor = simplegenomehub.population.fst.FstUiStyle.TOP_MARKER;  // 深绿
        intronColor = new Color(166, 166, 166);  // 灰色
        forwardExonColor = new Color(255, 210, 83);  // 黄色
        reverseExonColor = new Color(8, 211, 251);  // 青色
        // PI颜色从Settings的PI Color色盘中随机选择
        piColor = FSTSettingsDialog.getRandomPIColor();
    }
    
    // 选中的染色体（null表示显示所有）
    private String selectedChromosome = null;
    
    // 标记是否从VCF文件来的（即从FSTCalculaorJieMian来的）
    // 如果false，表示直接从FSTReadFile打开，关闭窗口时应退出程序
    private boolean isFromVCF = false;
    
    public FSTJieMian(String filename, String gff3FilePath) {
        this.filename = filename;
        this.filenames = null;
        this.multipleWindows = null;
        this.multipleThresholds = null;
        initializeCommon(gff3FilePath, () -> {
            // 加载单个FST文件
            if (filename != null && !filename.isEmpty()) {
                loadSingleFSTFile();
            }
        });
    }
    
    public FSTJieMian(List<String> filenames, String gff3FilePath) {
        this.filename = null;
        this.filenames = filenames;
        this.multipleWindows = new ArrayList<>();
        this.multipleThresholds = new ArrayList<>();
        initializeCommon(gff3FilePath, () -> {
            // 如果是多个文件，加载所有文件
            if (filenames != null && !filenames.isEmpty()) {
                loadMultipleFSTFiles();
            } else {
                loadSingleFSTFile();
            }
        });
    }
    
    // 构造函数：支持PI文件（单个）
    public FSTJieMian(String fstFilename, List<String> fstFilenames, String piFilename, String gff3FilePath) {
        this.filename = fstFilename;
        this.filenames = fstFilenames;
        this.piFilename = piFilename;
        this.piFilenames = null;
        this.multipleWindows = new ArrayList<>();
        this.multipleThresholds = new ArrayList<>();
        this.multiplePIWindows = new ArrayList<>();
        initializeCommon(gff3FilePath, () -> {
            // 同时加载FST和PI文件
            if (fstFilename != null && !fstFilename.isEmpty()) {
                loadSingleFSTFile();
            } else if (fstFilenames != null && !fstFilenames.isEmpty()) {
                loadMultipleFSTFiles();
            }
            
            if (piFilename != null && !piFilename.isEmpty()) {
                loadPIFile();
            }
        });
    }
    
    // 构造函数：支持PI文件（多个）
    public FSTJieMian(String fstFilename, List<String> fstFilenames, String piFilename, List<String> piFilenames, String gff3FilePath) {
        this.filename = fstFilename;
        this.filenames = fstFilenames;
        this.piFilename = piFilename;
        this.piFilenames = piFilenames;
        this.multipleWindows = new ArrayList<>();
        this.multipleThresholds = new ArrayList<>();
        this.multiplePIWindows = new ArrayList<>();
        initializeCommon(gff3FilePath, () -> {
            // 同时加载FST和PI文件
            if (fstFilename != null && !fstFilename.isEmpty()) {
                loadSingleFSTFile();
            } else if (fstFilenames != null && !fstFilenames.isEmpty()) {
                loadMultipleFSTFiles();
            }
            
            if (piFilenames != null && !piFilenames.isEmpty()) {
                loadMultiplePIFiles();
            } else if (piFilename != null && !piFilename.isEmpty()) {
                loadPIFile();
            }
        });
    }
    
    // 构造函数：支持FST、PI和TajimaD文件
    public FSTJieMian(String fstFilename, List<String> fstFilenames, String piFilename, List<String> piFilenames,
                     String tajimaDFilename, List<String> tajimaDFilenames, String gff3FilePath) {
        this.filename = fstFilename;
        this.filenames = fstFilenames;
        this.piFilename = piFilename;
        this.piFilenames = piFilenames;
        this.tajimaDFilename = tajimaDFilename;
        this.tajimaDFilenames = tajimaDFilenames;
        this.multipleWindows = new ArrayList<>();
        this.multipleThresholds = new ArrayList<>();
        this.multiplePIWindows = new ArrayList<>();
        this.multipleTajimaDWindows = new ArrayList<>();
        initializeCommon(gff3FilePath, () -> {
            // 统一加载所有文件，一次性创建图表
            loadAllFiles();
        });
    }
    
    // 构造函数：支持FST、PI、TajimaD和Logistic文件
    public FSTJieMian(String fstFilename, List<String> fstFilenames, String piFilename, List<String> piFilenames,
                     String tajimaDFilename, List<String> tajimaDFilenames,
                     String logisticFilename, List<String> logisticFilenames, String gff3FilePath) {
        this.filename = fstFilename;
        this.filenames = fstFilenames;
        this.piFilename = piFilename;
        this.piFilenames = piFilenames;
        this.tajimaDFilename = tajimaDFilename;
        this.tajimaDFilenames = tajimaDFilenames;
        this.logisticFilename = logisticFilename;
        this.logisticFilenames = logisticFilenames;
        this.multipleWindows = new ArrayList<>();
        this.multipleThresholds = new ArrayList<>();
        this.multiplePIWindows = new ArrayList<>();
        this.multipleTajimaDWindows = new ArrayList<>();
        this.multipleLogisticWindows = new ArrayList<>();
        initializeCommon(gff3FilePath, () -> {
            // 统一加载所有文件，一次性创建图表
            loadAllFiles();
        });
    }
    
    // 通用初始化方法：设置基本字段并执行UI初始化和文件加载
    private void initializeCommon(String gff3FilePath, Runnable fileLoadingTask) {
        this.gff3FilePath = gff3FilePath;
        this.isFromVCF = false;
        
        // 激活结果目录内的命名清单（run.names.tsv），
        // 让卡牌显示自定义的 Compare/类群名称并提供悬停明细
        java.util.List<String> manifestCandidates = new ArrayList<>();
        if (filename != null && !filename.isEmpty()) manifestCandidates.add(filename);
        if (filenames != null) manifestCandidates.addAll(filenames);
        if (piFilename != null && !piFilename.isEmpty()) manifestCandidates.add(piFilename);
        if (piFilenames != null) manifestCandidates.addAll(piFilenames);
        if (tajimaDFilename != null && !tajimaDFilename.isEmpty()) manifestCandidates.add(tajimaDFilename);
        if (tajimaDFilenames != null) manifestCandidates.addAll(tajimaDFilenames);
        if (logisticFilename != null && !logisticFilename.isEmpty()) manifestCandidates.add(logisticFilename);
        if (logisticFilenames != null) manifestCandidates.addAll(logisticFilenames);
        simplegenomehub.population.fst.FSTRunManifest.activateFromFiles(manifestCandidates.toArray(new String[0]));
        
        initializeDefaultColors();  // 从Settings读取默认颜色
        initializeUI();
        // 初始化时读取文本框的值并计算（在EDT线程中延迟执行，确保UI已完全初始化）
        SwingUtilities.invokeLater(() -> {
            initializeChartPanelSettings(gff3FilePath);
            if (fileLoadingTask != null) {
                fileLoadingTask.run();
            }
        });
    }
    
    // 在下方分隔栏里装配新的基因组浏览器（使用导入时的 FASTA + GFF3）
    private void initializeChartPanelSettings(String gff3FilePath) {
        File genomeFile = (genomeFilePath != null && !genomeFilePath.isEmpty()) ? new File(genomeFilePath) : null;
        File annotationFile = (gff3FilePath != null && !gff3FilePath.isEmpty()) ? new File(gff3FilePath) : null;
        genomeBrowserHolder.removeAll();
        if (genomeBrowserDock != null) {
            genomeBrowserDock.close();
            genomeBrowserDock = null;
        }
        if (genomeFile != null && genomeFile.isFile()) {
            genomeBrowserDock = new GenomeBrowserDock(this, genomeFile, annotationFile, "Genome Browser");
            genomeBrowser = genomeBrowserDock.getBrowser();
            genomeBrowser.setOnViewChanged(this::updatePositionLine);
            genomeBrowserHolder.add(genomeBrowserDock, BorderLayout.CENTER);
        } else {
            JLabel missing = new JLabel("No genome FASTA file is available for this result.", SwingConstants.CENTER);
            missing.setForeground(new Color(120, 128, 140));
            genomeBrowserHolder.add(missing, BorderLayout.CENTER);
        }
        genomeBrowserHolder.revalidate();
        genomeBrowserHolder.repaint();
    }
    
    private void initializeUI() {
        setTitle("FST Visualization");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        
        // 添加窗口关闭监听器，清除分类群窗口中的引用
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                clearReferenceInSampleGroupingDialog();
            }
            
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                // 清理资源。SimpleGenomeHub 承载该窗口，关闭时不再退出整个程序。
                cleanup();
            }
        });
        
        // 创建回调接口实现
        FSTUIBuilder.Callbacks callbacks = new FSTUIBuilder.Callbacks() {
            @Override
            public void onSettingsClick() {
                showSettingsDialog();
            }
            
            @Override
            public void onStepSizeChange() {
                updateStepSize();
            }
            
            @Override
            public void onPlotClick() {
                toggleShowMode();
            }
            
            @Override
            public void onTopClick() {
                applyTopPercent();
            }
            
            @Override
            public void onPositionLineUpdate() {
                updatePositionLine();
            }
            
            @Override
            public void onPositionChange() {
                // 位置显示由嵌入的基因组浏览器自己维护
            }
            
            @Override
            public void onRefresh() {
                refreshFromSource();
            }
            
            @Override
            public void onSortChromosomes() {
                sortChromosomesAndRefresh();
            }
            
            @Override
            public void onExportImage() {
                exportImage();
            }
        };
        
        // 创建菜单栏（Show 栏的四个动作已收进 FST 菜单）
        menuBar = FSTUIBuilder.createMenuBar(callbacks);
        
        // 创建包含图表和底部面板的分隔面板
        splitPane = FSTUIBuilder.createSplitPane(genomeBrowserHolder, callbacks);
        
        // 将组件添加到窗口（不再需要顶部工具栏）
        setJMenuBar(menuBar);
        add(splitPane, BorderLayout.CENTER);
        
        // 设置窗口大小和位置
        setSize(1400, 900);
        setLocationRelativeTo(null);
    }
    
    
    public void updateChart() {
        // 使用FSTChartMain更新图表
        FSTChartMain.updateChart(splitPane, genomeBrowser, windows, threshold, filename,
                                multiplePIWindows, piFilename, piFilenames,
                                multipleWindows, multipleThresholds, filenames,
                                topPercent, firstColor, secondColor, topColor, piColor);
    }
    
    //加载单个PI文件
    private void loadPIFile() {
        if (piFilename == null) {
            return;
        }
        
        try {
            List<FSTWindow> piWindows = PIReader.readAndCalculateWindows(piFilename);
            if (piWindows.isEmpty()) {
                showErrorDialog("The PI file is empty or malformed.");
                return;
            }
            
            List<List<FSTWindow>> piWindowList = new ArrayList<>();
            piWindowList.add(piWindows);
            List<String> piFileNames = new ArrayList<>();
            piFileNames.add(piFilename);
            
            FSTChartMain.updatePIChart(splitPane, genomeBrowser, piWindows, piWindowList, piFileNames,
                                     multipleWindows, multipleThresholds, filenames, filename,
                                     topPercent, firstColor, secondColor, topColor, piColor);
            
        } catch (IOException e) {
            showErrorDialog("Error reading the PI file: " + e.getMessage());
        }
    }
    
    //加载多个PI文件
    private void loadMultiplePIFiles() {
        if (piFilenames == null || piFilenames.isEmpty()) {
            return;
        }
        
        multiplePIWindows = new ArrayList<>();
        List<String> piFileNames = new ArrayList<>();
        
        try {
            for (String piFname : piFilenames) {
                multiplePIWindows.add(PIReader.readAndCalculateWindows(piFname));
                piFileNames.add(piFname);
            }
            
            if (!multiplePIWindows.isEmpty()) {
                FSTChartMain.updatePIChart(splitPane, genomeBrowser, multiplePIWindows.get(0), 
                                         multiplePIWindows, piFileNames,
                                         multipleWindows, multipleThresholds, filenames, filename,
                                         topPercent, firstColor, secondColor, topColor, piColor);
            }
        } catch (IOException e) {
            showErrorDialog("Error reading the PI file: " + e.getMessage());
        }
    }
    
    //加载单个TajimaD文件
    private void loadTajimaDFile() {
        if (tajimaDFilename == null) {
            return;
        }
        
        try {
            List<FSTWindow> tajimaDWindows = TajimaDReader.readAndCalculateWindows(tajimaDFilename);
            if (tajimaDWindows.isEmpty()) {
                showErrorDialog("The Tajima's D file is empty or malformed.");
                return;
            }
            
            List<List<FSTWindow>> tajimaDWindowList = new ArrayList<>();
            tajimaDWindowList.add(tajimaDWindows);
            List<String> tajimaDFileNames = new ArrayList<>();
            tajimaDFileNames.add(tajimaDFilename);
            
            FSTChartMain.updateTajimaDChart(splitPane, genomeBrowser, tajimaDWindows, tajimaDWindowList, tajimaDFileNames,
                                          multipleWindows, multipleThresholds, filenames, filename,
                                          multiplePIWindows, piFilename, piFilenames,
                                          topPercent, firstColor, secondColor, topColor, piColor);
            
        } catch (IOException e) {
            showErrorDialog("Error reading the Tajima's D file: " + e.getMessage());
        }
    }
    
    //加载多个TajimaD文件
    private void loadMultipleTajimaDFiles() {
        if (tajimaDFilenames == null || tajimaDFilenames.isEmpty()) {
            return;
        }
        
        multipleTajimaDWindows = new ArrayList<>();
        List<String> tajimaDFileNames = new ArrayList<>();
        
        try {
            for (String tajimaDFname : tajimaDFilenames) {
                multipleTajimaDWindows.add(TajimaDReader.readAndCalculateWindows(tajimaDFname));
                tajimaDFileNames.add(tajimaDFname);
            }
            
            if (!multipleTajimaDWindows.isEmpty()) {
                FSTChartMain.updateTajimaDChart(splitPane, genomeBrowser, multipleTajimaDWindows.get(0), 
                                              multipleTajimaDWindows, tajimaDFileNames,
                                              multipleWindows, multipleThresholds, filenames, filename,
                                              multiplePIWindows, piFilename, piFilenames,
                                              topPercent, firstColor, secondColor, topColor, piColor);
            }
        } catch (IOException e) {
            showErrorDialog("Error reading the Tajima's D file: " + e.getMessage());
        }
    }
    
    
    // 统一加载所有文件（FST、PI、TajimaD、Logistic），一次性创建图表
    private void loadAllFiles() {
        boolean hasFST = loadFSTFiles();
        boolean hasPI = loadPIFiles();
        boolean hasTajimaD = loadTajimaDFiles();
        boolean hasLogistic = loadLogisticFiles();
        
        if (hasLogistic) {
            // 如果有Logistic数据，优先显示Logistic图表
            if (!multipleLogisticWindows.isEmpty()) {
                FSTChartMain.updateLogisticChart(splitPane, genomeBrowser, multipleLogisticWindows.get(0), 
                                               multipleLogisticWindows, collectFileNames(logisticFilenames, logisticFilename),
                                               multipleWindows, multipleThresholds, filenames, filename,
                                               multiplePIWindows, piFilename, piFilenames,
                                               multipleTajimaDWindows, tajimaDFilename, tajimaDFilenames,
                                               topPercent, firstColor, secondColor, topColor, piColor);
            }
        } else if (hasTajimaD) {
            if (!multipleTajimaDWindows.isEmpty()) {
                FSTChartMain.updateTajimaDChart(splitPane, genomeBrowser, multipleTajimaDWindows.get(0), 
                                              multipleTajimaDWindows, collectFileNames(tajimaDFilenames, tajimaDFilename),
                                              multipleWindows, multipleThresholds, filenames, filename,
                                              multiplePIWindows, piFilename, piFilenames,
                                              topPercent, firstColor, secondColor, topColor, piColor);
            }
        } else if (hasFST || hasPI) {
            if (hasFST && hasPI) {
                List<FSTWindow> referenceWindows = !multipleWindows.isEmpty() ? multipleWindows.get(0) 
                    : (!multiplePIWindows.isEmpty() ? multiplePIWindows.get(0) : windows);
                
                FSTChartMain.updateMixedChart(splitPane, genomeBrowser, referenceWindows, 
                                            multipleWindows, multipleThresholds, collectFileNames(filenames, filename),
                                            multiplePIWindows, collectFileNames(piFilenames, piFilename), 
                                            topPercent, firstColor, secondColor, topColor, piColor);
            } else if (hasFST) {
                updateChart();
            } else if (hasPI && !multiplePIWindows.isEmpty()) {
                FSTChartMain.updatePIChart(splitPane, genomeBrowser, multiplePIWindows.get(0), 
                                         multiplePIWindows, collectFileNames(piFilenames, piFilename),
                                         multipleWindows, multipleThresholds, filenames, filename,
                                         topPercent, firstColor, secondColor, topColor, piColor);
            }
        }
    }
    
    // 加载FST文件
    private boolean loadFSTFiles() {
        if (filename != null && !filename.isEmpty()) {
            try {
                List<FSTWindow> fileWindows = FSTReader.readAndCalculateWindows(filename);
                double fileThreshold = FSTReader.calculateTopPercentThreshold(fileWindows, topPercent);
                multipleWindows.add(fileWindows);
                multipleThresholds.add(fileThreshold);
                windows = fileWindows;
                threshold = fileThreshold;
                return true;
            } catch (IOException e) {
                showErrorDialog("Error reading the FST file: " + e.getMessage());
                return false;
            }
        } else if (filenames != null && !filenames.isEmpty()) {
            try {
                for (String fname : filenames) {
                    List<FSTWindow> fileWindows = FSTReader.readAndCalculateWindows(fname);
                    double fileThreshold = FSTReader.calculateTopPercentThreshold(fileWindows, topPercent);
                    multipleWindows.add(fileWindows);
                    multipleThresholds.add(fileThreshold);
                }
                if (!multipleWindows.isEmpty()) {
                    windows = multipleWindows.get(0);
                    threshold = multipleThresholds.get(0);
                }
                return true;
            } catch (IOException e) {
                showErrorDialog("Error reading the FST file: " + e.getMessage());
                return false;
            }
        }
        return false;
    }
    
    // 加载PI文件
    private boolean loadPIFiles() {
        if (piFilenames != null && !piFilenames.isEmpty()) {
            try {
                multiplePIWindows = new ArrayList<>();
                for (String piFname : piFilenames) {
                    multiplePIWindows.add(PIReader.readAndCalculateWindows(piFname));
                }
                return !multiplePIWindows.isEmpty();
            } catch (IOException e) {
                showErrorDialog("Error reading the PI file: " + e.getMessage());
                return false;
            }
        } else if (piFilename != null && !piFilename.isEmpty()) {
            try {
                List<FSTWindow> piWindows = PIReader.readAndCalculateWindows(piFilename);
                if (!piWindows.isEmpty()) {
                    multiplePIWindows = new ArrayList<>();
                    multiplePIWindows.add(piWindows);
                    return true;
                }
            } catch (IOException e) {
                showErrorDialog("Error reading the PI file: " + e.getMessage());
            }
        }
        return false;
    }
    
    // 加载TajimaD文件
    private boolean loadTajimaDFiles() {
        if (tajimaDFilenames != null && !tajimaDFilenames.isEmpty()) {
            try {
                multipleTajimaDWindows = new ArrayList<>();
                for (String tajimaDFname : tajimaDFilenames) {
                    multipleTajimaDWindows.add(TajimaDReader.readAndCalculateWindows(tajimaDFname));
                }
                return !multipleTajimaDWindows.isEmpty();
            } catch (IOException e) {
                showErrorDialog("Error reading the Tajima's D file: " + e.getMessage());
                return false;
            }
        } else if (tajimaDFilename != null && !tajimaDFilename.isEmpty()) {
            try {
                List<FSTWindow> tajimaDWindows = TajimaDReader.readAndCalculateWindows(tajimaDFilename);
                if (!tajimaDWindows.isEmpty()) {
                    multipleTajimaDWindows = new ArrayList<>();
                    multipleTajimaDWindows.add(tajimaDWindows);
                    return true;
                }
            } catch (IOException e) {
                showErrorDialog("Error reading the Tajima's D file: " + e.getMessage());
            }
        }
        return false;
    }
    
    // 加载Logistic文件
    private boolean loadLogisticFiles() {
        if (logisticFilenames != null && !logisticFilenames.isEmpty()) {
            try {
                multipleLogisticWindows = new ArrayList<>();
                for (String logisticFname : logisticFilenames) {
                    multipleLogisticWindows.add(LogisticReader.readAndCalculateWindows(logisticFname));
                }
                return !multipleLogisticWindows.isEmpty();
            } catch (IOException e) {
                showErrorDialog("Error reading the Logistic file: " + e.getMessage());
                return false;
            }
        } else if (logisticFilename != null && !logisticFilename.isEmpty()) {
            try {
                List<FSTWindow> logisticWindows = LogisticReader.readAndCalculateWindows(logisticFilename);
                if (!logisticWindows.isEmpty()) {
                    multipleLogisticWindows = new ArrayList<>();
                    multipleLogisticWindows.add(logisticWindows);
                    return true;
                }
            } catch (IOException e) {
                showErrorDialog("Error reading the Logistic file: " + e.getMessage());
            }
        }
        return false;
    }
    
    // 收集文件名列表（从列表或单个文件名）
    private List<String> collectFileNames(List<String> fileList, String singleFile) {
        List<String> result = new ArrayList<>();
        if (fileList != null && !fileList.isEmpty()) {
            result.addAll(fileList);
        } else if (singleFile != null && !singleFile.isEmpty()) {
            result.add(singleFile);
        }
        return result;
    }
    
    // 显示错误对话框
    private void showErrorDialog(String message) {
        JOptionPane.showMessageDialog(this, message, "File error", JOptionPane.ERROR_MESSAGE);
    }
    
    //加载多个FST文件
    private void loadMultipleFSTFiles() {
        multipleWindows.clear();
        multipleThresholds.clear();
        
        try {
            for (String fname : filenames) {
                List<FSTWindow> fileWindows = FSTReader.readAndCalculateWindows(fname);
                double fileThreshold = FSTReader.calculateTopPercentThreshold(fileWindows, topPercent);
                multipleWindows.add(fileWindows);
                multipleThresholds.add(fileThreshold);
            }
            
            // 使用第一个文件的数据作为主数据（用于染色体范围等）
            if (!multipleWindows.isEmpty()) {
                windows = multipleWindows.get(0);
                threshold = multipleThresholds.get(0);
            }
            
            FSTChartMain.updateMultipleCharts(splitPane, genomeBrowser, multipleWindows, multipleThresholds,
                                             filenames, multiplePIWindows, piFilename, piFilenames,
                                             topPercent, firstColor, secondColor, topColor, piColor);
        } catch (IOException e) {
            showErrorDialog("Error reading the FST file: " + e.getMessage());
        }
    }
    
    // 获取当前的图表面板（使用FSTChartMain）
    private FSTTrackChartPanel getCurrentChartPanel() {
        return FSTChartMain.getCurrentChartPanel(splitPane);
    }
    
    // 切换Show按钮的显示模式（已废弃，样式切换现在通过卡牌右下角按钮）
    @Deprecated
    private void toggleShowMode() {
        // Plot按钮功能已废弃，样式切换现在通过卡牌右下角按钮实现
        // 保留此方法以避免编译错误，但不执行任何操作
    }
    
    public void setWindows(List<FSTWindow> windows) {
        this.windows = windows;
        updateChart();
    }
    
    public void setThreshold(double threshold) {
        this.threshold = threshold;
        updateChart();
    }
    

    // 显示设置对话框
    private void showSettingsDialog() {
        FSTSettingsDialog dialog = new FSTSettingsDialog(this);
        dialog.setFirstColor(firstColor);
        dialog.setSecondColor(secondColor);
        dialog.setTopColor(topColor);
        dialog.setIntronColor(intronColor);
        dialog.setForwardExonColor(forwardExonColor);
        dialog.setReverseExonColor(reverseExonColor);
        dialog.setVisible(true);
        
        if (dialog.isSettingsChanged()) {
            // 更新颜色设置
            firstColor = dialog.getFirstColor();
            secondColor = dialog.getSecondColor();
            topColor = dialog.getTopColor();
            intronColor = dialog.getIntronColor();
            forwardExonColor = dialog.getForwardExonColor();
            reverseExonColor = dialog.getReverseExonColor();
            
            // 统一使用FSTTrackChartPanel，重新创建以应用新颜色（使用FSTChartMain）
            if (multipleWindows != null && !multipleWindows.isEmpty()) {
                FSTChartMain.updateMultipleCharts(splitPane, genomeBrowser, multipleWindows, multipleThresholds,
                                                 filenames, multiplePIWindows, piFilename, piFilenames,
                                                 topPercent, firstColor, secondColor, topColor, piColor);
            } else if (windows != null && !windows.isEmpty()) {
                updateChart(); // 重新创建图表面板以应用新颜色
            }
            
        }
    }

    //显示窗口详情（使用CardLoad类）
    private void showWindowDetails(FSTWindow window, int index) {
        CardLoad.showWindowDetails(window, index);
    }

    //应用Top百分比：全局同步所有小卡牌的Top按钮状态和文本框值（使用CardLoad类）
    private void applyTopPercent() {
        FSTTrackChartPanel chartPanel = getCurrentChartPanel();
        CardLoad.applyTopPercent(chartPanel, topPercent);
    }

    //更新步长：嵌入的基因组浏览器会根据缩放自行选择刻度，无需再下发
    private void updateStepSize() {
    }

    /**
     * 读取单个 FST 文件（windowed.weir.fst 格式）并刷新图表。
     * 工具栏上的 Window 栏现在只做展示，因此这里只负责“打开时加载”这一个用途。
     */
    private void loadSingleFSTFile() {
        if (filename == null || filename.isEmpty()) {
            return;
        }
        try {
            List<FSTWindow> fileWindows = FSTReader.readAndCalculateWindows(filename);
            double fileThreshold = FSTReader.calculateTopPercentThreshold(fileWindows, topPercent);

            windows = fileWindows;
            threshold = fileThreshold;

            if (multipleWindows == null) {
                multipleWindows = new ArrayList<>();
            } else {
                multipleWindows.clear();
            }
            multipleWindows.add(fileWindows);

            if (multipleThresholds == null) {
                multipleThresholds = new ArrayList<>();
            } else {
                multipleThresholds.clear();
            }
            multipleThresholds.add(fileThreshold);

            updateChart();
        } catch (IOException e) {
            showErrorDialog("Error reading the FST file: " + e.getMessage());
        }
    }

    /**
     * 工具栏“按照染色体排序”：沿用策略窗口里的排序选项（FSTTrackChartPanel 的按编号排序），
     * 打开该选项后重新读取结果并重绘，让染色体尺子与所有轨道都按数字顺序（Chr1, Chr2, …, Chr10）排列。
     */
    private void sortChromosomesAndRefresh() {
        FSTTrackChartPanel.setSortChromosomesByNumber(true);
        refreshFromSource();
    }

    //设置文件名
    public void setFilename(String filename) {
        this.filename = filename;
    }
    
    //设置是否从VCF文件来的
    public void setFromVCF(boolean fromVCF) {
        this.isFromVCF = fromVCF;
    }
    
    // 设置所属基因组单元的 FASTA 路径（供下方嵌入的基因组浏览器使用）
    public void setGenomeFilePath(String genomeFilePath) {
        this.genomeFilePath = genomeFilePath;
    }
    
    // 设置结果来源目录（用于 Refresh 按钮重新扫描，并展示本次运行使用的窗口/步长）
    public void setWorkDirectory(String workDirectory) {
        this.workDirectory = workDirectory;
        showRunWindowStep(workDirectory);
    }
    
    /**
     * 结果目录名（w&lt;窗口&gt;_s&lt;步长&gt;）就是本次计算使用的窗口与步长，
     * 工具栏上的 Window 栏只做展示，数值直接取自该目录名。
     */
    private void showRunWindowStep(String directory) {
        if (directory == null) {
            return;
        }
        String name = new File(directory).getName();
        java.util.regex.Matcher matcher = RUN_WINDOW_STEP.matcher(name);
        if (!matcher.find()) {
            return;
        }
        String text = "Window Size: " + matcher.group(1) + "\nWindow Step: " + matcher.group(2);
        FSTTrackChartPanel.setRunWindowStepText(text);
        FSTTrackChartPanel panel = getCurrentChartPanel();
        if (panel != null) {
            panel.setRunWindowStep(text);
        }
    }
    
    /**
     * Refresh：重新读取结果文件并刷新重绘。
     * 如果知道结果来源目录，会先重新扫描该目录，拾取新增/删除的文件后再重绘。
     */
    private void refreshFromSource() {
        try {
            // 重新扫描来源目录，拾取新增的结果文件（例如新追加运行的比较策略）
            if (workDirectory != null && !workDirectory.isEmpty()) {
                File dir = new File(workDirectory);
                if (dir.isDirectory()) {
                    simplegenomehub.population.fst.ResultDirectoryScanner.ResultFiles rf =
                        simplegenomehub.population.fst.ResultDirectoryScanner.scanDirectory(workDirectory);
                    if (!rf.isEmpty()) {
                        filename = rf.getSingleFSTFile();
                        filenames = rf.getMultipleFSTFiles();
                        piFilename = rf.getSinglePIFile();
                        piFilenames = rf.getMultiplePIFiles();
                        tajimaDFilename = rf.getSingleTajimaDFile();
                        tajimaDFilenames = rf.getMultipleTajimaDFiles();
                        logisticFilename = rf.getSingleLogisticFile();
                        logisticFilenames = rf.getMultipleLogisticFiles();
                    }
                }
            }
            
            // 重置已加载状态，避免重复叠加
            windows = null;
            threshold = 0.0;
            multipleWindows = new ArrayList<>();
            multipleThresholds = new ArrayList<>();
            multiplePIWindows = new ArrayList<>();
            multipleTajimaDWindows = new ArrayList<>();
            multipleLogisticWindows = new ArrayList<>();
            
            // 重新激活命名清单（run.names.tsv）
            List<String> manifestCandidates = new ArrayList<>();
            if (filename != null && !filename.isEmpty()) manifestCandidates.add(filename);
            if (filenames != null) manifestCandidates.addAll(filenames);
            if (piFilename != null && !piFilename.isEmpty()) manifestCandidates.add(piFilename);
            if (piFilenames != null) manifestCandidates.addAll(piFilenames);
            if (tajimaDFilename != null && !tajimaDFilename.isEmpty()) manifestCandidates.add(tajimaDFilename);
            if (tajimaDFilenames != null) manifestCandidates.addAll(tajimaDFilenames);
            if (logisticFilename != null && !logisticFilename.isEmpty()) manifestCandidates.add(logisticFilename);
            if (logisticFilenames != null) manifestCandidates.addAll(logisticFilenames);
            simplegenomehub.population.fst.FSTRunManifest.activateFromFiles(manifestCandidates.toArray(new String[0]));
            
            // 重新读取并重绘
            loadAllFiles();
        } catch (Exception e) {
            showErrorDialog("Refresh failed: " + e.getMessage());
        }
    }

    /**
     * 导出可视化区域为图片：弹出选择格式与保存路径的小窗口（PNG / SVG / PDF）。
     */
    private void exportImage() {
        final FSTTrackChartPanel chartPanel = getCurrentChartPanel();
        if (chartPanel == null || chartPanel.getBoardModel().getTracks().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "There is nothing to export right now.",
                "Export failed", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export visualization");
        chooser.setAcceptAllFileFilterUsed(true);
        final String[] formats = {"PNG (*.png)", "SVG (*.svg)", "PDF (*.pdf)"};
        final JComboBox<String> formatBox = new JComboBox<>(formats);
        JPanel accessory = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        accessory.add(new JLabel("Format:"));
        accessory.add(formatBox);
        chooser.setAccessory(accessory);
        chooser.setSelectedFile(new File("visualization.png"));

        // 切换格式时同步文件扩展名
        formatBox.addActionListener(e -> {
            File current = chooser.getSelectedFile();
            String base = (current != null && current.getName() != null && !current.getName().isEmpty())
                    ? current.getName() : "visualization";
            int dot = base.lastIndexOf('.');
            if (dot > 0) base = base.substring(0, dot);
            chooser.setSelectedFile(new File(base + extensionOf((String) formatBox.getSelectedItem())));
        });

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (file == null) {
            return;
        }

        ImageExporter.Format format = formatOf((String) formatBox.getSelectedItem());
        file = ensureExtension(file, format.getExtension());

        if (file.exists()) {
            int choice = JOptionPane.showConfirmDialog(this,
                "The file already exists. Overwrite?\n" + file.getAbsolutePath(),
                "Confirm overwrite", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }

        try {
            Dimension figureSize = chartPanel.getFigureSize();
            ImageExporter.exportFigure(
                (g, w, h) -> chartPanel.paintFigure(g, w, h),
                figureSize.width, figureSize.height, format, file);
            JOptionPane.showMessageDialog(this,
                "Export finished:\n" + file.getAbsolutePath(),
                "Export complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (NoClassDefFoundError err) {
            JOptionPane.showMessageDialog(this,
                "The vector export library VectorGraphics2D is missing.\n"
                + "Make sure lib/VectorGraphics2D-0.13.jar is on the classpath.\n\n" + err,
                "Export failed", JOptionPane.ERROR_MESSAGE);
        } catch (Throwable t) {
            t.printStackTrace();
            JOptionPane.showMessageDialog(this,
                "Export failed: " + t.getMessage(),
                "Export failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** 导出目标：当前图表区域（轨道可视化面板）；取不到时退回分隔面板上半部分的视图 */
    private Component getExportTarget() {
        FSTTrackChartPanel panel = getCurrentChartPanel();
        if (panel != null) {
            return panel;
        }
        if (splitPane != null && splitPane.getLeftComponent() instanceof JScrollPane) {
            Component view = ((JScrollPane) splitPane.getLeftComponent()).getViewport().getView();
            if (view != null) {
                return view;
            }
        }
        return null;
    }

    private static String extensionOf(String formatLabel) {
        if (formatLabel != null && formatLabel.startsWith("SVG")) return ".svg";
        if (formatLabel != null && formatLabel.startsWith("PDF")) return ".pdf";
        return ".png";
    }

    private static ImageExporter.Format formatOf(String formatLabel) {
        if (formatLabel != null && formatLabel.startsWith("SVG")) return ImageExporter.Format.SVG;
        if (formatLabel != null && formatLabel.startsWith("PDF")) return ImageExporter.Format.PDF;
        return ImageExporter.Format.PNG;
    }

    private static File ensureExtension(File file, String extension) {
        String name = file.getName();
        if (!name.toLowerCase().endsWith(extension)) {
            return new File(file.getParentFile(), name + extension);
        }
        return file;
    }

    //更新位置线（从Browser文本框读取位置，统一使用FSTTrackChartPanel）
    private void updatePositionLine() {
        FSTTrackChartPanel panel = getCurrentChartPanel();
        PositionLineParser.updatePositionLine(panel, genomeBrowser);
    }

    // 清除分类群窗口中的引用（SimpleGenomeHub 中直接通知可视化管理器）
    private void clearReferenceInSampleGroupingDialog() {
        try {
            simplegenomehub.population.fst.chart.FSTVisualizationManager.clearCurrentResultWindow();
        } catch (Exception e) {
            // 若清理失败则忽略：结果窗口可能已经关闭
        }
    }
    
    // 清理资源
    private void cleanup() {
        // 清理数据引用，帮助GC回收内存
        if (windows != null) {
            windows.clear();
            windows = null;
        }
        if (genomeBrowserDock != null) {
            genomeBrowserDock.close();
            genomeBrowserDock = null;
            genomeBrowser = null;
        } else if (genomeBrowser != null) {
            genomeBrowser.close();
            genomeBrowser = null;
        }
    }
}
