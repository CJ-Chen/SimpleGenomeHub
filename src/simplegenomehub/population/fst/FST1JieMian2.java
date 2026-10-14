package simplegenomehub.population.fst;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * FST1JieMian2 - 界面辅助功能类
 * 包含搜索、窗口设置、文件拖拽等功能
 */
public class FST1JieMian2 {
    
    /**
     * 更新数值字段（通用方法）
     */
    private static void updateNumericField(JTextField field, int[] ref, int defaultValue) {
        if (field == null) return;
        try {
            String text = field.getText().trim();
            if (text.isEmpty()) {
                ref[0] = defaultValue;
                return;
            }
            int value = Integer.parseInt(text);
            ref[0] = value > 0 ? value : defaultValue;
        } catch (NumberFormatException e) {
            ref[0] = defaultValue;
        }
    }
    
    /**
     * 更新窗口大小
     */
    public static void updateWindowSize(JTextField windowSizeField, int[] windowSizeRef) {
        updateNumericField(windowSizeField, windowSizeRef, 1000000);
    }

    /**
     * 更新步长
     */
    public static void updateStepSize(JTextField windowStepField, int[] stepSizeRef) {
        updateNumericField(windowStepField, stepSizeRef, 100000);
    }
    
    /**
     * 设置搜索功能
     */
    public static void setupSearchFilter(JTextField searchField, DefaultListModel<String> listModel, List<String> allSamples) {
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filterSamples(searchField, listModel, allSamples);
            }
            
            @Override
            public void removeUpdate(DocumentEvent e) {
                filterSamples(searchField, listModel, allSamples);
            }
            
