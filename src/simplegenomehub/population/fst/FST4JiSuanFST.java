package simplegenomehub.population.fst;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.zip.GZIPInputStream;

/**
 * FST4JiSuanFST - FST计算核心逻辑
 * 整合FSTSiteCalculator和FSTCalculatorParallel的功能
 * 负责并行计算per-site FST值
 */
public class FST4JiSuanFST {

    /**
     * 群体统计信息
     */
    public static final class PopStats {
        public int n;     // called diploid individuals
        public int xAlt;  // total ALT allele copies (0..2n)
        public int het;   // heterozygous individuals (0/1)

        public void reset() { n = 0; xAlt = 0; het = 0; }

        public double p() { return (n == 0) ? Double.NaN : (double) xAlt / (2.0 * n); }
    }

    /**
     * Worker计算结果
     */
    private static final class WorkerResult {
        long sitesUsed = 0;
        long countFst = 0;
        double sumFst = 0.0;
        double sumA = 0.0;
        double sumDen = 0.0;
    }

    /**
     * 处理单行VCF数据，计算FST
     */
    private static void processLine(String line, Map<String, Integer> formatGtCache,
                                   PopStats s1, PopStats s2,
                                   StringBuilder sb, DecimalFormat df6, DecimalFormat df10,
                                   BufferedWriter bw, WorkerResult result,
                                   int[] pop1Idx, int[] pop2Idx) throws IOException {
        String[] fields = FSTGongJu.splitTab(line);
        if (fields.length < 10) return;

        String ref = fields[3];
        String alt = fields[4];

        // 只保留简单二等位SNP（合并条件判断）
        if (ref.length() != 1 || alt.length() != 1 || alt.indexOf(',') >= 0 
            || alt.startsWith("<") || alt.contains("<")) return;

        String format = fields[8];
        Integer cached = formatGtCache.get(format);
        int gtIndex = (cached != null) ? cached : findFormatIndex(format, "GT");
        if (gtIndex < 0) return;
        if (cached == null) formatGtCache.put(format, gtIndex);

        s1.reset();
        s2.reset();

        computePopStatsFast(fields, pop1Idx, gtIndex, s1);
        computePopStatsFast(fields, pop2Idx, gtIndex, s2);

        if (s1.n == 0 || s2.n == 0) return;

        double[] aDen = wcNumeratorDenominator2(s1, s2);
        double a = aDen[0];
        double den = aDen[1];
        double fst = (den == 0.0 || Double.isNaN(den) || Double.isNaN(a)) ? Double.NaN : (a / den);

        // 输出行（简化字符串拼接）
        sb.setLength(0);
        sb.append(fields[0]).append('\t')
          .append(fields[1]).append('\t')
          .append(Double.isNaN(fst) || Double.isInfinite(fst) ? "nan" : df6.format(fst))
          .append('\t')
          .append(Double.isNaN(a) || Double.isInfinite(a) ? "nan" : df10.format(a))
          .append('\t')
          .append(Double.isNaN(den) || Double.isInfinite(den) || den == 0.0 ? "nan" : df10.format(den))
          .append('\n');

        bw.write(sb.toString());
        result.sitesUsed++;

        if (!Double.isNaN(fst) && !Double.isInfinite(fst)) {
            result.sumFst += fst;
            result.countFst++;
        }
        if (!Double.isNaN(den) && !Double.isInfinite(den) && den != 0.0
                && !Double.isNaN(a) && !Double.isInfinite(a)) {
            result.sumA += a;
            result.sumDen += den;
        }
    }

    /**
     * 快速计算群体统计信息
     */
    public static void computePopStatsFast(String[] fields, int[] sampleFieldIdx, int gtIndex, PopStats st) {
        if (gtIndex == 0) {
            for (int col : sampleFieldIdx) {
                if (col >= fields.length) continue;
                String sample = fields[col];
                if (sample == null || sample.isEmpty() || sample.equals(".")) continue;

                int altCount = parseAltCountFromSampleGT0(sample);
                if (altCount < 0) continue;
                st.n++;
                st.xAlt += altCount;
                if (altCount == 1) st.het++;
            }
        } else {
            for (int col : sampleFieldIdx) {
                if (col >= fields.length) continue;
                String sample = fields[col];
                if (sample == null || sample.isEmpty() || sample.equals(".")) continue;

                String gt = extractFieldByIndex(sample, gtIndex);
                if (gt == null) continue;

                int altCount = parseAltCountFromGTString(gt);
                if (altCount < 0) continue;
                st.n++;
                st.xAlt += altCount;
                if (altCount == 1) st.het++;
            }
        }
    }

