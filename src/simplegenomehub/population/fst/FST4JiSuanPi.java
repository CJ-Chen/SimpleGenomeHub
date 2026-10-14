package simplegenomehub.population.fst;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.zip.GZIPInputStream;

/**
 * FST4JiSuanPi - Pi计算核心逻辑
 * 实现与vcftools完全一致的计算逻辑
 */
public class FST4JiSuanPi {

    /**
     * Per-site Pi计算结果
     */
    public static final class SitePiResult {
        public final int pos;
        public final double pi1;      // 群体1的Pi
        public final double pi2;      // 群体2的Pi
        public final double pi12;     // 群体间Pi
        public final long mismatches1;
        public final long mismatches2;
        public final long mismatches12;
        public final long pairs1;
        public final long pairs2;
        public final long pairs12;
        public final int n1;           // 群体1的个体数（二倍体）
        public final int n2;           // 群体2的个体数（二倍体）
        public final boolean isPolymorphic1;
        public final boolean isPolymorphic2;

        public SitePiResult(int pos, double pi1, double pi2, double pi12, 
                           long mismatches1, long mismatches2, long mismatches12,
                           long pairs1, long pairs2, long pairs12, int n1, int n2,
                           boolean isPolymorphic1, boolean isPolymorphic2) {
            this.pos = pos;
            this.pi1 = pi1;
            this.pi2 = pi2;
            this.pi12 = pi12;
            this.mismatches1 = mismatches1;
            this.mismatches2 = mismatches2;
            this.mismatches12 = mismatches12;
            this.pairs1 = pairs1;
            this.pairs2 = pairs2;
            this.pairs12 = pairs12;
            this.n1 = n1;
            this.n2 = n2;
            this.isPolymorphic1 = isPolymorphic1;
            this.isPolymorphic2 = isPolymorphic2;
        }
    }

    /**
     * Worker结果统计
     */
    public static final class WorkerResult {
        public long sitesUsed = 0;
    }

    /**
     * 计算单个位点的Pi值（与vcftools逻辑完全一致）
     */
    public static SitePiResult calculateSitePi(String[] fields, int[] pop1Idx, int[] pop2Idx, int gtIndex) {
        if (fields.length < 10) return null;

        String ref = fields[3];
        String alt = fields[4];

        // 只保留简单二等位SNP
        if (ref.length() != 1 || alt.length() != 1 || alt.indexOf(',') >= 0 
            || alt.startsWith("<") || alt.contains("<")) return null;

        // 解析群体1的等位基因计数
        int[] alleleCounts1 = getAlleleCounts(fields, pop1Idx, gtIndex);
        if (alleleCounts1 == null) return null;
        int n1 = alleleCounts1[0];
        int refCount1 = alleleCounts1[1];
        int altCount1 = alleleCounts1[2];
        int totalAlleles1 = refCount1 + altCount1;

        // 解析群体2的等位基因计数
        int[] alleleCounts2 = getAlleleCounts(fields, pop2Idx, gtIndex);
        if (alleleCounts2 == null) return null;
        int n2 = alleleCounts2[0];
        int refCount2 = alleleCounts2[1];
        int altCount2 = alleleCounts2[2];
        int totalAlleles2 = refCount2 + altCount2;

        if (n1 == 0 && n2 == 0) return null;

        int pos = Integer.parseInt(fields[1]);

        // 计算群体1的Pi
        double pi1 = Double.NaN;
        long mismatches1 = 0;
        long pairs1 = 0;
        boolean isPolymorphic1 = false;
        if (n1 > 0 && totalAlleles1 > 0) {
            mismatches1 = (long) refCount1 * (totalAlleles1 - refCount1) + (long) altCount1 * (totalAlleles1 - altCount1);
            pairs1 = (long) totalAlleles1 * (totalAlleles1 - 1);
            if (pairs1 > 0) {
                pi1 = (double) mismatches1 / pairs1;
            }
            isPolymorphic1 = (refCount1 < totalAlleles1);
        }

        // 计算群体2的Pi
        double pi2 = Double.NaN;
        long mismatches2 = 0;
        long pairs2 = 0;
        boolean isPolymorphic2 = false;
        if (n2 > 0 && totalAlleles2 > 0) {
            mismatches2 = (long) refCount2 * (totalAlleles2 - refCount2) + (long) altCount2 * (totalAlleles2 - altCount2);
            pairs2 = (long) totalAlleles2 * (totalAlleles2 - 1);
            if (pairs2 > 0) {
                pi2 = (double) mismatches2 / pairs2;
            }
            isPolymorphic2 = (refCount2 < totalAlleles2);
        }

        // 计算群体间Pi
        double pi12 = Double.NaN;
        long mismatches12 = 0;
        long pairs12 = 0;
        if (n1 > 0 && n2 > 0 && totalAlleles1 > 0 && totalAlleles2 > 0) {
            mismatches12 = (long) refCount1 * altCount2 + (long) altCount1 * refCount2;
            pairs12 = (long) totalAlleles1 * totalAlleles2;
            if (pairs12 > 0) {
                pi12 = (double) mismatches12 / pairs12;
            }
        }

        return new SitePiResult(pos, pi1, pi2, pi12, mismatches1, mismatches2, mismatches12,
                                pairs1, pairs2, pairs12, n1, n2, isPolymorphic1, isPolymorphic2);
    }

