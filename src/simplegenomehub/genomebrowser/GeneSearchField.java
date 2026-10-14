package simplegenomehub.genomebrowser;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Gene/label search box with a candidate popup (ported from PlastYes' GenomeGeneSearch). */
public final class GeneSearchField extends JTextField {

    private static final int MAX_CANDIDATES = 60;

    private final AnnotationIndex index;
    private final Consumer<AnnotationIndex.Locus> onSelect;
    private final DefaultListModel<AnnotationIndex.Locus> model = new DefaultListModel<>();
    private final JList<AnnotationIndex.Locus> candidates = new JList<>(model);
    private final JPopupMenu popup = new JPopupMenu();
    private final JScrollPane scroll = new JScrollPane(candidates);
    private boolean suppressPopup;

    public GeneSearchField(AnnotationIndex index, Consumer<AnnotationIndex.Locus> onSelect) {
        super(16);
        this.index = index;
        this.onSelect = onSelect;
        setToolTipText("Type a gene ID or name to search");
        candidates.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        candidates.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int position,
                                                                     boolean selected, boolean focused) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, position, selected, focused);
                label.putClientProperty("html.disable", Boolean.TRUE);
                AnnotationIndex.Locus locus = (AnnotationIndex.Locus) value;
                label.setText(locus.displayName() + "   " + locus.start + "-" + locus.end);
                return label;
            }
        });
        scroll.setPreferredSize(new Dimension(360, 220));
        popup.setLayout(new BorderLayout());
        popup.setFocusable(false);
        popup.setRequestFocusEnabled(false);
        candidates.setFocusable(false);
        candidates.setRequestFocusEnabled(false);
        scroll.setFocusable(false);
        popup.add(scroll, BorderLayout.CENTER);

        getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { update(); }
            @Override public void removeUpdate(DocumentEvent e) { update(); }
            @Override public void changedUpdate(DocumentEvent e) { update(); }
        });
        candidates.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                int position = candidates.locationToIndex(e.getPoint());
                Rectangle bounds = position < 0 ? null : candidates.getCellBounds(position, position);
                if (bounds == null || !bounds.contains(e.getPoint())) return;
                candidates.setSelectedIndex(position);
                select();
            }
        });
        addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { update(); }
            @Override public void focusLost(FocusEvent e) { popup.setVisible(false); }
        });
        bind(KeyEvent.VK_DOWN, "next", () -> move(1));
        bind(KeyEvent.VK_UP, "previous", () -> move(-1));
        bind(KeyEvent.VK_ENTER, "select", this::select);
        bind(KeyEvent.VK_ESCAPE, "dismiss", () -> popup.setVisible(false));
    }

    private void bind(int key, String name, Runnable action) {
        getInputMap().put(KeyStroke.getKeyStroke(key, 0), name);
        getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { action.run(); }
        });
    }

    private void move(int delta) {
        if (model.isEmpty()) return;
        int position = Math.max(0, Math.min(model.size() - 1, candidates.getSelectedIndex() + delta));
        candidates.setSelectedIndex(position);
        candidates.ensureIndexIsVisible(position);
    }

    /** Fills the box from code (for example after a card jump) without popping the list. */
    public void setTextQuietly(String text) {
        suppressPopup = true;
        try {
            setText(text == null ? "" : text);
        } finally {
            suppressPopup = false;
        }
    }

    private void update() {
        if (index == null) return;
        if (suppressPopup) {
            model.clear();
            popup.setVisible(false);
            return;
        }
        String query = getText();
        model.clear();
        if (query == null || query.trim().isEmpty()) {
            popup.setVisible(false);
            return;
        }
        List<AnnotationIndex.Locus> matches = index.search(query);
        int count = Math.min(MAX_CANDIDATES, matches.size());
        for (int i = 0; i < count; i++) model.addElement(matches.get(i));
        if (model.isEmpty()) {
            popup.setVisible(false);
            return;
        }
        candidates.setSelectedIndex(0);
        if (isShowing()) {
            popup.setPopupSize(Math.max(getWidth(), 360), Math.min(220, model.size() * 18 + 8));
            popup.show(this, 0, getHeight());
        }
    }

    private void select() {
        AnnotationIndex.Locus locus = candidates.getSelectedValue();
        popup.setVisible(false);
        if (locus != null && onSelect != null) onSelect.accept(locus);
    }
}
