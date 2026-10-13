package simplegenomehub.population.fst.chart.view;

import simplegenomehub.genomebrowser.GenomeBrowserPanel;
import simplegenomehub.population.fst.chart.FSTTrackChartPanel;
import simplegenomehub.population.fst.chart.FSTWindow;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

// 图表绘制主类：负责所有图表绘制和更新相关的操作
public class FSTChartMain {
    
    // 更新图表（单个FST文件）
    public static void updateChart(JSplitPane splitPane,
                                   GenomeBrowserPanel genomeBrowser,
                                   List<FSTWindow> windows,
                                   double threshold,
                                   String filename,
                                   List<List<FSTWindow>> multiplePIWindows,
                                   String piFilename,
                                   List<String> piFilenames,
                                   List<List<FSTWindow>> multipleWindows,
                                   List<Double> multipleThresholds,
                                   List<String> filenames,
                                   double topPercent,
                                   Color firstColor,
                                   Color secondColor,
                                   Color topColor,
                                   Color piColor) {
        // 统一使用FSTTrackChartPanel，无论是单个还是多个文件
        if (multipleWindows != null && !multipleWindows.isEmpty()) {
            // 多个文件的情况
            updateMultipleCharts(splitPane, genomeBrowser, multipleWindows, multipleThresholds,
                               filenames, multiplePIWindows, piFilename, piFilenames,
                               topPercent, firstColor, secondColor, topColor, piColor);
        } else if (windows != null && !windows.isEmpty()) {
            // 单个文件的情况：转换为多图表面板的格式
            List<List<FSTWindow>> singleWindowList = new ArrayList<>();
            singleWindowList.add(windows);
            List<Double> singleThresholdList = new ArrayList<>();
            singleThresholdList.add(threshold);
            List<String> singleFileNames = new ArrayList<>();
            // 确保文件名以.fst结尾，以便正确识别数据类型
            String displayFileName = filename != null ? filename : "file.fst";
            // 如果文件名不以.fst结尾，添加.fst后缀以便正确识别
            if (!displayFileName.toLowerCase().endsWith(".fst") && !displayFileName.toLowerCase().endsWith(".fst.gz")) {
                displayFileName = displayFileName + ".fst";
            }
            singleFileNames.add(displayFileName);
            
            // 检查是否已有PI数据，如果有则合并显示
            if (multiplePIWindows != null && !multiplePIWindows.isEmpty()) {
                // 有PI数据，合并显示
                List<String> piFileNames = new ArrayList<>();
                if (piFilenames != null && !piFilenames.isEmpty()) {
                    piFileNames.addAll(piFilenames);
                } else if (piFilename != null && !piFilename.isEmpty()) {
                    piFileNames.add(piFilename);
                }
                updateMixedChart(splitPane, genomeBrowser, windows, singleWindowList, singleThresholdList, singleFileNames,
                               multiplePIWindows, piFileNames, topPercent, firstColor, secondColor, topColor, piColor);
            } else {
                // 只有FST数据
                updateSingleFSTChart(splitPane, genomeBrowser, windows, singleWindowList, singleThresholdList, singleFileNames,
                                   topPercent, firstColor, secondColor, topColor);
            }
        }
    }
    
    // 更新单个FST图表
    private static void updateSingleFSTChart(JSplitPane splitPane,
                                            GenomeBrowserPanel genomeBrowser,
                                            List<FSTWindow> windows,
                                            List<List<FSTWindow>> singleWindowList,
                                            List<Double> singleThresholdList,
                                            List<String> singleFileNames,
                                            double topPercent,
                                            Color firstColor,
                                            Color secondColor,
                                            Color topColor) {
        FSTTrackChartPanel trackChartPanel = new FSTTrackChartPanel(
            windows, singleWindowList, singleThresholdList, singleFileNames,
            topPercent, firstColor, secondColor, topColor
        );
        updateChartPanel(splitPane, genomeBrowser, trackChartPanel);
    }
    
