package simplegenomehub.gui;

import simplegenomehub.util.fileio.SetNoteSupport;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-entry note editor of one set: one card per id, a note field and a colour button per
 * card. Nothing is validated here - the caller validates the finished set as before.
 */
final class SetNoteEditorDialog extends JDialog {

    private static final Color TEXT_COLOR = new Color(45, 63, 90);
    private static final Color HINT_COLOR = new Color(120, 128, 140);
    private static final Color ROW_BORDER = new Color(213, 225, 238);
    private static final Color ROW_BACKGROUND = new Color(247, 250, 254);
    private static final Color FIELD_BORDER = new Color(196, 210, 226);
    private static final Dimension COLOR_BUTTON_SIZE = new Dimension(132, 34);
    private static final int ID_LABEL_WIDTH = 220;
    private static final int ROW_HEIGHT = 46;

    /** Card colours offered by the drop-down palette. */
    private static final Color[] PALETTE = {
        new Color(255, 235, 156),
        new Color(255, 214, 165),
        new Color(255, 190, 190),
        new Color(238, 187, 232),
        new Color(212, 200, 245),
        new Color(190, 220, 250),
        new Color(183, 235, 240),
        new Color(196, 240, 205),
        new Color(224, 240, 180),
        new Color(246, 222, 198),
        new Color(222, 231, 242),
        new Color(232, 232, 236)
    };

    private final Map<String, SetNoteSupport.Note> notes = new LinkedHashMap<>();
    private boolean confirmed;

    SetNoteEditorDialog(Window parent, String title, List<String> keys,
                        Map<String, SetNoteSupport.Note> initialNotes) {
        super(parent, title, ModalityType.APPLICATION_MODAL);
        if (initialNotes != null) {
            notes.putAll(initialNotes);
        }
        buildUi(keys);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(new Dimension(760, 560));
        setMinimumSize(new Dimension(600, 380));
        setLocationRelativeTo(parent);
    }

    /** The edited notes, keyed by entry id. */
    Map<String, SetNoteSupport.Note> getNotes() {
        return notes;
    }

    boolean isConfirmed() {
        return confirmed;
    }

    private void buildUi(List<String> keys) {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        root.setBackground(SimpleGenomeHubUi.DIALOG_BACKGROUND);

        int entryCount = 0;
        JPanel rows = new JPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setBackground(Color.WHITE);
        rows.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        if (keys != null) {
            for (String key : keys) {
                if (key == null || key.trim().isEmpty()) {
                    continue;
                }
                String trimmedKey = key.trim();
                notes.putIfAbsent(trimmedKey, new SetNoteSupport.Note("", null));
                rows.add(createRow(trimmedKey));
                rows.add(Box.createVerticalStrut(6));
                entryCount++;
            }
        }

        JLabel hintLabel = new JLabel("<html>Each entry gets a card: type its note and pick the card colour.<br>"
            + "The notes are stored next to the set file. Ids are not checked here - the set itself is "
            + "validated when it is created or saved.</html>");
        hintLabel.setFont(SimpleGenomeHubStyle.FONT_SANS_PLAIN_12);
        hintLabel.setForeground(HINT_COLOR);

        JLabel countLabel = new JLabel(entryCount + (entryCount == 1 ? " entry" : " entries"));
        countLabel.setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_12);
        countLabel.setForeground(TEXT_COLOR);
        countLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setOpaque(false);
        header.add(hintLabel, BorderLayout.CENTER);
        header.add(countLabel, BorderLayout.SOUTH);

        JScrollPane scrollPane = new JScrollPane(rows);
        scrollPane.setBorder(BorderFactory.createLineBorder(ROW_BORDER, 1));
        scrollPane.getViewport().setBackground(Color.WHITE);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        ModernScrollBarStyle.applyTo(scrollPane);

        JButton okButton = new JButton("OK");
        okButton.setPreferredSize(new Dimension(110, 36));
        okButton.setBackground(SimpleGenomeHubStyle.DIALOG_PRIMARY_BUTTON);
        okButton.setForeground(SimpleGenomeHubStyle.DIALOG_PRIMARY_BUTTON_TEXT);
        okButton.setFocusPainted(false);
        okButton.addActionListener(e -> {
            confirmed = true;
            dispose();
        });

        JButton cancelButton = new JButton("Cancel");
        cancelButton.setPreferredSize(new Dimension(110, 36));
        cancelButton.setBackground(SimpleGenomeHubStyle.DIALOG_SECONDARY_BUTTON);
        cancelButton.setForeground(SimpleGenomeHubStyle.DIALOG_SECONDARY_BUTTON_TEXT);
        cancelButton.setFocusPainted(false);
        cancelButton.addActionListener(e -> dispose());

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        actionPanel.setOpaque(false);
        actionPanel.add(okButton);
        actionPanel.add(cancelButton);

        root.add(header, BorderLayout.NORTH);
        root.add(scrollPane, BorderLayout.CENTER);
        root.add(actionPanel, BorderLayout.SOUTH);
        setContentPane(root);

