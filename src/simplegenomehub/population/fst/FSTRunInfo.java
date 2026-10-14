package simplegenomehub.population.fst;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * FSTRunInfo - 运行信息文件（结果目录下的 run.info.txt）。
 * <p>
 * 记录本次运行的输入文件路径、窗口设置，以及"群体（类群）与比较策略"，
 * 便于以后直接把结果目录拖进主界面复现绘图；也便于后续依据该文本还原
 * 群体与比较策略。
 * <p>
 * 文本按区块组织（UTF-8，key=value，值只在第一个等号处切分）：
 * <pre>
 * [Run]
 * version=1
 * created=2026-09-18T16:30:00
 * vcf=/path/all.vcf
 * genome=/path/genome.fa
 * gff3=/path/anno.gff3
 * windowSize=1000000
 * stepSize=100000
 * sortChromosomes=false
 *
 * [Group]
 * name=Group1
 * sample=SampleA
 * sample=SampleB
 *
 * [Comparison]
 * name=Group1_vs_Group2
 * left=Group1
 * right=Group2
 * </pre>
 */
public class FSTRunInfo {

    /** 信息文件名（放在结果输出目录内） */
    public static final String FILE_NAME = "run.info.txt";

    /** 当前格式版本 */
    public static final int VERSION = 1;

    /** 一个群体（类群）及其样本 */
    public static class Group {
        public String name = "";
        public final List<String> samples = new ArrayList<>();

        public Group() {}

        public Group(String name) {
            this.name = name == null ? "" : name;
        }
    }

    /** 一次比较：左/右各可包含多个群体名 */
    public static class Comparison {
        public String name = "";
        public final List<String> leftGroups = new ArrayList<>();
        public final List<String> rightGroups = new ArrayList<>();
    }

    /** 一次运行的全部信息 */
    public static class Info {
        public int version = VERSION;
        public String created = "";
        /** SimpleGenomeHub 数据单元身份（物种名与版本），便于从结果目录回溯。 */
        public String speciesName = "";
        public String speciesVersion = "";
        public String vcf = "";
        public String genome = "";
        public String gff3 = "";
        public int windowSize = -1;
        public int stepSize = -1;
        public boolean sortChromosomes = false;
        public final List<Group> groups = new ArrayList<>();
        public final List<Comparison> comparisons = new ArrayList<>();
    }

    /** 运行信息文件对象（不判断是否存在） */
    public static File fileOf(File directory) {
        if (directory == null) return null;
        return new File(directory, FILE_NAME);
    }

    /** 目录下是否存在运行信息文件 */
    public static boolean exists(File directory) {
        File f = fileOf(directory);
        return f != null && f.isFile();
    }