    // 更新多个图表（支持与PI数据合并）
    public static void updateMultipleCharts(JSplitPane splitPane,
                                           GenomeBrowserPanel genomeBrowser,
                                           List<List<FSTWindow>> multipleWindows,
                                           List<Double> multipleThresholds,
                                           List<String> filenames,
                                           List<List<FSTWindow>> multiplePIWindows,
                                           String piFilename,
                                           List<String> piFilenames,
                                           double topPercent,
                                           Color firstColor,
                                           Color secondColor,
                                           Color topColor,
                                           Color piColor) {
        if (multipleWindows == null || multipleWindows.isEmpty()) return;
        
        // 检查是否已有PI数据，如果有则合并显示
        if (multiplePIWindows != null && !multiplePIWindows.isEmpty()) {
            // 有PI数据，合并显示
            List<String> fstFileNames = new ArrayList<>();
            if (filenames != null && !filenames.isEmpty()) {
                fstFileNames.addAll(filenames);
            } else {
                for (int i = 0; i < multipleWindows.size(); i++) {
                    fstFileNames.add("FST File " + (i + 1));
                }
            }
            
            List<String> piFileNames = new ArrayList<>();
            if (piFilenames != null && !piFilenames.isEmpty()) {
                piFileNames.addAll(piFilenames);
            } else if (piFilename != null && !piFilename.isEmpty()) {
                piFileNames.add(piFilename);
            }
            
            List<FSTWindow> referenceWindows = multipleWindows.get(0);
            updateMixedChart(splitPane, genomeBrowser, referenceWindows, multipleWindows, multipleThresholds, fstFileNames,
                           multiplePIWindows, piFileNames, topPercent, firstColor, secondColor, topColor, piColor);
        } else {
            List<String> fileNames = new ArrayList<>();
            if (filenames != null && !filenames.isEmpty()) {
                fileNames.addAll(filenames);
            } else {
                for (int i = 0; i < multipleWindows.size(); i++) {
                    fileNames.add("FST File " + (i + 1));
                }
            }
            
            FSTTrackChartPanel trackChartPanel = new FSTTrackChartPanel(
                multipleWindows.get(0), multipleWindows, multipleThresholds, fileNames,
                topPercent, firstColor, secondColor, topColor
            );
            updateChartPanel(splitPane, genomeBrowser, trackChartPanel);
        }
    }
    
    // 更新PI图表（支持与FST数据合并）
    public static void updatePIChart(JSplitPane splitPane,
                                    GenomeBrowserPanel genomeBrowser,
                                    List<FSTWindow> referenceWindows,
                                    List<List<FSTWindow>> multiplePIWindows,
                                    List<String> piFileNames,
                                    List<List<FSTWindow>> multipleWindows,
                                    List<Double> multipleThresholds,
                                    List<String> filenames,
                                    String filename,
                                    double topPercent,
                                    Color firstColor,
                                    Color secondColor,
                                    Color topColor,
                                    Color piColor) {
        // 检查是否已有FST数据，如果有则合并显示
        if (multipleWindows != null && !multipleWindows.isEmpty()) {
            // 有FST数据，合并显示
            List<String> fstFileNames = filenames != null ? filenames : 
                                       (filename != null ? java.util.Arrays.asList(filename) : new ArrayList<>());
            updateMixedChart(splitPane, genomeBrowser, referenceWindows, multipleWindows, multipleThresholds, fstFileNames,
                           multiplePIWindows, piFileNames, topPercent, firstColor, secondColor, topColor, piColor);
        } else {
            FSTTrackChartPanel trackChartPanel = new FSTTrackChartPanel(
                referenceWindows, null, null, piFileNames,
                topPercent, firstColor, secondColor, topColor,
                multiplePIWindows, piColor, null
            );
            updateChartPanel(splitPane, genomeBrowser, trackChartPanel);
        }
    }
    
