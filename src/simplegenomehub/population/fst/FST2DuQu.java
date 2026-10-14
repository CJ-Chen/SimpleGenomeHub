package simplegenomehub.population.fst;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * FST2DuQu - 数据读取类
 * 负责读取VCF文件和样本文件，解析VCF表头和样本索引
 */
public class FST2DuQu {
    
    /**
     * VCF数据读取结果
     */
    public static class VCFData {
        public final String vcfPath;
        public final boolean isGzip;
        public final VCFIndex vcfIndex;
        public final List<String> chromosomes;
        public final FSTGongJu.VCFHeaderInfo headerInfo;
        
        public VCFData(String vcfPath, boolean isGzip, VCFIndex vcfIndex, 
                     List<String> chromosomes, FSTGongJu.VCFHeaderInfo headerInfo) {
            this.vcfPath = vcfPath;
            this.isGzip = isGzip;
            this.vcfIndex = vcfIndex;
            this.chromosomes = chromosomes;
            this.headerInfo = headerInfo;
        }
    }
    
    /**
     * 样本数据
     */
    public static class SampleData {
        public final LinkedHashSet<String> pop1Samples;
        public final LinkedHashSet<String> pop2Samples;
        
        public SampleData(LinkedHashSet<String> pop1Samples, LinkedHashSet<String> pop2Samples) {
            this.pop1Samples = pop1Samples;
            this.pop2Samples = pop2Samples;
        }
    }
    
    /**
     * 读取VCF文件信息
     * 
     * @param vcfPath VCF文件路径
     * @param pop1Samples 群体1样本集合
     * @param pop2Samples 群体2样本集合
     * @return VCFData对象
     * @throws IOException 文件读取错误
     */
    public static VCFData readVCFData(String vcfPath, Set<String> pop1Samples, Set<String> pop2Samples) throws IOException {
        return readVCFData(vcfPath, pop1Samples, pop2Samples, new File(VCFIndex.getIndexPath(vcfPath)));
    }

    /** The caller owns the cache location, which may be outside the input directory. */
    public static VCFData readVCFData(String vcfPath, Set<String> pop1Samples,
            Set<String> pop2Samples, File indexFile) throws IOException {
        boolean isGzip = vcfPath.endsWith(".gz");
        VCFIndex vcfIndex = null;
        List<String> chromosomes = new java.util.ArrayList<>();
        
        // 对于未压缩文件，构建或加载索引
        if (!isGzip) {
            vcfIndex = VCFIndex.loadOrBuild(vcfPath, indexFile);
            chromosomes = vcfIndex.getChromosomes();
            if (!chromosomes.isEmpty()) {
                System.out.println("VCF index reports " + chromosomes.size() + " chromosomes: " +
                                 String.join(", ", chromosomes.subList(0, Math.min(10, chromosomes.size()))) +
                                 (chromosomes.size() > 10 ? "..." : ""));
            }
        } else {
            System.out.println("Compressed VCF (.gz) detected; skipping index build");
        }
        
        // 读取VCF表头并解析样本索引
        FSTGongJu.VCFHeaderInfo headerInfo = FSTGongJu.readVCFHeader(vcfPath, pop1Samples, pop2Samples);
        System.out.println("Group 1 samples: " + pop1Samples.size());
        System.out.println("Group 2 samples: " + pop2Samples.size());
        
        return new VCFData(vcfPath, isGzip, vcfIndex, chromosomes, headerInfo);
    }
    
    /**
     * 从样本文件读取样本列表
     * 
     * @param pop1Path 群体1样本文件路径
     * @param pop2Path 群体2样本文件路径
     * @return SampleData对象
     * @throws IOException 文件读取错误
     */
    public static SampleData readSampleData(String pop1Path, String pop2Path) throws IOException {
        LinkedHashSet<String> pop1Samples = FSTGongJu.readSampleSet(pop1Path);
        LinkedHashSet<String> pop2Samples = FSTGongJu.readSampleSet(pop2Path);
        
        if (pop1Samples.isEmpty() || pop2Samples.isEmpty()) {
            throw new IllegalArgumentException("Population file must not be empty.");
        }
        
        return new SampleData(pop1Samples, pop2Samples);
    }
    
    /**
     * 从样本列表创建临时样本文件
     * 
     * @param samples 样本列表
     * @param prefix 文件前缀
     * @return 临时文件
     * @throws IOException 文件创建错误
     */
    public static File createTempSampleFile(java.util.List<String> samples, String prefix) throws IOException {
        File tempFile = File.createTempFile(prefix + "_", ".txt");
        try (java.io.BufferedWriter bw = new java.io.BufferedWriter(
                new java.io.OutputStreamWriter(new java.io.FileOutputStream(tempFile), 
                java.nio.charset.StandardCharsets.UTF_8))) {
            for (String sample : samples) {
                bw.write(sample);
                bw.write('\n');
            }
        }
        return tempFile;
    }
}
