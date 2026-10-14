package simplegenomehub.genomebrowser;

import java.util.ArrayList;
import java.util.List;

/** Shared coordinate state of one genome-browser window (single chromosome). */
public final class BrowserViewState {

    public enum StrandMode { FORWARD, REVERSE, BOTH }

    private final List<Runnable> listeners = new ArrayList<>();
    private String chromosome;
    private int length;
    private double start;
    private double basesPerPixel = 1.0;
    private StrandMode strandMode = StrandMode.BOTH;
    private int translationTable = 1;

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public String chromosome() { return chromosome; }
    public int length() { return length; }
    public double start() { return start; }
    public double basesPerPixel() { return basesPerPixel; }
    public double pixelsPerBase() { return 1.0 / basesPerPixel; }
    public StrandMode strandMode() { return strandMode; }
    public int translationTable() { return translationTable; }

    public void setStrandMode(StrandMode value) {
        strandMode = value == null ? StrandMode.BOTH : value;
        changed();
    }

    public void setTranslationTable(int value) {
        if (!CodonTable.supports(value)) return;
        translationTable = value;
        changed();
    }

    public void setChromosome(String name, int chromosomeLength, int width) {
        chromosome = name;
        length = Math.max(0, chromosomeLength);
        start = 0;
        basesPerPixel = Math.max(1, length) / (double) Math.max(1, width);
        changed();
    }

    public void fit(int width) {
        start = 0;
        basesPerPixel = Math.max(1, length) / (double) Math.max(1, width);
        changed();
    }

    public void jump(double position, int width) {
        start = position - width * basesPerPixel / 2.0;
        normalize(width);
        changed();
    }

    public void showRange(double from, double to, int width) {
        start = Math.min(from, to);
        basesPerPixel = Math.max(0.02, Math.abs(to - from) / Math.max(20.0, width));
        normalize(width);
        changed();
    }

    public void setNormalizedStart(double value, int width) {
        start = value;
        normalize(width);
        changed();
    }

    public void zoomAt(int mouseX, int width, int wheelRotation) {
        double anchor = start + Math.max(0, mouseX) * basesPerPixel;
        double factor = Math.pow(1.28, wheelRotation);
        double maximum = Math.max(0.02, length / 20.0);
        basesPerPixel = Math.max(0.02, Math.min(maximum, basesPerPixel * factor));
        double viewportBases = Math.max(1, width) * basesPerPixel;
        if (viewportBases > length && length > 0) basesPerPixel = length / (double) Math.max(1, width);
        start = anchor - Math.max(0, mouseX) * basesPerPixel;
        normalize(width);
        changed();
    }

    public void normalize(int width) {
        if (length <= 0) {
            start = 0;
            return;
        }
        basesPerPixel = Math.min(basesPerPixel, length / (double) Math.max(1, width));
        basesPerPixel = Math.max(0.02, basesPerPixel);
        double maximum = Math.max(0.0, length - visibleBases(width));
        start = Math.max(0.0, Math.min(maximum, start));
    }

    public double visibleBases(int width) {
        return Math.max(1.0, Math.max(1, width) * basesPerPixel);
    }

    public double end(int width) {
        return Math.min(length, start + visibleBases(width));
    }

    public long normalized(long coordinate) {
        if (length <= 0) return 0;
        return Math.max(0, Math.min(length - 1L, coordinate));
    }

    private void changed() {
        for (Runnable listener : new ArrayList<>(listeners)) listener.run();
    }
}