    /**
     * Weir & Cockerham FST计算（两个群体）
     * 返回 {a, a+b+c}，用于 fst = a/(a+b+c)
     */
    public static double[] wcNumeratorDenominator2(PopStats p1, PopStats p2) {
        int n1 = p1.n, n2 = p2.n;
        if (n1 == 0 || n2 == 0) return new double[]{Double.NaN, Double.NaN};

        final int r = 2;
        double sumN = (double) n1 + (double) n2;
        double nBar = sumN / r;
        if (nBar <= 1.0) return new double[]{Double.NaN, Double.NaN};

        double sumN2 = (double) n1 * n1 + (double) n2 * n2;
        double nC = sumN - (sumN2 / sumN);
        if (nC <= 0.0) return new double[]{Double.NaN, Double.NaN};

        double sumAlt = (double) p1.xAlt + (double) p2.xAlt;
        double pBar = sumAlt / (2.0 * sumN);

        double sumHet = (double) p1.het + (double) p2.het;
        double hBar = sumHet / sumN;

        double pi1 = p1.p();
        double pi2 = p2.p();
        if (Double.isNaN(pi1) || Double.isNaN(pi2)) return new double[]{Double.NaN, Double.NaN};

        double s2_num = (double) n1 * (pi1 - pBar) * (pi1 - pBar)
                + (double) n2 * (pi2 - pBar) * (pi2 - pBar);
        double s2 = s2_num / nBar;

        double pBarq = pBar * (1.0 - pBar);
        double t1 = pBarq - 0.5 * s2 - 0.25 * hBar;

        double a = (nBar / nC) * (s2 - (1.0 / (nBar - 1.0)) * t1);

        double b = (nBar / (nBar - 1.0)) *
                (pBarq - 0.5 * s2 - ((2.0 * nBar - 1.0) / (4.0 * nBar)) * hBar);

        double c = 0.5 * hBar;

        double den = a + b + c;
        return new double[]{a, den};
    }

    /**
     * FORMAT字段中找key的位置（避免split正则）
     */
    public static int findFormatIndex(String format, String key) {
        int keyLen = key.length();
        int idx = 0;
        int start = 0;
        while (true) {
            int colonPos = format.indexOf(':', start);
            if (colonPos < 0) {
                if (format.length() - start == keyLen && format.regionMatches(start, key, 0, keyLen)) return idx;
                return -1;
            }
            if (colonPos - start == keyLen && format.regionMatches(start, key, 0, keyLen)) return idx;
            idx++;
            start = colonPos + 1;
        }
    }

    /**
     * 解析GT字段（GT是第一个字段时）
     */
    private static int parseAltCountFromSampleGT0(String sample) {
        if (sample.length() < 3) return -2;

        char a0 = sample.charAt(0);
        if (a0 == '.') return -1;
        char sep = sample.charAt(1);
        if (sep != '/' && sep != '|') return -2;
        char a1 = sample.charAt(2);
        if (a1 == '.') return -1;

        if (a0 > '1' || a1 > '1' || a0 < '0' || a1 < '0') return -2;
        return (a0 - '0') + (a1 - '0');
    }

    /**
     * 解析GT字符串
     */
    private static int parseAltCountFromGTString(String gt) {
        if (gt.length() < 3) return -2;

        char a0 = gt.charAt(0);
        if (a0 == '.') return -1;
        char sep = gt.charAt(1);
        if (sep != '/' && sep != '|') return -2;
        char a1 = gt.charAt(2);
        if (a1 == '.') return -1;

        if (a0 > '1' || a1 > '1' || a0 < '0' || a1 < '0') return -2;
        return (a0 - '0') + (a1 - '0');
    }

    /**
     * 从sample字段中提取指定索引的子字段
     */
    private static String extractFieldByIndex(String sample, int idx) {
        int start = 0;
        int cur = 0;
        while (true) {
            int colon = sample.indexOf(':', start);
            if (colon < 0) {
                if (cur == idx) return sample.substring(start);
                return null;
            }
            if (cur == idx) return sample.substring(start, colon);
            cur++;
            start = colon + 1;
        }
    }

