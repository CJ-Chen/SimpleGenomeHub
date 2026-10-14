package simplegenomehub.gui;

import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;

/**
 * The "card" look used by the genome browser's set windows: a soft button per entry,
 * plus the two states a card can be in after it is clicked.
 */
public final class GeneSetCardsUi {

    /** Fixed card size, so the wall can compute how many fit per row. */
    public static final int CARD_WIDTH = 156;
    public static final int CARD_HEIGHT = 32;

    private static final Color SELECTED_BORDER = new Color(27, 115, 119);
    private static final Color NOT_FOUND_BORDER = new Color(190, 36, 40);
    private static final Color NOT_FOUND_TEXT = new Color(160, 34, 38);
    private static final Color DEFAULT_TEXT = new Color(38, 62, 71);

    private GeneSetCardsUi() {
    }

    public static JButton cardButton(String label, String tooltip) {
        return cardButton(label, tooltip, null);
    }

    /** A card painted in the stored note colour of its entry; {@code background} may be null. */
    public static JButton cardButton(String label, String tooltip, Color background) {
        Dimension size = new Dimension(CARD_WIDTH, CARD_HEIGHT);
        JButton button = background == null
            ? SimpleGenomeHubUi.createSoftButton(shorten(label), size)
            : SimpleGenomeHubUi.createSoftButton(shorten(label), size,
                SetNoteUiSupport.lighter(background, 0.55f),
                SetNoteUiSupport.lighter(background, 0.10f),
                SetNoteUiSupport.darker(background, 0.28f));
        button.setToolTipText(tooltip == null || tooltip.isEmpty() ? label : tooltip);
        button.setForeground(background != null && SetNoteUiSupport.isDark(background) ? Color.WHITE : DEFAULT_TEXT);
        button.setFont(SimpleGenomeHubStyle.FONT_SANS_PLAIN_12);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    /** Marks the card the user just jumped to, and clears the mark from its siblings. */
    public static void markSelected(Container parent, JComponent selected) {
        for (java.awt.Component child : parent.getComponents()) {
            if (child instanceof JButton) {
                JButton button = (JButton) child;
                button.setForeground(DEFAULT_TEXT);
                button.setBorder(defaultBorder());
            }
        }
        selected.setForeground(SELECTED_BORDER);
        selected.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(SELECTED_BORDER, 1),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)));
    }

    /** Marks a card whose entry is not present in the genome being browsed. */
    public static void markNotFound(JComponent card) {
        card.setForeground(NOT_FOUND_TEXT);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(NOT_FOUND_BORDER, 1),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)));
    }

    private static javax.swing.border.Border defaultBorder() {
        return BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(SimpleGenomeHubStyle.SOFT_BUTTON_BORDER, 1),
            BorderFactory.createEmptyBorder(4, 10, 4, 10));
    }

    /** Keeps long ids readable on a fixed-width card; the tooltip carries the full text. */
    private static String shorten(String label) {
        if (label == null) {
            return "";
        }
        return label.length() <= 22 ? label : label.substring(0, 20) + "...";
    }
}