    /**
     * 获取等位基因计数
     */
    public static int[] getAlleleCounts(String[] fields, int[] sampleIdx, int gtIndex) {
        int n = 0;
        int refCount = 0;
        int altCount = 0;

        if (gtIndex == 0) {
            for (int col : sampleIdx) {
                if (col >= fields.length) continue;
                String sample = fields[col];
                if (sample == null || sample.isEmpty() || sample.equals(".")) continue;

                int altCountFromGT = parseAltCountFromSampleGT0(sample);
                if (altCountFromGT < 0) continue;
                n++;
                refCount += (2 - altCountFromGT);
                altCount += altCountFromGT;
            }
        } else {
            for (int col : sampleIdx) {
                if (col >= fields.length) continue;
                String sample = fields[col];
                if (sample == null || sample.isEmpty() || sample.equals(".")) continue;

                String gt = extractFieldByIndex(sample, gtIndex);
                if (gt == null) continue;

                int altCountFromGT = parseAltCountFromGTString(gt);
                if (altCountFromGT < 0) continue;
                n++;
                refCount += (2 - altCountFromGT);
                altCount += altCountFromGT;
            }
        }

        if (n == 0) return null;
        return new int[]{n, refCount, altCount};
    }

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
     * 直接读取并处理染色体的Worker
     */
    public static final class DirectChromosomeWorker implements Runnable, FST3GuanLi.Worker {
        public final String chrom;
        public final File tmpFile1;
        public final File tmpFile2;
        public final File tmpFile12;
        public final File tmpTajimaD1;
        public final File tmpTajimaD2;
        public final String vcfPath;
        public final VCFIndex.ChromosomeIndex chromIndex;
        public final String[] headerLines;
        public final int[] pop1Idx;
        public final int[] pop2Idx;
        public final int tajimaDWindowSize;
        public final int nPop1;
        public final int nPop2;

        public Thread thread;
        private volatile Throwable failure;
        @Override public Throwable getFailure() { return failure; }
        public final WorkerResult result = new WorkerResult();
        public long readTimeMs = 0;

        public DirectChromosomeWorker(String chrom, File tmpFile1, File tmpFile2, File tmpFile12,
                                     File tmpTajimaD1, File tmpTajimaD2, String vcfPath,
                                     VCFIndex.ChromosomeIndex chromIndex, String[] headerLines,
                                     int[] pop1Idx, int[] pop2Idx, int tajimaDWindowSize, int nPop1, int nPop2) {
            this.chrom = chrom;
            this.tmpFile1 = tmpFile1;
            this.tmpFile2 = tmpFile2;
            this.tmpFile12 = tmpFile12;
            this.tmpTajimaD1 = tmpTajimaD1;
            this.tmpTajimaD2 = tmpTajimaD2;
            this.vcfPath = vcfPath;
            this.chromIndex = chromIndex;
            this.headerLines = headerLines;
            this.pop1Idx = pop1Idx;
            this.pop2Idx = pop2Idx;
            this.tajimaDWindowSize = tajimaDWindowSize;
            this.nPop1 = nPop1;
            this.nPop2 = nPop2;
        }

