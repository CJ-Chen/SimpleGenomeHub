package simplegenomehub.population.fst;

import java.io.File;

/**
 * Receives the result folder of a finished FST run.
 * <p>
 * This replaces FSTdraw's direct dependency on its chart window: the calculation side only
 * reports where the results were written, and SimpleGenomeHub decides what to do with them.
 * The chart-based viewer is ported in a later step and will be plugged in behind this interface.
 */
public interface FstResultHandler {

    /**
     * Called on the Swing event thread after a run has written its results.
     *
     * @param parameterDirectory the {@code w<window>_s<step>} folder holding the result files
     */
    void onResultsReady(File parameterDirectory);
}