    // 更新TajimaD图表（支持与FST和PI数据合并）
    public static void updateTajimaDChart(JSplitPane splitPane,
                                          GenomeBrowserPanel genomeBrowser,
                                          List<FSTWindow> referenceWindows,
                                          List<List<FSTWindow>> multipleTajimaDWindows,
                                          List<String> tajimaDFileNames,
                                          List<List<FSTWindow>> multipleWindows,
                                          List<Double> multipleThresholds,
                                          List<String> filenames,
                                          String filename,
                                          List<List<FSTWindow>> multiplePIWindows,
                                          String piFilename,
                                          List<String> piFilenames,
                                          double topPercent,
                                          Color firstColor,
                                          Color secondColor,
                                          Color topColor,
                                          Color piColor) {
        List<String> allFileNames = mergeFileNames(filenames, filename, piFilenames, piFilename, tajimaDFileNames, null, null, null);
        
        FSTTrackChartPanel trackChartPanel = new FSTTrackChartPanel(
            referenceWindows, multipleWindows, multipleThresholds, allFileNames,
            topPercent, firstColor, secondColor, topColor,
            multiplePIWindows, piColor, multipleTajimaDWindows
        );
        
        updateChartPanel(splitPane, genomeBrowser, trackChartPanel);
    }
    
    // 更新Logistic图表（支持与FST、PI和TajimaD数据合并）
    public static void updateLogisticChart(JSplitPane splitPane,
                                          GenomeBrowserPanel genomeBrowser,
                                          List<FSTWindow> referenceWindows,
                                          List<List<FSTWindow>> multipleLogisticWindows,
                                          List<String> logisticFileNames,
                                          List<List<FSTWindow>> multipleWindows,
                                          List<Double> multipleThresholds,
                                          List<String> filenames,
                                          String filename,
                                          List<List<FSTWindow>> multiplePIWindows,
                                          String piFilename,
                                          List<String> piFilenames,
                                          List<List<FSTWindow>> multipleTajimaDWindows,
                                          String tajimaDFilename,
                                          List<String> tajimaDFilenames,
                                          double topPercent,
                                          Color firstColor,
                                          Color secondColor,
                                          Color topColor,
                                          Color piColor) {
        // 合并所有文件名，包括logistic文件名
        String singleLogisticFile = (logisticFileNames != null && logisticFileNames.size() == 1) ? logisticFileNames.get(0) : null;
        List<String> multipleLogisticFiles = (logisticFileNames != null && logisticFileNames.size() > 1) ? logisticFileNames : null;
        List<String> allFileNames = mergeFileNames(filenames, filename, piFilenames, piFilename, 
                                                   tajimaDFilenames, tajimaDFilename, 
                                                   multipleLogisticFiles, singleLogisticFile);
        
        FSTTrackChartPanel trackChartPanel = new FSTTrackChartPanel(
            referenceWindows, multipleWindows, multipleThresholds, allFileNames,
            topPercent, firstColor, secondColor, topColor,
            multiplePIWindows, piColor, multipleTajimaDWindows, multipleLogisticWindows
        );
        
        updateChartPanel(splitPane, genomeBrowser, trackChartPanel);
    }
    
    // 更新混合图表（同时包含FST和PI数据）
    public static void updateMixedChart(JSplitPane splitPane,
                                        GenomeBrowserPanel genomeBrowser,
                                        List<FSTWindow> referenceWindows,
                                        List<List<FSTWindow>> multipleFSTWindows,
                                        List<Double> multipleFSTThresholds,
                                        List<String> fstFileNames,
                                        List<List<FSTWindow>> multiplePIWindows,
                                        List<String> piFileNames,
                                        double topPercent,
                                        Color firstColor,
                                        Color secondColor,
                                        Color topColor,
                                        Color piColor) {
        List<String> allFileNames = mergeFileNames(fstFileNames, null, piFileNames, null, null, null, null, null);
        
        FSTTrackChartPanel trackChartPanel = new FSTTrackChartPanel(
            referenceWindows, multipleFSTWindows, multipleFSTThresholds, allFileNames,
            topPercent, firstColor, secondColor, topColor,
            multiplePIWindows, piColor, null
        );
        
        updateChartPanel(splitPane, genomeBrowser, trackChartPanel);
    }
    
    // 统一的图表更新方法（提取公共逻辑）
    private static void updateChartPanel(JSplitPane splitPane, GenomeBrowserPanel genomeBrowser, FSTTrackChartPanel trackChartPanel) {
        ChartState state = saveChartState(splitPane);
        trackChartPanel.setPositionBrowser(genomeBrowser);
        restoreChartState(trackChartPanel, state);
        updateChartView(splitPane, trackChartPanel);
    }
    
