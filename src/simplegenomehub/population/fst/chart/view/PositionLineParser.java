package simplegenomehub.population.fst.chart.view;

import simplegenomehub.genomebrowser.GenomeBrowserPanel;
import simplegenomehub.population.fst.chart.FSTTrackChartPanel;

/** Pushes the embedded genome browser's visible range onto the FST chart's red position line. */
public class PositionLineParser {

    public static void updatePositionLine(FSTTrackChartPanel panel, GenomeBrowserPanel browser) {
        if (panel == null || browser == null || !browser.isReady()) {
            return;
        }
        String chromosome = browser.currentChromosome();
        int[] range = browser.visibleRange();
        if (chromosome == null || range == null) {
            panel.setPositionLineInfo(null, -1);
            return;
        }
        panel.setPositionLineInfo(chromosome, range[0], range[1]);
    }
}
