package simplegenomehub.population.fst.view;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * FST设置对话框
 */
public class FSTSettingsDialog extends JDialog {
    private Color firstColor;
    private Color secondColor;
    private Color topColor;
    
    // Gene colors
    private Color intronColor;
    private Color forwardExonColor;
    private Color reverseExonColor;
    
    private JButton firstColorButton;
    private JButton secondColorButton;
    private JButton topColorButton;
    
    private JButton intronColorButton;
    private JButton forwardExonColorButton;
    private JButton reverseExonColorButton;
    
    private boolean settingsChanged = false;
    
    public FSTSettingsDialog(JFrame parent) {
        super(parent, "Settings", true);
        
        // 初始化默认颜色
        firstColor = simplegenomehub.population.fst.FstUiStyle.SERIES_FIRST;  // 蓝（跟随 SimpleGenomeHub 主色）
        secondColor = simplegenomehub.population.fst.FstUiStyle.SERIES_SECOND;  // 橙（跟随 SimpleGenomeHub 主色）
        topColor = simplegenomehub.population.fst.FstUiStyle.TOP_MARKER;  // 深绿
        
        // 初始化基因颜色
        intronColor = new Color(166, 166, 166);  // 灰色
        forwardExonColor = new Color(255, 210, 83);  // 黄色
        reverseExonColor = new Color(8, 211, 251);  // 青色
        
        initializeUI();
    }
    
    private void initializeUI() {
        setLayout(new BorderLayout());
        setSize(400, 500);
        setLocationRelativeTo(getParent());
        
        // 创建主面板
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Color栏
        JPanel colorPanel = createColorPanel();
        mainPanel.add(colorPanel);
        mainPanel.add(Box.createVerticalStrut(10));
        
        // Gene Color栏
        JPanel geneColorPanel = createGeneColorPanel();
        mainPanel.add(geneColorPanel);
        mainPanel.add(Box.createVerticalStrut(10));
        
        // Lucky Color栏
        JPanel luckyColorPanel = createLuckyColorPanel();
        mainPanel.add(luckyColorPanel);
        mainPanel.add(Box.createVerticalStrut(10));
        
        // Size栏（空）
        JPanel sizePanel = createEmptyPanel("Size");
        mainPanel.add(sizePanel);
        mainPanel.add(Box.createVerticalStrut(10));
        
        // Window栏（空）
        JPanel windowPanel = createEmptyPanel("Window");
        mainPanel.add(windowPanel);
        mainPanel.add(Box.createVerticalStrut(10));
        
        // Show栏（空）
        JPanel showPanel = createEmptyPanel("Show");
        mainPanel.add(showPanel);
        mainPanel.add(Box.createVerticalStrut(10));
        
        // Genome栏（空）
        JPanel genomePanel = createEmptyPanel("Genome");
        mainPanel.add(genomePanel);
        
        // 添加滚动面板
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);
        
