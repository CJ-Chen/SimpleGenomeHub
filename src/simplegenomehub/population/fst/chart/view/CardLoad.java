package simplegenomehub.population.fst.chart.view;

import simplegenomehub.population.fst.chart.FSTTrackChartPanel;
import simplegenomehub.population.fst.chart.FSTWindow;
import simplegenomehub.population.fst.chart.model.DataSet;
import simplegenomehub.population.fst.chart.model.PlotItem;
import simplegenomehub.population.fst.chart.model.TrackBoardModel;
import simplegenomehub.population.fst.chart.model.TrackModel;
import simplegenomehub.population.fst.FSTRunManifest;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;

// 卡牌加载器和操作类：负责卡牌的加载、操作、统计、文本处理等所有相关功能
public class CardLoad {

    /**
     * 卡牌显示名：优先使用命名清单里的自定义名（Compare/类群名）；
     * 没有清单时回退到旧逻辑——取文件基名第 1、2 个点号之间的片段
     */
    public static String getCardDisplayName(DataSet dataSet) {
        if (dataSet == null) return "";
        String display = dataSet.getDisplayName();
        if (display != null && !display.trim().isEmpty()) {
            return display;
        }
        String parsed = extractCardNameFromFileName(dataSet.getFileName());
        return parsed != null ? parsed : dataSet.getFileName();
    }

