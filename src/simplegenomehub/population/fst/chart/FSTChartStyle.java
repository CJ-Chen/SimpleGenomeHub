package simplegenomehub.population.fst.chart;

import java.awt.*;

public final class FSTChartStyle {
    private FSTChartStyle() {}

    public static final int PADDING = 100;
    public static final int POINT_SIZE = 5;

    // 渲染/对象缓存（减少频繁 new）。字体来自应用统一字体，跟随设置里的字号缩放。
    public static Font axisFont() {
        return simplegenomehub.population.fst.FstUiStyle.axisFont();
    }

    public static Font chromosomeFont() {
        return simplegenomehub.population.fst.FstUiStyle.chromosomeFont();
    }

    public static Font thresholdFont() {
        return simplegenomehub.population.fst.FstUiStyle.thresholdFont();
    }

    public static final Stroke STROKE_AXIS = new BasicStroke(2f);
    public static final Stroke STROKE_LINE = new BasicStroke(1.5f);
    public static final Stroke STROKE_THIN = new BasicStroke(1f);
    public static final Stroke STROKE_HOVER = new BasicStroke(2f);
    public static final Stroke STROKE_SELECTED = new BasicStroke(3f);

    public static final Stroke STROKE_DASH = new BasicStroke(
            1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
            10.0f, new float[]{5.0f}, 0.0f
    );

    public static final Stroke STROKE_POSITION_LINE = new BasicStroke(
            1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
            10.0f, new float[]{5.0f, 3.0f}, 0.0f
    );

    public static final Stroke STROKE_RECT_HL = new BasicStroke(2f);

    public static double clamp01(double v) {
        if (!Double.isFinite(v)) return 0.0;
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    public static double safeScalePerIndex(int plotWidth, int n) {
        if (plotWidth <= 0) return 0.0;
        int denom = Math.max(10, n);
        return (double) plotWidth / (double) denom;
    }
}
