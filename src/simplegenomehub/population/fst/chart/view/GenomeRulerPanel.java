package simplegenomehub.population.fst.chart.view;

import simplegenomehub.population.fst.chart.FSTChartStyle;
import simplegenomehub.population.fst.chart.FSTWindow;
import simplegenomehub.population.fst.chart.model.GenomeContext;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.List;
import javax.swing.JComponent;

/**
 * 基因组尺子面板：显示染色体矩形，支持点击选择和拖拽重排
 */
public class GenomeRulerPanel extends JComponent {
    private static final int RECT_HEIGHT = 20;
    private static final int LABEL_Y_OFFSET = 3;
    
    private final GenomeContext genomeContext;
    private final List<FSTWindow> referenceWindows;
    private final Color firstColor;
    private final Color secondColor;
    
    private int hoveredRectIndex = -1;
    private int selectedRectIndex = -1;
    private int draggingRectIndex = -1;
    private Point dragStartPoint;
    
    private GenomeRulerListener listener;
    private String runWindowStep = "";
    
    public interface GenomeRulerListener {
        void onChromosomeSelected(String chromosome);
        void onChromosomeOrderChanged(List<String> newOrder);
    }
    
    public GenomeRulerPanel(GenomeContext genomeContext, List<FSTWindow> referenceWindows,
                           Color firstColor, Color secondColor) {
        this.genomeContext = genomeContext;
        this.referenceWindows = referenceWindows;
        this.firstColor = firstColor;
        this.secondColor = secondColor;
        
        // 宽度不写死：随窗口宽度自适应（由 Scrollable 的面板拉伸）
        setPreferredSize(new Dimension(100, 50));
        setMinimumSize(new Dimension(0, 50));
        
        attachMouseListeners();
    }
    
    public void setListener(GenomeRulerListener listener) {
        this.listener = listener;
    }
    
    /** 本次运行的窗口/步长，显示在染色体矩形左侧（只读文本）。 */
    public void setRunWindowStep(String text) {
        this.runWindowStep = text == null ? "" : text;
        repaint();
    }
    
    private void attachMouseListeners() {
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int newHovered = findRectIndexAt(e.getX(), e.getY());
                if (newHovered != hoveredRectIndex) {
                    hoveredRectIndex = newHovered;
                    repaint();
                }
            }
            