    /**
     * 卡牌悬停提示：显示显示名 + 明细说明；都没有时返回 null（不显示提示）
     */
    public static String getCardToolTip(DataSet dataSet) {
        if (dataSet == null) return null;
        StringBuilder sb = new StringBuilder();
        String display = getCardDisplayName(dataSet);
        if (display != null && !display.isEmpty()) {
            sb.append(display);
        }
        String desc = dataSet.getDescription();
        if (desc != null && !desc.trim().isEmpty()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(desc.trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /**
     * 旧的文件名解析规则：取文件名第一个.和第二个.之间的内容；
     * GWAS 开头的文件取第一个.之前的内容
     */
    public static String extractCardNameFromFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) return null;

        if (fileName.toUpperCase().startsWith("GWAS")) {
            int dotIndex = fileName.indexOf('.');
            if (dotIndex > 0) {
                return fileName.substring(0, dotIndex); // 返回"GWAS1"、"GWAS2"等
            }
            return fileName; // 如果没有点，返回整个文件名
        }

        int firstDot = fileName.indexOf('.');
        if (firstDot < 0) return null;
        int secondDot = fileName.indexOf('.', firstDot + 1);
        if (secondDot < 0) return null;
        String segment = fileName.substring(firstDot + 1, secondDot);
        return segment.isEmpty() ? null : segment;
    }

    // 从文件数据创建数据集列表
    public static List<DataSet> createDataSets(List<List<FSTWindow>> multipleWindows,
                                               List<String> fileNames,
                                               List<Double> thresholds) {
        List<DataSet> dataSets = new ArrayList<>();
        int datasetIndex = 0;
        
        for (int i = 0; i < multipleWindows.size(); i++) {
            String fileName = findFileNameByType(fileNames, i, "fst");
            Double threshold = thresholds != null && i < thresholds.size() ? thresholds.get(i) : null;
            DataSet dataSet = createDataSetFromFile(fileName, multipleWindows.get(i), datasetIndex, threshold);
            dataSets.add(dataSet);
            datasetIndex++;
        }
        
        return dataSets;
    }
    
    // 创建FST和PI混合数据集列表
    public static List<DataSet> createMixedDataSets(List<List<FSTWindow>> multipleFSTWindows,
                                                    List<Double> multipleFSTThresholds,
                                                    List<String> fileNames,
                                                    List<List<FSTWindow>> multiplePIWindows) {
        List<DataSet> dataSets = new ArrayList<>();
        int datasetIndex = 0;
        
        // 创建FST数据集
        if (multipleFSTWindows != null && multipleFSTThresholds != null) {
            for (int i = 0; i < multipleFSTWindows.size(); i++) {
                String fileName = findFileNameByType(fileNames, i, "fst");
                Double threshold = i < multipleFSTThresholds.size() ? multipleFSTThresholds.get(i) : null;
                DataSet dataSet = createDataSetFromFile(fileName, multipleFSTWindows.get(i), datasetIndex, threshold);
                dataSets.add(dataSet);
                datasetIndex++;
            }
        }
        
        // 创建PI数据集
        if (multiplePIWindows != null) {
            for (int i = 0; i < multiplePIWindows.size(); i++) {
                String fileName = findFileNameByType(fileNames, i, "pi");
                DataSet dataSet = createDataSetFromFile(fileName, multiplePIWindows.get(i), datasetIndex, null);
                dataSets.add(dataSet);
                datasetIndex++;
            }
        }
        
        return dataSets;
    }
    
    // 创建FST、PI和TajimaD混合数据集列表
    public static List<DataSet> createMixedDataSetsWithTajimaD(List<List<FSTWindow>> multipleFSTWindows,
                                                               List<Double> multipleFSTThresholds,
                                                               List<String> fileNames,
                                                               List<List<FSTWindow>> multiplePIWindows,
                                                               List<List<FSTWindow>> multipleTajimaDWindows) {
        List<DataSet> dataSets = new ArrayList<>();
        int datasetIndex = 0;
        
        // 创建FST数据集
        if (multipleFSTWindows != null && multipleFSTThresholds != null) {
            for (int i = 0; i < multipleFSTWindows.size(); i++) {
                String fileName = findFileNameByType(fileNames, i, "fst");
                Double threshold = i < multipleFSTThresholds.size() ? multipleFSTThresholds.get(i) : null;
                DataSet dataSet = createDataSetFromFile(fileName, multipleFSTWindows.get(i), datasetIndex, threshold);
                dataSets.add(dataSet);
                datasetIndex++;
            }
        }
        
        // 创建PI数据集
        if (multiplePIWindows != null) {
            for (int i = 0; i < multiplePIWindows.size(); i++) {
                String fileName = findFileNameByType(fileNames, i, "pi");
                DataSet dataSet = createDataSetFromFile(fileName, multiplePIWindows.get(i), datasetIndex, null);
                dataSets.add(dataSet);
                datasetIndex++;
            }
        }
        
        // 创建TajimaD数据集
        if (multipleTajimaDWindows != null) {
            for (int i = 0; i < multipleTajimaDWindows.size(); i++) {
                String fileName = findFileNameByType(fileNames, i, "tajimad");
                DataSet dataSet = createDataSetFromFile(fileName, multipleTajimaDWindows.get(i), datasetIndex, null);
                dataSets.add(dataSet);
                datasetIndex++;
            }
        }
        
        return dataSets;
    }
    
    // 创建FST、PI、TajimaD和Logistic混合数据集列表
    public static List<DataSet> createMixedDataSetsWithLogistic(List<List<FSTWindow>> multipleFSTWindows,
                                                               List<Double> multipleFSTThresholds,
                                                               List<String> fileNames,
                                                               List<List<FSTWindow>> multiplePIWindows,
                                                               List<List<FSTWindow>> multipleTajimaDWindows,
                                                               List<List<FSTWindow>> multipleLogisticWindows) {
        List<DataSet> dataSets = new ArrayList<>();
        int datasetIndex = 0;
        
        // 创建FST数据集
        if (multipleFSTWindows != null && multipleFSTThresholds != null) {
            for (int i = 0; i < multipleFSTWindows.size(); i++) {
                String fileName = findFileNameByType(fileNames, i, "fst");
                Double threshold = i < multipleFSTThresholds.size() ? multipleFSTThresholds.get(i) : null;
                DataSet dataSet = createDataSetFromFile(fileName, multipleFSTWindows.get(i), datasetIndex, threshold);
                dataSets.add(dataSet);
                datasetIndex++;
            }
        }
        
        // 创建PI数据集
        if (multiplePIWindows != null) {
            for (int i = 0; i < multiplePIWindows.size(); i++) {
                String fileName = findFileNameByType(fileNames, i, "pi");
                DataSet dataSet = createDataSetFromFile(fileName, multiplePIWindows.get(i), datasetIndex, null);
                dataSets.add(dataSet);
                datasetIndex++;
            }
        }
        
        // 创建TajimaD数据集
        if (multipleTajimaDWindows != null) {
            for (int i = 0; i < multipleTajimaDWindows.size(); i++) {
                String fileName = findFileNameByType(fileNames, i, "tajimad");
                DataSet dataSet = createDataSetFromFile(fileName, multipleTajimaDWindows.get(i), datasetIndex, null);
                dataSets.add(dataSet);
                datasetIndex++;
            }
        }
        
        // 创建Logistic数据集
        if (multipleLogisticWindows != null) {
            for (int i = 0; i < multipleLogisticWindows.size(); i++) {
                String fileName = findFileNameByType(fileNames, i, "logistic");
                DataSet dataSet = createDataSetFromFile(fileName, multipleLogisticWindows.get(i), datasetIndex, null);
                dataSets.add(dataSet);
                datasetIndex++;
            }
        }
        
        return dataSets;
    }
    
    // 从文件数据创建单个数据集
    public static DataSet createDataSetFromFile(String fileName, List<FSTWindow> windows, int datasetIndex, Double threshold) {
        // 从文件名识别数据类型
        DataSet.DataType dataType = detectDataTypeFromFileName(fileName);
        
        // 从命名清单读取自定义显示名与明细说明（没有则保持null，展示时回退文件名解析）
        FSTRunManifest.RunEntry entry = FSTRunManifest.lookup(fileName);

        // 创建数据集
        DataSet dataSet = new DataSet("dataset_" + datasetIndex, fileName, dataType, windows, datasetIndex + 1,
                                      entry != null ? entry.displayName : null,
                                      entry != null ? entry.description : null);
        
        return dataSet;
    }
    
    // 从文件名识别数据类型
    public static DataSet.DataType detectDataTypeFromFileName(String fileName) {
        if (fileName == null) {
            return DataSet.DataType.OTHER;
        }
        
        String lowerFileName = fileName.toLowerCase();
        
        // 优先检查Logistic文件（包含logistic、gwas等关键词，且以.logistic结尾）
        // 注意：必须在检查FST之前，因为某些文件名可能同时包含fst和logistic
        if (lowerFileName.endsWith(".logistic") || lowerFileName.endsWith(".logistic.gz")) {
            return DataSet.DataType.LOGISTIC;
        }
        
        // 检查是否是TajimaD文件（包含tajimad、tajima_d或tajima.d）
        if (lowerFileName.contains("tajimad") || lowerFileName.contains("tajima_d") || 
            lowerFileName.contains("tajima.d")) {
            return DataSet.DataType.TAJIMAD;
        }
        
        // 检查是否是PI文件（包含windowed和pi，且以.pi结尾）
        if (lowerFileName.contains("windowed") && lowerFileName.contains(".pi")) {
            return DataSet.DataType.PI;
        }
        
        // 检查是否是FST文件（包含fst，且以.fst结尾，或者文件名包含"fst"关键词）
        // 注意：对于单个文件拖拽的情况，文件名可能是"FST File"，需要识别这种情况
        if (lowerFileName.endsWith(".fst") || lowerFileName.endsWith(".fst.gz") || 
            (lowerFileName.contains("fst") && !lowerFileName.contains("logistic") && !lowerFileName.contains("tajimad"))) {
            return DataSet.DataType.FST;
        }
        
        // 默认返回OTHER
        return DataSet.DataType.OTHER;
    }
    
    // 从文件名列表中根据类型和索引查找对应的文件名
    public static String findFileNameByType(List<String> fileNames, int index, String typeKeyword) {
        if (fileNames == null || fileNames.isEmpty()) {
            // 返回能够被识别的默认文件名
            return typeKeyword.equals("pi") ? "file" + (index + 1) + ".windowed.pi" : "file" + (index + 1) + ".fst";
        }
        
        // 根据类型关键字查找匹配的文件名
        String lowerKeyword = typeKeyword.toLowerCase();
        List<String> matchingFiles = new ArrayList<>();
        for (String fileName : fileNames) {
            String lowerFileName = fileName.toLowerCase();
            if (lowerKeyword.equals("pi")) {
                // PI文件：包含windowed和pi
                if (lowerFileName.contains("windowed") && lowerFileName.contains("pi")) {
                    matchingFiles.add(fileName);
                }
            } else if (lowerKeyword.equals("fst")) {
                // FST文件：包含fst但不包含pi
                if (lowerFileName.contains("fst") && !lowerFileName.contains("pi")) {
                    matchingFiles.add(fileName);
                }
            } else if (lowerKeyword.equals("tajimad") || lowerKeyword.equals("td")) {
                // TajimaD文件：包含tajimad、tajima_d或tajima.d
                if (lowerFileName.contains("tajimad") || lowerFileName.contains("tajima_d") || 
                    lowerFileName.contains("tajima.d")) {
                    matchingFiles.add(fileName);
                }
            } else if (lowerKeyword.equals("logistic") || lowerKeyword.equals("gwas")) {
                // Logistic文件：包含logistic或gwas
                if (lowerFileName.contains("logistic") || lowerFileName.contains("gwas")) {
                    matchingFiles.add(fileName);
                }
            }
        }
        
        // 如果找到匹配的文件，返回对应索引的文件
        if (index < matchingFiles.size()) {
            return matchingFiles.get(index);
        }
        
        // 如果找不到匹配的文件，但fileNames中有文件，尝试直接使用索引（向后兼容）
        if (index < fileNames.size()) {
            return fileNames.get(index);
        }
        
        // 如果找不到匹配的文件，返回能够被识别的默认名称
        if (typeKeyword.equals("pi")) {
            return "file" + (index + 1) + ".windowed.pi";
        } else if (typeKeyword.equals("tajimad") || typeKeyword.equals("td")) {
            return "file" + (index + 1) + ".Tajima.D";
        } else if (typeKeyword.equals("logistic") || typeKeyword.equals("gwas")) {
            return "GWAS" + (index + 1) + ".logistic";
        } else {
            return "file" + (index + 1) + ".fst";
        }
    }
    
    // ========== 卡牌操作相关方法 ==========
    
    // 应用Top百分比：全局同步所有小卡牌的Top按钮状态和文本框值
    public static void applyTopPercent(FSTTrackChartPanel chartPanel, double topPercent) {
        if (chartPanel == null) return;
        
        try {
            // 获取所有轨道中的所有PlotItem
            TrackBoardModel boardModel = chartPanel.getBoardModel();
            boolean allEnabled = false; // 检查当前是否所有都启用
            
            // 第一次遍历：检查当前状态
            for (TrackModel track : boardModel.getTracks()) {
                for (PlotItem item : track.getItems()) {
                    if (item.isTopEnabled()) {
                        allEnabled = true;
                        break;
                    }
                }
                if (allEnabled) break;
            }
            
            // 第二次遍历：切换所有PlotItem的topEnabled状态，并设置topPercent为工具栏文本框的值
            boolean newState = !allEnabled; // 切换状态
            for (TrackModel track : boardModel.getTracks()) {
                for (PlotItem item : track.getItems()) {
                    item.setTopEnabled(newState);
                    item.setTopPercent(topPercent); // 统一设置为工具栏文本框的值
                    if (newState) {
                        // 如果启用，重新计算top bits
                        chartPanel.recalculateTopBitsForItem(item);
                    }
                }
            }
            
            // 更新图表显示
            chartPanel.repaint();
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, 
                "Error applying top percent: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    // 显示窗口详情（在底部面板绘制长方形）
    // 注意：现在底部面板是染色体浏览器，此功能暂时保留但可能不再使用
    public static void showWindowDetails(FSTWindow window, int index) {
        // 底部面板现在是染色体浏览器，不再绘制红色长方形
        // 如果需要，可以在这里添加其他功能，比如高亮显示对应的染色体区域
        System.out.println("Clicked window: Chrom=" + window.getChrom() + 
                         ", Start=" + window.getStartPos() + 
                         ", End=" + window.getEndPos() + 
                         ", AvgFst=" + window.getAvgFst());
    }
    
    // 获取卡牌统计信息（可选，用于将来扩展）
    public static String getCardStatistics(PlotItem item, FSTTrackChartPanel chartPanel) {
        if (item == null || chartPanel == null) {
            return "";
        }
        
        // 这里可以添加统计信息的计算逻辑
        // 例如：计算该卡牌对应的数据集的统计信息
        StringBuilder stats = new StringBuilder();
        stats.append("Top Enabled: ").append(item.isTopEnabled() ? "Yes" : "No");
        stats.append(", Top Percent: ").append(String.format("%.4f", item.getTopPercent()));
        stats.append(", Style: ").append(item.getStyle());
        
        return stats.toString();
    }
    
    // 格式化Top百分比显示文本
    public static String formatTopPercentText(double percent) {
        if (percent <= 0) return "0.000";
        if (percent < 0.0001) return String.format("%.5f", percent);
        if (percent < 0.001) return String.format("%.4f", percent);
        if (percent < 0.01) return String.format("%.3f", percent);
        if (percent < 0.1) return String.format("%.2f", percent);
        if (percent < 1.0) return String.format("%.1f", percent);
        return String.format("%.0f", percent);
    }
    
    // 检查所有卡牌的Top状态是否一致
    public static boolean areAllCardsTopEnabled(TrackBoardModel boardModel) {
        if (boardModel == null) return false;
        
        boolean hasAnyEnabled = false;
        boolean hasAnyDisabled = false;
        
        for (TrackModel track : boardModel.getTracks()) {
            for (PlotItem item : track.getItems()) {
                if (item.isTopEnabled()) {
                    hasAnyEnabled = true;
                } else {
                    hasAnyDisabled = true;
                }
            }
        }
        
        // 如果只有启用的，返回true；如果只有禁用的，返回false；如果混合，返回false
        return hasAnyEnabled && !hasAnyDisabled;
    }
    
    // 获取所有卡牌的平均Top百分比
    public static double getAverageTopPercent(TrackBoardModel boardModel) {
        if (boardModel == null) return 0.01;
        
        double sum = 0.0;
        int count = 0;
        
        for (TrackModel track : boardModel.getTracks()) {
            for (PlotItem item : track.getItems()) {
                sum += item.getTopPercent();
                count++;
            }
        }
        
        return count > 0 ? sum / count : 0.01;
    }
}