    // 合并文件名列表（统一处理）
    private static List<String> mergeFileNames(List<String> fstFileList, String fstSingleFile,
                                               List<String> piFileList, String piSingleFile,
                                               List<String> tajimaDFileList, String tajimaDSingleFile,
                                               List<String> logisticFileList, String logisticSingleFile) {
        List<String> allFileNames = new ArrayList<>();
        if (fstFileList != null && !fstFileList.isEmpty()) {
            allFileNames.addAll(fstFileList);
        } else if (fstSingleFile != null && !fstSingleFile.isEmpty()) {
            allFileNames.add(fstSingleFile);
        }
        if (piFileList != null && !piFileList.isEmpty()) {
            allFileNames.addAll(piFileList);
        } else if (piSingleFile != null && !piSingleFile.isEmpty()) {
            allFileNames.add(piSingleFile);
        }
        if (tajimaDFileList != null && !tajimaDFileList.isEmpty()) {
            allFileNames.addAll(tajimaDFileList);
        } else if (tajimaDSingleFile != null && !tajimaDSingleFile.isEmpty()) {
            allFileNames.add(tajimaDSingleFile);
        }
        if (logisticFileList != null && !logisticFileList.isEmpty()) {
            allFileNames.addAll(logisticFileList);
        } else if (logisticSingleFile != null && !logisticSingleFile.isEmpty()) {
            allFileNames.add(logisticSingleFile);
        }
        return allFileNames;
    }
    
    // 保存图表状态
    private static ChartState saveChartState(JSplitPane splitPane) {
        ChartState state = new ChartState();
        Component leftComponent = splitPane.getLeftComponent();
        if (leftComponent instanceof JScrollPane) {
            Component view = ((JScrollPane) leftComponent).getViewport().getView();
            if (view instanceof FSTTrackChartPanel) {
                FSTTrackChartPanel oldPanel = (FSTTrackChartPanel) view;
                state.positionLineChromosome = oldPanel.getPositionLineChromosome();
                state.positionLinePosition = oldPanel.getPositionLinePosition();
                state.positionLinePosition2 = oldPanel.getPositionLinePosition2();
            }
        }
        return state;
    }
    
    // 恢复图表状态
    private static void restoreChartState(FSTTrackChartPanel trackChartPanel, ChartState state) {
        if (state.positionLineChromosome != null) {
            if (state.positionLinePosition2 > 0) {
                trackChartPanel.setPositionLineInfo(state.positionLineChromosome, state.positionLinePosition, state.positionLinePosition2);
            } else {
                trackChartPanel.setPositionLineInfo(state.positionLineChromosome, state.positionLinePosition);
            }
        }
    }
    
    // 更新图表视图
    private static void updateChartView(JSplitPane splitPane, FSTTrackChartPanel trackChartPanel) {
        trackChartPanel.setBorder(javax.swing.BorderFactory.createLoweredBevelBorder());
        
        Component leftComponent = splitPane.getLeftComponent();
        if (leftComponent instanceof JScrollPane) {
            ((JScrollPane) leftComponent).setViewportView(trackChartPanel);
            trackChartPanel.revalidate();
            trackChartPanel.repaint();
        } else {
            splitPane.setLeftComponent(new JScrollPane(trackChartPanel));
        }
    }
    
    // 获取当前的图表面板
    public static FSTTrackChartPanel getCurrentChartPanel(JSplitPane splitPane) {
        Component leftComponent = splitPane.getLeftComponent();
        if (leftComponent instanceof JScrollPane) {
            Component view = ((JScrollPane) leftComponent).getViewport().getView();
            if (view instanceof FSTTrackChartPanel) {
                return (FSTTrackChartPanel) view;
            }
        }
        return null;
    }
    
    // 图表状态类
    private static class ChartState {
        String positionLineChromosome = null;
        int positionLinePosition = -1;
        int positionLinePosition2 = -1;
    }
}