            @Override
            public void changedUpdate(DocumentEvent e) {
                filterSamples(searchField, listModel, allSamples);
            }
        });
    }
    
    /**
     * 过滤样本
     */
    private static void filterSamples(JTextField searchField, DefaultListModel<String> listModel, List<String> allSamples) {
        String searchText = searchField.getText().trim().toLowerCase();
        listModel.clear();
        
        List<String> filtered = searchText.isEmpty() 
            ? allSamples 
            : allSamples.stream()
                .filter(sample -> sample.toLowerCase().contains(searchText))
                .collect(Collectors.toList());
        
        filtered.forEach(listModel::addElement);
    }

    /**
     * 设置文本区域的文件拖拽功能
     */
    public static void setupTextAreaDropTarget(JTextArea textArea) {
        new DropTarget(textArea, new DropTargetAdapter() {
            @Override
            @SuppressWarnings("unchecked")
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    
                    if (dtde.getTransferable().isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        List<File> files = (List<File>) dtde.getTransferable()
                                .getTransferData(DataFlavor.javaFileListFlavor);
                        if (!files.isEmpty()) {
                            File file = files.get(0);
                            String content = readTextFile(file);
                            if (content != null && !content.isEmpty()) {
                                String[] lines = content.split("\r?\n");
                                StringBuilder sb = new StringBuilder();
                                for (String line : lines) {
                                    line = line.trim();
                                    if (!line.isEmpty()) {
                                        if (sb.length() > 0) {
                                            sb.append("\n");
                                        }
                                        sb.append(line);
                                    }
                                }
                                textArea.setText(sb.toString());
                            }
                        }
                    }
                    
                    dtde.dropComplete(true);
                } catch (Exception e) {
                    e.printStackTrace();
                    dtde.dropComplete(false);
                }
            }
        });
    }

    /**
     * 读取文本文件内容
     */
    public static String readTextFile(File file) {
        try {
            InputStream inputStream = new FileInputStream(file);
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (content.length() > 0) {
                        content.append("\n");
                    }
                    content.append(line);
                }
                return content.toString();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 给名称标签安装"双击改名"功能：双击后标签原位替换为输入框，
     * 回车/失焦提交，Esc 取消。提交前先经 validator 校验：
     * 返回 null 表示合法；返回字符串则弹出该提示并保持编辑状态。
     *
     * @param label     显示名称的标签（需要作为 BorderLayout 父容器的直接子组件）
     * @param validator 校验器（入参为新名称trim后的值）
     * @param onRename  成功改名后的回调（入参：旧名、新名），可用于同步其它界面/数据
     */
    public static void installLabelRenameOnDoubleClick(final JLabel label,
                                                       Function<String, String> validator,
                                                       BiConsumer<String, String> onRename) {
        if (label == null) return;
        label.setToolTipText("Double-click to rename");
        label.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    beginLabelRename(label, validator, onRename);
                }
            }
        });
    }

    /**
     * 开始就地编辑标签文本
     */
    private static void beginLabelRename(final JLabel label,
                                         Function<String, String> validator,
                                         BiConsumer<String, String> onRename) {
        final Container parent = label.getParent();
        if (parent == null) return;
        // 防止重复进入编辑状态
        if (Boolean.TRUE.equals(label.getClientProperty("__renaming__"))) return;

        LayoutManager layout = parent.getLayout();
        final boolean useBorderConstraint = layout instanceof BorderLayout;
        final Object constraint = useBorderConstraint
                ? ((BorderLayout) layout).getConstraints(label) : null;
        final int componentIndex = useBorderConstraint ? -1 : indexOfComponent(parent, label);
        if (!useBorderConstraint && componentIndex < 0) return;
        label.putClientProperty("__renaming__", Boolean.TRUE);

        final String oldName = label.getText();
        final JTextField field = new JTextField(oldName);
        field.setFont(label.getFont());
        field.setBorder(BorderFactory.createLineBorder(new Color(70, 130, 180)));

        final boolean[] finished = {false};

        // 结束编辑并把标签放回原位
        final Runnable closeEditor = () -> {
            if (finished[0]) return;
            finished[0] = true;
            parent.remove(field);
            if (useBorderConstraint) {
                parent.add(label, constraint);
            } else {
                parent.add(label, Math.min(componentIndex, parent.getComponentCount()));
            }
            parent.revalidate();
            parent.repaint();
            label.putClientProperty("__renaming__", null);
        };

        // 提交（校验通过才生效）
        final Runnable commit = () -> {
            if (finished[0]) return;
            String newName = field.getText() == null ? "" : field.getText().trim();
            String error = validator != null ? validator.apply(newName) : null;
            if (error != null) {
                JOptionPane.showMessageDialog(parent, error, "Invalid name", JOptionPane.WARNING_MESSAGE);
                return; // 保持编辑状态，让用户继续输入
            }
            label.setText(newName);
            closeEditor.run();
            if (onRename != null) {
                onRename.accept(oldName, newName);
            }
        };

        final Runnable cancel = () -> {
            if (!finished[0]) closeEditor.run();
        };

        field.addActionListener(e -> commit.run());
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                // 焦点丢失视为提交（Esc已处理取消场景）
                if (!finished[0]) commit.run();
            }
        });
        field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    cancel.run();
                }
            }
        });

        parent.remove(label);
        if (useBorderConstraint) {
            parent.add(field, constraint);
        } else {
            parent.add(field, Math.min(componentIndex, parent.getComponentCount()));
        }
        parent.revalidate();
        parent.repaint();
        field.requestFocusInWindow();
        field.selectAll();
    }

    /**
     * 返回组件在父容器中的索引；找不到返回 -1
     */
    private static int indexOfComponent(Container parent, Component component) {
        Component[] components = parent.getComponents();
        for (int i = 0; i < components.length; i++) {
            if (components[i] == component) return i;
        }
        return -1;
    }

    /**
     * 校验名称：非空、不包含文件名非法字符、不超长
     * 返回 null 表示合法，否则返回错误提示
     */
    public static String validateDisplayName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Name must not be empty.";
        }
        String n = name.trim();
        if (n.length() > 80) {
            return "Name is too long (80 characters at most).";
        }
        for (char c : n.toCharArray()) {
            if (c == '\\' || c == '/' || c == ':' || c == '*' || c == '?' ||
                c == '"' || c == '<' || c == '>' || c == '|' ||
                c == '\n' || c == '\t' || c == '\r') {
                return "Name must not contain \\ / : * ? \" < > |";
            }
        }
        return null;
    }
}
