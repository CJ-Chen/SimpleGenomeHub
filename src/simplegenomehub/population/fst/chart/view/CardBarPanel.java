package simplegenomehub.population.fst.chart.view;

import simplegenomehub.population.fst.chart.model.DataSet;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/**
 * 卡牌栏面板：顶部横向显示数据集的卡牌，支持按数据类型筛选与"从左往右发牌"动画
 */
public class CardBarPanel extends JPanel {
    private static final int DEAL_INTERVAL_MS = 40;   // 相邻两张卡牌的出现间隔（越小越快）
    private static final int ANIM_TICK_MS = 16;       // 动画刷新间隔
    private static final float ANIM_STEP = 0.12f;     // 每帧动画进度增量

    private final List<CardView> cardViews;                        // 全部卡牌（保持读取顺序）
    private final List<CardView> dealingCards = new ArrayList<>(); // 本次要发牌的卡牌
    private Timer dealTimer;
    private int dealIndex;
    private long lastDealTime;

    public CardBarPanel() {
        this.cardViews = new ArrayList<>();
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        setBackground(simplegenomehub.population.fst.FstUiStyle.CARD_BAR_BACKGROUND);
    }

    /**
     * 卡牌栏的高度始终按“全部卡牌”计算，不跟着发牌动画当前挂着的卡牌走。
     * <p>
     * “从左往右发牌”的实现是 {@link #showCards} 先 {@code removeAll()} 再逐张加回，所以动画期间
     * 面板会短暂变空，此时默认的 preferred height 只剩边框（十来个像素）。卡牌栏位于
     * {@link JScrollPane} 内部，而 JScrollPane 是校验根（{@code isValidateRoot() == true}），
     * 卡牌加回来时补发的 {@code revalidate()} 只会在滚动面板内部生效、不会向上冒泡，
     * 外层图表就把“空面板”时算出的高度一直保留下来 —— 表现就是刷新后卡牌栏被压成一条细缝。
     * 高度稳定之后，无论哪一次布局看到的都是卡牌应有的高度。
     */
    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        return new Dimension(size.width, Math.max(size.height, stableHeight()));
    }

    @Override
    public Dimension getMinimumSize() {
        Dimension size = super.getMinimumSize();
        return new Dimension(size.width, Math.max(size.height, stableHeight()));
    }

    /** 边框内边距 + 最高的一张卡牌。 */
    private int stableHeight() {
        Insets insets = getInsets();
        int cardHeight = 0;
        for (CardView card : cardViews) {
            cardHeight = Math.max(cardHeight, card.getPreferredSize().height);
        }
        return insets.top + insets.bottom + cardHeight;
    }

    public void addCard(DataSet dataSet) {
        CardView cardView = new CardView(dataSet);
        cardViews.add(cardView);
        add(cardView);
        add(Box.createHorizontalStrut(10)); // 卡牌间距
        revalidate();
        repaint();

        // 触发事件通知（供CardDragController监听）
        firePropertyChange("cardAdded", null, cardView);
    }

    /**
     * 只显示指定类型的卡牌，并播放"从左往右发牌"的动画。
     *
     * @param filterType 数据类型；null 表示显示全部（All）
     */
    public void showCards(DataSet.DataType filterType) {
        stopDealing();

        // 先让所有卡牌处于"已就位"，避免复用时残留动画状态
        for (CardView card : cardViews) {
            card.setAppearProgress(1f);
        }

        removeAll();

        dealingCards.clear();
        for (CardView card : cardViews) {
            if (filterType == null || card.getDataSet().getType() == filterType) {
                dealingCards.add(card);
            }
        }

        dealIndex = 0;
        lastDealTime = 0L;

        if (dealingCards.isEmpty()) {
            revalidate();
            repaint();
            return;
        }
        // 待发牌的卡牌从 0 进度开始，逐张滑入
        for (CardView card : dealingCards) {
            card.setAppearProgress(0f);
        }

        dealTimer = new Timer(ANIM_TICK_MS, e -> tickDeal());
        dealTimer.start();
        revalidate();
        repaint();
    }

    private void tickDeal() {
        long now = System.currentTimeMillis();

        // 到间隔就放下下一张牌（从左往右）
        if (dealIndex < dealingCards.size() && now - lastDealTime >= DEAL_INTERVAL_MS) {
            CardView card = dealingCards.get(dealIndex++);
            add(card);
            add(Box.createHorizontalStrut(10));
            lastDealTime = now;
            revalidate();
        }

        // 推进已显示卡牌的动画进度
        boolean animating = false;
        for (CardView card : dealingCards) {
            if (card.getParent() == this && card.getAppearProgress() < 1f) {
                card.setAppearProgress(card.getAppearProgress() + ANIM_STEP);
                animating = true;
            }
        }
        repaint();

        if (dealIndex >= dealingCards.size() && !animating) {
            stopDealing();
        }
    }

    private void stopDealing() {
        if (dealTimer != null) {
            dealTimer.stop();
            dealTimer = null;
        }
    }

    public void removeCard(String dataSetId) {
        stopDealing();
        for (int i = cardViews.size() - 1; i >= 0; i--) {
            CardView cardView = cardViews.get(i);
            if (cardView.getDataSet().getId().equals(dataSetId)) {
                cardViews.remove(i);
                dealingCards.remove(cardView);
                remove(cardView);
                // 移除后面的间距
                if (i < getComponentCount() - 1) {
                    Component next = getComponent(i);
                    if (next instanceof Box.Filler) {
                        remove(next);
                    }
                }
                break;
            }
        }
        revalidate();
        repaint();
    }

    public List<CardView> getCardViews() {
        return new ArrayList<>(cardViews);
    }

    public CardView getCardView(String dataSetId) {
        for (CardView cardView : cardViews) {
            if (cardView.getDataSet().getId().equals(dataSetId)) {
                return cardView;
            }
        }
        return null;
    }

    public CardView getCardViewAt(Point p) {
        // 只检查当前实际显示的卡牌，避免已隐藏卡牌的旧 bounds 误命中
        for (Component comp : getComponents()) {
            if (comp instanceof CardView) {
                CardView cardView = (CardView) comp;
                if (cardView.getBounds().contains(p)) {
                    return cardView;
                }
            }
        }
        return null;
    }
}
