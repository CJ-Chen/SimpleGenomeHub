package simplegenomehub.population.fst;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * FST计算工具类
 * 整合所有通用工具方法
 */
public class FSTGongJu {

    public static final int IO_BUFFER = 1 << 20;  // 1MB
    public static final String POISON_PILL = "__POISON_PILL__";  // 队列模式的毒丸标记
    public static final int QUEUE_CAPACITY = 10000;  // 每条染色体队列长度

    /**
     * 读取样本文件，返回样本集合
     */
    public static LinkedHashSet<String> readSampleSet(String path) throws IOException {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8),
                1 << 16)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                set.add(line);
            }
        }
        return set;
    }

    /**
     * 打开可能为gzip压缩的文件
     */
    public static InputStream openPossiblyGzip(String path) throws IOException {
        InputStream in = new FileInputStream(path);
        return path.endsWith(".gz") ? new GZIPInputStream(in, IO_BUFFER) : in;
    }

    /**
     * 解析样本索引：从VCF样本列表中找出群体样本的列索引
     */
    public static int[] resolveSampleIndices(List<String> vcfSamples, Set<String> popSet, String popName) {
        ArrayList<Integer> idx = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (int i = 0; i < vcfSamples.size(); i++) {
            String s = vcfSamples.get(i);
            if (popSet.contains(s)) {
                idx.add(9 + i); // fields数组中的列索引（VCF格式：前9列是固定列，第10列开始是样本）
                seen.add(s);
            }
        }
        int missing = 0;
        for (String s : popSet) if (!seen.contains(s)) missing++;
        if (missing > 0) {
            System.err.println("[WARN] " + popName + " has " + missing + " sample(s) not found in the VCF header (ignored)");
        }

        int[] arr = new int[idx.size()];
        for (int i = 0; i < idx.size(); i++) arr[i] = idx.get(i);
        return arr;
    }

    /**
     * 将字符串转换为安全的文件名（Windows/Unix兼容）
     */
    public static String sanitizeForFileName(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }

    /**
     * 为指定染色体创建BufferedReader（支持RandomAccessFile优化）
     */
    public static BufferedReader createChromosomeReader(String vcfPath, String chrom,
                                                       VCFIndex.ChromosomeIndex chromIndex,
                                                       String[] headerLines) throws IOException {
        boolean isGzip = vcfPath.endsWith(".gz");
        BufferedReader br = null;
        RandomAccessFile raf = null;

        // 对于未压缩文件，尝试使用RandomAccessFile直接定位
        if (!isGzip && chromIndex != null) {
            try {
                raf = new RandomAccessFile(vcfPath, "r");
                raf.seek(chromIndex.startBytePos);
                long remainingBytes = chromIndex.endBytePos - chromIndex.startBytePos;

                final RandomAccessFile finalRaf = raf;
                br = new BufferedReader(
                        new InputStreamReader(
                                new InputStream() {
                                    private long remaining = remainingBytes;
                                    @Override
                                    public int read() throws IOException {
                                        if (remaining <= 0) return -1;
                                        int b = finalRaf.read();
                                        if (b >= 0) remaining--;
                                        return b;
                                    }
                                    @Override
                                    public int read(byte[] b, int off, int len) throws IOException {
                                        if (remaining <= 0) return -1;
                                        int toRead = (int) Math.min(len, remaining);
                                        int n = finalRaf.read(b, off, toRead);
                                        if (n > 0) remaining -= n;
                                        return n;
                                    }
                                    @Override
                                    public void close() throws IOException {
                                        finalRaf.close();
                                    }
                                },
                                StandardCharsets.UTF_8),
                        IO_BUFFER);
            } catch (Exception e) {
                System.err.println("[WARN] Cannot use random access for " + chrom + "; falling back to sequential read: " + e.getMessage());
                if (raf != null) {
                    try { raf.close(); } catch (IOException ignored) {}
                    raf = null;
                }
            }
        }

        // 如果是压缩文件或随机访问失败，使用顺序读取
        if (br == null) {
            InputStream inputStream = new FileInputStream(vcfPath);
            if (isGzip) {
                inputStream = new GZIPInputStream(inputStream, IO_BUFFER);
            }
            br = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8),
                    IO_BUFFER);

            // 跳过表头
            for (String headerLine : headerLines) {
                br.readLine();
            }

            // 跳过到目标染色体
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                int tab = line.indexOf('\t');
                if (tab > 0) {
                    String lineChrom = line.substring(0, tab);
                    if (lineChrom.equals(chrom)) {
                        // 找到目标染色体，使用PushbackReader回退这一行
                        java.io.PushbackReader pbr = new java.io.PushbackReader(br, line.length() + 1);
                        pbr.unread(line.toCharArray());
                        pbr.unread('\n');
                        return new BufferedReader(pbr, IO_BUFFER);
                    }
                }
            }
            // 如果没找到目标染色体，返回null
            br.close();
            return null;
        }

        return br;
    }

    /**
     * 读取并处理指定染色体的所有VCF行
     */
    public static void processChromosomeLines(BufferedReader br, String chrom,
                                             LineProcessor lineProcessor) throws IOException {
        if (br == null) return;

        try {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;

                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                String lineChrom = line.substring(0, tab);

                // 如果遇到下一个染色体，停止
                if (!lineChrom.equals(chrom)) {
                    break;
                }

                // 处理当前行
                lineProcessor.process(line);
            }
        } finally {
            br.close();
        }
    }

    /**
     * VCF行处理器接口
     */
    @FunctionalInterface
    public interface LineProcessor {
        void process(String line) throws IOException;
    }

    /**
     * VCF表头信息
     */
    public static class VCFHeaderInfo {
        public final List<String> headerLines;
        public final List<String> sampleNames;
        public final int[] pop1Idx;
        public final int[] pop2Idx;
        public final String[] headerLinesArray;

        public VCFHeaderInfo(List<String> headerLines, List<String> sampleNames, 
                            int[] pop1Idx, int[] pop2Idx) {
            this.headerLines = headerLines;
            this.sampleNames = sampleNames;
            this.pop1Idx = pop1Idx;
            this.pop2Idx = pop2Idx;
            this.headerLinesArray = headerLines.toArray(new String[0]);
        }
    }

    /**
     * 读取VCF表头并解析样本索引
     */
    public static VCFHeaderInfo readVCFHeader(String vcfPath, Set<String> pop1, Set<String> pop2) throws IOException {
        List<String> headerLinesList = new ArrayList<>();
        BufferedReader br = new BufferedReader(
                new InputStreamReader(openPossiblyGzip(vcfPath), StandardCharsets.UTF_8),
                IO_BUFFER);

        List<String> sampleNames = null;
        int[] pop1Idx = null;
        int[] pop2Idx = null;

        String line;
        while ((line = br.readLine()) != null) {
            headerLinesList.add(line);
            if (line.isEmpty()) continue;
            if (line.startsWith("##")) continue;

            if (line.startsWith("#CHROM")) {
                String[] header = splitTab(line);
                if (header.length < 10) {
                    br.close();
                    throw new IllegalArgumentException("The VCF header must contain sample columns.");
                }

                sampleNames = new ArrayList<>(header.length - 9);
                for (int i = 9; i < header.length; i++) sampleNames.add(header[i]);

                pop1Idx = resolveSampleIndices(sampleNames, pop1, "Group 1");
                pop2Idx = resolveSampleIndices(sampleNames, pop2, "Group 2");

                if (pop1Idx.length == 0 || pop2Idx.length == 0) {
                    br.close();
                    throw new IllegalArgumentException("Some groups matched 0 samples in the VCF header. Check the sample names.");
                }
                System.out.println("Group 1 matched in the VCF: " + pop1Idx.length + " sample(s)");
                System.out.println("Group 2 matched in the VCF: " + pop2Idx.length + " sample(s)");
                break;
            }
        }
        br.close();
        if (sampleNames == null) {
            throw new IllegalArgumentException("No #CHROM header line found.");
        }

        return new VCFHeaderInfo(headerLinesList, sampleNames, pop1Idx, pop2Idx);
    }

    /**
     * 非正则tab切分（高效实现）
     */
    public static String[] splitTab(String line) {
        int tabs = 0;
        for (int i = 0; i < line.length(); i++) if (line.charAt(i) == '\t') tabs++;
        String[] out = new String[tabs + 1];

        int idx = 0;
        int start = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '\t') {
                out[idx++] = line.substring(start, i);
                start = i + 1;
            }
        }
        out[idx] = line.substring(start);
        return out;
    }
}
