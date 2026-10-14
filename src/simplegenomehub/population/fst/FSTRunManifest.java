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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FSTRunManifest - 运行命名清单
 * <p>
 * 每次计算运行结束后，在结果目录写入 run.names.tsv，把“结果文件名”映射为
 * “卡牌显示名（Compare/类群的自定义名称）”与“悬停明细说明”，
 * 供结果展示页直接使用，避免从文件名里猜名称（例如 VCF 名自带点号时
 * 卡牌简介会错误显示成 "cp" 之类的碎片）。
 * <p>
 * 格式（UTF-8，制表符分隔，每行一条）：
 * <pre>
 * 结果文件基名 \t 卡牌显示名 \t 明细说明
 * </pre>
 * 名称中的 \t / \r / \n 会在写入时被替换为空格。
 */
public class FSTRunManifest {

    /** 清单文件名（放在结果输出目录内） */
    public static final String FILE_NAME = "run.names.tsv";

    /**
     * 一条命名记录
     */
    public static final class RunEntry {
        public final String displayName;   // 卡牌上显示的简短名称
        public final String description;   // 悬停时显示的明细说明（可为空串，可含换行）

        public RunEntry(String displayName, String description) {
            this.displayName = stripTableChars(displayName);
            // 说明中允许换行（写入时会转义），但不允许制表符/回车破坏TSV结构
            this.description = description == null ? "" : description.replace('\t', ' ').replace('\r', ' ').trim();
        }
    }

    /**
     * 读取目录下的命名清单但**不改变**当前激活状态（供浏览/展示使用）；
     * 目录或清单不存在时返回空表。
     */
    public static synchronized Map<String, RunEntry> read(File directory) {
        Map<String, RunEntry> loaded = load(directory);
        return loaded == null ? Collections.<String, RunEntry>emptyMap() : loaded;
    }

    /**
     * 按结果文件路径或基名查找命名记录；找不到返回 null
     */
    public static synchronized RunEntry lookup(String filePathOrName) {
        if (filePathOrName == null || filePathOrName.isEmpty()) return null;
        File file = new File(filePathOrName);
        // A bare display name has no reliable directory identity.
        if (file.getParentFile() == null) return null;
        return read(file.getParentFile()).get(file.getName());
    }

    /**
     * 合并写入：读取目录中已有的 run.names.tsv，与 newEntries 合并后写回（新条目优先）。
     * 用于"加载结果目录后追加运行新比较策略"时，避免覆盖旧的卡牌命名。
     */
    public static synchronized void merge(File directory, Map<String, RunEntry> newEntries) throws IOException {
        if (directory == null) return;
        Map<String, RunEntry> merged = new HashMap<>();
        Map<String, RunEntry> existing = load(directory);
        if (existing != null) {
            merged.putAll(existing);
        }
        if (newEntries != null) {
            merged.putAll(newEntries);
        }
        write(directory, merged);
    }

    /**
     * 把命名表写入目录中的 run.names.tsv（按基名排序，便于人工查看）
     */
    public static synchronized void write(File directory, Map<String, RunEntry> entries) throws IOException {
        if (directory == null || entries == null) return;
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("Cannot create FST result directory: " + directory);

        List<String> keys = new ArrayList<>(entries.keySet());
        Collections.sort(keys);

        File outFile = new File(directory, FILE_NAME);
        simplegenomehub.util.fileio.AtomicFiles.write(outFile.toPath(), output -> {
            BufferedWriter bw = new BufferedWriter(output);
            for (String key : keys) {
                RunEntry e = entries.get(key);
                if (e == null) continue;
                bw.write(stripTableChars(key));
                bw.write('\t');
                bw.write(e.displayName == null ? "" : e.displayName);
                bw.write('\t');
                // 说明中的换行转义为 \n 两个字符，保证TSV按行解析
                String desc = e.description == null ? "" : e.description.replace("\n", "\\n");
                bw.write(desc);
                bw.newLine();
            }
            bw.flush();
        });
        System.out.println("Wrote name manifest: " + outFile.getAbsolutePath() + " (" + keys.size() + " entries)");
    }

    /**
     * 读取目录下的清单；目录或文件不存在返回 null
     */
    private static Map<String, RunEntry> load(File directory) {
        if (directory == null || !directory.isDirectory()) return null;
        File file = new File(directory, FILE_NAME);
        if (!file.isFile()) return null;

        Map<String, RunEntry> result = new HashMap<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty()) continue;
                String[] parts = line.split("\t", -1);
                if (parts.length < 2) continue;
                String key = parts[0].trim();
                if (key.isEmpty()) continue;
                String display = parts[1];
                String desc = parts.length >= 3 ? parts[2] : "";
                // 还原说明中的换行转义
                desc = desc.replace("\\n", "\n");
                result.put(key, new RunEntry(display, desc));
            }
            return result;
        } catch (IOException e) {
            System.err.println("Failed to read name manifest: " + file.getAbsolutePath() + " - " + e.getMessage());
            return null;
        }
    }

    /** 清洗：去掉会破坏TSV结构的制表符/回车/换行并去首尾空白（用于文件名键与显示名） */
    private static String stripTableChars(String s) {
        if (s == null) return "";
        s = s.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
        return s.trim();
    }
}
