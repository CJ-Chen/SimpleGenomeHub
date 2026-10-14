package simplegenomehub.gui;

import simplegenomehub.util.RuntimeLogManager;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/** Non-modal viewer for the current SimpleGenomeHub runtime log. */
public class RuntimeLogDialog extends JDialog {

    private static final int MAX_VISIBLE_CHARACTERS = 500_000;
    private static final int INITIAL_TAIL_BYTES = 250_000;
    private static final int READ_CHUNK_BYTES = 128_000;

    private final JTextArea logArea = new JTextArea();
    private final JCheckBox autoScrollCheckBox = new JCheckBox("Auto-scroll", true);
    private final Timer refreshTimer;
    private final simplegenomehub.runtime.HubSession session;
    private long fileOffset;

    public RuntimeLogDialog(Window owner) {
        this(owner, simplegenomehub.runtime.HubSession.forComponent(owner));
    }

    public RuntimeLogDialog(Window owner, simplegenomehub.runtime.HubSession owningSession) {
        super(owner, "Runtime Log", ModalityType.MODELESS);
        session = owningSession;
        if (session != null) simplegenomehub.runtime.HubSession.attach(this, session);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(920, 600);
        setLocationRelativeTo(owner);

        logArea.setEditable(false);
        logArea.setLineWrap(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setMargin(new Insets(8, 8, 8, 8));

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        JButton copyButton = new JButton("Copy");
        copyButton.addActionListener(event -> copyLog());
        JButton clearButton = new JButton("Clear View");
        clearButton.addActionListener(event -> logArea.setText(""));
        JButton openFileButton = new JButton("Open Log");
        openFileButton.addActionListener(event -> openPath(currentLogFile()));
        JButton openFolderButton = new JButton("Open Log Folder");
        openFolderButton.addActionListener(event -> openPath(logDirectory()));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(event -> dispose());

        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftButtons.add(copyButton);
        leftButtons.add(clearButton);
        leftButtons.add(openFileButton);
        leftButtons.add(openFolderButton);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightButtons.add(autoScrollCheckBox);
        rightButtons.add(closeButton);

        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        buttonPanel.add(leftButtons, BorderLayout.WEST);
        buttonPanel.add(rightButtons, BorderLayout.EAST);

        setLayout(new BorderLayout());
        add(scrollPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        loadInitialTail();
        refreshTimer = new Timer(600, event -> appendNewLogContent());
        refreshTimer.start();
    }

    @Override
    public void dispose() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
        super.dispose();
    }

    public static void showUnexpectedError(Window owner, Throwable error) {
        String detail = error == null || error.getMessage() == null
            ? "An unexpected error occurred."
            : error.getClass().getSimpleName() + ": " + error.getMessage();
        Object[] options = {"View Log", "Open Log Folder", "Close"};
        int choice = JOptionPane.showOptionDialog(
            owner,
            detail + "\n\nThe complete error details were written to the runtime log.",
            "SimpleGenomeHub Error",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.ERROR_MESSAGE,
            null,
            options,
            options[0]);

        if (choice == 0) {
            new RuntimeLogDialog(owner).setVisible(true);
        } else if (choice == 1) {
            openPath(RuntimeLogManager.getLogDirectory());
        }
    }

    private void loadInitialTail() {
        File logFile = currentLogFile();
        if (logFile == null || !logFile.isFile()) {
            logArea.setText("Runtime log is not available.\n");
            fileOffset = 0L;
            return;
        }

        try (RandomAccessFile input = new RandomAccessFile(logFile, "r")) {
            long length = input.length();
            long start = Math.max(0L, length - INITIAL_TAIL_BYTES);
            input.seek(start);
            if (start > 0L) {
                input.readLine();
            }
            int bytesToRead = (int) Math.min(Integer.MAX_VALUE, length - input.getFilePointer());
            byte[] data = new byte[bytesToRead];
            int bytesRead = input.read(data);
            if (bytesRead > 0) {
                logArea.setText(new String(data, 0, bytesRead, StandardCharsets.UTF_8));
            }
            fileOffset = length;
            moveCaretToEnd();
        } catch (IOException ex) {
            logArea.setText("Unable to read runtime log: " + ex.getMessage() + "\n");
            fileOffset = 0L;
        }
    }

    private void appendNewLogContent() {
        File logFile = currentLogFile();
        if (logFile == null || !logFile.isFile()) {
            return;
        }

        long length = logFile.length();
        if (length < fileOffset) {
            logArea.setText("");
            loadInitialTail();
            return;
        }
        if (length == fileOffset) {
            return;
        }

        try (RandomAccessFile input = new RandomAccessFile(logFile, "r")) {
            input.seek(fileOffset);
            int bytesToRead = (int) Math.min(READ_CHUNK_BYTES, length - fileOffset);
            byte[] data = new byte[bytesToRead];
            int bytesRead = input.read(data);
            if (bytesRead > 0) {
                fileOffset += bytesRead;
                appendText(new String(data, 0, bytesRead, StandardCharsets.UTF_8));
            }
        } catch (IOException ex) {
            appendText("\nUnable to refresh runtime log: " + ex.getMessage() + "\n");
        }
    }

    private void appendText(String text) {
        Document document = logArea.getDocument();
        try {
            document.insertString(document.getLength(), text, null);
            int excess = document.getLength() - MAX_VISIBLE_CHARACTERS;
            if (excess > 0) {
                document.remove(0, excess);
            }
            if (autoScrollCheckBox.isSelected()) {
                moveCaretToEnd();
            }
        } catch (BadLocationException ignored) {
            // The document can only change on the Swing event thread.
        }
    }

    private File currentLogFile() {
        return session == null ? RuntimeLogManager.getCurrentLogFile() : RuntimeLogManager.getCurrentLogFile(session);
    }

    private File logDirectory() {
        return session == null ? RuntimeLogManager.getLogDirectory() : RuntimeLogManager.getLogDirectory(session);
    }

    private void moveCaretToEnd() {
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void copyLog() {
        Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new StringSelection(logArea.getText()), null);
    }

    private static void openPath(File path) {
        if (path == null || !path.exists()) {
            JOptionPane.showMessageDialog(null, "The requested log path is not available.",
                "Runtime Log", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            if (!Desktop.isDesktopSupported()) {
                throw new IOException("Desktop integration is not supported on this system.");
            }
            Desktop.getDesktop().open(path);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null,
                "Unable to open:\n" + path.getAbsolutePath() + "\n\n" + ex.getMessage(),
                "Runtime Log", JOptionPane.ERROR_MESSAGE);
        }
    }
}
