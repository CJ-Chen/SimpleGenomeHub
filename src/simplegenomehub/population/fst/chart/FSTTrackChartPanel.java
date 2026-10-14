package simplegenomehub.population.fst.chart;

import simplegenomehub.genomebrowser.GenomeBrowserPanel;
import simplegenomehub.population.fst.chart.controller.CardDragController;
import simplegenomehub.population.fst.chart.controller.DropZoneInteractionController;
import simplegenomehub.population.fst.chart.controller.GhostCardOverlay;
import simplegenomehub.population.fst.chart.controller.InlineTopPercentController;
import simplegenomehub.population.fst.chart.controller.PlotInteractionController;
import simplegenomehub.population.fst.chart.controller.TrackReorderController;
import simplegenomehub.population.fst.chart.model.*;
import simplegenomehub.population.fst.chart.render.AxisRenderer;
import simplegenomehub.population.fst.chart.render.FstRenderer;
import simplegenomehub.population.fst.chart.render.PiRenderer;
import simplegenomehub.population.fst.chart.render.TajimaDRenderer;
import simplegenomehub.population.fst.chart.render.LogisticRenderer;
import simplegenomehub.population.fst.chart.render.PlotContext;
import simplegenomehub.population.fst.chart.view.*;
import simplegenomehub.population.fst.chart.view.TrackChartRenderer;

import java.awt.*;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.Container;
import java.awt.Rectangle;

// 轨道式图表面板：整合所有功能
public class FSTTrackChartPanel extends JPanel implements Scrollable {
    private final GenomeContext genomeContext;
    private final TrackBoardModel boardModel;
    private final List<DataSet> allDataSets;
    
    private CardBarPanel cardBarPanel;
    private JScrollPane cardBarScroll;        // 卡牌栏滚动容器（切换标签后回到最左）
    private boolean initialDealStarted = false; // 首次显示时播放一次发牌动画
    private GenomeRulerPanel rulerPanel;
    private TrackBoardPanel trackBoardPanel;
    private JPanel trackContainer; // 轨道容器面板
    private GhostCardOverlay ghostOverlay;
    private CardDragController cardDragController; // 保存引用以便访问拖拽状态
    private InlineTopPercentController inlineTopPercentController; // 内联Top百分比编辑控制器
    
    private final Color firstColor;
    private final Color secondColor;
    private final Color topColor;
    private final Color piColor;  // PI颜色
    private final Color tajimaDColor;  // TajimaD颜色
    private final double topPercent;
    
    private final Map<String, Double> thresholds; // dataSetId -> threshold
    private final Map<String, BitSet> topBitsMap; // dataSetId -> topBits
    
    private final AxisRenderer axisRenderer;
    private final Map<String, FstRenderer> rendererMap; // dataSetId -> renderer (FST)
    private final Map<String, PiRenderer> piRendererMap; // dataSetId -> renderer (PI)
    private final Map<String, TajimaDRenderer> tajimaDRendererMap; // dataSetId -> renderer (TajimaD)
    private final Map<String, LogisticRenderer> logisticRendererMap; // dataSetId -> renderer (Logistic)
    private final TrackChartRenderer chartRenderer;
    
    private Integer hoveredIndex = null;
    private Integer selectedIndex = null;
    private String hoveredDataSetId = null;
    
    private GenomeBrowserPanel positionBrowser;
    
    // 投放框内卡牌拖拽状态
    private PlotItem draggingItemFromDropZone = null;
    private TrackView draggingItemSourceTrack = null;
    private Point draggingItemLocation = null;
    private Point draggingItemOffset = null;
    
    // 轨道拖拽状态
    private TrackView draggingTrack = null;
    private Point draggingTrackLocation = null;
    private Point draggingTrackOffset = null;

    // ---- 染色体按编号排序选项（打开结果窗口时由 FSTVisualizationManager 设置）----
    private static volatile boolean sortChromosomesByNumber = false;

    /** 设置结果展示时是否按染色体名称末尾数字排序（如 Chr1, Chr2, ..., Chr10） */
    public static void setSortChromosomesByNumber(boolean enabled) {
        sortChromosomesByNumber = enabled;
    }

    /** 当前是否开启按染色体编号排序 */
    public static boolean isSortChromosomesByNumber() {
        return sortChromosomesByNumber;
    }

