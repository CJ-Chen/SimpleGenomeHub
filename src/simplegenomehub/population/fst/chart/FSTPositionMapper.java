package simplegenomehub.population.fst.chart;

import java.util.List;

public final class FSTPositionMapper {
    private FSTPositionMapper() {}

    /**
     * 根据染色体号和位置计算 x 坐标（plot 内偏移）
     * 逻辑与你原来一致：按 startPos 插值 + floor 二分
     */
    public static double calculateXFromPosition(
            List<FSTWindow> windows,
            String selectedChromosome,
            List<FSTChromosomeRange.ChromosomeRange> chromosomeRangesCache,
            FSTDisplayCache displayCache,
            int componentWidth,
            int preferredWidth,
            String chromosome,
            int position
    ) {
        if (windows == null || windows.isEmpty() || chromosome == null) return -1;

        // 若选中了染色体，且不匹配，直接不可见
        if (selectedChromosome != null && !chromosome.equals(selectedChromosome)) return -1;

        displayCache.ensure(selectedChromosome);

        int width = Math.max(componentWidth, preferredWidth);
        int plotWidth = width - 2 * FSTChartStyle.PADDING;

        int nDisplay = (selectedChromosome == null) ? windows.size() : displayCache.getDisplayCountUnsafe();
        if (nDisplay <= 0 || plotWidth <= 0) return -1;

        double scalePerIndex = FSTChartStyle.safeScalePerIndex(plotWidth, nDisplay);
        if (scalePerIndex <= 0) return -1;

        if (selectedChromosome != null) {
            int[] displayStartPos = displayCache.getDisplayStartPosUnsafe();
            if (displayStartPos == null || displayStartPos.length == 0) return -1;

            int idx = findFloorByStart(displayStartPos, position);
            if (idx < 0) return -1;

            if (idx >= displayStartPos.length - 1) {
                return idx * scalePerIndex;
            }

            int s0 = displayStartPos[idx];
            int s1 = displayStartPos[idx + 1];
            int step = s1 - s0;
            if (step <= 0) return idx * scalePerIndex;

            double frac = (double) (position - s0) / (double) step;
            if (frac < 0) frac = 0;
            if (frac > 1) frac = 1;
            return (idx + frac) * scalePerIndex;
        }

        // selectedChromosome == null：用 chromosomeRangesCache 找范围
        FSTChromosomeRange.ChromosomeRange range = null;
        for (FSTChromosomeRange.ChromosomeRange r : chromosomeRangesCache) {
            if (chromosome.equals(r.chrom)) {
                range = r;
                break;
            }
        }
        if (range == null) return -1;

        int lo = range.startIdx;
        int hi = range.endIdx;
        if (lo < 0 || hi < lo) return -1;

        int idx = findFloorByStartInWindows(windows, lo, hi, position);
        if (idx < 0) return -1;

        if (idx >= hi) {
            return idx * scalePerIndex;
        }

        int s0 = windows.get(idx).getStartPos();
        int s1 = windows.get(idx + 1).getStartPos();
        int step = s1 - s0;
        if (step <= 0) return idx * scalePerIndex;

        double frac = (double) (position - s0) / (double) step;
        if (frac < 0) frac = 0;
        if (frac > 1) frac = 1;

        return (idx + frac) * scalePerIndex;
    }

    /** 返回 start[i] <= pos 的最大 i；若 pos 小于最小 start，则返回 0 */
    private static int findFloorByStart(int[] starts, int pos) {
        int n = starts.length;
        if (n == 0) return -1;
        if (pos <= starts[0]) return 0;
        if (pos >= starts[n - 1]) return n - 1;

        int l = 0, r = n - 1;
        while (l <= r) {
            int m = (l + r) >>> 1;
            int v = starts[m];
            if (v == pos) return m;
            if (v < pos) l = m + 1;
            else r = m - 1;
        }
        return r;
    }

    /** 在 windows[lo..hi]（同染色体、startPos递增）里返回 startPos <= pos 的最大 index；若 pos 小于 lo 的 startPos 则返回 lo */
    private static int findFloorByStartInWindows(List<FSTWindow> windows, int lo, int hi, int pos) {
        int sLo = windows.get(lo).getStartPos();
        if (pos <= sLo) return lo;
        int sHi = windows.get(hi).getStartPos();
        if (pos >= sHi) return hi;

        int l = lo, r = hi;
        while (l <= r) {
            int m = (l + r) >>> 1;
            int v = windows.get(m).getStartPos();
            if (v == pos) return m;
            if (v < pos) l = m + 1;
            else r = m - 1;
        }
        return r;
    }
}
