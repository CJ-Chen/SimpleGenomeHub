package simplegenomehub.population.fst.chart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FSTChromosomeRange {
    private FSTChromosomeRange() {}

    public static final class ChromosomeRange {
        public final String chrom;
        public final int startIdx; // original index
        public final int endIdx;   // original index

        public ChromosomeRange(String chrom, int startIdx, int endIdx) {
            this.chrom = chrom;
            this.startIdx = startIdx;
            this.endIdx = endIdx;
        }
    }

    /** 基于 original windows，只构建一次 */
    public static List<ChromosomeRange> buildChromosomeRangesOnce(List<FSTWindow> windows) {
        ArrayList<ChromosomeRange> ranges = new ArrayList<>();
        if (windows == null || windows.isEmpty()) return ranges;

        String cur = null;
        int start = -1;
        for (int i = 0; i < windows.size(); i++) {
            String chrom = windows.get(i).getChrom();
            if (cur == null) {
                cur = chrom;
                start = i;
            } else if (!cur.equals(chrom)) {
                ranges.add(new ChromosomeRange(cur, start, i - 1));
                cur = chrom;
                start = i;
            }
        }
        ranges.add(new ChromosomeRange(cur, start, windows.size() - 1));
        return Collections.unmodifiableList(ranges);
    }

    /**
     * 按染色体名称末尾的数字排序窗口列表（稳定排序）。
     * <p>
     * 规则：
     * <ul>
     *   <li>名称末尾带数字的染色体（如 Chr1、Chr01、Chr10）按数字由小到大排列；
     *       数字相同（如 Chr01 与 Chr1）再按名称比较，保证结果确定；</li>
     *   <li>名称末尾没有数字的染色体（如 chrX、chrUn）排在所有带数字染色体的后面，
     *       并保持它们彼此在原始列表中的相对顺序；</li>
     *   <li>排序只调整染色体的先后顺序，不改变同一条染色体内部窗口的相对顺序。</li>
     * </ul>
     * 若列表为 null 或元素不足两个，则不执行任何操作。
     *
     * @param windows 窗口列表（就地重排）
     */
    public static void sortWindowsByChromosomeNumber(List<FSTWindow> windows) {
        if (windows == null || windows.size() < 2) return;
        final Map<String, Long> numberCache = new HashMap<>();
        windows.sort((a, b) -> compareChromByTrailingNumber(a.getChrom(), b.getChrom(), numberCache));
    }

    /**
     * 染色体排序比较：带末尾数字的在前（按数字升序），不带数字的在后（保持原相对顺序）
     */
    private static int compareChromByTrailingNumber(String ca, String cb,
                                                    Map<String, Long> numberCache) {
        Long na = trailingNumber(ca, numberCache);
        Long nb = trailingNumber(cb, numberCache);
        if (na == null && nb == null) return 0;   // 都没有数字：稳定排序保持原相对顺序
        if (na == null) return 1;                 // 无数字的排在带数字的后面
        if (nb == null) return -1;
        int c = Long.compare(na, nb);
        if (c != 0) return c;
        return ca.compareTo(cb);                  // 数字相同：按完整名称定序（如 Chr01 vs Chr1）
    }

    /**
     * 提取染色体名称末尾的连续数字（如 "Chr01" -> 1L，"Chr10" -> 10L）；
     * 名称不以数字结尾或解析失败时返回 null。结果缓存在 numberCache 中。
     */
    private static Long trailingNumber(String name, Map<String, Long> numberCache) {
        if (name == null) return null;
        Long cached = numberCache.get(name);
        if (cached != null) return cached;
        int i = name.length();
        while (i > 0 && Character.isDigit(name.charAt(i - 1))) i--;
        if (i == name.length()) return null;   // 末尾没有数字
        Long value;
        try {
            value = Long.parseLong(name.substring(i));
        } catch (NumberFormatException e) {
            return null;                       // 数字过长等异常情况：视为无数字
        }
        numberCache.put(name, value);
        return value;
    }
}