        getRootPane().registerKeyboardAction(e -> dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private JPanel createRow(String key) {
        NoteRow row = new NoteRow(key);
        return row.getPanel();
    }

    /** Card of one entry: id, note field and colour button. */
    private final class NoteRow {

        private final String key;
        private final JPanel panel;
        private final JTextField noteField;
        private final JButton colorButton;

        private NoteRow(String key) {
            this.key = key;

            panel = new JPanel(new BorderLayout(8, 0));
            panel.setOpaque(true);
            panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ROW_BORDER, 1, true),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)
            ));
            panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT));

            JLabel idLabel = new JLabel(shortenId(key));
            idLabel.setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_12);
            idLabel.setForeground(TEXT_COLOR);
            idLabel.setToolTipText(key);
            idLabel.setPreferredSize(new Dimension(ID_LABEL_WIDTH, 24));

            noteField = new JTextField();
            noteField.setFont(SimpleGenomeHubStyle.FONT_SANS_PLAIN_12);
            noteField.setForeground(TEXT_COLOR);
            noteField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(FIELD_BORDER, 1),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)
            ));
            SetNoteSupport.Note note = notes.get(key);
            noteField.setText(note == null ? "" : note.getText());
            noteField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    storeNoteText();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    storeNoteText();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    storeNoteText();
                }
            });

            colorButton = new JButton("Colour");
            colorButton.setPreferredSize(COLOR_BUTTON_SIZE);
            colorButton.setMinimumSize(COLOR_BUTTON_SIZE);
            colorButton.setMaximumSize(COLOR_BUTTON_SIZE);
            colorButton.setFont(SimpleGenomeHubStyle.FONT_SANS_PLAIN_11);
            colorButton.setForeground(TEXT_COLOR);
            colorButton.setFocusPainted(false);
            colorButton.setOpaque(true);
            colorButton.setContentAreaFilled(true);
            colorButton.setBorder(BorderFactory.createLineBorder(FIELD_BORDER, 1));
            colorButton.setToolTipText("Pick the card colour");
            colorButton.addActionListener(e -> showPalette());

            panel.add(idLabel, BorderLayout.WEST);
            panel.add(noteField, BorderLayout.CENTER);
            panel.add(colorButton, BorderLayout.EAST);
            refreshColor();
        }

        private JPanel getPanel() {
            return panel;
        }

        private void storeNoteText() {
            SetNoteSupport.Note current = notes.get(key);
            Integer rgb = current == null ? null : current.getRgb();
            notes.put(key, new SetNoteSupport.Note(noteField.getText(), rgb));
        }

        private void applyColor(Integer rgb) {
            notes.put(key, new SetNoteSupport.Note(noteField.getText(), rgb));
            refreshColor();
        }

        private Integer currentRgb() {
            SetNoteSupport.Note current = notes.get(key);
            return current == null ? null : current.getRgb();
        }

        private Color currentColor() {
            Integer rgb = currentRgb();
            return rgb == null ? Color.WHITE : new Color(rgb);
        }

        private void refreshColor() {
            Color color = currentRgb() == null ? null : new Color(currentRgb());
            colorButton.setBackground(color == null ? Color.WHITE : color);
            colorButton.setForeground(color != null && SetNoteUiSupport.isDark(color) ? Color.WHITE : TEXT_COLOR);
            colorButton.setText(color == null ? "Colour" : SetNoteSupport.formatRgb(currentRgb()));
            panel.setBackground(color == null
                ? ROW_BACKGROUND
                : SetNoteUiSupport.mix(color, Color.WHITE, 0.6f));
            panel.repaint();
        }

        private void showPalette() {
            JPopupMenu popup = new JPopupMenu();

            JPanel swatches = new JPanel(new GridLayout(2, 6, 3, 3));
            swatches.setOpaque(false);
            swatches.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
            for (Color paletteColor : PALETTE) {
                JButton swatch = new JButton();
                swatch.setPreferredSize(new Dimension(30, 22));
                swatch.setBackground(paletteColor);
                swatch.setOpaque(true);
                swatch.setContentAreaFilled(true);
                swatch.setFocusPainted(false);
                swatch.setBorder(BorderFactory.createLineBorder(FIELD_BORDER, 1));
                swatch.setToolTipText(SetNoteSupport.formatRgb(paletteColor.getRGB() & 0xFFFFFF));
                swatch.addActionListener(e -> {
                    applyColor(paletteColor.getRGB() & 0xFFFFFF);
                    popup.setVisible(false);
                });
                swatches.add(swatch);
            }
            popup.add(swatches);
            popup.addSeparator();

            JMenuItem defaultItem = new JMenuItem("Default colour");
            defaultItem.addActionListener(e -> {
                applyColor(null);
                popup.setVisible(false);
            });
            popup.add(defaultItem);

            JMenuItem moreItem = new JMenuItem("More colours...");
            moreItem.addActionListener(e -> {
                popup.setVisible(false);
                Color chosen = JColorChooser.showDialog(SetNoteEditorDialog.this, "Card colour", currentColor());
                if (chosen != null) {
                    applyColor(chosen.getRGB() & 0xFFFFFF);
                }
            });
            popup.add(moreItem);

            SimpleGenomeHubUi.stylePopupMenu(popup);
            popup.show(colorButton, 0, colorButton.getHeight());
        }

        private String shortenId(String value) {
            return value.length() <= 26 ? value : value.substring(0, 24) + "...";
        }
    }
}
