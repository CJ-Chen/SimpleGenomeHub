package simplegenomehub.genomebrowser;

import simplegenomehub.gui.GeneSetCardsUi;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.awt.Window;
import java.io.File;
import java.util.List;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

/**
 * Small non-modal window that lists one saved set as a wall of cards.
 *
 * <p>Only the selected set is read, and cards are plain buttons so a set with thousands of
 * entries still opens instantly; resolving a card happens when it is clicked. The drop-down
 * at the top switches between the sets of the current genome without closing the window.</p>
 */
public final class SetCardDialog extends JDialog {

    /** One entry of a set shown as a card. */
    public static final class Card {
        final String label;
        final String tooltip;
        final Function<Card, Boolean> action;
        /** Stored note colour of the entry; null keeps the default card look. */
        final java.awt.Color background;

        public Card(String label, String tooltip, Function<Card, Boolean> action) {
            this(label, tooltip, action, null);
        }

        public Card(String label, String tooltip, Function<Card, Boolean> action, java.awt.Color background) {
            this.label = label;
            this.tooltip = tooltip;
            this.action = action;
            this.background = background;
        }
    }

    /** One selectable set in the drop-down. */
    public static final class Option {
        final String displayName;
        final File file;

        public Option(String displayName, File file) {
            this.displayName = displayName;
            this.file = file;
        }

        @Override public String toString() {
            return displayName;
        }
    }

    /** Builds the cards of one set; called each time the user switches sets. */
    public interface CardLoader {
        List<Card> load(File setFile);
    }

    /** Card cell size, including the gap between cards. */
    private static final int CELL_WIDTH = GeneSetCardsUi.CARD_WIDTH + 6;
    private static final int CELL_HEIGHT = GeneSetCardsUi.CARD_HEIGHT + 6;

    private final JComboBox<Option> setCombo = new JComboBox<>();
    private final CardWall cardPanel = new CardWall();
    private final JLabel countLabel = new JLabel(" ");
    private final JLabel statusLabel = new JLabel(" ");
    private final CardLoader loader;

    public SetCardDialog(Window owner, String title, List<Option> options, CardLoader loader) {
        super(owner, title, ModalityType.MODELESS);
        this.loader = loader;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(6, 6));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        for (Option option : options) setCombo.addItem(option);
        setCombo.addActionListener(e -> showSelectedSet());

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        header.add(new JLabel("Set:"));
        header.add(setCombo);
        header.add(countLabel);
        add(header, BorderLayout.NORTH);

        cardPanel.setBackground(java.awt.Color.WHITE);
        JScrollPane scroll = new JScrollPane(cardPanel);
        scroll.setBorder(BorderFactory.createLineBorder(new java.awt.Color(213, 225, 228)));
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        // The wall re-wraps when the window is resized, so its height must be recomputed.
        scroll.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                cardPanel.revalidate();
            }
        });
        add(scroll, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        setSize(new Dimension(560, 420));
        setLocationRelativeTo(owner);
        showSelectedSet();
    }

    /** Rebuilds the card wall for the set currently chosen in the drop-down. */
    private void showSelectedSet() {
        Option option = (Option) setCombo.getSelectedItem();
        cardPanel.removeAll();
        statusLabel.setText(" ");
        if (option == null) {
            countLabel.setText(" ");
        } else {
            List<Card> cards = loader.load(option.file);
            for (Card card : cards) cardPanel.add(createCardButton(card));
            countLabel.setText(cards.size() + (cards.size() == 1 ? " item" : " items"));
        }
        cardPanel.revalidate();
        cardPanel.repaint();
    }

    private javax.swing.JButton createCardButton(Card card) {
        javax.swing.JButton button = GeneSetCardsUi.cardButton(card.label, card.tooltip, card.background);
        button.addActionListener(e -> activate(button, card));
        return button;
    }

    /**
     * Card wall: lays the cards out in a grid and reports the height of all rows.
     *
     * <p>A plain FlowLayout is not enough here - its preferred height assumes one single
     * row, so inside a scroll pane the wrapped rows would be cut off. This panel tracks the
     * viewport width and answers with the real wrapped height, which is what makes the
     * scroll bar usable.</p>
     */
    private static final class CardWall extends JPanel implements javax.swing.Scrollable {

        CardWall() {
            setLayout(null);
        }

        private int columns() {
            int width = Math.max(1, getWidth());
            return Math.max(1, width / CELL_WIDTH);
        }

        @Override public void doLayout() {
            int columns = columns();
            for (int i = 0; i < getComponentCount(); i++) {
                int column = i % columns;
                int row = i / columns;
                getComponent(i).setBounds(column * CELL_WIDTH, row * CELL_HEIGHT,
                    GeneSetCardsUi.CARD_WIDTH, GeneSetCardsUi.CARD_HEIGHT);
            }
        }

        @Override public Dimension getPreferredSize() {
            int columns = columns();
            int rows = (getComponentCount() + columns - 1) / columns;
            return new Dimension(columns * CELL_WIDTH, Math.max(CELL_HEIGHT, rows * CELL_HEIGHT));
        }

        @Override public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return CELL_HEIGHT;
        }

        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(CELL_HEIGHT, visible.height - CELL_HEIGHT);
        }

        @Override public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private void activate(javax.swing.JButton button, Card card) {
        boolean done = false;
        try {
            done = card.action != null && Boolean.TRUE.equals(card.action.apply(card));
        } catch (RuntimeException ex) {
            statusLabel.setText("Failed: " + ex.getMessage());
        }
        if (done) {
            statusLabel.setText(" ");
            GeneSetCardsUi.markSelected(cardPanel, button);
        } else {
            GeneSetCardsUi.markNotFound(button);
            if (" ".equals(statusLabel.getText())) {
                statusLabel.setText("Not found in this genome: " + card.label);
            }
        }
    }

}
