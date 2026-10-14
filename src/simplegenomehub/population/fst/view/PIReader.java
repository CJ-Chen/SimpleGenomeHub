package simplegenomehub.population.fst.view;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import simplegenomehub.population.fst.chart.FSTWindow;

/**
 * PI文件读取器，读取windowed类型的PI文件
 * 格式：CHROM	BIN_START	BIN_END	N_VARIANTS	N_MONOMORPHIC	PI
 */
public class PIReader {
    /**
     * 读取windowed类型的PI文件
     * @param filename 文件名
     * @return PI窗口列表（使用FSTWindow结构存储，value字段存储PI值）
     * @throws IOException 文件读取错误
     */
    public static List<FSTWindow> readAndCalculateWindows(String filename) throws IOException {
        // 判断文件格式：检查文件扩展名是否为.pi
        if (filename == null) {
            throw new IOException("File name must not be empty.");
        }
        String lowerFileName = filename.toLowerCase();
        if (!lowerFileName.endsWith(".pi") && !lowerFileName.endsWith(".pi.gz")) {
            throw new IOException("Unsupported file: PIReader reads .pi or .pi.gz files. Got: " + filename);
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
            int piIndex = -1;
            
            for (int i = 0; i < headerParts.length; i++) {
                String col = headerParts[i].trim().toUpperCase();
                if (col.equals("CHROM")) {
                    chromIndex = i;
                } else if (col.equals("BIN_START")) {
                    startIndex = i;
                } else if (col.equals("BIN_END")) {
                    endIndex = i;
                } else if (col.equals("PI")) {
                    piIndex = i;
                }
            }
            
            // 验证必需的列是否存在
            if (chromIndex < 0 || startIndex < 0 || endIndex < 0 || piIndex < 0) {
                throw new IOException("Invalid header format. Expected columns: CHROM, BIN_START, BIN_END, PI");
            }
            
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                
                String[] parts = line.split("\t");
                if (parts.length <= Math.max(Math.max(chromIndex, startIndex), Math.max(endIndex, piIndex))) {
                    continue;
                }
                
                String chrom = parts[chromIndex].trim();
                int startPos;
                int endPos;
                double piValue;
                
                try {
                    startPos = Integer.parseInt(parts[startIndex].trim());
                    endPos = Integer.parseInt(parts[endIndex].trim());
                    
                    String piStr = parts[piIndex].trim();
                    // 处理-nan和无效值
                    if (piStr.equalsIgnoreCase("-nan") || piStr.equalsIgnoreCase("nan") || 
                        piStr.equalsIgnoreCase("na") || piStr.isEmpty()) {
                        continue;
                    }
                    
                    piValue = Double.parseDouble(piStr);
                    
                    // 跳过无效的PI值
                    if (Double.isNaN(piValue) || Double.isInfinite(piValue)) {
                        continue;
                    }
                    
                    // 使用FSTWindow结构存储PI值（avgFst字段存储PI值）
                    windows.add(new FSTWindow(chrom, startPos, endPos, piValue));
                } catch (NumberFormatException e) {
                    // 跳过无效行
                    continue;
                }
            }
        }
        
        return windows;
    }
}
