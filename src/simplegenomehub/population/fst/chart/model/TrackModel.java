package simplegenomehub.population.fst.chart.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 轨道模型：表示一个标签纸轨道
 */
public class TrackModel {
    /** 轨道（大卡牌）默认高度 */
    public static final int DEFAULT_HEIGHT = 250;
    public static final int MIN_HEIGHT = 90;
    public static final int MAX_HEIGHT = 800;

    private final String id;
    private String title;
    private final List<PlotItem> items;
    private TrackModelListener listener;
    private int height = DEFAULT_HEIGHT;   // 大卡牌高度（可拖拽上下边界调整）
    private boolean collapsed = false;     // 折叠：Y轴按数据最大值收紧
    
    public interface TrackModelListener {
        void onItemsChanged();
    }
    
    public TrackModel(String id) {
        this.id = id;
        this.items = new ArrayList<>();
    }
    
    public String getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    /** 大卡牌高度（可拖拽上下边界调整） */
    public int getHeight() { return height; }
    public void setHeight(int height) {
        int h = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, height));
        if (h != this.height) {
            this.height = h;
            notifyListener();
        }
    }

    /** 是否折叠（Y轴只画到比数据最大值大的刻度） */
    public boolean isCollapsed() { return collapsed; }
    public void setCollapsed(boolean collapsed) {
        if (this.collapsed != collapsed) {
            this.collapsed = collapsed;
            notifyListener();
        }
    }
    
    public void setListener(TrackModelListener listener) {
        this.listener = listener;
    }
    
    public List<PlotItem> getItems() {
        return new ArrayList<>(items);
    }
    
    public void addItem(PlotItem item) {
        if (item != null && !items.contains(item)) {
            items.add(item);
            notifyListener();
        }
    }
    
    public void removeItem(PlotItem item) {
        if (items.remove(item)) {
            notifyListener();
        }
    }
    
    public void removeItem(String dataSetId) {
        if (items.removeIf(item -> item.getDataSetId().equals(dataSetId))) {
            notifyListener();
        }
    }
    
    public void moveItem(int fromIndex, int toIndex) {
        if (fromIndex < 0 || fromIndex >= items.size() ||
            toIndex < 0 || toIndex >= items.size() ||
            fromIndex == toIndex) {
            return;
        }
        PlotItem item = items.remove(fromIndex);
        items.add(toIndex, item);
        notifyListener();
    }
    
    public void moveItemToPosition(PlotItem item, int targetIndex) {
        int currentIndex = items.indexOf(item);
        if (currentIndex < 0) return;
        moveItem(currentIndex, targetIndex);
    }
    
    public boolean isEmpty() {
        return items.isEmpty();
    }
    
    private void notifyListener() {
        if (listener != null) {
            listener.onItemsChanged();
        }
    }
}
