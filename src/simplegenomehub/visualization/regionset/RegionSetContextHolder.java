package simplegenomehub.visualization.regionset;

/**
 * Holds the Region Set context of the currently open browser window.
 * The chart framework already exposes a single active result window
 * (see {@code FSTVisualizationManager#closePreviousResultWindow()}), so a
 * process-wide holder mirrors that lifecycle and keeps the plumbing small.
 */
public final class RegionSetContextHolder {

    private static volatile RegionSetContext current;

    private RegionSetContextHolder() {
    }

    public static void set(RegionSetContext context) {
        current = context;
    }

    public static RegionSetContext get() {
        return current;
    }

    public static void clear() {
        current = null;
    }
}