    /** 构造一个带输入路径的 Info */
    public static Info create(String vcf, String genome, String gff3,
                              int windowSize, int stepSize, boolean sortChromosomes) {
        Info info = new Info();
        info.version = VERSION;
        info.created = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date());
        info.vcf = nullToEmpty(vcf);
        info.genome = nullToEmpty(genome);
        info.gff3 = nullToEmpty(gff3);
        info.windowSize = windowSize;
        info.stepSize = stepSize;
        info.sortChromosomes = sortChromosomes;
        return info;
    }

    /**
     * 把运行信息写入目录下的 run.info.txt
     */
    public static void write(File directory, Info info) throws IOException {
        if (directory == null || info == null) return;
        if (!directory.exists() && !directory.mkdirs()) return;

        File outFile = fileOf(directory);
        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8))) {
            bw.write("# FSTool run info (v" + info.version + ")");
            bw.newLine();
            bw.write("# Input files, populations and comparison strategy of this run.");
            bw.newLine();

            bw.newLine();
            bw.write("[Run]");
            bw.newLine();
            writeKV(bw, "version", String.valueOf(info.version));
            writeKV(bw, "created", info.created);
            writeKV(bw, "speciesName", info.speciesName);
            writeKV(bw, "speciesVersion", info.speciesVersion);
            writeKV(bw, "vcf", info.vcf);
            writeKV(bw, "genome", info.genome);
            writeKV(bw, "gff3", info.gff3);
            writeKV(bw, "windowSize", info.windowSize > 0 ? String.valueOf(info.windowSize) : "");
            writeKV(bw, "stepSize", info.stepSize > 0 ? String.valueOf(info.stepSize) : "");
            writeKV(bw, "sortChromosomes", String.valueOf(info.sortChromosomes));

            if (info.groups != null) {
                for (Group group : info.groups) {
                    if (group == null) continue;
                    bw.newLine();
                    bw.write("[Group]");
                    bw.newLine();
                    writeKV(bw, "name", group.name);
                    for (String sample : group.samples) {
                        writeKV(bw, "sample", sample);
                    }
                }
            }

            if (info.comparisons != null) {
                for (Comparison comp : info.comparisons) {
                    if (comp == null) continue;
                    bw.newLine();
                    bw.write("[Comparison]");
                    bw.newLine();
                    writeKV(bw, "name", comp.name);
                    for (String g : comp.leftGroups) {
                        writeKV(bw, "left", g);
                    }
                    for (String g : comp.rightGroups) {
                        writeKV(bw, "right", g);
                    }
                }
            }
        }
        System.out.println("Wrote run info: " + outFile.getAbsolutePath());
    }

    /**
     * 读取目录下的 run.info.txt；文件不存在返回 null
     */
    public static Info read(File directory) {
        File file = fileOf(directory);
        if (file == null || !file.isFile()) return null;

        Info info = new Info();
        String section = "";
        Group currentGroup = null;
        Comparison currentComp = null;

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                if (line.startsWith("[") && line.endsWith("]")) {
                    section = line.substring(1, line.length() - 1).trim();
                    currentGroup = null;
                    currentComp = null;
                    if (section.equalsIgnoreCase("Group")) {
                        currentGroup = new Group();
                        info.groups.add(currentGroup);
                    } else if (section.equalsIgnoreCase("Comparison")) {
                        currentComp = new Comparison();
                        info.comparisons.add(currentComp);
                    }
                    continue;
                }

                int eq = line.indexOf('=');
                if (eq < 0) continue;
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();

                if (section.equalsIgnoreCase("Run")) {
                    applyRunValue(info, key, value);
                } else if (currentGroup != null) {
                    if (key.equalsIgnoreCase("name")) {
                        currentGroup.name = value;
                    } else if (key.equalsIgnoreCase("sample") && !value.isEmpty()) {
                        currentGroup.samples.add(value);
                    }
                } else if (currentComp != null) {
                    if (key.equalsIgnoreCase("name")) {
                        currentComp.name = value;
                    } else if (key.equalsIgnoreCase("left") && !value.isEmpty()) {
                        currentComp.leftGroups.add(value);
                    } else if (key.equalsIgnoreCase("right") && !value.isEmpty()) {
                        currentComp.rightGroups.add(value);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to read run info: " + file.getAbsolutePath() + " - " + e.getMessage());
            return null;
        }
        return info;
    }

    /**
     * 把若干比较策略追加写入目录下的 run.info.txt：
     * 已存在"相同左右类群"的策略则直接跳过（不重复写入）；保留原有 [Run] 与 [Group] 区块。
     *
     * @return 实际新增的策略条数
     */
    public static int addComparisonsIfAbsent(File directory, List<Comparison> additions) throws IOException {
        if (directory == null || additions == null || additions.isEmpty()) return 0;
        Info info = exists(directory) ? read(directory) : null;
        if (info == null) info = new Info();

        int added = 0;
        outer:
        for (Comparison add : additions) {
            if (add == null) continue;
            String key = comparisonKey(add.leftGroups, add.rightGroups);
            for (Comparison existing : info.comparisons) {
                if (comparisonKey(existing.leftGroups, existing.rightGroups).equals(key)) {
                    continue outer;
                }
            }
            info.comparisons.add(add);
            added++;
        }
        if (added > 0) {
            write(directory, info);
        }
        return added;
    }

    private static String comparisonKey(List<String> left, List<String> right) {
        return joinSorted(left) + ">" + joinSorted(right);
    }

    private static String joinSorted(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        List<String> copy = new ArrayList<>(list);
        java.util.Collections.sort(copy);
        return String.join(",", copy);
    }

    private static void applyRunValue(Info info, String key, String value) {
        if (key.equalsIgnoreCase("version")) {
            try { info.version = Integer.parseInt(value); } catch (NumberFormatException ignored) {}
        } else if (key.equalsIgnoreCase("created")) {
            info.created = value;
        } else if (key.equalsIgnoreCase("speciesName")) {
            info.speciesName = value;
        } else if (key.equalsIgnoreCase("speciesVersion")) {
            info.speciesVersion = value;
        } else if (key.equalsIgnoreCase("vcf")) {
            info.vcf = value;
        } else if (key.equalsIgnoreCase("genome")) {
            info.genome = value;
        } else if (key.equalsIgnoreCase("gff3")) {
            info.gff3 = value;
        } else if (key.equalsIgnoreCase("windowSize")) {
            try { info.windowSize = Integer.parseInt(value); } catch (NumberFormatException ignored) {}
        } else if (key.equalsIgnoreCase("stepSize")) {
            try { info.stepSize = Integer.parseInt(value); } catch (NumberFormatException ignored) {}
        } else if (key.equalsIgnoreCase("sortChromosomes")) {
            info.sortChromosomes = Boolean.parseBoolean(value);
        }
    }

    private static void writeKV(BufferedWriter bw, String key, String value) throws IOException {
        String v = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
        bw.write(key);
        bw.write('=');
        bw.write(v);
        bw.newLine();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
