package simplegenomehub.population.fst.chart.view;

import simplegenomehub.population.fst.chart.model.DataSet;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;

/**
 * 卡牌类型标签列。
 * <p>
 * 把左侧一条的高度等分成若干段，每段一个类型；无边框。
 * 未选中项背景与卡牌区不同，选中项背景与卡牌区背景一致，
 * 视觉上像"标签连着卡牌区"。
 */
public class CardTypeTabStrip extends JPanel {

    /** 选中回调 */
    public interface SelectionListener {
        void onSelect(DataSet.DataType type);
    }

    private static final int TAB_WIDTH = 96;
    private static final Color CARD_AREA_BG = simplegenomehub.population.fst.FstUiStyle.CARD_BAR_BACKGROUND; // 与卡牌区背景一致
    private static final Color TAB_BG = simplegenomehub.population.fst.FstUiStyle.TAB_BACKGROUND;
    private static final Color TAB_BG_HOVER = simplegenomehub.population.fst.FstUiStyle.TAB_BACKGROUND_HOVER;
    private static final Color TEXT_COLOR = simplegenomehub.population.fst.FstUiStyle.TAB_TEXT;
    private static final Color TEXT_COLOR_SELECTED = simplegenomehub.population.fst.FstUiStyle.TITLE_BLUE;

    private final List<Tab> tabs = new ArrayList<>();
    private int selectedIndex = -1;
    private SelectionListener listener;

    public CardTypeTabStrip() {
        setLayout(new GridLayout(0, 1, 0, 0)); // 按数量等分高度
        setOpaque(true);
        setBackground(TAB_BG);
        setPreferredSize(new Dimension(TAB_WIDTH, 10)); // 高度由布局拉伸
    }

    public void setSelectionListener(SelectionListener listener) {
        this.listener = listener;
    }

    public void addTab(String label, DataSet.DataType type) {
        Tab tab = new Tab(label, type);
        tabs.add(tab);
        add(tab);
    }

    /** 选中某项并触发回调 */
    public void selectIndex(int index) {
        if (index < 0 || index >= tabs.size()) return;
        selectedIndex = index;
        for (Tab tab : tabs) {
            tab.repaint();
        }
        if (listener != null) {
            listener.onSelect(tabs.get(index).type);
        }
    }

    /** 只设置选中态（不触发回调），用于初始化默认选中 */
    public void setSelectedIndexSilently(int index) {
        if (index < 0 || index >= tabs.size()) return;
        selectedIndex = index;
        for (Tab tab : tabs) {
            tab.repaint();
        }
    }

    /** 当前选中项的类型（null 表示 All） */
    public DataSet.DataType getSelectedType() {
        if (selectedIndex < 0 || selectedIndex >= tabs.size()) return null;
        return tabs.get(selectedIndex).type;
    }

    private int indexOf(Tab tab) {
        return tabs.indexOf(tab);
    }

    private class Tab extends JPanel {
        private final String label;
        private final DataSet.DataType type;
        private boolean hovered = false;

        Tab(String label, DataSet.DataType type) {
            this.label = label;
            this.type = type;
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(type == null ? "Show all cards" : ("Show only " + label + " cards"));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    selectIndex(indexOf(Tab.this));
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            boolean selected = indexOf(this) == selectedIndex;

            g.setColor(selected ? CARD_AREA_BG : (hovered ? TAB_BG_HOVER : TAB_BG));
            g.fillRect(0, 0, w, h);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(simplegenomehub.population.fst.FstUiStyle.sans(12, selected ? Font.BOLD : Font.PLAIN));
            g2.setColor(selected ? TEXT_COLOR_SELECTED : TEXT_COLOR);
            FontMetrics fm = g2.getFontMetrics();
            int textY = (h + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(label, 10, textY);
            g2.dispose();
        }
    }
}
