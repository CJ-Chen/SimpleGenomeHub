package simplegenomehub.population.fst.chart;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class FSTDisplayCache {
    private final List<FSTWindow> windows;

    private String cacheChrom = null;
    private int displayCount = -1;

    // displayIndex -> originalIndex
    private int[] displayToOriginal = null;
    // originalIndex -> displayIndex (not shown => -1)
    private int[] originalToDisplay = null;
    // displayIndex -> startPos（用于 position->x 插值）
    private int[] displayStartPos = null;

    public FSTDisplayCache(List<FSTWindow> windows) {
        this.windows = windows;
    }

    public void ensure(String selectedChromosome) {
        if (selectedChromosome == null) {
            cacheChrom = null;
            displayCount = (windows == null) ? 0 : windows.size();
            displayToOriginal = null;
            originalToDisplay = null;
            displayStartPos = null;
            return;
        }

        if (selectedChromosome.equals(cacheChrom) && displayToOriginal != null && displayCount >= 0) {
            return;
        }

        cacheChrom = selectedChromosome;

        int n = (windows == null) ? 0 : windows.size();
        ArrayList<Integer> tmp = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (selectedChromosome.equals(windows.get(i).getChrom())) tmp.add(i);
        }

        int m = tmp.size();
        displayCount = m;

        displayToOriginal = new int[m];
        displayStartPos = new int[m];
        originalToDisplay = new int[n];
        Arrays.fill(originalToDisplay, -1);

        for (int di = 0; di < m; di++) {
            int oi = tmp.get(di);
            displayToOriginal[di] = oi;
            originalToDisplay[oi] = di;
            displayStartPos[di] = windows.get(oi).getStartPos();
        }
    }

    public int getDisplayCount(String selectedChromosome) {
        ensure(selectedChromosome);
        return (selectedChromosome == null) ? windows.size() : displayCount;
    }

    public int displayIndexToOriginalIndex(String selectedChromosome, int displayIdx) {
        ensure(selectedChromosome);
        if (displayIdx < 0) return -1;

        if (selectedChromosome == null) {
            return (displayIdx < windows.size()) ? displayIdx : -1;
        }
        if (displayToOriginal == null || displayIdx >= displayToOriginal.length) return -1;
        return displayToOriginal[displayIdx];
    }

    public int[] getDisplayStartPosUnsafe() {
        return displayStartPos;
    }

    public int getDisplayCountUnsafe() {
        return displayCount;
    }

}
