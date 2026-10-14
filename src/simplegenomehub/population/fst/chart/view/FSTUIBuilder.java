package simplegenomehub.population.fst.chart.view;

import simplegenomehub.population.fst.FstUiStyle;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

// UI构建器：负责创建菜单栏和分隔面板
public class FSTUIBuilder {

    // 回调接口
    public interface Callbacks {
        void onSettingsClick();
        void onStepSizeChange();
        void onPlotClick();
        void onTopClick();
        void onPositionLineUpdate();
        void onPositionChange();
        void onRefresh();
        void onSortChromosomes();
        void onExportImage();
    }

    // 创建菜单栏：原 Show 栏的四个动作收进 FST 菜单，放在 Genome 菜单之前
    public static JMenuBar createMenuBar(Callbacks callbacks) {
        JMenuBar menuBar = new JMenuBar();

        // 文件菜单
        JMenu fileMenu = new JMenu("File");
        JMenuItem exportItem = new JMenuItem("Export Image...");
        exportItem.setToolTipText("Export the visualization area as PNG / SVG / PDF");
        exportItem.addActionListener(e -> callbacks.onExportImage());
        fileMenu.add(exportItem);
        menuBar.add(fileMenu);

        // FST 菜单（原工具栏 Show 栏）
        JMenu fstMenu = new JMenu("FST");
        JMenuItem plotItem = new JMenuItem("Plot");
        plotItem.setToolTipText("Switch between the plot and the plain display");
        plotItem.addActionListener(e -> callbacks.onPlotClick());
        fstMenu.add(plotItem);

        JMenuItem topItem = new JMenuItem("Top");
        topItem.setToolTipText("Recompute the Top percent markers");
        topItem.addActionListener(e -> callbacks.onTopClick());
        fstMenu.add(topItem);

        JMenuItem refreshItem = new JMenuItem("Refresh");
        refreshItem.setToolTipText("Re-read the result files and repaint (files added to the result folder are picked up as well)");
        refreshItem.addActionListener(e -> callbacks.onRefresh());
        fstMenu.add(refreshItem);

        JMenuItem sortItem = new JMenuItem("Sort chromosomes by number");
        sortItem.setToolTipText("<html>Same option as \"Sort chromosomes by number\" in the strategy window.<br>"
            + "Re-sorts the display by the number at the end of each chromosome name (Chr1, Chr2, ..., Chr10) and refreshes it.</html>");
        sortItem.addActionListener(e -> callbacks.onSortChromosomes());
        fstMenu.add(sortItem);
        menuBar.add(fstMenu);

        // 基因组菜单
        JMenu genomeMenu = new JMenu("Genome");
        menuBar.add(genomeMenu);

        // 设置菜单
        JMenu settingsMenu = new JMenu("Settings");
        JMenuItem settingsItem = new JMenuItem("Settings");
        settingsItem.addActionListener(e -> callbacks.onSettingsClick());
        settingsMenu.add(settingsItem);
        menuBar.add(settingsMenu);

        return menuBar;
    }

    // 创建分隔面板：上半部分是 FST 图表，下半部分嵌入基因组浏览器
    public static JSplitPane createSplitPane(JComponent browserComponent, Callbacks callbacks) {
        // 顶部面板：图表（统一使用FSTTrackChartPanel，将在updateChart中创建）
        JPanel placeholderPanel = new JPanel();
        placeholderPanel.setPreferredSize(new Dimension(1200, 400));
        placeholderPanel.setBackground(Color.WHITE);

        // 底部面板：基因组浏览器（工具栏与画布都在组件内部）
        JPanel browserPanel = new JPanel(new BorderLayout());
        browserPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "Genome Browser",
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));
        if (browserComponent != null) {
            browserPanel.add(browserComponent, BorderLayout.CENTER);
        }

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                                               new JScrollPane(placeholderPanel),
                                               browserPanel);
        splitPane.setResizeWeight(0.5);
        splitPane.setOneTouchExpandable(true);

        // 窗口真正显示之后再按比例放置分隔条：这时分割面板已有实际高度。
        SwingUtilities.invokeLater(() -> {
            if (splitPane.getHeight() > 0) {
                splitPane.setDividerLocation(0.5);
            } else {
                splitPane.setDividerLocation(600);
            }
        });

        return splitPane;
    }
}
