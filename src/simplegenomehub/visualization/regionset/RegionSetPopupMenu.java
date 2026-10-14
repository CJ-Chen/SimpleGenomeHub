package simplegenomehub.visualization.regionset;

import simplegenomehub.util.fileio.RegionSetSupport;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import java.awt.Component;
import java.io.File;
import java.util.List;

/**
 * Browser agnostic right-click menu offering "Add to Region Set" and
 * "Create Region Set..." for a genomic interval picked in a chart.
 */
public final class RegionSetPopupMenu {

    private RegionSetPopupMenu() {
    }

    public static void show(Component invoker, int x, int y, RegionSetContext context, GenomicInterval interval) {
        JPopupMenu menu = new JPopupMenu();

        if (context == null || !context.isAvailable()) {
            JMenuItem addItem = new JMenuItem("Add to Region Set");
            addItem.setEnabled(false);
            addItem.setToolTipText("No genome unit is associated with this visualization.");
            JMenuItem createItem = new JMenuItem("Create Region Set...");
            createItem.setEnabled(false);
            createItem.setToolTipText("No genome unit is associated with this visualization.");
            menu.add(addItem);
            menu.add(createItem);
            simplegenomehub.runtime.HubSession session = simplegenomehub.runtime.HubSession.forComponent(invoker);
        simplegenomehub.runtime.HubSession.attach(menu, session);
        if (session != null) session.guardActionsIn(menu);
        menu.show(invoker, x, y);
            return;
        }

        JMenu addMenu = new JMenu("Add to Region Set");
        List<File> regionSets = RegionSetActions.listRegionSets(context);
        if (regionSets.isEmpty()) {
            JMenuItem none = new JMenuItem("(No Region Sets)");
            none.setEnabled(false);
            addMenu.add(none);
        } else {
            for (File regionSetFile : regionSets) {
                JMenuItem item = new JMenuItem(RegionSetSupport.extractDisplayName(regionSetFile));
                item.addActionListener(e -> addToRegionSet(invoker, context, regionSetFile, interval));
                addMenu.add(item);
            }
        }
        menu.add(addMenu);

        JMenuItem createItem = new JMenuItem("Create Region Set...");
        createItem.addActionListener(e -> createRegionSet(invoker, context, interval));
        menu.add(createItem);

        simplegenomehub.runtime.HubSession session = simplegenomehub.runtime.HubSession.forComponent(invoker);
        simplegenomehub.runtime.HubSession.attach(menu, session);
        if (session != null) session.guardActionsIn(menu);
        menu.show(invoker, x, y);
    }

    private static void addToRegionSet(Component parent, RegionSetContext context,
                                       File regionSetFile, GenomicInterval interval) {
        if (!confirmSoftWarnings(parent, context, interval)) {
            return;
        }
        RegionSetActions.Result result = RegionSetActions.addToRegionSet(context, regionSetFile, interval);
        showResult(parent, result, "Add to Region Set");
    }

    private static void createRegionSet(Component parent, RegionSetContext context, GenomicInterval interval) {
        if (!confirmSoftWarnings(parent, context, interval)) {
            return;
        }

        String suggestedName = buildSuggestedName(interval);
        while (true) {
            String name = promptForName(parent, suggestedName);
            if (name == null) {
                return;
            }

            RegionSetActions.Result result = RegionSetActions.createRegionSet(context, name, interval);
            if (result.getStatus() == RegionSetActions.Status.INVALID_NAME
                || result.getStatus() == RegionSetActions.Status.NAME_CONFLICT) {
                JOptionPane.showMessageDialog(parent, result.getMessage(),
                    "Create Region Set", JOptionPane.WARNING_MESSAGE);
                suggestedName = name;
                continue;
            }

            showResult(parent, result, "Create Region Set");
            return;
        }
    }

    private static String promptForName(Component parent, String initialValue) {
        JTextField nameField = new JTextField(initialValue == null ? "" : initialValue, 26);
        nameField.selectAll();
        Object[] message = {"Region Set Name:", nameField};
        Object[] options = {"Create", "Cancel"};

        int choice = JOptionPane.showOptionDialog(parent, message, "Create Region Set",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (choice != 0) {
            return null;
        }
        return nameField.getText() == null ? "" : nameField.getText().trim();
    }

    private static boolean confirmSoftWarnings(Component parent, RegionSetContext context, GenomicInterval interval) {
        List<String> warnings = RegionSetActions.softWarnings(context, interval);
        if (warnings.isEmpty()) {
            return true;
        }

        StringBuilder builder = new StringBuilder("The selected region may not match the genome:\n\n");
        for (String warning : warnings) {
            builder.append("- ").append(warning).append('\n');
        }
        builder.append("\nUse it anyway?");

        int choice = JOptionPane.showConfirmDialog(parent, builder.toString(), "Region Check",
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        return choice == JOptionPane.YES_OPTION;
    }

    private static void showResult(Component parent, RegionSetActions.Result result, String title) {
        if (result == null) {
            return;
        }

        String message = result.getMessage();
        if (result.getStatus() == RegionSetActions.Status.SUCCESS) {
            if (result.getFile() != null) {
                message = message + "\n" + result.getFile().getName();
            }
            JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.WARNING_MESSAGE);
    }

    private static String buildSuggestedName(GenomicInterval interval) {
        if (interval == null) {
            return "RegionSet";
        }
        String chromosome = sanitizeFileNamePart(interval.getChromosome());
        return "RegionSet_" + chromosome + "_" + interval.getStart() + "_" + interval.getEnd();
    }

    private static String sanitizeFileNamePart(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "region";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '/' || c == ':' || c == '*' || c == '?'
                || c == '"' || c == '<' || c == '>' || c == '|' || Character.isWhitespace(c)) {
                builder.append('_');
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }
}
