package simplegenomehub.genomebrowser;

import java.awt.Color;

/** PlastYes-Kuai palette subset used by the standalone genome browser. */
public final class BrowserPalette {

    public static final Color MENU_TEXT = new Color(17, 97, 106);
    public static final Color GRID = new Color(232, 236, 239);
    public static final Color BORDER = new Color(213, 225, 228);
    public static final Color MUTED = new Color(112, 131, 139);
    public static final Color TEXT = new Color(38, 62, 71);
    public static final Color GENE_BAR = new Color(151, 160, 164);
    public static final Color FORWARD = new Color(239, 192, 65);
    public static final Color REVERSE = new Color(84, 158, 202);
    public static final Color START_CODON = new Color(80, 174, 103);
    public static final Color STOP_CODON = new Color(235, 145, 148);
    public static final Color SELECTION = new Color(30, 34, 36);
    public static final Color AA_FORWARD = new Color(231, 235, 238);
    public static final Color AA_REVERSE = new Color(219, 231, 244);
    /** 选中基因的 CDS 在氨基酸行上的深灰底（PlastYes 的 SELECTION）。 */
    public static final Color AA_FORWARD_SELECTED = new Color(191, 191, 191);
    /** 灰底之上叠的密码子小格（比普通氨基酸行略亮）。 */
    public static final Color AA_FORWARD_SELECTED_CELL = new Color(232, 236, 239);
    public static final Color AA_REVERSE_SELECTED_CELL = new Color(218, 231, 245);
    /** 选中 CDS 里氨基酸字母的颜色。 */
    public static final Color AA_SELECTED_TEXT = new Color(45, 55, 60);
    public static final Color BASE_ROW = new Color(228, 234, 236);
    public static final Color MARGIN = new Color(246, 248, 249);
    public static final Color NAVIGATOR = new Color(240, 244, 246);
    public static final Color NAVIGATOR_THUMB = new Color(118, 159, 175);
    public static final Color NAVIGATOR_TRACK = new Color(212, 222, 225);
    public static final Color RULER_TEXT = new Color(79, 101, 109);
    public static final Color LABEL_TEXT = new Color(41, 56, 63);
    public static final Color CDS_BOX = new Color(91, 116, 125);
    /** Light green band marking the FST window selected in the result browser. */
    public static final Color HIGHLIGHT_REGION = new Color(198, 232, 198);
    /** Small clickable exon handles shown once the introns are wide enough. */
    public static final Color HANDLE = new Color(75, 94, 104);

    private BrowserPalette() {
    }

    public static Color baseColor(char base) {
        switch (Character.toUpperCase(base)) {
            case 'A': return new Color(36, 132, 84);
            case 'C': return new Color(49, 105, 180);
            case 'G': return new Color(183, 121, 22);
            case 'T': case 'U': return new Color(185, 61, 60);
            default: return Color.GRAY;
        }
    }
}