    // ---- 本次运行的窗口/步长文本，显示在染色体尺子左侧（打开结果窗口时设置）----
    private static volatile String runWindowStepText = "";

    /** 记录本次运行的窗口/步长文本；之后创建的尺子会显示它。 */
    public static void setRunWindowStepText(String text) {
        runWindowStepText = text == null ? "" : text;
    }

    /** 把窗口/步长文本应用到当前尺子面板（面板重建后再调用也安全）。 */
    public void setRunWindowStep(String text) {
        if (rulerPanel != null) {
            rulerPanel.setRunWindowStep(text);
        }
    }

    /**
     * 开启排序时：把参与绘制的所有窗口列表按染色体编号稳定重排。
     * 关闭时不执行任何操作。referenceWindows 决定染色体尺子与基因组坐标顺序。
     */
    private static void sortWindowGroupsIfEnabled(List<FSTWindow> referenceWindows,
                                                  List<List<FSTWindow>>... windowGroups) {
        if (!sortChromosomesByNumber) return;
        FSTChromosomeRange.sortWindowsByChromosomeNumber(referenceWindows);
        if (windowGroups == null) return;
        for (List<List<FSTWindow>> group : windowGroups) {
            if (group == null) continue;
            for (List<FSTWindow> windows : group) {
                FSTChromosomeRange.sortWindowsByChromosomeNumber(windows);
            }
        }
    }
    
    public FSTTrackChartPanel(List<FSTWindow> referenceWindows, 
                             List<List<FSTWindow>> multipleWindows,
                             List<Double> multipleThresholds,
                             List<String> fileNames,
                             double topPercent,
                             Color firstColor,
                             Color secondColor,
                             Color topColor) {
        sortWindowGroupsIfEnabled(referenceWindows, multipleWindows);
        this.genomeContext = new GenomeContext(referenceWindows);
        this.boardModel = new TrackBoardModel();
        this.allDataSets = new ArrayList<>();
        this.topPercent = topPercent;
        this.firstColor = firstColor;
        this.secondColor = secondColor;
        this.topColor = topColor;
        this.piColor = simplegenomehub.population.fst.FstUiStyle.SERIES_PI;  // 默认PI颜色（绿色）
        this.tajimaDColor = simplegenomehub.population.fst.FstUiStyle.SERIES_TAJIMA_D;  // 默认TajimaD颜色（紫色）
        this.thresholds = new HashMap<>();
        this.topBitsMap = new HashMap<>();
        this.rendererMap = new HashMap<>();
        this.piRendererMap = new HashMap<>();
        this.tajimaDRendererMap = new HashMap<>();
        this.logisticRendererMap = new HashMap<>();
        
        this.axisRenderer = new AxisRenderer(firstColor, secondColor);
        this.chartRenderer = new TrackChartRenderer(this, axisRenderer, rendererMap, piRendererMap, tajimaDRendererMap, logisticRendererMap);
        
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);
        
        // 创建数据集并配置renderer
        List<DataSet> dataSets = CardLoad.createDataSets(multipleWindows, fileNames, multipleThresholds);
        allDataSets.addAll(dataSets);
        configureRenderers(dataSets, multipleThresholds);
        
