package simplegenomehub.population.fst.view;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import simplegenomehub.population.fst.chart.FSTWindow;

/**
 * FST文件读取器，读取vcftools windowed.weir.fst格式文件
 * 格式：CHROM	BIN_START	BIN_END	N_VARIANTS	WEIGHTED_FST	MEAN_FST
 */
public class FSTReader {
    /**
     * 读取windowed.weir.fst格式的FST文件
     * @param filename 文件名
     * @return FST窗口列表
     * @throws IOException 文件读取错误
     */
    public static List<FSTWindow> readAndCalculateWindows(String filename) throws IOException {
        // 判断文件格式：检查文件扩展名是否为.fst
        if (filename == null) {
            throw new IOException("File name must not be empty.");
        }
        String lowerFileName = filename.toLowerCase();
        if (!lowerFileName.endsWith(".fst") && !lowerFileName.endsWith(".fst.gz")) {
            throw new IOException("Unsupported file: FSTReader reads .fst or .fst.gz files. Got: " + filename);
        }
        
        List<FSTWindow> windows = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            // 读取表头
            String header = reader.readLine();
            if (header == null) {
                return windows;
            }
            
            // 解析表头，确定列索引
            String[] headerParts = header.split("\t");
            int chromIndex = -1;
            int startIndex = -1;
            int endIndex = -1;
            int weightedFstIndex = -1;
            
            for (int i = 0; i < headerParts.length; i++) {
                String col = headerParts[i].trim().toUpperCase();
                if (col.equals("CHROM")) {
                    chromIndex = i;
                } else if (col.equals("BIN_START")) {
                    startIndex = i;
                } else if (col.equals("BIN_END")) {
                    endIndex = i;
                } else if (col.equals("WEIGHTED_FST")) {
                    weightedFstIndex = i;
                }
            }
            
            // 验证必需的列是否存在
            if (chromIndex < 0 || startIndex < 0 || endIndex < 0 || weightedFstIndex < 0) {
                throw new IOException("Invalid header format. Expected columns: CHROM, BIN_START, BIN_END, WEIGHTED_FST");
            }
            
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                
                String[] parts = line.split("\t");
                if (parts.length <= Math.max(Math.max(chromIndex, startIndex), Math.max(endIndex, weightedFstIndex))) {
                    continue;
                }
                
                String chrom = parts[chromIndex].trim();
                int startPos;
                int endPos;
                double weightedFst;
                
                try {
                    startPos = Integer.parseInt(parts[startIndex].trim());
                    endPos = Integer.parseInt(parts[endIndex].trim());
                    
                    String fstStr = parts[weightedFstIndex].trim();
                    // 处理-nan和无效值
                    if (fstStr.equalsIgnoreCase("-nan") || fstStr.equalsIgnoreCase("nan") || 
                        fstStr.equalsIgnoreCase("na") || fstStr.isEmpty()) {
                        continue;
                    }
                    
                    weightedFst = Double.parseDouble(fstStr);
                    
                    // 跳过无效的FST值
                    if (Double.isNaN(weightedFst) || Double.isInfinite(weightedFst)) {
                        continue;
                    }
                    
                    windows.add(new FSTWindow(chrom, startPos, endPos, weightedFst));
                } catch (NumberFormatException e) {
                    // 跳过无效行
                    continue;
                }
            }
        }
        
        return windows;
    }
    
    /**
     * 计算指定百分比的FST阈值
     * @param windows 窗口列表
     * @param percent 百分比（0-1之间，例如0.01表示1%）
     */
    public static double calculateTopPercentThreshold(List<FSTWindow> windows, double percent) {
        if (windows.isEmpty()) {
            return 0.0;
        }
        
        // 提取所有FST值并排序
        List<Double> fstValues = new ArrayList<>();
        for (FSTWindow window : windows) {
            double fst = window.getAvgFst();
            if (!Double.isNaN(fst) && !Double.isInfinite(fst)) {
                fstValues.add(fst);
            }
        }
        
        if (fstValues.isEmpty()) {
            return 0.0;
        }
        
        fstValues.sort((a, b) -> Double.compare(b, a)); // 降序排序
        
        // 计算指定百分比的索引
        int topPercentIndex = (int) Math.ceil(fstValues.size() * percent);
        if (topPercentIndex >= fstValues.size()) {
            topPercentIndex = fstValues.size() - 1;
        }
        if (topPercentIndex < 0) {
            topPercentIndex = 0;
        }
        
        return fstValues.get(topPercentIndex);
    }

    //获取top位点的窗口列表（未排序，保持原始顺序）
    public static List<FSTWindow> getTop1PercentWindowsUnsorted(List<FSTWindow> windows, double threshold) {
        List<FSTWindow> topWindows = new ArrayList<>();
        for (FSTWindow window : windows) {
            double fst = window.getAvgFst();
            if (!Double.isNaN(fst) && !Double.isInfinite(fst) && fst >= threshold) {
                topWindows.add(window);
            }
        }
        return topWindows;
    }

    //获取前top位点的窗口列表（按FST值降序排序）
    public static List<FSTWindow> getTop1PercentWindows(List<FSTWindow> windows, double threshold) {
        List<FSTWindow> topWindows = getTop1PercentWindowsUnsorted(windows, threshold);
        // 按FST值降序排序
        topWindows.sort((a, b) -> Double.compare(b.getAvgFst(), a.getAvgFst()));
        return topWindows;
    }
}
