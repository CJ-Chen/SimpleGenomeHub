package simplegenomehub.population.fst;

import simplegenomehub.config.SimpleGenomeHubConfig;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.util.HashMap;
import java.util.Map;

/**
 * Palette and fonts used by the ported population-genetics windows and charts.
 * <p>
 * The values mirror {@code simplegenomehub.gui.SimpleGenomeHubStyle} so the FST screens match the
 * rest of the application. They are repeated here instead of imported to keep the
 * {@code simplegenomehub.gui} package free of a reverse dependency on this one.
 * <p>
 * <b>Fonts</b> use the very same recipe as {@code SimpleGenomeHubStyle}: logical
 * {@link Font#SANS_SERIF} / {@link Font#MONOSPACED} / {@code Arial} families at the same base sizes,
 * scaled by {@code 1.2 * fontScalePercent}. The percentage comes from the application configuration
 * (which the font slider persists) and can be overridden with the in-progress value while the user
 * drags the slider, so painted text follows the application font scale exactly like widget text.
 */
public final class FstUiStyle {

    // ---- chrome ----
    public static final Color TITLE_BLUE = new Color(29, 71, 127);
    public static final Color CARD_BORDER = new Color(210, 220, 233);
    public static final Color DIALOG_BACKGROUND = new Color(246, 250, 255);
    public static final Color PANEL_BACKGROUND = new Color(252, 254, 255);
    public static final Color PRIMARY_ACTION = new Color(233, 143, 39);
    public static final Color SECONDARY_BUTTON = new Color(241, 246, 253);

    // ---- text ----
    public static final Color TEXT_PRIMARY = new Color(45, 63, 90);
    public static final Color TEXT_SECONDARY = new Color(110, 130, 160);

    // ---- plot furniture ----
    public static final Color GRID = new Color(223, 231, 242);
    public static final Color AXIS_LINE = new Color(180, 199, 224);
    public static final Color PLOT_BACKGROUND = Color.WHITE;
    public static final Color CHROMOSOME_BAR = new Color(126, 166, 214);
    public static final Color CHROMOSOME_BAR_ALT = new Color(233, 143, 39);
    public static final Color SELECTION = new Color(255, 236, 205);
    public static final Color HOVER = new Color(214, 228, 246);

    // ---- cards / tracks ----
    public static final Color CARD_BAR_BACKGROUND = new Color(239, 246, 253);
    public static final Color TAB_BACKGROUND = new Color(222, 235, 250);
    public static final Color TAB_BACKGROUND_HOVER = new Color(214, 228, 246);
    public static final Color TAB_TEXT = new Color(47, 66, 92);
    public static final Color TRACK_BACKGROUND = new Color(252, 254, 255);
    public static final Color TRACK_BORDER = new Color(210, 220, 233);

    // ---- data series ----
    public static final Color SERIES_FIRST = new Color(46, 109, 180);
    public static final Color SERIES_SECOND = new Color(233, 143, 39);
    public static final Color SERIES_PI = new Color(0, 128, 0);
    public static final Color SERIES_TAJIMA_D = new Color(150, 0, 150);
    public static final Color TOP_MARKER = new Color(0, 100, 0);

    // ---- fonts ----
    /** Same base factor as {@code SimpleGenomeHubStyle.FONT_SCALE_FACTOR}. */
    private static final double BASE_FONT_SCALE_FACTOR = 1.2d;
    /** Same range as {@code SimpleGenomeHubFontManager}. */
    private static final int MIN_SCALE_PERCENT = 90;
    private static final int MAX_SCALE_PERCENT = 140;
    private static final int DEFAULT_SCALE_PERCENT = 100;

    private static final String FAMILY_SANS = Font.SANS_SERIF;
    private static final String FAMILY_MONOSPACED = Font.MONOSPACED;
    private static final String FAMILY_ARIAL = "Arial";

    /** Same base size the application uses for body text (SGH's FONT_SANS_PLAIN_12 / Label.font). */
    private static final int BODY_BASE_SIZE = 12;

    private static final Map<String, Font> FONT_CACHE = new HashMap<>();
    private static int cachedScalePercent = Integer.MIN_VALUE;

    /** In-progress slider value; {@code null} means "read the configured percentage". */
    private static volatile Integer scalePercentOverride;

    private FstUiStyle() {
    }

    /**
     * Pins the scale used by the drawn fonts, e.g. while the font slider is being dragged before its
     * value is persisted. Pass {@code null} to read the configured percentage again.
     */
    public static void setScalePercentOverride(Integer scalePercent) {
        scalePercentOverride = scalePercent;
    }

    /** Font of the given logical family, style and base size, scaled like the application fonts. */
    public static Font scaledFont(String family, int style, int baseSize) {
        int percent = effectiveScalePercent();
        String key = family + '|' + style + '|' + baseSize + '|' + percent;
        synchronized (FONT_CACHE) {
            if (percent != cachedScalePercent) {
                FONT_CACHE.clear();
                cachedScalePercent = percent;
            }
            Font cached = FONT_CACHE.get(key);
            if (cached == null) {
                cached = new Font(family, style, scaledSize(baseSize, percent));
                FONT_CACHE.put(key, cached);
            }
            return cached;
        }
    }