    /**
     * 直接读取并处理染色体的Worker（多线程读取）
     */
    private static final class DirectChromosomeWorker implements Runnable, FST3GuanLi.Worker {
        final String chrom;
        final File tmpFile;
        final String vcfPath;
        final VCFIndex.ChromosomeIndex chromIndex;
        final String[] headerLines;
        final int[] pop1Idx;
        final int[] pop2Idx;

        Thread thread;
        private volatile Throwable failure;
        @Override public Throwable getFailure() { return failure; }
        final WorkerResult result = new WorkerResult();
        long readTimeMs = 0;

        DirectChromosomeWorker(String chrom, File tmpFile, String vcfPath,
                               VCFIndex.ChromosomeIndex chromIndex, String[] headerLines,
                               int[] pop1Idx, int[] pop2Idx) {
            this.chrom = chrom;
            this.tmpFile = tmpFile;
            this.vcfPath = vcfPath;
            this.chromIndex = chromIndex;
            this.headerLines = headerLines;
            this.pop1Idx = pop1Idx;
            this.pop2Idx = pop2Idx;
        }

        @Override
        public void start() {
            thread = new Thread(this, "FST-Read-" + chrom);
            thread.start();
        }

        @Override
        public void join() throws InterruptedException {
            if (thread != null) thread.join();
        }