        initializeUI();
    }
    
    // 构造函数：支持PI数据
    public FSTTrackChartPanel(List<FSTWindow> referenceWindows, 
                             List<List<FSTWindow>> multipleWindows,
                             List<Double> multipleThresholds,
                             List<String> fileNames,
                             double topPercent,
                             Color firstColor,
                             Color secondColor,
                             Color topColor,
                             List<List<FSTWindow>> multiplePIWindows,
                             Color piColor) {
        sortWindowGroupsIfEnabled(referenceWindows, multipleWindows, multiplePIWindows);
        this.genomeContext = new GenomeContext(referenceWindows);
        this.boardModel = new TrackBoardModel();
        this.allDataSets = new ArrayList<>();
        this.topPercent = topPercent;
        this.firstColor = firstColor;
        this.secondColor = secondColor;
        this.topColor = topColor;
        this.piColor = piColor;
        // TajimaD颜色字段保留（用于兼容性），但实际每个卡牌会独立随机选择
        this.tajimaDColor = simplegenomehub.population.fst.FstUiStyle.SERIES_TAJIMA_D;
        this.thresholds = new HashMap<>();
        this.topBitsMap = new HashMap<>();
        this.rendererMap = new HashMap<>();
        this.piRendererMap = new HashMap<>();
        this.tajimaDRendererMap = new HashMap<>();
        this.logisticRendererMap = new HashMap<>();
        
        this.axisRenderer = new AxisRenderer(firstColor, secondColor);
        this.chartRenderer = new TrackChartRenderer(this, axisRenderer, rendererMap, piRendererMap, tajimaDRendererMap, logisticRendererMap);
        
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);
        
        // 创建FST和PI混合数据集并配置renderer
        List<DataSet> dataSets = CardLoad.createMixedDataSets(multipleWindows, multipleThresholds, fileNames, multiplePIWindows);
        allDataSets.addAll(dataSets);
        configureRenderers(dataSets, multipleThresholds);
        
        initializeUI();
    }
    
    // 构造函数：支持TajimaD数据
    public FSTTrackChartPanel(List<FSTWindow> referenceWindows, 
                             List<List<FSTWindow>> multipleWindows,
                             List<Double> multipleThresholds,
                             List<String> fileNames,
                             double topPercent,
                             Color firstColor,
                             Color secondColor,
                             Color topColor,
                             List<List<FSTWindow>> multiplePIWindows,
                             Color piColor,
                             List<List<FSTWindow>> multipleTajimaDWindows) {
        sortWindowGroupsIfEnabled(referenceWindows, multipleWindows, multiplePIWindows, multipleTajimaDWindows);
        this.genomeContext = new GenomeContext(referenceWindows);
        this.boardModel = new TrackBoardModel();
        this.allDataSets = new ArrayList<>();
        this.topPercent = topPercent;
        this.firstColor = firstColor;
        this.secondColor = secondColor;
        this.topColor = topColor;
        this.piColor = piColor;
        // TajimaD颜色字段保留（用于兼容性），但实际每个卡牌会独立随机选择
        this.tajimaDColor = simplegenomehub.population.fst.FstUiStyle.SERIES_TAJIMA_D;
        this.thresholds = new HashMap<>();
        this.topBitsMap = new HashMap<>();
        this.rendererMap = new HashMap<>();
        this.piRendererMap = new HashMap<>();
        this.tajimaDRendererMap = new HashMap<>();
        this.logisticRendererMap = new HashMap<>();
        
        this.axisRenderer = new AxisRenderer(firstColor, secondColor);
        this.chartRenderer = new TrackChartRenderer(this, axisRenderer, rendererMap, piRendererMap, tajimaDRendererMap, logisticRendererMap);
        
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);
        
        // 创建数据集并配置renderer
        List<DataSet> dataSets = CardLoad.createMixedDataSetsWithTajimaD(
            multipleWindows, multipleThresholds, fileNames, 
            multiplePIWindows, multipleTajimaDWindows);
        allDataSets.addAll(dataSets);
        configureRenderers(dataSets, multipleThresholds);
        
        initializeUI();
    }
    
    // 构造函数：支持Logistic数据
    public FSTTrackChartPanel(List<FSTWindow> referenceWindows, 
                             List<List<FSTWindow>> multipleWindows,
                             List<Double> multipleThresholds,
                             List<String> fileNames,
                             double topPercent,
                             Color firstColor,
                             Color secondColor,
                             Color topColor,
                             List<List<FSTWindow>> multiplePIWindows,
                             Color piColor,
                             List<List<FSTWindow>> multipleTajimaDWindows,
                             List<List<FSTWindow>> multipleLogisticWindows) {
        sortWindowGroupsIfEnabled(referenceWindows, multipleWindows, multiplePIWindows, multipleTajimaDWindows, multipleLogisticWindows);
        this.genomeContext = new GenomeContext(referenceWindows);
        this.boardModel = new TrackBoardModel();
        this.allDataSets = new ArrayList<>();
        this.topPercent = topPercent;
        this.firstColor = firstColor;
        this.secondColor = secondColor;
        this.topColor = topColor;
        this.piColor = piColor;
        this.tajimaDColor = simplegenomehub.population.fst.FstUiStyle.SERIES_TAJIMA_D;
        this.thresholds = new HashMap<>();
        this.topBitsMap = new HashMap<>();
        this.rendererMap = new HashMap<>();
        this.piRendererMap = new HashMap<>();
        this.tajimaDRendererMap = new HashMap<>();
        this.logisticRendererMap = new HashMap<>();
        
        this.axisRenderer = new AxisRenderer(firstColor, secondColor);
        this.chartRenderer = new TrackChartRenderer(this, axisRenderer, rendererMap, piRendererMap, tajimaDRendererMap, logisticRendererMap);
        
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);
        
        // 创建数据集并配置renderer
        List<DataSet> dataSets = CardLoad.createMixedDataSetsWithLogistic(
            multipleWindows, multipleThresholds, fileNames, 
            multiplePIWindows, multipleTajimaDWindows, multipleLogisticWindows);
        allDataSets.addAll(dataSets);
        configureRenderers(dataSets, multipleThresholds);
        
        initializeUI();
    }
    
    // 从Settings获取随机颜色（通过反射，避免包导入问题）
    private Color getRandomColorFromSettings(String methodName, Color defaultColor) {
        try {
            Class<?> settingsClass = Class.forName("simplegenomehub.population.fst.view.FSTSettingsDialog");
            java.lang.reflect.Method method = settingsClass.getMethod(methodName);
            return (Color) method.invoke(null);
        } catch (Exception e) {
            return defaultColor;
        }
    }
    
    // 配置renderer（提取公共逻辑）
    private void configureRenderers(List<DataSet> dataSets, List<Double> multipleThresholds) {
        int fstIndex = 0;
        for (DataSet dataSet : dataSets) {
            switch (dataSet.getType()) {
                case FST:
                    if (multipleThresholds != null && fstIndex < multipleThresholds.size()) {
                        Double threshold = multipleThresholds.get(fstIndex);
                        thresholds.put(dataSet.getId(), threshold);
                        topBitsMap.put(dataSet.getId(), calculateTopBits(dataSet.getWindows(), threshold));
                        rendererMap.put(dataSet.getId(), new FstRenderer(firstColor, secondColor, topColor, threshold, topPercent));
                    }
                    fstIndex++;
                    break;
                case PI:
                    piRendererMap.put(dataSet.getId(), new PiRenderer(
                        getRandomColorFromSettings("getRandomPIColor", simplegenomehub.population.fst.FstUiStyle.SERIES_PI)));
                    break;
                case TAJIMAD:
                    tajimaDRendererMap.put(dataSet.getId(), new TajimaDRenderer(
                        getRandomColorFromSettings("getRandomTajimaDColor", simplegenomehub.population.fst.FstUiStyle.SERIES_TAJIMA_D)));
                    break;
                case LOGISTIC:
                    logisticRendererMap.put(dataSet.getId(), new LogisticRenderer(
                        getRandomColorFromSettings("getRandomPIColor", simplegenomehub.population.fst.FstUiStyle.PRIMARY_ACTION))); // 使用橙色作为默认颜色
                    break;
            }
        }
    }
    
    private void initializeUI() {
        // 创建卡牌栏
        cardBarPanel = new CardBarPanel();
        for (DataSet dataSet : allDataSets) {
            cardBarPanel.addCard(dataSet);
        }
        JScrollPane cardBarScroll = new JScrollPane(cardBarPanel);
        cardBarScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        cardBarScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        cardBarScroll.setBorder(BorderFactory.createEmptyBorder());
        
        // 创建染色体尺子
        rulerPanel = new GenomeRulerPanel(genomeContext, genomeContext.getReferenceWindows(),
                                         firstColor, secondColor);
        rulerPanel.setListener(new GenomeRulerPanel.GenomeRulerListener() {
            @Override
            public void onChromosomeSelected(String chromosome) {
                repaint();
            }
            
            @Override
            public void onChromosomeOrderChanged(List<String> newOrder) {
                repaint();
            }
        });
        rulerPanel.setRunWindowStep(runWindowStepText);
        
        // 创建轨道板（直接放入容器；滚动交给外层 JScrollPane，保证与尺子同宽、随窗口缩放）
        trackBoardPanel = new TrackBoardPanel(boardModel);
        trackBoardPanel.rebuildViews(this);
        
        // 创建幽灵卡牌覆盖层（使用自定义面板来绘制）
        ghostOverlay = new GhostCardOverlay();
        ghostOverlay.setVisible(false);
        
        // 创建轨道容器面板（拖拽卡牌在主面板的paintComponent中绘制）
        trackContainer = new JPanel(new BorderLayout());
        trackContainer.add(trackBoardPanel, BorderLayout.CENTER);
        
        // 组装布局
        this.cardBarScroll = cardBarScroll;
        JPanel topPanel = new JPanel(new BorderLayout());
        JPanel cardBarArea = new JPanel(new BorderLayout());
        cardBarArea.add(buildCardTypeTabs(cardBarScroll), BorderLayout.WEST);
        cardBarArea.add(cardBarScroll, BorderLayout.CENTER);
        topPanel.add(cardBarArea, BorderLayout.NORTH);
        topPanel.add(rulerPanel, BorderLayout.CENTER);
        
        add(topPanel, BorderLayout.NORTH);
        add(trackContainer, BorderLayout.CENTER);
        
        // 设置控制器
        setupControllers();
        // 滚轮缩放（鼠标在上半部分轨道绘制区时）
        setupZoomWheel();
    }
    
    // ===== 滚轮缩放：在上半部分（各条大轨道绘制 FST 图的位置）滚轮缩放整体大小 =====
    private static final double MIN_CHART_ZOOM = 0.4;
    private static final double MAX_CHART_ZOOM = 4.0;
    private double chartZoom = 1.0;
    
    private void setupZoomWheel() {
        trackBoardPanel.addMouseWheelListener(e -> {
            // 鼠标在投放框内：留给小卡牌滚动，不缩放
            Point p = e.getPoint();
            for (TrackView tv : trackBoardPanel.getTrackViews()) {
                if (tv.getBounds().contains(p)) {
                    Point tp = SwingUtilities.convertPoint(trackBoardPanel, p, tv);
                    if (tv.getDropZoneBounds().contains(tp)) {
                        return;
                    }
                    break;
                }
            }
            
            double factor = Math.pow(1.12, -e.getWheelRotation());
            double newZoom = chartZoom * factor;
            if (newZoom < MIN_CHART_ZOOM) newZoom = MIN_CHART_ZOOM;
            if (newZoom > MAX_CHART_ZOOM) newZoom = MAX_CHART_ZOOM;
            e.consume();
            if (Math.abs(newZoom - chartZoom) < 1e-6) return;
            
            double ratio = newZoom / chartZoom;
            chartZoom = newZoom;
            for (TrackModel model : boardModel.getTracks()) {
                model.setHeight((int) Math.round(model.getHeight() * ratio));
            }
            trackBoardPanel.revalidate();
            revalidate();
            repaint();
        });
    }
    
    // ===== Scrollable：让整个图表随窗口宽度自适应，高度不足时铺满、超出时滚动 =====
    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }
    
    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL ? 24 : 48;
    }
    
    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL
                ? Math.max(24, visibleRect.height - 24)
                : Math.max(48, visibleRect.width - 48);
    }
    
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true; // 始终铺满窗口宽度（不再出现横向滚动条）
    }
    
    @Override
    public boolean getScrollableTracksViewportHeight() {
        Container vp = getParent();
        if (vp instanceof JViewport) {
            return getPreferredSize().height <= ((JViewport) vp).getHeight();
        }
        return false;
    }
    
    /**
     * 构建卡牌栏左侧的类型标签列：All + 本次实际加载到的各类型（按固定顺序）。
     * 点击标签 → 只列出该类型卡牌并播放"发牌"动画。
     */
    private JPanel buildCardTypeTabs(final JScrollPane cardBarScroll) {
        CardTypeTabStrip strip = new CardTypeTabStrip();
        
        // All（显示全部）
        strip.addTab("All", null);
        
        // 收集本次实际存在的类型
        java.util.LinkedHashSet<DataSet.DataType> presentTypes = new java.util.LinkedHashSet<>();
        for (DataSet dataSet : allDataSets) {
            presentTypes.add(dataSet.getType());
        }
        // 固定显示顺序
        DataSet.DataType[] order = {
            DataSet.DataType.FST, DataSet.DataType.PI, DataSet.DataType.TAJIMAD,
            DataSet.DataType.LOGISTIC, DataSet.DataType.D, DataSet.DataType.PBS,
            DataSet.DataType.OTHER
        };
        for (DataSet.DataType type : order) {
            if (presentTypes.contains(type)) {
                strip.addTab(cardTypeLabel(type), type);
            }
        }
        
        strip.setSelectionListener(type -> {
            cardBarPanel.showCards(type);
            // 切换后滚动条回到最左
            if (cardBarScroll != null && cardBarScroll.getHorizontalScrollBar() != null) {
                cardBarScroll.getHorizontalScrollBar().setValue(0);
            }
        });
        // 默认选中 All；初始发牌动画统一由 addNotify 触发
        strip.setSelectedIndexSilently(0);
        return strip;
    }
    
    /**
     * 数据类型 → 标签文字（按文件后缀习惯显示）
     */
    private static String cardTypeLabel(DataSet.DataType type) {
        switch (type) {
            case FST: return "FST";
            case PI: return "PI";
            case TAJIMAD: return "Tajima.D";
            case LOGISTIC: return "Logistic";
            case D: return "D";
            case PBS: return "PBS";
            default: return "Other";
        }
    }
    
    /**
     * 首次加入显示层级时播放一次"发牌"动画
     */
    @Override
    public void addNotify() {
        super.addNotify();
        if (!initialDealStarted) {
            initialDealStarted = true;
            SwingUtilities.invokeLater(() -> {
                if (cardBarPanel != null) {
                    cardBarPanel.showCards(null);
                }
            });
        }
    }
    
    private void setupControllers() {
        // 内联Top百分比编辑控制器
        inlineTopPercentController = new InlineTopPercentController(this);
        
        // 卡牌拖拽控制器
        cardDragController = new CardDragController(cardBarPanel, trackBoardPanel, boardModel, allDataSets, ghostOverlay, this);
        
        // 轨道重排控制器
        new TrackReorderController(trackBoardPanel, boardModel, this);
        
        // 绘图交互控制器
        new PlotInteractionController(trackBoardPanel, this);
        
        // 投放框交互控制器
        new DropZoneInteractionController(trackBoardPanel, this, cardDragController);
    }
    
    private BitSet calculateTopBits(List<FSTWindow> windows, double threshold) {
        BitSet bits = new BitSet(windows.size());
        String selectedChrom = genomeContext.getSelectedChromosome();
        for (int i = 0; i < windows.size(); i++) {
            FSTWindow w = windows.get(i);
            if (selectedChrom != null && !selectedChrom.equals(w.getChrom())) continue;
            double fst = w.getAvgFst();
            if (Double.isFinite(fst) && fst >= threshold) {
                bits.set(i);
            }
        }
        return bits;
    }
    
    public void setPositionBrowser(GenomeBrowserPanel browser) {
        this.positionBrowser = browser;
    }
    
    public void setPositionLineInfo(String chromosome, int position) {
        setPositionLineInfo(chromosome, position, -1);
    }
    
    public void setPositionLineInfo(String chromosome, int position1, int position2) {
        if (chromosome == null || position1 < 0) {
            genomeContext.setPositionLine(null);
        } else {
            genomeContext.setPositionLine(new GenomeContext.PositionLine(chromosome, position1, position2));
        }
        repaint();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
    }
    
    @Override
    public void paint(Graphics g) {
        super.paint(g);
        
        // 绘制从卡牌栏拖拽的卡牌
        if (ghostOverlay != null && ghostOverlay.isVisible() && ghostOverlay.getDataSet() != null) {
            try {
                Graphics2D g2 = (Graphics2D) g.create();
                if (g2 != null) {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
                    
                    Point location = ghostOverlay.getLocation();
                    if (location != null) {
                        chartRenderer.drawGhostCard(g2, ghostOverlay.getDataSet(), location);
                    }
                    g2.dispose();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        // 绘制从投放框拖拽的卡牌
        if (draggingItemFromDropZone != null && draggingItemLocation != null) {
            try {
                Graphics2D g2 = (Graphics2D) g.create();
                if (g2 != null) {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
                    
                    DataSet dataSet = findDataSet(draggingItemFromDropZone.getDataSetId());
                    if (dataSet != null) {
                        chartRenderer.drawDropZoneCard(g2, dataSet, draggingItemFromDropZone, draggingItemLocation);
                    }
                    g2.dispose();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        // 绘制拖拽中的轨道
        if (draggingTrack != null && draggingTrackLocation != null && draggingTrackOffset != null) {
            try {
                Graphics2D g2 = (Graphics2D) g.create();
                if (g2 != null) {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
                    
                    chartRenderer.drawDraggingTrack(g2, draggingTrack, draggingTrackLocation, draggingTrackOffset);
                    g2.dispose();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    // 绘制坐标系（由TrackView调用）
    public void paintAxis(Graphics2D g, TrackView trackView, Rectangle plotBounds) {
        chartRenderer.paintAxis(g, trackView, plotBounds, genomeContext);
    }
    
    // 绘制轨道上的数据（由TrackView调用）
    public void paintTrackData(Graphics2D g, TrackView trackView) {
        chartRenderer.paintTrackData(g, trackView, genomeContext, hoveredIndex, selectedIndex);
    }
    
    /**
     * 轨道上某类数据的 Y 轴上限（与绘制/坐标轴一致）。
     * 鼠标命中测试必须用同一个值换算像素位置，这样折叠 Y 轴或改变轨道高度后点击位置才会同步更新。
     */
    public double axisMaxFor(TrackView trackView, DataSet.DataType type) {
        return trackView == null ? 1.0 : chartRenderer.axisMaxFor(trackView.getTrackModel(), type);
    }
    
    /** 当前鼠标悬停的位点（原始 index；未悬停为 null）。 */
    public Integer getHoveredIndex() {
        return hoveredIndex;
    }
    
    /** 当前选中的 top 位点（原始 index；未选中为 null）。 */
    public Integer getSelectedIndex() {
        return selectedIndex;
    }
    
    // 绘制投放框内的卡牌（由TrackView调用）
    public void paintDropZoneCards(Graphics2D g, TrackView trackView, Rectangle dropZoneBounds) {
        chartRenderer.paintDropZoneCards(g, trackView, dropZoneBounds, 
                                        draggingItemFromDropZone, draggingItemSourceTrack);
    }
    
    /**
     * 导出用纯图尺寸：顶部基因组条带 + 所有轨道内容（不受滚动可见区域限制）。
     */
    public Dimension getFigureSize() {
        int width = getWidth() > 0 ? getWidth() : 1200;
        if (width < 600) width = 600;
        int height = RULER_HEIGHT + TRACK_GAP;
        for (TrackModel model : boardModel.getTracks()) {
            height += model.getHeight() + TRACK_GAP;
        }
        height += TRACK_GAP;
        return new Dimension(width, height);
    }
    
    /**
     * 导出用纯图渲染：纯白背景 + 顶部基因组条带 + 下方按顺序的每条轨道（刻度轴 + 数据点/线）。
     * 不画卡牌栏、投放框小卡牌、把手、删除按钮、滚动条、悬停/选中高亮；红色位置线保留。
     */
    public void paintFigure(Graphics2D g, int width, int height) {
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        
        // 顶部：基因组条带（干净版）
        int y = 0;
        if (rulerPanel != null) {
            rulerPanel.paintClean(g, width);
            y += RULER_HEIGHT;
        }
        
        // 下方：按顺序的每条轨道（只画轴 + 数据）
        for (TrackModel model : boardModel.getTracks()) {
            g.setColor(simplegenomehub.population.fst.FstUiStyle.GRID);
            g.drawLine(0, y + TRACK_GAP / 2, width, y + TRACK_GAP / 2);
            y += TRACK_GAP;
            
            Rectangle plotBounds = new Rectangle(200, y + 10, width - 400,
                                                 Math.max(24, model.getHeight() - 20));
            if (plotBounds.width > 0) {
                chartRenderer.paintAxis(g, model, plotBounds, genomeContext);
                chartRenderer.paintTrackData(g, model, plotBounds, genomeContext, null, null);
            }
            y += model.getHeight();
        }
    }
    
    private static final int RULER_HEIGHT = 50;
    private static final int TRACK_GAP = 6;
    
    public DataSet findDataSet(String dataSetId) {
        for (DataSet dataSet : allDataSets) {
            if (dataSet.getId().equals(dataSetId)) {
                return dataSet;
            }
        }
        return null;
    }
    
    public PiRenderer getPiRenderer(String dataSetId) {
        return piRendererMap.get(dataSetId);
    }
    
    public TajimaDRenderer getTajimaDRenderer(String dataSetId) {
        return tajimaDRendererMap.get(dataSetId);
    }
    
    public LogisticRenderer getLogisticRenderer(String dataSetId) {
        return logisticRendererMap.get(dataSetId);
    }
    
    public GenomeContext getGenomeContext() {
        return genomeContext;
    }
    
    public double getThreshold(String dataSetId) {
        Double threshold = thresholds.get(dataSetId);
        return threshold != null ? threshold : 0.0;
    }
    
    public BitSet getTopBits(String dataSetId) {
        return topBitsMap.get(dataSetId);
    }
    
    public GenomeBrowserPanel getPositionBrowser() {
        return positionBrowser;
    }
    
    public TrackBoardModel getBoardModel() {
        return boardModel;
    }
    
    public CardBarPanel getCardBarPanel() {
        return cardBarPanel;
    }
    
    public InlineTopPercentController getInlineTopPercentController() {
        return inlineTopPercentController;
    }
    
    // 投放框内卡牌拖拽相关方法
    public void setDraggingItemFromDropZone(PlotItem item, TrackView sourceTrack, Point offset) {
        // 如果正在编辑该卡牌的文本框，先结束编辑
        if (inlineTopPercentController.isEditingItem(item, sourceTrack)) {
            inlineTopPercentController.finishInlineTopPercentEditing();
        }
        
        this.draggingItemFromDropZone = item;
        this.draggingItemSourceTrack = sourceTrack;
        this.draggingItemOffset = offset;
    }
    
    public void updateDraggingItemLocation(Point location) {
        this.draggingItemLocation = location;
    }
    
    public void clearDraggingItemFromDropZone() {
        this.draggingItemFromDropZone = null;
        this.draggingItemSourceTrack = null;
        this.draggingItemLocation = null;
        this.draggingItemOffset = null;
    }
    
    // 轨道拖拽状态管理方法
    public void setDraggingTrack(TrackView track, Point offset) {
        this.draggingTrack = track;
        this.draggingTrackOffset = offset;
    }
    
    public void updateDraggingTrackLocation(Point location) {
        this.draggingTrackLocation = location;
    }
    
    public void clearDraggingTrack() {
        this.draggingTrack = null;
        this.draggingTrackLocation = null;
        this.draggingTrackOffset = null;
    }
    
    public TrackView getDraggingTrack() {
        return draggingTrack;
    }
    
    public GenomeContext.PositionLine getPositionLine() {
        return genomeContext.getPositionLine();
    }
    
    public String getPositionLineChromosome() {
        GenomeContext.PositionLine posLine = genomeContext.getPositionLine();
        return posLine != null ? posLine.chromosome : null;
    }
    
    public int getPositionLinePosition() {
        GenomeContext.PositionLine posLine = genomeContext.getPositionLine();
        return posLine != null ? posLine.position1 : -1;
    }
    
    public int getPositionLinePosition2() {
        GenomeContext.PositionLine posLine = genomeContext.getPositionLine();
        return posLine != null ? posLine.position2 : -1;
    }
    
    public void updateHoveredIndex(Integer index, String dataSetId) {
        // 只有当hoveredIndex或dataSetId改变时才更新
        if ((hoveredIndex == null && index != null) || 
            (hoveredIndex != null && !hoveredIndex.equals(index)) ||
            (hoveredDataSetId == null && dataSetId != null) ||
            (hoveredDataSetId != null && !hoveredDataSetId.equals(dataSetId))) {
            this.hoveredIndex = index;
            this.hoveredDataSetId = dataSetId;
            repaint();
        }
    }
    
    public void updateSelectedIndex(Integer index, String dataSetId) {
        // 只有当selectedIndex或dataSetId改变时才更新
        if ((selectedIndex == null && index != null) || 
            (selectedIndex != null && !selectedIndex.equals(index)) ||
            (hoveredDataSetId == null && dataSetId != null) ||
            (hoveredDataSetId != null && !hoveredDataSetId.equals(dataSetId))) {
            this.selectedIndex = index;
            this.hoveredDataSetId = dataSetId;
            repaint();
        }
    }
    
    
    public void recalculateTopBits() {
        // 重新计算所有数据集的top bits
        topBitsMap.clear();
        for (DataSet dataSet : allDataSets) {
            Double threshold = thresholds.get(dataSet.getId());
            if (threshold != null) {
                BitSet topBits = calculateTopBits(dataSet.getWindows(), threshold);
                topBitsMap.put(dataSet.getId(), topBits);
            }
        }
        repaint();
    }
    
    // 为特定的PlotItem重新计算top bits（使用PlotItem的topPercent）
    public void recalculateTopBitsForItem(PlotItem item) {
        DataSet dataSet = findDataSet(item.getDataSetId());
        if (dataSet == null) return;
        
        BitSet topBits = chartRenderer.calculateTopBitsForItem(dataSet.getWindows(), item.getTopPercent(), genomeContext);
        topBitsMap.put(dataSet.getId(), topBits);
        repaint();
    }
}