        @Override
        public void start() {
            thread = new Thread(this, "PI-Read-" + chrom);
            thread.start();
        }

        @Override
        public void join() throws InterruptedException {
            if (thread != null) thread.join();
        }

        @Override
        public void run() {
            long readStartTime = System.currentTimeMillis();

            try (
                BufferedWriter bw1 = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(tmpFile1), StandardCharsets.UTF_8),
                    FSTGongJu.IO_BUFFER);
                BufferedWriter bw2 = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(tmpFile2), StandardCharsets.UTF_8),
                    FSTGongJu.IO_BUFFER);
                BufferedWriter bw12 = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(tmpFile12), StandardCharsets.UTF_8),
                    FSTGongJu.IO_BUFFER)
            ) {
                Map<String, Integer> formatGtCache = new HashMap<>(16);
                StringBuilder sb1 = new StringBuilder(96);
                StringBuilder sb2 = new StringBuilder(96);
                StringBuilder sb12 = new StringBuilder(96);

                DecimalFormatSymbols sym = DecimalFormatSymbols.getInstance(Locale.US);
                DecimalFormat df = new DecimalFormat("0.0000000000", sym);
                df.setGroupingUsed(false);

                BufferedReader br = FSTGongJu.createChromosomeReader(vcfPath, chrom, chromIndex, headerLines);
                
                FSTGongJu.processChromosomeLines(br, chrom, line -> {
                    try {
                        processLine(line, formatGtCache, sb1, sb2, sb12, df, bw1, bw2, bw12, result, pop1Idx, pop2Idx);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

                bw1.flush();
                bw2.flush();
                bw12.flush();
                readTimeMs = System.currentTimeMillis() - readStartTime;

                // TajimaD现在在最后统一从sites.pi文件计算（使用stepSize），这里不再单独计算

            } catch (Throwable e) {
                failure = e;
                System.err.println("[ERROR] DirectWorker chrom=" + chrom + " failed: " + e.getMessage());
                e.printStackTrace(System.err);
            }
        }

        private void processLine(String line, Map<String, Integer> formatGtCache,
                                StringBuilder sb1, StringBuilder sb2, StringBuilder sb12, DecimalFormat df,
                                BufferedWriter bw1, BufferedWriter bw2, BufferedWriter bw12, WorkerResult result,
                                int[] pop1Idx, int[] pop2Idx) throws IOException {
            String[] fields = FSTGongJu.splitTab(line);
            if (fields.length < 10) return;

            String format = fields[8];
            Integer cached = formatGtCache.get(format);
            int gtIndex = (cached != null) ? cached : FST4JiSuanFST.findFormatIndex(format, "GT");
            if (gtIndex < 0) return;
            if (cached == null) formatGtCache.put(format, gtIndex);

            SitePiResult piResult = calculateSitePi(fields, pop1Idx, pop2Idx, gtIndex);
            if (piResult == null) return;

            // 输出群体1
            sb1.setLength(0);
            sb1.append(fields[0]).append('\t').append(piResult.pos).append('\t');
            if (Double.isNaN(piResult.pi1) || Double.isInfinite(piResult.pi1)) {
                sb1.append("nan");
            } else {
                sb1.append(df.format(piResult.pi1));
            }
            sb1.append('\t').append(piResult.mismatches1)
               .append('\t').append(piResult.pairs1)
               .append('\t').append(piResult.n1)
               .append('\t').append(piResult.isPolymorphic1 ? 1 : 0)
               .append('\n');
            bw1.write(sb1.toString());

            // 输出群体2
            sb2.setLength(0);
            sb2.append(fields[0]).append('\t').append(piResult.pos).append('\t');
            if (Double.isNaN(piResult.pi2) || Double.isInfinite(piResult.pi2)) {
                sb2.append("nan");
            } else {
                sb2.append(df.format(piResult.pi2));
            }
            sb2.append('\t').append(piResult.mismatches2)
               .append('\t').append(piResult.pairs2)
               .append('\t').append(piResult.n2)
               .append('\t').append(piResult.isPolymorphic2 ? 1 : 0)
               .append('\n');
            bw2.write(sb2.toString());

            // 输出群体间PI
            sb12.setLength(0);
            sb12.append(fields[0]).append('\t').append(piResult.pos).append('\t');
            if (Double.isNaN(piResult.pi12) || Double.isInfinite(piResult.pi12)) {
                sb12.append("nan");
            } else {
                sb12.append(df.format(piResult.pi12));
            }
            sb12.append('\t').append(piResult.mismatches12)
                .append('\t').append(piResult.pairs12)
                .append('\t').append(piResult.n1 + piResult.n2)
                .append('\t').append((piResult.isPolymorphic1 || piResult.isPolymorphic2) ? 1 : 0)
                .append('\n');
            bw12.write(sb12.toString());

            result.sitesUsed++;
        }
    }

    /**
     * 队列模式的Worker（用于压缩文件）
     */
    public static final class ChromosomeWorker implements Runnable, FST3GuanLi.QueueWorker {
        public final String chrom;
        public final ArrayBlockingQueue<String> queue;
        public final File tmpFile1;
        public final File tmpFile2;
        public final File tmpFile12;
        public final File tmpTajimaD1;
        public final File tmpTajimaD2;
        public final int[] pop1Idx;
        public final int[] pop2Idx;
        public final int tajimaDWindowSize;
        public final int nPop1;
        public final int nPop2;

        public Thread thread;
        private volatile Throwable failure;
        @Override public Throwable getFailure() { return failure; }
        public final WorkerResult result = new WorkerResult();

        public ChromosomeWorker(String chrom, File tmpFile1, File tmpFile2, File tmpFile12,
                              File tmpTajimaD1, File tmpTajimaD2, int[] pop1Idx, int[] pop2Idx,
                              int tajimaDWindowSize, int nPop1, int nPop2) {
            this.chrom = chrom;
            this.tmpFile1 = tmpFile1;
            this.tmpFile2 = tmpFile2;
            this.tmpFile12 = tmpFile12;
            this.tmpTajimaD1 = tmpTajimaD1;
            this.tmpTajimaD2 = tmpTajimaD2;
            this.pop1Idx = pop1Idx;
            this.pop2Idx = pop2Idx;
            this.tajimaDWindowSize = tajimaDWindowSize;
            this.nPop1 = nPop1;
            this.nPop2 = nPop2;
            this.queue = new ArrayBlockingQueue<>(FSTGongJu.QUEUE_CAPACITY);
        }

        @Override
        public void start() {
            thread = new Thread(this, "PI-" + chrom);
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
            try (
                BufferedWriter bw1 = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(tmpFile1), StandardCharsets.UTF_8),
                    FSTGongJu.IO_BUFFER);
                BufferedWriter bw2 = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(tmpFile2), StandardCharsets.UTF_8),
                    FSTGongJu.IO_BUFFER);
                BufferedWriter bw12 = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(tmpFile12), StandardCharsets.UTF_8),
                    FSTGongJu.IO_BUFFER)
            ) {
                Map<String, Integer> formatGtCache = new HashMap<>(16);
                StringBuilder sb1 = new StringBuilder(96);
                StringBuilder sb2 = new StringBuilder(96);
                StringBuilder sb12 = new StringBuilder(96);

                DecimalFormatSymbols sym = DecimalFormatSymbols.getInstance(Locale.US);
                DecimalFormat df = new DecimalFormat("0.0000000000", sym);
                df.setGroupingUsed(false);

                while (true) {
                    String line = queue.take();
                    if (FSTGongJu.POISON_PILL.equals(line)) break;
                    if (line == null || line.isEmpty()) continue;

                    String[] fields = FSTGongJu.splitTab(line);
                    if (fields.length < 10) continue;

                    String format = fields[8];
                    Integer cached = formatGtCache.get(format);
                    int gtIndex = (cached != null) ? cached : FST4JiSuanFST.findFormatIndex(format, "GT");
                    if (gtIndex < 0) continue;
                    if (cached == null) formatGtCache.put(format, gtIndex);

                    SitePiResult piResult = calculateSitePi(fields, pop1Idx, pop2Idx, gtIndex);
                    if (piResult == null) continue;

                    // 输出群体1
                    sb1.setLength(0);
                    sb1.append(fields[0]).append('\t').append(piResult.pos).append('\t');
                    if (Double.isNaN(piResult.pi1) || Double.isInfinite(piResult.pi1)) {
                        sb1.append("nan");
                    } else {
                        sb1.append(df.format(piResult.pi1));
                    }
                    sb1.append('\t').append(piResult.mismatches1)
                       .append('\t').append(piResult.pairs1)
                       .append('\t').append(piResult.n1)
                       .append('\t').append(piResult.isPolymorphic1 ? 1 : 0)
                       .append('\n');
                    bw1.write(sb1.toString());

                    // 输出群体2
                    sb2.setLength(0);
                    sb2.append(fields[0]).append('\t').append(piResult.pos).append('\t');
                    if (Double.isNaN(piResult.pi2) || Double.isInfinite(piResult.pi2)) {
                        sb2.append("nan");
                    } else {
                        sb2.append(df.format(piResult.pi2));
                    }
                    sb2.append('\t').append(piResult.mismatches2)
                       .append('\t').append(piResult.pairs2)
                       .append('\t').append(piResult.n2)
                       .append('\t').append(piResult.isPolymorphic2 ? 1 : 0)
                       .append('\n');
                    bw2.write(sb2.toString());

                    // 输出群体间PI
                    sb12.setLength(0);
                    sb12.append(fields[0]).append('\t').append(piResult.pos).append('\t');
                    if (Double.isNaN(piResult.pi12) || Double.isInfinite(piResult.pi12)) {
                        sb12.append("nan");
                    } else {
                        sb12.append(df.format(piResult.pi12));
                    }
                    sb12.append('\t').append(piResult.mismatches12)
                        .append('\t').append(piResult.pairs12)
                        .append('\t').append(piResult.n1 + piResult.n2)
                        .append('\t').append((piResult.isPolymorphic1 || piResult.isPolymorphic2) ? 1 : 0)
                        .append('\n');
                    bw12.write(sb12.toString());

                    result.sitesUsed++;
                }

                bw1.flush();
                bw2.flush();
                bw12.flush();

                // TajimaD现在在最后统一从sites.pi文件计算（使用stepSize），这里不再单独计算

            } catch (Throwable e) {
                failure = e;
                System.err.println("[ERROR] Worker chrom=" + chrom + " failed: " + e.getMessage());
                e.printStackTrace(System.err);
            }
        }
    }

    /**
     * 并行计算Pi（按染色体划分）
     * 
     * @param vcfData VCF数据（已读取）
     * @param pop1Idx 群体1样本索引
     * @param pop2Idx 群体2样本索引
     * @param outPrefix 输出文件前缀
     * @param pop1Name 群体1名称
     * @param pop2Name 群体2名称
     * @param tajimaDWindowSize Tajima's D窗口大小（如果>0则计算Tajima's D）
     * @param tajimaDStepSize Tajima's D步长（如果>0则使用，否则使用windowSize）
     * @return 返回[pop1样本数, pop2样本数]数组
     * @throws Exception 计算错误
     */
    public static int[] calculatePiParallel(FST2DuQu.VCFData vcfData,
                                            int[] pop1Idx, int[] pop2Idx,
                                            String outPrefix, String pop1Name, String pop2Name,
                                            int tajimaDWindowSize, int tajimaDStepSize) throws Exception {
        String vcfPath = vcfData.vcfPath;
        boolean isGzip = vcfData.isGzip;
        VCFIndex vcfIndex = vcfData.vcfIndex;
        List<String> chromosomes = vcfData.chromosomes;
        String[] headerLines = vcfData.headerInfo.headerLinesArray;

        int nPop1 = pop1Idx.length;
        int nPop2 = pop2Idx.length;

        LinkedHashMap<String, ? extends Object> workers;
        long parallelTime;

        if (isGzip) {
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
                    File tmp1 = new File(outPrefix + "." + pop1Name + "." + safeChrom + ".part");
                    File tmp2 = new File(outPrefix + "." + pop2Name + "." + safeChrom + ".part");
                    File tmp12 = new File(outPrefix + "." + pop1Name + "_" + pop2Name + "." + safeChrom + ".part");
                    // TajimaD临时文件不再使用（现在直接从sites.pi文件计算），传入null
                    w = new ChromosomeWorker(chrom, tmp1, tmp2, tmp12, null, null, 
                                             pop1Idx, pop2Idx, tajimaDWindowSize, nPop1, nPop2);
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
            System.out.println("\nUsing multi-threaded direct read mode");
            LinkedHashMap<String, DirectChromosomeWorker> directWorkers = new LinkedHashMap<>();

            for (String chrom : chromosomes) {
                VCFIndex.ChromosomeIndex chromIndex = vcfIndex.getChromosomeIndex(chrom);
                String safeChrom = FSTGongJu.sanitizeForFileName(chrom);
                File tmp1 = new File(outPrefix + "." + pop1Name + "." + safeChrom + ".part");
                File tmp2 = new File(outPrefix + "." + pop2Name + "." + safeChrom + ".part");
                File tmp12 = new File(outPrefix + "." + pop1Name + "_" + pop2Name + "." + safeChrom + ".part");
                // TajimaD临时文件不再使用（现在直接从sites.pi文件计算），传入null
                DirectChromosomeWorker w = new DirectChromosomeWorker(
                    chrom, tmp1, tmp2, tmp12, null, null, 
                    vcfPath, chromIndex, headerLines, pop1Idx, pop2Idx, 
                    tajimaDWindowSize, nPop1, nPop2);
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

        // 输出文件路径
        String outPath1 = outPrefix + "." + pop1Name + ".sites.pi";
        String outPath2 = outPrefix + "." + pop2Name + ".sites.pi";
        String outPath12 = outPrefix + "." + pop1Name + "_" + pop2Name + ".sites.pi";

        // 合并输出文件
        long sitesUsed = FST5ShuChu.mergePiFiles(workers, outPath1, outPath2, outPath12);

        System.out.println("\n=== Pi parallel calculation statistics ===");
        System.out.println("Chromosome threads:\t" + workers.size());
        System.out.println("Sites used:\t" + sitesUsed);
        System.out.println("Output file 1:\t" + outPath1);
        System.out.println("Output file 2:\t" + outPath2);
        System.out.println("Combined output:\t" + outPath12);

        // 计算Tajima's D（如果指定了窗口大小）
        if (tajimaDWindowSize > 0) {
            String outTajimaD1 = outPrefix + "." + pop1Name + ".Tajima.D";
            String outTajimaD2 = outPrefix + "." + pop2Name + ".Tajima.D";
            
            // 使用传入的stepSize（如果未指定，使用windowSize）
            int stepSize = (tajimaDStepSize > 0 && tajimaDStepSize <= tajimaDWindowSize) ? tajimaDStepSize : tajimaDWindowSize;
            
            // 从sites.pi文件直接计算windowed格式的TajimaD（使用stepSize，与PI和FST一致）
            String sitePiFile1 = outPrefix + "." + pop1Name + ".sites.pi";
            String sitePiFile2 = outPrefix + "." + pop2Name + ".sites.pi";
            
            try {
                if (new File(sitePiFile1).exists()) {
                    FST5ShuChu.convertTajimaDToWindowed(sitePiFile1, tajimaDWindowSize, stepSize, outTajimaD1, nPop1);
                }
                if (new File(sitePiFile2).exists()) {
                    FST5ShuChu.convertTajimaDToWindowed(sitePiFile2, tajimaDWindowSize, stepSize, outTajimaD2, nPop2);
                }
            } catch (Exception e) {
                throw e;
            }
            
            System.out.println("Tajima's D output 1:\t" + outTajimaD1);
            System.out.println("Tajima's D output 2:\t" + outTajimaD2);
        }

        // 删除临时文件
        FST5ShuChu.cleanupPiTempFiles(workers);

        // 返回群体样本数
        return new int[]{nPop1, nPop2};
    }
}
