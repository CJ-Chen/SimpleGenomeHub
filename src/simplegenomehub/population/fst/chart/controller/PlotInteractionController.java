package simplegenomehub.population.fst.chart.controller;

import simplegenomehub.genomebrowser.GenomeBrowserPanel;
import simplegenomehub.population.fst.chart.FSTDisplayCache;
import simplegenomehub.population.fst.chart.FSTTrackChartPanel;
import simplegenomehub.population.fst.chart.FSTWindow;
import simplegenomehub.population.fst.chart.model.DataSet;
import simplegenomehub.population.fst.chart.model.GenomeContext;
import simplegenomehub.population.fst.chart.model.PlotItem;
import simplegenomehub.population.fst.chart.render.PlotContext;
import simplegenomehub.population.fst.chart.view.TrackBoardPanel;
import simplegenomehub.population.fst.chart.view.TrackView;
import simplegenomehub.visualization.regionset.GenomicInterval;
import simplegenomehub.visualization.regionset.RegionSetContext;
import simplegenomehub.visualization.regionset.RegionSetContextHolder;
import simplegenomehub.visualization.regionset.RegionSetPopupMenu;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.BitSet;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * 绘图交互控制器：处理轨道上的hover和click
 */
public class PlotInteractionController {
    private static final int HOVER_DISTANCE = 20;
    
    private final TrackBoardPanel trackBoardPanel;
    private final FSTTrackChartPanel mainPanel;
    
    public PlotInteractionController(TrackBoardPanel trackBoardPanel, FSTTrackChartPanel mainPanel) {
        this.trackBoardPanel = trackBoardPanel;
        this.mainPanel = mainPanel;
        
        setupListeners();
    }
    