        // 添加按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> {
            settingsChanged = true;
            dispose();
        });
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }
    
    private JPanel createColorPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "Color",
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));
        
        // First Color
        JPanel firstPanel = createColorRow("First Color", firstColor, c -> firstColor = c);
        panel.add(firstPanel);
        panel.add(Box.createVerticalStrut(5));
        
        // Second Color
        JPanel secondPanel = createColorRow("Second Color", secondColor, c -> secondColor = c);
        panel.add(secondPanel);
        panel.add(Box.createVerticalStrut(5));
        
        // Top Color
        JPanel topPanel = createColorRow("Top Color", topColor, c -> topColor = c);
        panel.add(topPanel);
        
        return panel;
    }
    
    private JPanel createGeneColorPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "Gene Color",
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));
        
        // Intron Color
        JPanel intronPanel = createColorRow("Intron Color", intronColor, c -> intronColor = c);
        panel.add(intronPanel);
        panel.add(Box.createVerticalStrut(5));
        
        // Forward Exon Color
        JPanel forwardPanel = createColorRow("Forward Exon Color", forwardExonColor, c -> forwardExonColor = c);
        panel.add(forwardPanel);
        panel.add(Box.createVerticalStrut(5));
        
        // Reverse Exon Color
        JPanel reversePanel = createColorRow("Reverse Exon Color", reverseExonColor, c -> reverseExonColor = c);
        panel.add(reversePanel);
        
        return panel;
    }
    
    private JPanel createColorRow(String label, Color initialColor, java.util.function.Consumer<Color> colorSetter) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.add(new JLabel(label + ":"));
        
        JButton colorButton = new JButton();
        colorButton.setPreferredSize(new Dimension(30, 30));  // 正方形
        colorButton.setMinimumSize(new Dimension(30, 30));
        colorButton.setMaximumSize(new Dimension(30, 30));
        colorButton.setBackground(initialColor);
        colorButton.setOpaque(true);
        colorButton.setBorderPainted(true);
        
        colorButton.addActionListener(e -> {
            Color newColor = JColorChooser.showDialog(this, "Choose " + label, initialColor);
            if (newColor != null) {
                colorSetter.accept(newColor);
                colorButton.setBackground(newColor);
            }
        });
        
        if (label.equals("First Color")) {
            firstColorButton = colorButton;
        } else if (label.equals("Second Color")) {
            secondColorButton = colorButton;
        } else if (label.equals("Top Color")) {
            topColorButton = colorButton;
        } else if (label.equals("Intron Color")) {
            intronColorButton = colorButton;
        } else if (label.equals("Forward Exon Color")) {
            forwardExonColorButton = colorButton;
        } else if (label.equals("Reverse Exon Color")) {
            reverseExonColorButton = colorButton;
        }
        
        row.add(colorButton);
        return row;
    }
    
    private JPanel createEmptyPanel(String title) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            title,
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));
        panel.setPreferredSize(new Dimension(0, 50));
        return panel;
    }
    
    public Color getFirstColor() {
        return firstColor;
    }
    
    public Color getSecondColor() {
        return secondColor;
    }
    
    public Color getTopColor() {
        return topColor;
    }
    
    public void setFirstColor(Color color) {
        this.firstColor = color;
        if (firstColorButton != null) {
            firstColorButton.setBackground(color);
        }
    }
    
    public void setSecondColor(Color color) {
        this.secondColor = color;
        if (secondColorButton != null) {
            secondColorButton.setBackground(color);
        }
    }
    
    public void setTopColor(Color color) {
        this.topColor = color;
        if (topColorButton != null) {
            topColorButton.setBackground(color);
        }
    }
    
    public Color getIntronColor() {
        return intronColor;
    }
    
    public Color getForwardExonColor() {
        return forwardExonColor;
    }
    
    public Color getReverseExonColor() {
        return reverseExonColor;
    }
    
    public void setIntronColor(Color color) {
        this.intronColor = color;
        if (intronColorButton != null) {
            intronColorButton.setBackground(color);
        }
    }
    
    public void setForwardExonColor(Color color) {
        this.forwardExonColor = color;
        if (forwardExonColorButton != null) {
            forwardExonColorButton.setBackground(color);
        }
    }
    
    public void setReverseExonColor(Color color) {
        this.reverseExonColor = color;
        if (reverseExonColorButton != null) {
            reverseExonColorButton.setBackground(color);
        }
    }
    
    public boolean isSettingsChanged() {
        return settingsChanged;
    }
    
    // 创建Lucky Color面板
    private JPanel createLuckyColorPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "Lucky Color",
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));
        
        // PI Color
        JPanel piColorSection = createColorSectionWithSwatches("PI Color", getPIColorSwatchesPrivate());
        panel.add(piColorSection);
        panel.add(Box.createVerticalStrut(10));
        
        // TajimaD Color
        JPanel tajimaDColorSection = createColorSectionWithSwatches("TajimaD Color", getTajimaDColorSwatchesPrivate());
        panel.add(tajimaDColorSection);
        
        return panel;
    }
    
    // 创建带色块的颜色区域
    private JPanel createColorSectionWithSwatches(String label, Color[] swatchColors) {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        
        // 标签
        JLabel labelComponent = new JLabel(label + ":");
        section.add(labelComponent);
        section.add(Box.createVerticalStrut(5));
        
        // 色块网格
        JPanel swatchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        for (Color color : swatchColors) {
            JButton swatchButton = createSwatchButton(color);
            swatchPanel.add(swatchButton);
        }
        section.add(swatchPanel);
        
        return section;
    }
    
    // 创建正方形色块按钮
    private JButton createSwatchButton(Color color) {
        JButton button = new JButton();
        button.setPreferredSize(new Dimension(30, 30));  // 正方形
        button.setMinimumSize(new Dimension(30, 30));
        button.setMaximumSize(new Dimension(30, 30));
        button.setBackground(color);
        button.setOpaque(true);
        button.setBorderPainted(true);
        button.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        
        button.addActionListener(e -> {
            // 点击色块可以选择该颜色（可以扩展功能）
            // 目前仅显示颜色
        });
        
        return button;
    }
    
    // 获取PI Color的10个色块颜色（公共方法）
    public static Color[] getPIColorSwatches() {
        return new Color[] {
            new Color(175, 211, 217),
            new Color(227, 239, 199),
            new Color(181, 209, 159),
            new Color(249, 200, 159),
            new Color(242, 236, 175),
            new Color(134, 170, 210),
            new Color(255, 245, 175),
            new Color(156, 187, 204),
            new Color(200, 230, 226),
            new Color(132, 192, 196)
        };
    }
    
    // 获取TajimaD Color的10个色块颜色（公共方法）
    public static Color[] getTajimaDColorSwatches() {
        return new Color[] {
            new Color(29, 85, 181),
            new Color(155, 141, 174),
            new Color(0, 136, 144),
            new Color(185, 181, 201),
            new Color(236, 186, 193),
            new Color(149, 0, 149),
            new Color(155, 141, 174),
            new Color(199, 124, 252),
            new Color(190, 101, 108),
            new Color(29, 138, 137)
        };
    }
    
    // 从PI色盘中随机选择一个颜色
    public static Color getRandomPIColor() {
        Color[] colors = getPIColorSwatches();
        return colors[(int)(Math.random() * colors.length)];
    }
    
    // 从TajimaD色盘中随机选择一个颜色
    public static Color getRandomTajimaDColor() {
        Color[] colors = getTajimaDColorSwatches();
        return colors[(int)(Math.random() * colors.length)];
    }
    
    // 获取PI Color的10个色块颜色（私有方法，用于UI显示）
    private Color[] getPIColorSwatchesPrivate() {
        return getPIColorSwatches();
    }
    
    // 获取TajimaD Color的10个色块颜色（私有方法，用于UI显示）
    private Color[] getTajimaDColorSwatchesPrivate() {
        return getTajimaDColorSwatches();
    }
}
