package simplegenomehub.genomebrowser;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import javax.swing.BorderFactory;
import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.model.SpeciesManager;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Reusable host for a {@link GenomeBrowserPanel}.
 *
 * <p>The browser starts docked inside the host component and can be popped out
 * into a separate window. The same browser instance is moved between the two
 * containers, so its chromosome, zoom, selection and highlight state survive
 * the transition.</p>
 */
public final class GenomeBrowserDock extends JPanel {

    private final Window owner;
    private final String title;
    private final GenomeBrowserPanel browser;
    private final JPanel browserHolder = new JPanel(new BorderLayout());
    private JButton dockButton;
    private JFrame floatingFrame;
    private Point dragOffset;
    private boolean floating;

    public GenomeBrowserDock(Window owner, File fastaFile, File gffFile, String title) {
        this(owner, fastaFile, gffFile, title, null, null);
    }

    /** Same, with the genome unit the files belong to (enables BLAST / gene info in the menu). */
    public GenomeBrowserDock(Window owner, File fastaFile, File gffFile, String title,
                             SpeciesInfo species, SpeciesManager speciesManager) {
        super(new BorderLayout());
        this.owner = owner;
        this.title = title == null || title.trim().isEmpty() ? "Genome Browser" : title.trim();
        this.browser = new GenomeBrowserPanel(fastaFile, gffFile, species, speciesManager);
        buildDockedUi();
    }

    public GenomeBrowserPanel getBrowser() {
        return browser;
    }

    public boolean isFloating() {
        return floating;
    }

    /** Moves the shared browser into a separate window. */
    public void popOut() {
        if (floating) {
            return;
        }
        floating = true;
        browserHolder.remove(browser);
        browserHolder.revalidate();
        browserHolder.repaint();

        floatingFrame = new JFrame(title);
        floatingFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        floatingFrame.setLayout(new BorderLayout());
        floatingFrame.add(createFloatingHeader(), BorderLayout.NORTH);
        floatingFrame.add(browser, BorderLayout.CENTER);
        floatingFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                dock();
            }

            @Override
            public void windowClosed(WindowEvent event) {
                if (floatingFrame == event.getWindow()) {
                    floatingFrame = null;
                }
            }
        });
        floatingFrame.setSize(new Dimension(1250, 780));
        floatingFrame.setLocationRelativeTo(owner);
        floatingFrame.setVisible(true);
        if (dockButton != null) {
            dockButton.setText("Dock");
        }
    }

    /** Returns the shared browser to the original host. */
    public void dock() {
        if (!floating) {
            return;
        }
        if (floatingFrame != null) {
            floatingFrame.remove(browser);
            floatingFrame.dispose();
            floatingFrame = null;
        }
        floating = false;
        browserHolder.add(browser, BorderLayout.CENTER);
        browserHolder.revalidate();
        browserHolder.repaint();
        if (dockButton != null) {
            dockButton.setText("Pop out");
        }
    }

    /** Releases the browser data and any floating window. */
    public void close() {
        if (floatingFrame != null) {
            floatingFrame.remove(browser);
            floatingFrame.dispose();
            floatingFrame = null;
        }
        floating = false;
        browser.close();
    }

    private void buildDockedUi() {
        JPanel header = createHeader();
        browserHolder.setBorder(BorderFactory.createEmptyBorder());
        browserHolder.add(browser, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);
        add(browserHolder, BorderLayout.CENTER);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(210, 214, 220)),
            BorderFactory.createEmptyBorder(3, 8, 3, 6)));
        header.setBackground(new Color(245, 247, 249));
        JLabel titleLabel = new JLabel(this.title);
        titleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        header.add(titleLabel, BorderLayout.CENTER);
        JButton floatButton = new JButton(floating ? "Dock" : "Pop out");
        if (!floating) {
            dockButton = floatButton;
        }
        floatButton.setFocusable(false);
        floatButton.addActionListener(event -> {
            if (floating) dock(); else popOut();
        });
        header.add(floatButton, BorderLayout.EAST);
        installDragHandler(header);
        installDragHandler(titleLabel);
        return header;
    }

    private JPanel createFloatingHeader() {
        JPanel header = createHeader();
        return header;
    }

    private void installDragHandler(java.awt.Component component) {
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (!floating && SwingUtilities.isLeftMouseButton(event)) {
                    dragOffset = event.getPoint();
                }
            }
        });
        component.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent event) {
                if (floating || dragOffset == null || !SwingUtilities.isLeftMouseButton(event)) {
                    return;
                }
                Point screen = event.getLocationOnScreen();
                if (Math.abs(event.getX() - dragOffset.x) + Math.abs(event.getY() - dragOffset.y) < 10) {
                    return;
                }
                popOut();
                if (floatingFrame != null) {
                    floatingFrame.setLocation(screen.x - dragOffset.x, screen.y - dragOffset.y);
                }
                dragOffset = null;
            }
        });
    }
}