            @Override
            public void mouseDragged(MouseEvent e) {
                if (draggingRectIndex >= 0) {
                    // 计算新的插入位置
                    int newIndex = findRectIndexAt(e.getX(), e.getY());
                    if (newIndex >= 0 && newIndex != draggingRectIndex) {
                        // 更新顺序（这里只是视觉反馈，实际更新在mouseReleased中）
                        repaint();
                    }
                }
            }
        });
        
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int clickedIndex = findRectIndexAt(e.getX(), e.getY());
                if (clickedIndex >= 0) {
                    // 检查是否点击在把手区域（左侧10像素）
                    Rectangle rectBounds = getRectBounds(clickedIndex);
                    if (e.getX() < rectBounds.x + 10) {
                        // 开始拖拽
                        draggingRectIndex = clickedIndex;
                        dragStartPoint = e.getPoint();
                    } else {
                        // 点击选择染色体
                        List<String> order = genomeContext.getChromosomeOrder();
                        if (clickedIndex < order.size()) {
                            String clickedChrom = order.get(clickedIndex);
                            if (selectedRectIndex == clickedIndex) {
                                selectedRectIndex = -1;
                                genomeContext.setSelectedChromosome(null);
                            } else {
                                selectedRectIndex = clickedIndex;
                                genomeContext.setSelectedChromosome(clickedChrom);
                            }
                            if (listener != null) {
                                listener.onChromosomeSelected(genomeContext.getSelectedChromosome());
                            }
                            repaint();
                        }
                    }
                }
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                if (draggingRectIndex >= 0) {
                    int targetIndex = findRectIndexAt(e.getX(), e.getY());
                    if (targetIndex >= 0 && targetIndex != draggingRectIndex) {
                        // 更新染色体顺序
                        genomeContext.moveChromosome(draggingRectIndex, targetIndex);
                        if (listener != null) {
                            listener.onChromosomeOrderChanged(genomeContext.getChromosomeOrder());
                        }
                        repaint();
                    }
                    draggingRectIndex = -1;
                }
            }
        });
    }
    
    private int findRectIndexAt(int mouseX, int mouseY) {
        if (referenceWindows == null || referenceWindows.isEmpty()) return -1;
        if (mouseY < 0 || mouseY > RECT_HEIGHT + 20) return -1;
        
        int w = getWidth();
        // 绘制区域要左右各从屏幕区域减去200
        int leftMargin = 200;
        int rightMargin = 200;
        int plotW = w - leftMargin - rightMargin;
        if (plotW <= 0) return -1;
        
        int totalOriginal = referenceWindows.size();
        if (totalOriginal <= 0) return -1;
        
        double scale = FSTChartStyle.safeScalePerIndex(plotW, totalOriginal);
        List<String> order = genomeContext.getChromosomeOrder();
        
        // 根据当前顺序计算每个染色体的位置
        for (int i = 0; i < order.size(); i++) {
            String chrom = order.get(i);
            // 找到该染色体在原列表中的范围
            int startIdx = -1, endIdx = -1;
            for (int j = 0; j < referenceWindows.size(); j++) {
                if (referenceWindows.get(j).getChrom().equals(chrom)) {
                    if (startIdx < 0) startIdx = j;
                    endIdx = j;
                }
            }
            if (startIdx >= 0) {
                int rectX1 = leftMargin + (int) (startIdx * scale);
                int rectX2 = leftMargin + (int) ((endIdx + 1) * scale);
                if (mouseX >= rectX1 && mouseX < rectX2) {
                    return i;
                }
            }
        }
        return -1;
    }
    
    private Rectangle getRectBounds(int index) {
        List<String> order = genomeContext.getChromosomeOrder();
        if (index < 0 || index >= order.size()) return null;
        
        int w = getWidth();
        // 绘制区域要左右各从屏幕区域减去200
        int leftMargin = 200;
        int rightMargin = 200;
        int plotW = w - leftMargin - rightMargin;
        double scale = FSTChartStyle.safeScalePerIndex(plotW, referenceWindows.size());
        
        String chrom = order.get(index);
        int startIdx = -1, endIdx = -1;
        for (int j = 0; j < referenceWindows.size(); j++) {
            if (referenceWindows.get(j).getChrom().equals(chrom)) {
                if (startIdx < 0) startIdx = j;
                endIdx = j;
            }
        }
        if (startIdx >= 0) {
            int x1 = leftMargin + (int) (startIdx * scale);
            int x2 = leftMargin + (int) ((endIdx + 1) * scale);
            return new Rectangle(x1, 30, x2 - x1, RECT_HEIGHT);
        }
        return null;
    }
    
    /**
     * 导出纯图用：只画染色体色块与名称，不画悬停/选中红框、不画拖拽把手。
     */
    public void paintClean(Graphics2D g2, int width) {
        if (referenceWindows == null || referenceWindows.isEmpty()) return;
        int leftMargin = 200;
        int rightMargin = 200;
        int plotW = width - leftMargin - rightMargin;
        if (plotW <= 0) return;
        
        int totalOriginal = referenceWindows.size();
        double scale = FSTChartStyle.safeScalePerIndex(plotW, totalOriginal);
        List<String> order = genomeContext.getChromosomeOrder();
        
        g2.setFont(FSTChartStyle.chromosomeFont());
        FontMetrics fm = g2.getFontMetrics();
        int labelY = 30 - LABEL_Y_OFFSET;
        
        int color = 0;
        for (String chrom : order) {
            int startIdx = -1, endIdx = -1;
            for (int j = 0; j < referenceWindows.size(); j++) {
                if (referenceWindows.get(j).getChrom().equals(chrom)) {
                    if (startIdx < 0) startIdx = j;
                    endIdx = j;
                }
            }
            if (startIdx < 0) continue;
            
            int x1 = leftMargin + (int) (startIdx * scale);
            int x2 = leftMargin + (int) ((endIdx + 1) * scale);
            int rectW = Math.max(1, x2 - x1);
            
            g2.setColor(color == 0 ? firstColor : secondColor);
            g2.fillRect(x1, 30, rectW, RECT_HEIGHT);
            
            int mid = (startIdx + endIdx) >>> 1;
            int labelX = leftMargin + (int) (mid * scale);
            g2.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_PRIMARY);
            g2.drawString(chrom, labelX - fm.stringWidth(chrom) / 2, labelY);
            
            color ^= 1;
        }
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        if (referenceWindows == null || referenceWindows.isEmpty()) return;
        
        int w = getWidth();
        // 绘制区域要左右各从屏幕区域减去200
        int leftMargin = 200;
        int rightMargin = 200;
        // 窗口/步长只读文本，两行显示在染色体矩形左侧
        if (!runWindowStep.isEmpty()) {
            g2.setFont(FSTChartStyle.chromosomeFont().deriveFont(Font.PLAIN, 13f));
            g2.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_SECONDARY);
            int baseline = 22;
            for (String line : runWindowStep.split("\n")) {
                g2.drawString(line, 6, baseline);
                baseline += 19;
            }
        }
        int plotW = w - leftMargin - rightMargin;
        if (plotW <= 0) return;
        
        int totalOriginal = referenceWindows.size();
        double scale = FSTChartStyle.safeScalePerIndex(plotW, totalOriginal);
        
        List<String> order = genomeContext.getChromosomeOrder();
        
        // 绘制标签
        g2.setFont(FSTChartStyle.chromosomeFont());
        g2.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_PRIMARY);
        FontMetrics fm = g2.getFontMetrics();
        int labelY = 30 - LABEL_Y_OFFSET;
        
        // 绘制矩形和标签
        int color = 0;
        for (int i = 0; i < order.size(); i++) {
            String chrom = order.get(i);
            
            // 找到该染色体在原列表中的范围
            int startIdx = -1, endIdx = -1;
            for (int j = 0; j < referenceWindows.size(); j++) {
                if (referenceWindows.get(j).getChrom().equals(chrom)) {
                    if (startIdx < 0) startIdx = j;
                    endIdx = j;
                }
            }
            if (startIdx < 0) continue;
            
            int x1 = leftMargin + (int) (startIdx * scale);
            int x2 = leftMargin + (int) ((endIdx + 1) * scale);
            int rectW = Math.max(1, x2 - x1);
            int rectY = 30;
            
            // 绘制矩形
            g2.setColor(color == 0 ? firstColor : secondColor);
            g2.fillRect(x1, rectY, rectW, RECT_HEIGHT);
            
            // 高亮
            if (i == hoveredRectIndex || i == selectedRectIndex) {
                g2.setColor(Color.RED);
                g2.setStroke(FSTChartStyle.STROKE_RECT_HL);
                g2.drawRect(x1, rectY, rectW, RECT_HEIGHT);
                g2.setStroke(FSTChartStyle.STROKE_THIN);
            }
            
            // 绘制标签
            int mid = (startIdx + endIdx) >>> 1;
            int labelX = leftMargin + (int) (mid * scale);
            g2.drawString(chrom, labelX - fm.stringWidth(chrom) / 2, labelY);
            
            color ^= 1;
        }
        
        // 绘制把手（在拖拽的矩形左侧）
        if (draggingRectIndex >= 0) {
            Rectangle rectBounds = getRectBounds(draggingRectIndex);
            if (rectBounds != null) {
                g2.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_SECONDARY);
                g2.fillRect(rectBounds.x - 15, rectBounds.y, 10, rectBounds.height);
                g2.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_PRIMARY);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRect(rectBounds.x - 15, rectBounds.y, 10, rectBounds.height);
            }
        }
        
        g2.dispose();
    }
}
