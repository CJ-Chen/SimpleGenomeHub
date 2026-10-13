package simplegenomehub.population.fst.view;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import simplegenomehub.population.fst.chart.FSTWindow;

/**
 * Logistic文件读取器，读取窗口化后的GWAS结果文件
 * 格式：CHROM\tBIN_START\tBIN_END\tN_VARIANTS\tMIN_P\tNEG_LOG10_MIN_P
 * 读取CHROM、BIN_START、BIN_END和NEG_LOG10_MIN_P列
 */
public class LogisticReader {
    /**
     * 读取窗口化logistic格式的GWAS结果文件
     * @param filename 文件名（窗口化后的文件）
     * @return Logistic窗口列表（使用FSTWindow结构存储，avgFst字段存储NEG_LOG10_MIN_P值）
     * @throws IOException 文件读取错误
     */
    public static List<FSTWindow> readAndCalculateWindows(String filename) throws IOException {
        // 判断文件格式：检查文件扩展名是否为.logistic
        if (filename == null) {
            throw new IOException("File name must not be empty.");
        }
        String lowerFileName = filename.toLowerCase();
        if (!lowerFileName.endsWith(".logistic") && !lowerFileName.endsWith(".logistic.gz")) {
            throw new IOException("Unsupported file: LogisticReader reads .logistic or .logistic.gz files. Got: " + filename);
        }
        
        List<FSTWindow> windows = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            // 读取表头
            String header = reader.readLine();
            if (header == null) {
                return windows;
            }
            
            // 解析表头，确定列索引
            String[] headerParts = header.trim().split("\t");
            int chromIndex = -1;
            int binStartIndex = -1;
            int binEndIndex = -1;
            int negLog10PIndex = -1;
            
            for (int i = 0; i < headerParts.length; i++) {
                String col = headerParts[i].trim().toUpperCase();
                if (col.equals("CHROM") || col.equals("CHR")) {
                    chromIndex = i;
                } else if (col.equals("BIN_START")) {
                    binStartIndex = i;
                } else if (col.equals("BIN_END")) {
                    binEndIndex = i;
                } else if (col.equals("NEG_LOG10_MIN_P")) {
                    negLog10PIndex = i;
                }
            }
            
            // 验证必需的列是否存在
            if (chromIndex < 0 || binStartIndex < 0 || binEndIndex < 0 || negLog10PIndex < 0) {
                throw new IOException("Invalid header format. Expected columns: CHROM, BIN_START, BIN_END, NEG_LOG10_MIN_P");
            }
            
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                
                String[] parts = line.split("\t");
                if (parts.length <= Math.max(Math.max(chromIndex, binStartIndex), Math.max(binEndIndex, negLog10PIndex))) {
                    continue;
                }
                
                String chrom = parts[chromIndex].trim();
                int startPos;
                int endPos;
                double negLog10P;
                
                try {
                    startPos = Integer.parseInt(parts[binStartIndex].trim());
                    endPos = Integer.parseInt(parts[binEndIndex].trim());
                    
                    String negLog10PStr = parts[negLog10PIndex].trim();
                    // 处理-nan和无效值
                    if (negLog10PStr.equalsIgnoreCase("-nan") || negLog10PStr.equalsIgnoreCase("nan") || 
                        negLog10PStr.equalsIgnoreCase("na") || negLog10PStr.isEmpty()) {
                        continue;
                    }
                    
                    negLog10P = Double.parseDouble(negLog10PStr);
                    
                    // 跳过无效的值
                    if (Double.isNaN(negLog10P) || Double.isInfinite(negLog10P)) {
                        continue;
                    }
                    
                    // 使用FSTWindow结构存储窗口数据
                    // avgFst字段存储NEG_LOG10_MIN_P值
                    windows.add(new FSTWindow(chrom, startPos, endPos, negLog10P));
                } catch (NumberFormatException e) {
                    // 跳过无效行
                    continue;
                }
            }
        }
        
        return windows;
    }
}