    private void setupListeners() {
        trackBoardPanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                handleMouseMove(e);
            }
        });
        
        trackBoardPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleMouseClick(e);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                handlePopupTrigger(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handlePopupTrigger(e);
            }
        });
    }

    /**
     * Right-click on a threshold-exceeding point offers Region Set actions.
     * Which points qualify is browser specific (FST today; GWAS and others can
     * extend {@link #isRegionSetCandidate(DataSet, FSTWindow, double)}).
     */
    private void handlePopupTrigger(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }

        TrackView trackView = trackBoardPanel.getTrackViewAt(e.getPoint());
        if (trackView == null) {
            return;
        }

        Point trackPoint = SwingUtilities.convertPoint(trackBoardPanel, e.getPoint(), trackView);
        Rectangle plotBounds = trackView.getPlotAreaBounds();
        if (!plotBounds.contains(trackPoint)) {
            return;
        }

        HitResult result = findHitPoint(trackView, trackPoint, plotBounds);
        if (result == null || result.originalIndex < 0) {
            return;
        }

        DataSet dataSet = mainPanel.findDataSet(result.dataSetId);
        if (dataSet == null || result.originalIndex >= dataSet.size()) {
            return;
        }

        FSTWindow window = dataSet.get(result.originalIndex);
        double threshold = mainPanel.getThreshold(result.dataSetId);
        // Qualify when the value exceeds the stored threshold, or when the point is
        // already drawn as a top marker (e.g. after an inline Top% edit on the card).
        if (!isRegionSetCandidate(dataSet, window, threshold) && !result.isTop) {
            return;
        }

        mainPanel.updateSelectedIndex(result.originalIndex, result.dataSetId);

        // Right-click selection also marks the window on the genome browser ruler.
        GenomeBrowserPanel browser = mainPanel.getPositionBrowser();
        if (browser != null) {
            browser.setHighlightRegion(window.getChrom(), window.getStartPos(), window.getEndPos());
        }

        RegionSetContext context = RegionSetContextHolder.get();
        GenomicInterval interval = new GenomicInterval(window.getChrom(), window.getStartPos(), window.getEndPos());
        RegionSetPopupMenu.show(trackBoardPanel, e.getX(), e.getY(), context, interval);
    }

    private boolean isRegionSetCandidate(DataSet dataSet, FSTWindow window, double threshold) {
        if (dataSet == null || window == null) {
            return false;
        }
        double value = window.getAvgFst();
        if (!Double.isFinite(value)) {
            return false;
        }

        switch (dataSet.getType()) {
            case FST:
                return value >= threshold;
            default:
                // Future browsers (e.g. GWAS) can add their own threshold rule here.
                return false;
        }
    }
    
    private void handleMouseMove(MouseEvent e) {
        TrackView trackView = trackBoardPanel.getTrackViewAt(e.getPoint());
        if (trackView == null) {
            mainPanel.updateHoveredIndex(null, null);
            return;
        }
        
        Point trackPoint = SwingUtilities.convertPoint(trackBoardPanel, e.getPoint(), trackView);
        Rectangle plotBounds = trackView.getPlotAreaBounds();
        
        if (!plotBounds.contains(trackPoint)) {
            mainPanel.updateHoveredIndex(null, null);
            return;
        }
        
        // 找到命中的数据点
        HitResult result = findHitPoint(trackView, trackPoint, plotBounds);
        if (result != null && result.originalIndex >= 0) {
            mainPanel.updateHoveredIndex(result.originalIndex, result.dataSetId);
        } else {
            mainPanel.updateHoveredIndex(null, null);
        }
    }
    
    private void handleMouseClick(MouseEvent e) {
        if (!SwingUtilities.isLeftMouseButton(e)) {
            return;
        }

        TrackView trackView = trackBoardPanel.getTrackViewAt(e.getPoint());
        if (trackView == null) return;
        
        Point trackPoint = SwingUtilities.convertPoint(trackBoardPanel, e.getPoint(), trackView);
        Rectangle plotBounds = trackView.getPlotAreaBounds();
        
        if (!plotBounds.contains(trackPoint)) return;
        
        // 找到命中的数据点
        HitResult result = findHitPoint(trackView, trackPoint, plotBounds);
        if (result != null && result.originalIndex >= 0 && result.isTop) {
            // 只允许点击top位点
            mainPanel.updateSelectedIndex(result.originalIndex, result.dataSetId);
            
            // 调用browser jump
            DataSet dataSet = mainPanel.findDataSet(result.dataSetId);
            if (dataSet != null && result.originalIndex < dataSet.size()) {
                FSTWindow window = dataSet.get(result.originalIndex);
                if (window != null) {
                    jumpToBrowser(window.getChrom(), window.getStartPos(), window.getEndPos());
                }
            }
        }
    }
    
    private HitResult findHitPoint(TrackView trackView, Point p, Rectangle plotBounds) {
        // 与绘制共用同一个 PlotContext：数值→像素的换算必须完全一致
        PlotContext ctx = PlotContext.of(plotBounds);
        
        GenomeContext genome = mainPanel.getGenomeContext();
        if (genome == null) return null;
        
        // 遍历该轨道上的所有PlotItem
        for (PlotItem item : trackView.getTrackModel().getItems()) {
            if (!item.isVisible()) continue;
            
            DataSet dataSet = mainPanel.findDataSet(item.getDataSetId());
            if (dataSet == null) continue;
            
            List<FSTWindow> windows = dataSet.getWindows();
            if (windows == null || windows.isEmpty()) continue;
            
            FSTDisplayCache displayCache = new FSTDisplayCache(windows);
            displayCache.ensure(genome.getSelectedChromosome());
            int n = displayCache.getDisplayCount(genome.getSelectedChromosome());
            if (n <= 0) continue;
            
            double scale = ctx.getScalePerIndex(n);
            if (scale <= 0) continue;
            
            // Y轴上限：折叠轨道或改过Y轴范围时它不再是1.0，绘制和命中必须用同一个值
            double axisMax = mainPanel.axisMaxFor(trackView, dataSet.getType());
            boolean signedAxis = dataSet.getType() == DataSet.DataType.TAJIMAD;
            
            // 计算鼠标对应的displayIndex
            int relX = p.x - plotBounds.x;
            int approxDi = (int) Math.round(relX / scale);
            if (approxDi < 0) approxDi = 0;
            if (approxDi >= n) approxDi = n - 1;
            
            int range = (int) Math.ceil(HOVER_DISTANCE / scale) + 2;
            int startDi = Math.max(0, approxDi - range);
            int endDi = Math.min(n - 1, approxDi + range);
            
            final int r2 = HOVER_DISTANCE * HOVER_DISTANCE;
            // 只有当PlotItem启用top显示时，才获取topBits
            BitSet topBits = null;
            if (item.isTopEnabled()) {
                topBits = mainPanel.getTopBits(item.getDataSetId());
            }
            
            int bestTopOi = -1, bestTopD2 = Integer.MAX_VALUE;
            int bestOi = -1, bestD2 = Integer.MAX_VALUE;
            
            for (int di = startDi; di <= endDi; di++) {
                int oi = displayCache.displayIndexToOriginalIndex(genome.getSelectedChromosome(), di);
                if (oi < 0 || oi >= windows.size()) continue;
                
                FSTWindow w = windows.get(oi);
                double fst = w.getAvgFst();
                if (!Double.isFinite(fst)) continue;
                
                int x = ctx.xForIndex(di, scale);
                int y = signedAxis ? ctx.yForSignedValue(fst, axisMax) : ctx.yForValue(fst, axisMax);
                
                int dx = p.x - x;
                int dy = p.y - y;
                int d2 = dx * dx + dy * dy;
                
                if (d2 < bestD2) {
                    bestD2 = d2;
                    bestOi = oi;
                }
                
                // 判断是否为top位点：只检查topBits，因为topBits已经基于PlotItem的topPercent正确计算
                boolean isTop = (topBits != null && topBits.get(oi));
                if (isTop && d2 < bestTopD2) {
                    bestTopD2 = d2;
                    bestTopOi = oi;
                }
            }
            
            if (bestTopOi >= 0 && bestTopD2 <= r2) {
                return new HitResult(bestTopOi, item.getDataSetId(), true);
            }
            if (bestOi >= 0 && bestD2 <= r2) {
                return new HitResult(bestOi, item.getDataSetId(), false);
            }
        }
        
        return null;
    }
    
    private void jumpToBrowser(String chrom, int start, int end) {
        GenomeBrowserPanel browser = mainPanel.getPositionBrowser();
        if (browser == null) return;
        // Mark the selected FST window on the genome browser's ruler, then jump there.
        browser.setHighlightRegion(chrom, start, end);
        browser.setPosition(chrom, start, end);
    }
    
    private static class HitResult {
        final int originalIndex;
        final String dataSetId;
        final boolean isTop;
        
        HitResult(int originalIndex, String dataSetId, boolean isTop) {
            this.originalIndex = originalIndex;
            this.dataSetId = dataSetId;
            this.isTop = isTop;
        }
    }
}
