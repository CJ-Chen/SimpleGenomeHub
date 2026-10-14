package simplegenomehub.population.fst;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * FST5ShuChu - 文件输出和格式转换类
 * 负责：
 * 1. 合并多个临时文件为最终输出文件
 * 2. 将per-site文件转换为windowed格式
 */
public class FST5ShuChu {

    /**
     * 将per-site FST文件转换为windowed格式
     */
    public static void convertFSTToWindowed(String siteFilePath, int windowSize, int stepSize, 
                                           String outFilePath, boolean includeEmpty) throws IOException {
        if (windowSize <= 0) {
            throw new IllegalArgumentException("windowSize must be positive.");
        }
        if (stepSize <= 0 || stepSize > windowSize) {
            stepSize = windowSize;
        }

        try (
            BufferedReader br = new BufferedReader(
                new InputStreamReader(FSTGongJu.openPossiblyGzip(siteFilePath), StandardCharsets.UTF_8), 
                FSTGongJu.IO_BUFFER);
            BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(outFilePath), StandardCharsets.UTF_8), 
                FSTGongJu.IO_BUFFER)
        ) {
            bw.write("CHROM\tBIN_START\tBIN_END\tN_VARIANTS\tWEIGHTED_FST\tMEAN_FST\n");

            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty()) continue;
                if (line.startsWith("#")) continue;
                if (line.startsWith("CHROM\t") || line.startsWith("CHROM ")) continue;
                break;
            }

            Map<String, Map<Integer, double[]>> bins = new LinkedHashMap<>();
            List<String> chromosomes = new ArrayList<>();
            String lastChrom = null;

            FstRec next = null;
            if (line != null) {
                try {
                    next = parseFstLine(line);
                } catch (Exception e) {
                    next = null;
                }
            }

            while (next != null) {
                String chrom = extractChrom(line);
                int pos = next.pos;
                
                if (!chrom.equals(lastChrom)) {
                    if (lastChrom != null) {
                        chromosomes.add(lastChrom);
                    }
                    lastChrom = chrom;
                    if (!bins.containsKey(chrom)) {
                        bins.put(chrom, new TreeMap<>());
                    }
                }

                if (!Double.isNaN(next.fst) && !Double.isInfinite(next.fst) &&
                    !Double.isNaN(next.a) && !Double.isInfinite(next.a) &&
                    !Double.isNaN(next.den) && !Double.isInfinite(next.den) && next.den != 0.0) {
                    
                    int first = (int) Math.ceil((pos - windowSize) / (double) stepSize);
                    if (first < 0) first = 0;
                    int last = (int) Math.ceil(pos / (double) stepSize);
                    
                    Map<Integer, double[]> chromBins = bins.get(chrom);
                    for (int idx = first; idx < last; idx++) {
                        chromBins.putIfAbsent(idx, new double[]{0.0, 0.0, 0.0, 0.0});
                        double[] bin = chromBins.get(idx);
                        bin[0] += next.a;
                        bin[1] += next.den;
                        bin[2] += next.fst;
                        bin[3] += 1.0;
                    }
                }

                line = br.readLine();
                while (line != null && (line.isEmpty() || line.startsWith("#") || line.startsWith("CHROM"))) {
                    line = br.readLine();
                }
                if (line == null) {
                    next = null;
                } else {
                    try {
                        next = parseFstLine(line);
                    } catch (Exception e) {
                        System.err.println("Warning: Skipping invalid line: " + line);
                        line = br.readLine();
                        while (line != null && (line.isEmpty() || line.startsWith("#") || line.startsWith("CHROM"))) {
                            line = br.readLine();
                        }
                        next = (line == null) ? null : parseFstLine(line);
                    }
                }
            }

            if (lastChrom != null && !chromosomes.contains(lastChrom)) {
                chromosomes.add(lastChrom);
            }

            for (String chrom : chromosomes) {
                Map<Integer, double[]> chromBins = bins.get(chrom);
                if (chromBins == null) continue;

                List<Integer> sortedIndices = new ArrayList<>(chromBins.keySet());
                Collections.sort(sortedIndices);

                for (int s : sortedIndices) {
                    double[] bin = chromBins.get(s);
                    double sumA = bin[0];
                    double sumAll = bin[1];
                    double sumFst = bin[2];
                    double count = bin[3];

                    if (count > 0 || includeEmpty) {
                        int binStart = s * stepSize + 1;
                        int binEnd = s * stepSize + windowSize;

                        double weightedFst = (sumAll == 0.0 || Double.isNaN(sumAll) || Double.isNaN(sumA)) 
                            ? Double.NaN : (sumA / sumAll);
                        double meanFst = (count == 0) ? Double.NaN : (sumFst / count);

                        if ((sumAll != 0.0) && (!Double.isNaN(sumA)) && (!Double.isNaN(sumAll)) && (count > 0)) {
                            writeFstWindow(bw, chrom, binStart, binEnd, (int) count, weightedFst, meanFst);
                        } else if (includeEmpty && count == 0) {
                            writeFstWindow(bw, chrom, binStart, binEnd, 0, Double.NaN, Double.NaN);
                        }
                    }
                }
            }

            bw.flush();
        }
    }

    /**
     * 将per-site Pi文件转换为windowed格式
     */
    public static void convertPiToWindowed(String siteFilePath, int windowSize, int stepSize,
                                          String outFilePath, int nIndividuals) throws IOException {
        if (windowSize <= 0) {
            throw new IllegalArgumentException("windowSize must be positive.");
        }
        if (stepSize <= 0 || stepSize > windowSize) {
            stepSize = windowSize;
        }

        if (nIndividuals <= 0) {
            nIndividuals = inferNIndividualsFromSiteFile(siteFilePath);
            if (nIndividuals <= 0) {
                throw new IllegalArgumentException("Cannot infer the number of individuals from the per-site file; pass nIndividuals.");
            }
        }

        try (
            BufferedReader br = new BufferedReader(
                new InputStreamReader(FSTGongJu.openPossiblyGzip(siteFilePath), StandardCharsets.UTF_8),
                FSTGongJu.IO_BUFFER);
            BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(outFilePath), StandardCharsets.UTF_8),
                FSTGongJu.IO_BUFFER)
        ) {
            bw.write("CHROM\tBIN_START\tBIN_END\tN_VARIANTS\tN_MONOMORPHIC\tPI\n");

            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty()) continue;
                if (line.startsWith("#")) continue;
                if (line.startsWith("CHROM\t") || line.startsWith("CHROM ")) continue;
                break;
            }

            Map<String, Map<Integer, long[]>> bins = new LinkedHashMap<>();
            List<String> chromosomes = new ArrayList<>();
            String lastChrom = null;

            PiRec next = null;
            if (line != null) {
                try {
                    next = parsePiLine(line);
                } catch (Exception e) {
                    next = null;
                }
            }

            while (next != null) {
                String chrom = extractChrom(line);

                if (!chrom.equals(lastChrom)) {
                    if (lastChrom != null) {
                        chromosomes.add(lastChrom);
                    }
                    lastChrom = chrom;
                    if (!bins.containsKey(chrom)) {
                        bins.put(chrom, new TreeMap<>());
                    }
                }

                if (next.mismatches > 0 && next.pairs > 0) {
                    int first = (int) Math.ceil((next.pos - windowSize) / (double) stepSize);
                    if (first < 0) first = 0;
                    int last = (int) Math.ceil(next.pos / (double) stepSize);

                    Map<Integer, long[]> chromBins = bins.get(chrom);
                    for (int idx = first; idx < last; idx++) {
                        chromBins.putIfAbsent(idx, new long[]{0, 0, 0, 0});
                        long[] bin = chromBins.get(idx);
                        bin[0]++;
                        bin[1] += next.pairs;
                        bin[2] += next.mismatches;
                        if (next.isPolymorphic) {
                            bin[3]++;
                        }
                    }
                }

                line = br.readLine();
                while (line != null && (line.isEmpty() || line.startsWith("#") || line.startsWith("CHROM"))) {
                    line = br.readLine();
                }
                if (line == null) {
                    next = null;
                } else {
                    try {
                        next = parsePiLine(line);
                    } catch (Exception e) {
                        System.err.println("Warning: Skipping invalid line: " + line);
                        line = br.readLine();
                        while (line != null && (line.isEmpty() || line.startsWith("#") || line.startsWith("CHROM"))) {
                            line = br.readLine();
                        }
                        next = (line == null) ? null : parsePiLine(line);
                    }
                }
            }

            if (lastChrom != null && !chromosomes.contains(lastChrom)) {
                chromosomes.add(lastChrom);
            }

            int nKeptChr = 2 * nIndividuals;
            long nComparisons = (long) nKeptChr * (nKeptChr - 1);

            for (String chrom : chromosomes) {
                Map<Integer, long[]> chromBins = bins.get(chrom);
                if (chromBins == null) continue;

                List<Integer> sortedIndices = new ArrayList<>(chromBins.keySet());
                Collections.sort(sortedIndices);

                for (int s : sortedIndices) {
                    long[] bin = chromBins.get(s);
                    long nVariantSites = bin[0];
                    long nVariantSitePairs = bin[1];
                    long nMismatches = bin[2];
                    long nPolymorphicSites = bin[3];

                    if (nPolymorphicSites > 0 || nMismatches > 0) {
                        int binStart = s * stepSize + 1;
                        int binEnd = s * stepSize + windowSize;

                        long nMonomorphicSites = windowSize - nVariantSites;
                        if (nMonomorphicSites < 0) nMonomorphicSites = 0;

                        long nPairs = nVariantSitePairs + (nMonomorphicSites * nComparisons);
                        double pi = (nPairs == 0) ? Double.NaN : ((double) nMismatches / nPairs);

                        writePiWindow(bw, chrom, binStart, binEnd, (int) nPolymorphicSites, 
                                     (int) nMonomorphicSites, pi);
                    }
                }
            }

            bw.flush();
        }
    }

    /**
     * 合并Pi计算的临时文件
     */
    public static long mergePiFiles(LinkedHashMap<String, ? extends Object> workers,
                                   String outPath1, String outPath2, String outPath12) throws IOException {
        long sitesUsed = 0;

        // 合并群体1的输出
        try (BufferedWriter out1 = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(outPath1), StandardCharsets.UTF_8),
                FSTGongJu.IO_BUFFER)) {
            out1.write("CHROM\tPOS\tPI\tMISMATCHES\tPAIRS\tN\tIS_POLYMORPHIC\n");

            byte[] buf = new byte[FSTGongJu.IO_BUFFER];
            for (Object wObj : workers.values()) {
                File tmpFile;
                if (wObj instanceof FST4JiSuanPi.DirectChromosomeWorker) {
                    FST4JiSuanPi.DirectChromosomeWorker w = (FST4JiSuanPi.DirectChromosomeWorker) wObj;
                    tmpFile = w.tmpFile1;
                    sitesUsed += w.result.sitesUsed;
                } else if (wObj instanceof FST4JiSuanPi.ChromosomeWorker) {
                    FST4JiSuanPi.ChromosomeWorker w = (FST4JiSuanPi.ChromosomeWorker) wObj;
                    tmpFile = w.tmpFile1;
                    sitesUsed += w.result.sitesUsed;
                } else {
                    continue;
                }

                try (InputStream in = new BufferedInputStream(new FileInputStream(tmpFile), FSTGongJu.IO_BUFFER)) {
                    int n;
                    while ((n = in.read(buf)) >= 0) {
                        if (n == 0) continue;
                        out1.write(new String(buf, 0, n, StandardCharsets.UTF_8));
                    }
                }
            }
            out1.flush();
        }

        // 合并群体2的输出
        try (BufferedWriter out2 = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(outPath2), StandardCharsets.UTF_8),
                FSTGongJu.IO_BUFFER)) {
            out2.write("CHROM\tPOS\tPI\tMISMATCHES\tPAIRS\tN\tIS_POLYMORPHIC\n");

            byte[] buf = new byte[FSTGongJu.IO_BUFFER];
            for (Object wObj : workers.values()) {
                File tmpFile;
                if (wObj instanceof FST4JiSuanPi.DirectChromosomeWorker) {
                    tmpFile = ((FST4JiSuanPi.DirectChromosomeWorker) wObj).tmpFile2;
                } else if (wObj instanceof FST4JiSuanPi.ChromosomeWorker) {
                    tmpFile = ((FST4JiSuanPi.ChromosomeWorker) wObj).tmpFile2;
                } else {
                    continue;
                }

                try (InputStream in = new BufferedInputStream(new FileInputStream(tmpFile), FSTGongJu.IO_BUFFER)) {
                    int n;
                    while ((n = in.read(buf)) >= 0) {
                        if (n == 0) continue;
                        out2.write(new String(buf, 0, n, StandardCharsets.UTF_8));
                    }
                }
            }
            out2.flush();
        }

        // 合并群体间PI的输出
        try (BufferedWriter out12 = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(outPath12), StandardCharsets.UTF_8),
                FSTGongJu.IO_BUFFER)) {
            out12.write("CHROM\tPOS\tPI\tMISMATCHES\tPAIRS\tN\tIS_POLYMORPHIC\n");

            byte[] buf = new byte[FSTGongJu.IO_BUFFER];
            for (Object wObj : workers.values()) {
                File tmpFile;
                if (wObj instanceof FST4JiSuanPi.DirectChromosomeWorker) {
                    tmpFile = ((FST4JiSuanPi.DirectChromosomeWorker) wObj).tmpFile12;
                } else if (wObj instanceof FST4JiSuanPi.ChromosomeWorker) {
                    tmpFile = ((FST4JiSuanPi.ChromosomeWorker) wObj).tmpFile12;
                } else {
                    continue;
                }

                try (InputStream in = new BufferedInputStream(new FileInputStream(tmpFile), FSTGongJu.IO_BUFFER)) {
                    int n;
                    while ((n = in.read(buf)) >= 0) {
                        if (n == 0) continue;
                        out12.write(new String(buf, 0, n, StandardCharsets.UTF_8));
                    }
                }
            }
            out12.flush();
        }

        return sitesUsed;
    }

    /**
     * 从sites.pi文件计算TajimaD并转换为windowed格式（使用stepSize创建滑动窗口）
     * 类似于convertPiToWindowed，但计算TajimaD值
     */
    public static void convertTajimaDToWindowed(String sitePiFilePath, int windowSize, int stepSize,
                                                String outFilePath, int nIndividuals) throws IOException {
        if (windowSize <= 0) {
            throw new IllegalArgumentException("windowSize must be positive.");
        }
        if (stepSize <= 0 || stepSize > windowSize) {
            stepSize = windowSize;
        }
        if (nIndividuals <= 0) {
            throw new IllegalArgumentException("nIndividuals must be positive.");
        }

        // 计算Tajima's D常数（复制FST4JiSuanTajimaD的逻辑）
        TajimaDConstants constants = new TajimaDConstants(nIndividuals);
        
        try (
            BufferedReader br = new BufferedReader(
                new InputStreamReader(FSTGongJu.openPossiblyGzip(sitePiFilePath), StandardCharsets.UTF_8),
                FSTGongJu.IO_BUFFER);
            BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(outFilePath), StandardCharsets.UTF_8),
                FSTGongJu.IO_BUFFER)
        ) {
            bw.write("CHROM\tBIN_START\tBIN_END\tN_SNPS\tTajimaD\n");

            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty()) continue;
                if (line.startsWith("#")) continue;
                if (line.startsWith("CHROM\t") || line.startsWith("CHROM ")) continue;
                break;
            }

            Map<String, Map<Integer, TajimaDWindowData>> bins = new LinkedHashMap<>();
            List<String> chromosomes = new ArrayList<>();
            String lastChrom = null;

            PiRec next = null;
            if (line != null) {
                try {
                    next = parsePiLine(line);
                } catch (Exception e) {
                    next = null;
                }
            }

            while (next != null) {
                String chrom = extractChrom(line);

                if (!chrom.equals(lastChrom)) {
                    if (lastChrom != null) {
                        chromosomes.add(lastChrom);
                    }
                    lastChrom = chrom;
                    if (!bins.containsKey(chrom)) {
                        bins.put(chrom, new TreeMap<>());
                    }
                }

                // 只处理多态位点（与FST4JiSuanTajimaD逻辑一致）
                if (next.isPolymorphic && next.mismatches > 0 && next.pairs > 0) {
                    int pos = next.pos;
                    // 计算该位点应该属于哪些滑动窗口索引（与convertPiToWindowed逻辑一致）
                    int first = (int) Math.ceil((pos - windowSize) / (double) stepSize);
                    if (first < 0) first = 0;
                    int last = (int) Math.ceil(pos / (double) stepSize);

                    Map<Integer, TajimaDWindowData> chromBins = bins.get(chrom);
                    for (int idx = first; idx < last; idx++) {
                        chromBins.putIfAbsent(idx, new TajimaDWindowData());
                        TajimaDWindowData window = chromBins.get(idx);
                        window.addSite(next.mismatches, next.pairs, next.n);
                    }
                }

                line = br.readLine();
                while (line != null && (line.isEmpty() || line.startsWith("#") || line.startsWith("CHROM"))) {
                    line = br.readLine();
                }
                if (line == null) {
                    next = null;
                } else {
                    try {
                        next = parsePiLine(line);
                    } catch (Exception e) {
                        System.err.println("Warning: Skipping invalid line: " + line);
                        line = br.readLine();
                        while (line != null && (line.isEmpty() || line.startsWith("#") || line.startsWith("CHROM"))) {
                            line = br.readLine();
                        }
                        next = (line == null) ? null : parsePiLine(line);
                    }
                }
            }

            if (lastChrom != null && !chromosomes.contains(lastChrom)) {
                chromosomes.add(lastChrom);
            }

            java.text.DecimalFormatSymbols sym = java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US);
            java.text.DecimalFormat df = new java.text.DecimalFormat("0.0000000000", sym);
            df.setGroupingUsed(false);

            for (String chrom : chromosomes) {
                Map<Integer, TajimaDWindowData> chromBins = bins.get(chrom);
                if (chromBins == null) continue;

                List<Integer> sortedIndices = new ArrayList<>(chromBins.keySet());
                Collections.sort(sortedIndices);

                for (int s : sortedIndices) {
                    TajimaDWindowData window = chromBins.get(s);
                    int S = window.nSNPs;
                    double tajimaD = Double.NaN;

                    if (S > 0) {
                        // 计算 π = 2 × Σ(p × (1-p)) × n / (n-1)
                        double pi = 2.0 * window.sumPOneMinusP * constants.n / (constants.n - 1.0);
                        // 计算 θw = S / a1
                        double thetaW = (double) S / constants.a1;
                        // 计算方差 Var = e1×S + e2×S×(S-1)
                        double var = constants.e1 * S + constants.e2 * S * (S - 1);
                        // 计算 Tajima's D = (π - θw) / sqrt(Var)
                        if (var > 0) {
                            tajimaD = (pi - thetaW) / Math.sqrt(var);
                        }
                    }

                    int binStart = s * stepSize + 1;
                    int binEnd = s * stepSize + windowSize;

                    bw.write(chrom);
                    bw.write('\t');
                    bw.write(String.valueOf(binStart));
                    bw.write('\t');
                    bw.write(String.valueOf(binEnd));
                    bw.write('\t');
                    bw.write(String.valueOf(S));
                    bw.write('\t');
                    if (Double.isNaN(tajimaD) || Double.isInfinite(tajimaD)) {
                        bw.write("nan");
                    } else {
                        bw.write(df.format(tajimaD));
                    }
                    bw.write('\n');
                }
            }

            bw.flush();
        }
    }

    /**
     * TajimaD窗口数据（用于滑动窗口计算）
     */
    private static final class TajimaDWindowData {
        int nSNPs = 0;
        double sumPOneMinusP = 0.0;

        void addSite(long mismatches, long pairs, int n) {
            if (pairs > 0 && n > 0) {
                int totalAlleles = 2 * n;
                double pOneMinusP = (mismatches / 2.0) / ((double) totalAlleles * totalAlleles);
                sumPOneMinusP += pOneMinusP;
                nSNPs++;
            }
        }
    }

    /**
     * Tajima's D计算所需的常数（复制自FST4JiSuanTajimaD）
     */
    private static final class TajimaDConstants {
        final double a1;
        final double a2;
        final double e1;
        final double e2;
        final int n;

        TajimaDConstants(int nIndividuals) {
            this.n = 2 * nIndividuals;
            if (this.n < 2) {
                throw new IllegalArgumentException("At least 2 chromosome rows (1 individual) are required.");
            }

            double a1Sum = 0.0;
            double a2Sum = 0.0;
            for (int i = 1; i < this.n; i++) {
                double invI = 1.0 / i;
                a1Sum += invI;
                a2Sum += invI * invI;
            }
            this.a1 = a1Sum;
            this.a2 = a2Sum;

            double b1 = (double)(this.n + 1) / (3.0 * (this.n - 1));
            double b2 = 2.0 * (this.n * this.n + this.n + 3) / (9.0 * this.n * (this.n - 1));
            double c1 = b1 - (1.0 / this.a1);
            double c2 = b2 - ((double)(this.n + 2) / (this.a1 * this.n)) + (this.a2 / (this.a1 * this.a1));
            this.e1 = c1 / this.a1;
            this.e2 = c2 / ((this.a1 * this.a1) + this.a2);
        }
    }

    /**
     * 合并Tajima's D计算的临时文件
     */
    public static void mergeTajimaDFiles(LinkedHashMap<String, ? extends Object> workers,
                                        String outTD1, String outTD2) throws IOException {
        // 合并群体1的Tajima's D
        try (BufferedWriter outTD1Writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(outTD1), StandardCharsets.UTF_8),
                FSTGongJu.IO_BUFFER)) {
            outTD1Writer.write("CHROM\tBIN_START\tN_SNPS\tTajimaD\n");

            boolean headerWritten = false;
            for (Object wObj : workers.values()) {
                File tmpFile;
                if (wObj instanceof FST4JiSuanPi.DirectChromosomeWorker) {
                    tmpFile = ((FST4JiSuanPi.DirectChromosomeWorker) wObj).tmpTajimaD1;
                } else if (wObj instanceof FST4JiSuanPi.ChromosomeWorker) {
                    tmpFile = ((FST4JiSuanPi.ChromosomeWorker) wObj).tmpTajimaD1;
                } else {
                    continue;
                }

                if (tmpFile != null && tmpFile.exists() && tmpFile.length() > 0) {
                    try (BufferedReader brTD = new BufferedReader(
                            new InputStreamReader(new FileInputStream(tmpFile), StandardCharsets.UTF_8),
                            FSTGongJu.IO_BUFFER)) {
                        String lineTD;
                        while ((lineTD = brTD.readLine()) != null) {
                            if (!headerWritten && (lineTD.startsWith("CHROM") || lineTD.startsWith("#"))) {
                                headerWritten = true;
                                continue;
                            }
                            if (lineTD.isEmpty()) continue;
                            outTD1Writer.write(lineTD);
                            outTD1Writer.write('\n');
                        }
                    }
                }
            }
            outTD1Writer.flush();
        }

        // 合并群体2的Tajima's D
        try (BufferedWriter outTD2Writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(outTD2), StandardCharsets.UTF_8),
                FSTGongJu.IO_BUFFER)) {
            outTD2Writer.write("CHROM\tBIN_START\tN_SNPS\tTajimaD\n");

            boolean headerWritten = false;
            for (Object wObj : workers.values()) {
                File tmpFile;
                if (wObj instanceof FST4JiSuanPi.DirectChromosomeWorker) {
                    tmpFile = ((FST4JiSuanPi.DirectChromosomeWorker) wObj).tmpTajimaD2;
                } else if (wObj instanceof FST4JiSuanPi.ChromosomeWorker) {
                    tmpFile = ((FST4JiSuanPi.ChromosomeWorker) wObj).tmpTajimaD2;
                } else {
                    continue;
                }

                if (tmpFile != null && tmpFile.exists() && tmpFile.length() > 0) {
                    try (BufferedReader brTD2 = new BufferedReader(
                            new InputStreamReader(new FileInputStream(tmpFile), StandardCharsets.UTF_8),
                            FSTGongJu.IO_BUFFER)) {
                        String lineTD2;
                        while ((lineTD2 = brTD2.readLine()) != null) {
                            if (!headerWritten && (lineTD2.startsWith("CHROM") || lineTD2.startsWith("#"))) {
                                headerWritten = true;
                                continue;
                            }
                            if (lineTD2.isEmpty()) continue;
                            outTD2Writer.write(lineTD2);
                            outTD2Writer.write('\n');
                        }
                    }
                }
            }
            outTD2Writer.flush();
        }
    }

    /**
     * 清理Pi计算的临时文件
     */
    public static void cleanupPiTempFiles(LinkedHashMap<String, ? extends Object> workers) {
        for (Object wObj : workers.values()) {
            File tmpFile1, tmpFile2, tmpFile12, tmpTajimaD1, tmpTajimaD2;
            if (wObj instanceof FST4JiSuanPi.DirectChromosomeWorker) {
                FST4JiSuanPi.DirectChromosomeWorker w = (FST4JiSuanPi.DirectChromosomeWorker) wObj;
                tmpFile1 = w.tmpFile1;
                tmpFile2 = w.tmpFile2;
                tmpFile12 = w.tmpFile12;
                tmpTajimaD1 = w.tmpTajimaD1;
                tmpTajimaD2 = w.tmpTajimaD2;
            } else if (wObj instanceof FST4JiSuanPi.ChromosomeWorker) {
                FST4JiSuanPi.ChromosomeWorker w = (FST4JiSuanPi.ChromosomeWorker) wObj;
                tmpFile1 = w.tmpFile1;
                tmpFile2 = w.tmpFile2;
                tmpFile12 = w.tmpFile12;
                tmpTajimaD1 = w.tmpTajimaD1;
                tmpTajimaD2 = w.tmpTajimaD2;
            } else {
                continue;
            }

            if (!tmpFile1.delete()) {
                System.err.println("[WARN] Failed to delete temporary file: " + tmpFile1.getAbsolutePath());
            }
            if (!tmpFile2.delete()) {
                System.err.println("[WARN] Failed to delete temporary file: " + tmpFile2.getAbsolutePath());
            }
            if (!tmpFile12.delete()) {
                System.err.println("[WARN] Failed to delete temporary file: " + tmpFile12.getAbsolutePath());
            }
            // TajimaD临时文件不再使用（现在直接从sites.pi文件计算），无需删除
        }
    }

    // --- 内部类和方法 ---

    private static final class FstRec {
        final int pos;
        final double fst;
        final double a;
        final double den;
        FstRec(int pos, double fst, double a, double den) { 
            this.pos = pos; 
            this.fst = fst; 
            this.a = a; 
            this.den = den; 
        }
    }

    private static final class PiRec {
        final int pos;
        final double pi;
        final long mismatches;
        final long pairs;
        final int n;
        final boolean isPolymorphic;

        PiRec(int pos, double pi, long mismatches, long pairs, int n, boolean isPolymorphic) {
            this.pos = pos;
            this.pi = pi;
            this.mismatches = mismatches;
            this.pairs = pairs;
            this.n = n;
            this.isPolymorphic = isPolymorphic;
        }
    }

    private static FstRec parseFstLine(String line) {
        String[] f = line.split("\t");
        if (f.length < 5) {
            throw new IllegalArgumentException("Bad site line (need 5 cols): " + line);
        }
        int pos = Integer.parseInt(f[1]);
        double fst = parseDoubleOrNaN(f[2]);
        double a = parseDoubleOrNaN(f[3]);
        double den = parseDoubleOrNaN(f[4]);
        return new FstRec(pos, fst, a, den);
    }

    private static PiRec parsePiLine(String line) {
        String[] f = line.split("\t");
        if (f.length < 7) {
            throw new IllegalArgumentException("Bad site line (need 7 cols): " + line);
        }
        int pos = Integer.parseInt(f[1]);
        double pi = parseDoubleOrNaN(f[2]);
        long mismatches = Long.parseLong(f[3]);
        long pairs = Long.parseLong(f[4]);
        int n = Integer.parseInt(f[5]);
        boolean isPolymorphic = Integer.parseInt(f[6]) != 0;
        return new PiRec(pos, pi, mismatches, pairs, n, isPolymorphic);
    }

    private static double parseDoubleOrNaN(String s) {
        if (s == null || s.trim().isEmpty() || s.equalsIgnoreCase("nan") || s.equalsIgnoreCase("-nan")) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static String extractChrom(String line) {
        int tab = line.indexOf('\t');
        if (tab < 0) {
            throw new IllegalArgumentException("Bad line: " + line);
        }
        return line.substring(0, tab);
    }

    private static void writeFstWindow(BufferedWriter bw, String chrom, int start, int end, int nVar,
                                    double weightedFst, double meanFst) throws IOException {
        bw.write(chrom);
        bw.write('\t');
        bw.write(Integer.toString(start));
        bw.write('\t');
        bw.write(Integer.toString(end));
        bw.write('\t');
        bw.write(Integer.toString(nVar));
        bw.write('\t');
        
        if (Double.isNaN(weightedFst) || Double.isInfinite(weightedFst)) {
            bw.write("nan");
        } else {
            bw.write(String.format("%.7f", weightedFst));
        }
        bw.write('\t');
        
        if (Double.isNaN(meanFst) || Double.isInfinite(meanFst)) {
            bw.write("nan");
        } else {
            bw.write(String.format("%.7f", meanFst));
        }
        bw.write('\n');
    }

    private static void writePiWindow(BufferedWriter bw, String chrom, int start, int end,
                                    int nVariants, int nMonomorphic, double pi) throws IOException {
        bw.write(chrom);
        bw.write('\t');
        bw.write(Integer.toString(start));
        bw.write('\t');
        bw.write(Integer.toString(end));
        bw.write('\t');
        bw.write(Integer.toString(nVariants));
        bw.write('\t');
        bw.write(Integer.toString(nMonomorphic));
        bw.write('\t');

        if (Double.isNaN(pi) || Double.isInfinite(pi)) {
            bw.write("nan");
        } else {
            bw.write(String.format(Locale.US, "%.9f", pi));
        }
        bw.write('\n');
    }

    /**
     * 从per-site文件中推断个体数（取最常见的n值）
     */
    private static int inferNIndividualsFromSiteFile(String siteFilePath) throws IOException {
        Map<Integer, Integer> nCounts = new HashMap<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(FSTGongJu.openPossiblyGzip(siteFilePath), StandardCharsets.UTF_8),
                FSTGongJu.IO_BUFFER)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("CHROM\t") || line.startsWith("CHROM ")) continue;
                break;
            }

            int lineCount = 0;
            while (line != null && lineCount < 1000) {
                try {
                    String[] fields = line.split("\t");
                    if (fields.length >= 6) {
                        int n = Integer.parseInt(fields[5]);
                        nCounts.put(n, nCounts.getOrDefault(n, 0) + 1);
                    }
                } catch (Exception e) {
                    // 忽略解析错误
                }
                line = br.readLine();
                lineCount++;
            }
        }

        int maxCount = 0;
        int mostCommonN = 0;
        for (Map.Entry<Integer, Integer> entry : nCounts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                mostCommonN = entry.getKey();
            }
        }
        return mostCommonN;
    }
}
