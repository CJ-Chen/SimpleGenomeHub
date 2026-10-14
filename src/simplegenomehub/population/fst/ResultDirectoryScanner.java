package simplegenomehub.population.fst;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 结果目录扫描器
 * 自动扫描工作目录，识别FST、PI等结果文件
 */
public class ResultDirectoryScanner {
    
    /**
     * 扫描结果目录，返回找到的所有结果文件
     * 
     * @param workDirectory 工作目录路径
     * @return 结果文件信息
     */
    public static ResultFiles scanDirectory(String workDirectory) {
        File dir = new File(workDirectory);
        if (!dir.exists() || !dir.isDirectory()) {
            return new ResultFiles();
        }
        
        ResultFiles result = new ResultFiles();
        File[] files = dir.listFiles();
        
        if (files == null) {
            return result;
        }
        
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            
            String fileName = file.getName().toLowerCase();
            String absolutePath = file.getAbsolutePath();
            
            // 识别FST文件
            if (fileName.endsWith(".windowed.weir.fst") || 
                (fileName.endsWith(".fst") && !fileName.contains("sites"))) {
                result.fstFiles.add(absolutePath);
            }
            // 识别PI文件
            else if (fileName.contains("windowed") && fileName.contains("pi") && 
                     fileName.endsWith(".pi")) {
                result.piFiles.add(absolutePath);
            }
            // 识别TajimaD文件（支持.tajimad、.tajima_d或.tajima.d格式）
            else if (fileName.contains("tajimad") || fileName.contains("tajima_d") || 
                     fileName.contains("tajima.d")) {
                result.tajimaDFiles.add(absolutePath);
            }
            // 识别Logistic/GWAS窗口化文件
            else if (fileName.endsWith(".logistic") || fileName.endsWith(".logistic.gz")) {
                result.logisticFiles.add(absolutePath);
            }
        }
        
        return result;
    }
    
    /**
     * 结果文件信息类
     */
    public static class ResultFiles {
        public List<String> fstFiles = new ArrayList<>();
        public List<String> piFiles = new ArrayList<>();
        public List<String> tajimaDFiles = new ArrayList<>();
        public List<String> logisticFiles = new ArrayList<>();
        
        public boolean hasFSTFiles() {
            return !fstFiles.isEmpty();
        }
        
        public boolean hasPIFiles() {
            return !piFiles.isEmpty();
        }
        
        public boolean hasTajimaDFiles() {
            return !tajimaDFiles.isEmpty();
        }
        
        public boolean hasLogisticFiles() {
            return !logisticFiles.isEmpty();
        }
        
        public boolean isEmpty() {
            return fstFiles.isEmpty() && piFiles.isEmpty() && tajimaDFiles.isEmpty()
                    && logisticFiles.isEmpty();
        }
        
        /**
         * 获取单个FST文件路径（如果有且只有一个）
         */
        public String getSingleFSTFile() {
            return fstFiles.size() == 1 ? fstFiles.get(0) : null;
        }
        
        /**
         * 获取多个FST文件路径列表（如果有多个）
         */
        public List<String> getMultipleFSTFiles() {
            return fstFiles.size() > 1 ? fstFiles : null;
        }
        
        /**
         * 获取单个PI文件路径（如果有且只有一个）
         */
        public String getSinglePIFile() {
            return piFiles.size() == 1 ? piFiles.get(0) : null;
        }
        
        /**
         * 获取多个PI文件路径列表（如果有多个）
         */
        public List<String> getMultiplePIFiles() {
            return piFiles.size() > 1 ? piFiles : null;
        }
        
        /**
         * 获取单个TajimaD文件路径（如果有且只有一个）
         */
        public String getSingleTajimaDFile() {
            return tajimaDFiles.size() == 1 ? tajimaDFiles.get(0) : null;
        }
        
        /**
         * 获取多个TajimaD文件路径列表（如果有多个）
         */
        public List<String> getMultipleTajimaDFiles() {
            return tajimaDFiles.size() > 1 ? tajimaDFiles : null;
        }
        
        /**
         * 获取单个Logistic文件路径（如果有且只有一个）
         */
        public String getSingleLogisticFile() {
            return logisticFiles.size() == 1 ? logisticFiles.get(0) : null;
        }
        
        /**
         * 获取多个Logistic文件路径列表（如果有多个）
         */
        public List<String> getMultipleLogisticFiles() {
            return logisticFiles.size() > 1 ? logisticFiles : null;
        }
    }
}