    /**
     * Same arithmetic as {@code SimpleGenomeHubStyle}: the scale factor is multiplied in first, then
     * rounded. Doing it in another order shifts some sizes by one pixel (9pt at 125% for instance).
     */
    private static int scaledSize(int baseSize, int percent) {
        double factor = BASE_FONT_SCALE_FACTOR * (percent / 100.0d);
        return Math.max(1, (int) Math.round(baseSize * factor));
    }

    /** Sans-serif font at the given base size (application body font family). */
    public static Font sans(int baseSize, int style) {
        return scaledFont(FAMILY_SANS, style, baseSize);
    }

    /** Monospaced font at the given base size. */
    public static Font monospaced(int baseSize, int style) {
        return scaledFont(FAMILY_MONOSPACED, style, baseSize);
    }

    /** Small Arial label, used on dense canvases like the application's own charts. */
    public static Font arial(int baseSize, int style) {
        return scaledFont(FAMILY_ARIAL, style, baseSize);
    }

    /**
     * Font derived from the application body font: {@code baseSize} 12 plus the given delta, so the
     * long-standing {@code font(0, PLAIN)} call sites keep the application's body size.
     */
    public static Font font(int sizeDelta, int style) {
        return sans(Math.max(1, BODY_BASE_SIZE + sizeDelta), style);
    }

    /** Bold version of the given font (same size), following the application font scale. */
    public static Font bold(Font baseFont, float baseSize) {
        return deriveScaled(baseFont, Font.BOLD, baseSize);
    }

    /** Italic version of the given font (same size), following the application font scale. */
    public static Font italic(Font baseFont, float baseSize) {
        return deriveScaled(baseFont, Font.ITALIC, baseSize);
    }

    /** Keeps a font's family/style but resizes it to the given base size, scaled like the app fonts. */
    public static Font resize(Font baseFont, float baseSize) {
        return deriveScaled(baseFont, baseFont == null ? Font.PLAIN : baseFont.getStyle(), baseSize);
    }

    private static Font deriveScaled(Font baseFont, int style, float baseSize) {
        String family = baseFont != null ? baseFont.getFamily() : FAMILY_SANS;
        int percent = effectiveScalePercent();
        double factor = BASE_FONT_SCALE_FACTOR * (percent / 100.0d);
        int size = Math.max(1, (int) Math.round(baseSize * factor));
        return new Font(family, style, size);
    }

    private static int effectiveScalePercent() {
        Integer override = scalePercentOverride;
        if (override != null) {
            return clampScalePercent(override);
        }
        int percent = DEFAULT_SCALE_PERCENT;
        try {
            percent = SimpleGenomeHubConfig.getInstance().getUiFontScalePercent();
        } catch (RuntimeException ignored) {
            // Unreadable configuration: fall back to the default percentage.
        }
        return clampScalePercent(percent);
    }

    private static int clampScalePercent(int percent) {
        if (percent <= 0) {
            return DEFAULT_SCALE_PERCENT;
        }
        return Math.max(MIN_SCALE_PERCENT, Math.min(MAX_SCALE_PERCENT, percent));
    }

    // ---- the named sizes the application uses ----

    /** Axis tick labels. */
    public static Font axisFont() {
        return sans(BODY_BASE_SIZE, Font.PLAIN);
    }

    /** Chromosome ruler labels. */
    public static Font chromosomeFont() {
        return sans(11, Font.PLAIN);
    }

    /** Base letters drawn at high zoom in the genome browser. */
    public static Font sequenceFont() {
        return monospaced(25, Font.PLAIN);
    }

    /** Threshold labels drawn on a track. */
    public static Font thresholdFont() {
        return sans(BODY_BASE_SIZE, Font.BOLD);
    }

    // ---- toolbar sections ----

    /**
     * A titled strip of toolbar widgets that always reports a single-row preferred size.
     * <p>
     * A plain {@link FlowLayout} panel computes its preferred size before it knows how wide it will
     * actually become. Once the toolbar gets tight the widgets are laid out in a second row that
     * the strip's height does not account for, so the bottom of that row (buttons such as
     * {@code Refresh}) is clipped away. Reporting the single-row size, and refusing to shrink below
     * it, keeps every widget of the strip fully visible.
     */
    public static JPanel toolbarSection(String title) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2)) {
            @Override
            public Dimension getPreferredSize() {
                return singleRowSize(this);
            }

            @Override
            public Dimension getMinimumSize() {
                return getPreferredSize();
            }

            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), title, TitledBorder.LEFT, TitledBorder.TOP,
            font(0, Font.PLAIN)));
        return panel;
    }

    /** Preferred size of a FlowLayout strip laid out as exactly one row. */
    private static Dimension singleRowSize(JPanel panel) {
        Insets insets = panel.getInsets();
        FlowLayout layout = (FlowLayout) panel.getLayout();
        int width = insets.left + insets.right + layout.getHgap() * 2;
        int contentHeight = 0;
        for (Component child : panel.getComponents()) {
            if (!child.isVisible()) {
                continue;
            }
            Dimension size = child.getPreferredSize();
            width += size.width + layout.getHgap();
            contentHeight = Math.max(contentHeight, size.height);
        }
        // The row height has to be added on top of the border insets (the title bar), otherwise the
        // strip reports a height that only fits the title and the widgets below it get clipped.
        int height = insets.top + insets.bottom + layout.getVgap() * 2 + contentHeight;
        return new Dimension(width, height);
    }
}
