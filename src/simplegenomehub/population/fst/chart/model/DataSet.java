package simplegenomehub.population.fst.chart.model;

import simplegenomehub.population.fst.chart.FSTWindow;
import java.util.List;

/**
 * 数据集：代表一个输入文件的数据（如FST、Pi、D等）
 */
public class DataSet {
    private final String id;
    private final String name;
    private final DataType type;
    private final List<FSTWindow> windows; // 目前只支持FST，后续可扩展
    private final int index; // 文件读取顺序索引（从1开始）
    private final String displayName; // 卡牌显示名（Compare/类群自定义名；null 时回退到文件名解析）
    private final String description; // 悬停明细说明（可为null）
    
    public DataSet(String id, String name, DataType type, List<FSTWindow> windows) {
        this(id, name, type, windows, 0, null, null);
    }
    
    public DataSet(String id, String name, DataType type, List<FSTWindow> windows, int index) {
        this(id, name, type, windows, index, null, null);
    }

    public DataSet(String id, String name, DataType type, List<FSTWindow> windows, int index,
                   String displayName, String description) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.windows = windows;
        this.index = index;
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getId() { return id; }
    public String getName() { return name; }
    public DataType getType() { return type; }
    public List<FSTWindow> getWindows() { return windows; }
    public int getIndex() { return index; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    
    /**
     * 获取文件名（不包含路径）
     */
    public String getFileName() {
        if (name == null) return "";
        int lastSeparator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (lastSeparator >= 0 && lastSeparator < name.length() - 1) {
            return name.substring(lastSeparator + 1);
        }
        return name;
    }
    
    public int size() {
        return windows == null ? 0 : windows.size();
    }
    
    public FSTWindow get(int index) {
        return windows == null || index < 0 || index >= windows.size() ? null : windows.get(index);
    }
    
    public double getValue(int index) {
        FSTWindow w = get(index);
        return w == null ? Double.NaN : w.getAvgFst();
    }
    
    public enum DataType {
        FST, PI, D, PBS, TAJIMAD, LOGISTIC, OTHER
    }
}
