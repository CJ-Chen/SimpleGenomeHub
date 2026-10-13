package simplegenomehub.population.fst;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * VCF文件索引类
 * 用于快速定位VCF文件中每个染色体的位置，加速后续读取
 */
public class VCFIndex {
    
    /**
     * 染色体索引信息
     */
    public static class ChromosomeIndex {
        public final String chromosome;
        public final long startBytePos;  // 起始字节位置
        public final long endBytePos;    // 结束字节位置（下一个染色体的起始位置）
        public final long lineCount;     // 行数
        
        public ChromosomeIndex(String chromosome, long startBytePos, long endBytePos, long lineCount) {
            this.chromosome = chromosome;
            this.startBytePos = startBytePos;
            this.endBytePos = endBytePos;
            this.lineCount = lineCount;
        }
    }
    
    private final Map<String, ChromosomeIndex> chromosomeMap = new LinkedHashMap<>();
    private final List<ChromosomeIndex> chromosomeList = new ArrayList<>();
    private long headerEndPos = 0;  // 表头结束位置
    private long fileSize = 0;
    private long fileLastModified = 0;
    
    /**
     * 构建VCF文件索引
     * @param vcfPath VCF文件路径
     * @return 索引对象
     */
    public static VCFIndex buildIndex(String vcfPath) throws IOException {
        VCFIndex index = new VCFIndex();
        
        File vcfFile = new File(vcfPath);
        index.fileSize = vcfFile.length();
        index.fileLastModified = vcfFile.lastModified();
        
        InputStream inputStream = new FileInputStream(vcfFile);
        if (vcfPath.endsWith(".gz")) {
            inputStream = new java.util.zip.GZIPInputStream(inputStream, 1 << 20);
        }
        
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8),
                1 << 20)) {
            
            String line;
            String currentChrom = null;
            long currentStartPos = 0;
            long currentLineCount = 0;
            long currentBytePos = 0;
            boolean headerEnded = false;
            
            // 读取表头
            while ((line = br.readLine()) != null) {
                currentBytePos += line.getBytes(StandardCharsets.UTF_8).length + 1; // +1 for newline
                
                if (line.isEmpty()) continue;
                if (line.startsWith("##")) continue;
                
                if (line.startsWith("#CHROM")) {
                    headerEnded = true;
                    index.headerEndPos = currentBytePos;
                    // 继续读取，找到第一个数据行
                    continue;
                }
                
                if (headerEnded && !line.startsWith("#")) {
                    // 第一个数据行
                    int tab = line.indexOf('\t');
                    if (tab > 0) {
                        currentChrom = line.substring(0, tab);
                        currentStartPos = currentBytePos - line.getBytes(StandardCharsets.UTF_8).length - 1;
                        currentLineCount = 1;
                    }
                    break;
                }
            }
            
            // 读取数据行
            while ((line = br.readLine()) != null) {
                currentBytePos += line.getBytes(StandardCharsets.UTF_8).length + 1;
                
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                
                String chrom = line.substring(0, tab);
                
                if (!chrom.equals(currentChrom)) {
                    // 染色体变化，保存上一个染色体的索引
                    if (currentChrom != null) {
                        ChromosomeIndex chromIndex = new ChromosomeIndex(
                            currentChrom, 
                            currentStartPos, 
                            currentBytePos - line.getBytes(StandardCharsets.UTF_8).length - 1,
                            currentLineCount
                        );
                        index.chromosomeMap.put(currentChrom, chromIndex);
                        index.chromosomeList.add(chromIndex);
                    }
                    
                    // 开始新染色体
                    currentChrom = chrom;
                    currentStartPos = currentBytePos - line.getBytes(StandardCharsets.UTF_8).length - 1;
                    currentLineCount = 1;
                } else {
                    currentLineCount++;
                }
            }
            
            // 保存最后一个染色体
            if (currentChrom != null) {
                ChromosomeIndex chromIndex = new ChromosomeIndex(
                    currentChrom,
                    currentStartPos,
                    currentBytePos,
                    currentLineCount
                );
                index.chromosomeMap.put(currentChrom, chromIndex);
                index.chromosomeList.add(chromIndex);
            }
        }
        
        return index;
    }
    
    /**
     * 保存索引到文件
     * @param indexPath 索引文件路径
     */
    public void save(String indexPath) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(indexPath), StandardCharsets.UTF_8))) {
            
            // 写入元数据
            bw.write("#VCF_INDEX_VERSION:1.0\n");
            bw.write("#FILE_SIZE:" + fileSize + "\n");
            bw.write("#FILE_LAST_MODIFIED:" + fileLastModified + "\n");
            bw.write("#HEADER_END_POS:" + headerEndPos + "\n");
            bw.write("#CHROMOSOME_COUNT:" + chromosomeList.size() + "\n");
            bw.write("#FORMAT:CHROM\tSTART_BYTE\tEND_BYTE\tLINE_COUNT\n");
            
            // 写入每个染色体的索引
            for (ChromosomeIndex chromIndex : chromosomeList) {
                bw.write(chromIndex.chromosome + "\t" +
                        chromIndex.startBytePos + "\t" +
                        chromIndex.endBytePos + "\t" +
                        chromIndex.lineCount + "\n");
            }
        }
    }
    
    /**
     * 从文件加载索引
     * @param indexPath 索引文件路径
     * @return 索引对象
     */
    public static VCFIndex load(String indexPath) throws IOException {
        VCFIndex index = new VCFIndex();
        
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(indexPath), StandardCharsets.UTF_8))) {
            
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    // 解析元数据
                    if (line.startsWith("#FILE_SIZE:")) {
                        index.fileSize = Long.parseLong(line.substring("#FILE_SIZE:".length()));
                    } else if (line.startsWith("#FILE_LAST_MODIFIED:")) {
                        index.fileLastModified = Long.parseLong(line.substring("#FILE_LAST_MODIFIED:".length()));
                    } else if (line.startsWith("#HEADER_END_POS:")) {
                        index.headerEndPos = Long.parseLong(line.substring("#HEADER_END_POS:".length()));
                    }
                    continue;
                }
                
                // 解析染色体索引行
                String[] parts = line.split("\t");
                if (parts.length >= 4) {
                    String chrom = parts[0];
                    long startByte = Long.parseLong(parts[1]);
                    long endByte = Long.parseLong(parts[2]);
                    long lineCount = Long.parseLong(parts[3]);
                    
                    ChromosomeIndex chromIndex = new ChromosomeIndex(chrom, startByte, endByte, lineCount);
                    index.chromosomeMap.put(chrom, chromIndex);
                    index.chromosomeList.add(chromIndex);
                }
            }
        }
        
        return index;
    }
    
    /**
     * 检查索引是否有效（文件未被修改）
     * @param vcfPath VCF文件路径
     * @return 是否有效
     */
    public boolean isValid(String vcfPath) {
        File vcfFile = new File(vcfPath);
        return vcfFile.exists() && 
               vcfFile.length() == fileSize && 
               vcfFile.lastModified() == fileLastModified;
    }
    
    /**
     * 获取索引文件路径
     * @param vcfPath VCF文件路径
     * @return 索引文件路径
     */
    public static String getIndexPath(String vcfPath) {
        return vcfPath + ".idx";
    }
    
    /**
     * 加载或构建索引
     * @param vcfPath VCF文件路径
     * @return 索引对象
     */
    public static VCFIndex loadOrBuild(String vcfPath) throws IOException {
        String indexPath = getIndexPath(vcfPath);
        File indexFile = new File(indexPath);
        
        // 尝试加载现有索引
        if (indexFile.exists()) {
            try {
                VCFIndex index = load(indexPath);
                if (index.isValid(vcfPath)) {
                    System.out.println("Loading VCF index: " + indexPath);
                    return index;
                } else {
                    System.out.println("VCF index is stale; rebuilding...");
                }
            } catch (Exception e) {
                System.err.println("Failed to load the VCF index; rebuilding: " + e.getMessage());
            }
        }
        
        // 构建新索引
        System.out.println("Building VCF index...");
        long startTime = System.currentTimeMillis();
        VCFIndex index = buildIndex(vcfPath);
        long buildTime = System.currentTimeMillis() - startTime;
        System.out.println("VCF index built in " + buildTime + " ms");
        
        // 保存索引
        try {
            index.save(indexPath);
            System.out.println("VCF index saved: " + indexPath);
        } catch (Exception e) {
            System.err.println("Failed to save the VCF index: " + e.getMessage());
        }
        
        return index;
    }
    
    /**
     * 获取所有染色体列表
     */
    public List<String> getChromosomes() {
        List<String> result = new ArrayList<>();
        for (ChromosomeIndex idx : chromosomeList) {
            result.add(idx.chromosome);
        }
        return result;
    }
    
    /**
     * 获取染色体索引信息
     */
    public ChromosomeIndex getChromosomeIndex(String chromosome) {
        return chromosomeMap.get(chromosome);
    }
    
    /**
     * 获取表头结束位置
     */
    public long getHeaderEndPos() {
        return headerEndPos;
    }
}