        @Override
        public void run() {
            long readStartTime = System.currentTimeMillis();

            try (BufferedWriter bw = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(tmpFile), StandardCharsets.UTF_8),
                    FSTGongJu.IO_BUFFER)) {

                Map<String, Integer> formatGtCache = new HashMap<>(16);
                PopStats s1 = new PopStats();
                PopStats s2 = new PopStats();
                StringBuilder sb = new StringBuilder(96);

                DecimalFormatSymbols sym = DecimalFormatSymbols.getInstance(Locale.US);
                DecimalFormat df6 = new DecimalFormat("0.000000", sym);
                DecimalFormat df10 = new DecimalFormat("0.0000000000", sym);
                df6.setGroupingUsed(false);
                df10.setGroupingUsed(false);

                BufferedReader br = FSTGongJu.createChromosomeReader(vcfPath, chrom, chromIndex, headerLines);
                
                FSTGongJu.processChromosomeLines(br, chrom, line -> {
                    try {
                        processLine(line, formatGtCache, s1, s2, sb, df6, df10, bw, result, pop1Idx, pop2Idx);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

                bw.flush();
                readTimeMs = System.currentTimeMillis() - readStartTime;

            } catch (Throwable e) {
                failure = e;
                System.err.println("[ERROR] DirectWorker chrom=" + chrom + " failed: " + e.getMessage());
                e.printStackTrace(System.err);
            }
        }
    }

    /**
     * 队列模式的Worker（用于压缩文件）
     */
    private static final class ChromosomeWorker implements Runnable, FST3GuanLi.QueueWorker {
        final String chrom;
        final ArrayBlockingQueue<String> queue;
        final File tmpFile;
        final int[] pop1Idx;
        final int[] pop2Idx;

        Thread thread;
        private volatile Throwable failure;
        @Override public Throwable getFailure() { return failure; }
        final WorkerResult result = new WorkerResult();

        ChromosomeWorker(String chrom, File tmpFile, int[] pop1Idx, int[] pop2Idx) {
            this.chrom = chrom;
            this.tmpFile = tmpFile;
            this.pop1Idx = pop1Idx;
            this.pop2Idx = pop2Idx;
            this.queue = new ArrayBlockingQueue<>(FSTGongJu.QUEUE_CAPACITY);
        }

        @Override
        public void start() {
            thread = new Thread(this, "FST-" + chrom);
            thread.start();
        }

        @Override
        public void stop() throws InterruptedException {
            while (thread != null && thread.isAlive()
                    && !queue.offer(FSTGongJu.POISON_PILL, 100, java.util.concurrent.TimeUnit.MILLISECONDS)) { }

        }

        @Override
        public void join() throws InterruptedException {
            if (thread != null) thread.join();
        }

        @Override
        public void run() {
            try (BufferedWriter bw = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(tmpFile), StandardCharsets.UTF_8),
                    FSTGongJu.IO_BUFFER)) {

                Map<String, Integer> formatGtCache = new HashMap<>(16);
                PopStats s1 = new PopStats();
                PopStats s2 = new PopStats();
                StringBuilder sb = new StringBuilder(96);

                DecimalFormatSymbols sym = DecimalFormatSymbols.getInstance(Locale.US);
                DecimalFormat df6 = new DecimalFormat("0.000000", sym);
                DecimalFormat df10 = new DecimalFormat("0.0000000000", sym);
                df6.setGroupingUsed(false);
                df10.setGroupingUsed(false);

                while (true) {
                    String line = queue.take();
                    if (FSTGongJu.POISON_PILL.equals(line)) break;
                    if (line == null || line.isEmpty()) continue;

                    processLine(line, formatGtCache, s1, s2, sb, df6, df10, bw, result, pop1Idx, pop2Idx);
                }

                bw.flush();

            } catch (Throwable e) {
                failure = e;
                System.err.println("[ERROR] Worker chrom=" + chrom + " failed: " + e.getMessage());
                e.printStackTrace(System.err);
            }
        }
    }

    /**
     * 并行计算FST（按染色体划分）
     * 
     * @param vcfData VCF数据（已读取）
     * @param pop1Idx 群体1样本索引
     * @param pop2Idx 群体2样本索引
     * @param outPath 输出文件路径
     * @return 计算结果统计
     * @throws Exception 计算错误
     */
    public static CalculationResult calculateFSTParallel(FST2DuQu.VCFData vcfData,
                                                       int[] pop1Idx, int[] pop2Idx,
                                                       String outPath) throws Exception {
        String vcfPath = vcfData.vcfPath;
        boolean isGzip = vcfData.isGzip;
        VCFIndex vcfIndex = vcfData.vcfIndex;
        List<String> chromosomes = vcfData.chromosomes;
        String[] headerLines = vcfData.headerInfo.headerLinesArray;

        LinkedHashMap<String, ? extends Object> workers;
        long parallelTime;

        if (isGzip) {
            // 压缩文件：队列模式
            System.out.println("\nCompressed VCF (.gz) detected; using queue mode");
            LinkedHashMap<String, ChromosomeWorker> queueWorkers = new LinkedHashMap<>();

            long readStartTime = System.currentTimeMillis();
            long sitesTotalRead = 0;
            try (InputStream inputStream = new FileInputStream(vcfPath);
                 GZIPInputStream gzipStream = new GZIPInputStream(inputStream, FSTGongJu.IO_BUFFER);
                 BufferedReader brGzip = new BufferedReader(
                    new InputStreamReader(gzipStream, StandardCharsets.UTF_8),
                    FSTGongJu.IO_BUFFER)) {

            for (String headerLine : headerLines) {
                brGzip.readLine();
            }

            String line;
            while ((line = brGzip.readLine()) != null) {
                if (line == null || line.isEmpty()) continue;
                if (line.charAt(0) == '#') continue;

                sitesTotalRead++;
                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                String chrom = line.substring(0, tab);

                ChromosomeWorker w = queueWorkers.get(chrom);
                if (w == null) {
                    String safeChrom = FSTGongJu.sanitizeForFileName(chrom);
                    File tmp = new File(outPath + "." + safeChrom + ".part");
                    w = new ChromosomeWorker(chrom, tmp, pop1Idx, pop2Idx);
                    queueWorkers.put(chrom, w);
                    w.start();
                }

                while (!w.queue.offer(line, 100, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    if (w.getFailure() != null || !w.thread.isAlive())
                        throw new IOException("Chromosome worker stopped: " + chrom, w.getFailure());
                }
            }
            } finally {
                FST3GuanLi.finishQueues(queueWorkers);
            }


            long readTime = System.currentTimeMillis() - readStartTime;
            System.out.println("VCF read finished in " + readTime + " ms, sites read: " + sitesTotalRead);

            long parallelStartTime = System.currentTimeMillis();
            FST3GuanLi.checkFailures(queueWorkers);
            parallelTime = System.currentTimeMillis() - parallelStartTime;

            workers = queueWorkers;
            System.out.println("\n=== Compressed queue mode statistics ===");
            System.out.println("Total parallel time: " + parallelTime + "ms");

        } else {
            // 未压缩文件：直接读取模式
            System.out.println("\nUsing multi-threaded direct read mode");
            LinkedHashMap<String, DirectChromosomeWorker> directWorkers = new LinkedHashMap<>();

            for (String chrom : chromosomes) {
                VCFIndex.ChromosomeIndex chromIndex = vcfIndex.getChromosomeIndex(chrom);
                String safeChrom = FSTGongJu.sanitizeForFileName(chrom);
                File tmp = new File(outPath + "." + safeChrom + ".part");
                DirectChromosomeWorker w = new DirectChromosomeWorker(
                    chrom, tmp, vcfPath, chromIndex, headerLines, pop1Idx, pop2Idx);
                directWorkers.put(chrom, w);
            }

            long parallelStartTime = System.currentTimeMillis();
            try { FST3GuanLi.startAllWorkers(directWorkers); }
            finally { FST3GuanLi.joinAllWorkers(directWorkers); }
            FST3GuanLi.checkFailures(directWorkers);
            parallelTime = System.currentTimeMillis() - parallelStartTime;

            workers = directWorkers;
            System.out.println("\n=== Multi-threaded direct read statistics ===");
            System.out.println("Total parallel time: " + parallelTime + "ms");
        }

        // 合并输出和汇总统计
        long sitesUsed = 0;
        long sitesTotal = 0;
        long countFst = 0;
        double sumFst = 0.0;
        double sumA = 0.0;
        double sumDen = 0.0;

        if (vcfIndex != null) {
            for (String chrom : chromosomes) {
                VCFIndex.ChromosomeIndex chromIndex = vcfIndex.getChromosomeIndex(chrom);
                if (chromIndex != null) {
                    sitesTotal += chromIndex.lineCount;
                }
            }
        }

        try (BufferedWriter out = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(outPath), StandardCharsets.UTF_8),
                FSTGongJu.IO_BUFFER)) {
            out.write("CHROM\tPOS\tWEIR_AND_COCKERHAM_FST\tWC_A\tWC_DEN\n");

            byte[] buf = new byte[FSTGongJu.IO_BUFFER];
            for (Object wObj : workers.values()) {
                File tmpFile;
                WorkerResult result;

                if (wObj instanceof DirectChromosomeWorker) {
                    DirectChromosomeWorker w = (DirectChromosomeWorker) wObj;
                    tmpFile = w.tmpFile;
                    result = w.result;
                } else if (wObj instanceof ChromosomeWorker) {
                    ChromosomeWorker w = (ChromosomeWorker) wObj;
                    tmpFile = w.tmpFile;
                    result = w.result;
                } else {
                    continue;
                }

                sitesUsed += result.sitesUsed;
                countFst += result.countFst;
                sumFst += result.sumFst;
                sumA += result.sumA;
                sumDen += result.sumDen;

                try (InputStream in = new BufferedInputStream(new FileInputStream(tmpFile), FSTGongJu.IO_BUFFER)) {
                    int n;
                    while ((n = in.read(buf)) >= 0) {
                        if (n == 0) continue;
                        out.write(new String(buf, 0, n, StandardCharsets.UTF_8));
                    }
                }
            }
            out.flush();
        }

        // 删除临时文件
        for (Object wObj : workers.values()) {
            File tmpFile;
            if (wObj instanceof DirectChromosomeWorker) {
                tmpFile = ((DirectChromosomeWorker) wObj).tmpFile;
            } else if (wObj instanceof ChromosomeWorker) {
                tmpFile = ((ChromosomeWorker) wObj).tmpFile;
            } else {
                continue;
            }
            if (!tmpFile.delete()) {
                System.err.println("[WARN] Failed to delete temporary file: " + tmpFile.getAbsolutePath());
            }
        }

        double meanFst = (countFst == 0) ? Double.NaN : (sumFst / (double) countFst);
        double weightedFst = (sumDen == 0.0) ? Double.NaN : (sumA / sumDen);

        System.out.println("\n=== FST parallel calculation statistics ===");
        System.out.println("Chromosome threads:\t" + workers.size());
        System.out.println("Total sites:\t" + sitesTotal);
        System.out.println("Sites used:\t" + sitesUsed);
        System.out.println("Mean Fst:\t" + (Double.isNaN(meanFst) ? "nan" : String.format(Locale.US, "%.6f", meanFst)));
        System.out.println("Weighted Fst:\t" + (Double.isNaN(weightedFst) ? "nan" : String.format(Locale.US, "%.6f", weightedFst)));
        System.out.println("Output file:\t" + outPath);

        return new CalculationResult(sitesUsed, sitesTotal, meanFst, weightedFst);
    }

    /**
     * 计算结果
     */
    public static class CalculationResult {
        public final long sitesUsed;
        public final long sitesTotal;
        public final double meanFst;
        public final double weightedFst;

        public CalculationResult(long sitesUsed, long sitesTotal, double meanFst, double weightedFst) {
            this.sitesUsed = sitesUsed;
            this.sitesTotal = sitesTotal;
            this.meanFst = meanFst;
            this.weightedFst = weightedFst;
        }
    }
}
