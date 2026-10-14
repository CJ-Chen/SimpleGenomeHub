package simplegenomehub.population.fst.view;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import simplegenomehub.population.fst.chart.FSTWindow;

/**
 * TajimaD文件读取器，读取windowed类型的TajimaD文件
 * 格式：CHROM	BIN_START	N_SNPS	TajimaD（注意：没有BIN_END列）
 */
public class TajimaDReader {
    /**
     * 读取windowed类型的TajimaD文件
     * @param filename 文件名
     * @return TajimaD窗口列表（使用FSTWindow结构存储，avgFst字段存储TajimaD值）
     * @throws IOException 文件读取错误
     */
    public static List<FSTWindow> readAndCalculateWindows(String filename) throws IOException {
        // 判断文件格式：检查文件名是否包含tajimad相关关键词
        if (filename == null) {
            throw new IOException("File name must not be empty.");
        }
        String lowerFileName = filename.toLowerCase();
        if (!lowerFileName.contains("tajimad") && !lowerFileName.contains("tajima_d") && 
            !lowerFileName.contains("tajima.d")) {
            throw new IOException("Unsupported file: TajimaDReader needs a name containing tajimad, tajima_d or tajima.d. Got: " + filename);
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
            int endIndex = -1;  // 可选，可能不存在
            int tajimaDIndex = -1;
            
            for (int i = 0; i < headerParts.length; i++) {
                String col = headerParts[i].trim().toUpperCase();
                if (col.equals("CHROM")) {
                    chromIndex = i;
                } else if (col.equals("BIN_START")) {
                    startIndex = i;
                } else if (col.equals("BIN_END")) {
                    endIndex = i;
                } else if (col.equals("TAJIMAD") || col.equals("TAJIMA_D") || col.equals("TAJIMA D")) {
                    tajimaDIndex = i;
                }
            }
            
            // 验证必需的列是否存在（BIN_END是可选的）
            if (chromIndex < 0 || startIndex < 0 || tajimaDIndex < 0) {
                throw new IOException("Invalid header format. Expected columns: CHROM, BIN_START, TajimaD (BIN_END is optional)");
            }
            
            // 读取所有行到列表，以便计算BIN_END
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                lines.add(line);
            }
            
            // 处理每一行，计算BIN_END
            for (int i = 0; i < lines.size(); i++) {
                String currentLine = lines.get(i);
                String[] parts = currentLine.split("\t");
                if (parts.length <= Math.max(Math.max(chromIndex, startIndex), tajimaDIndex)) {
                    continue;
                }
                
                String chrom = parts[chromIndex].trim();
                int startPos;
                int endPos;
                double tajimaDValue;
                
                try {
                    startPos = Integer.parseInt(parts[startIndex].trim());
                    
                    // 如果文件有BIN_END列，使用它；否则根据下一个窗口的BIN_START计算
                    if (endIndex >= 0 && endIndex < parts.length) {
                        endPos = Integer.parseInt(parts[endIndex].trim());
                    } else {
                        // 没有BIN_END列，需要计算
                        // 查找下一个窗口的BIN_START作为当前窗口的BIN_END
                        endPos = startPos;  // 默认值
                        if (i + 1 < lines.size()) {
                            String nextLine = lines.get(i + 1);
                            String[] nextParts = nextLine.split("\t");
                            if (nextParts.length > startIndex) {
                                try {
                                    String nextChrom = nextParts[chromIndex].trim();
                                    // 如果是同一个染色体，使用下一个窗口的BIN_START作为当前窗口的BIN_END
                                    if (nextChrom.equals(chrom)) {
                                        int nextStart = Integer.parseInt(nextParts[startIndex].trim());
                                        endPos = nextStart - 1;  // 减1，使窗口不重叠
                                    } else {
                                        // 不同染色体，使用一个较大的默认值（可以根据实际情况调整）
                                        endPos = startPos + 1000000;  // 默认窗口大小1Mb
                                    }
                                } catch (NumberFormatException e) {
                                    // 如果无法解析下一个窗口，使用默认值
                                    endPos = startPos + 1000000;
                                }
                            }
                        } else {
                            // 最后一个窗口，使用默认窗口大小
                            endPos = startPos + 1000000;
                        }
                    }
                    
                    String tajimaDStr = parts[tajimaDIndex].trim();
                    // 处理-nan和无效值
                    if (tajimaDStr.equalsIgnoreCase("-nan") || tajimaDStr.equalsIgnoreCase("nan") || 
                        tajimaDStr.equalsIgnoreCase("na") || tajimaDStr.isEmpty()) {
                        continue;
                    }
                    
                    tajimaDValue = Double.parseDouble(tajimaDStr);
                    
                    // 跳过无效的TajimaD值
                    if (Double.isNaN(tajimaDValue) || Double.isInfinite(tajimaDValue)) {
                        continue;
                    }
                    
                    // 使用FSTWindow结构存储TajimaD值（avgFst字段存储TajimaD值）
                    windows.add(new FSTWindow(chrom, startPos, endPos, tajimaDValue));
                } catch (NumberFormatException e) {
                    // 跳过无效行
                    continue;
                }
            }
        }
        
        return windows;
    }
}
