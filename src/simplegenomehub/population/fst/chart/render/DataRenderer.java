package simplegenomehub.population.fst.chart.render;

import simplegenomehub.population.fst.chart.model.PlotItem;
import simplegenomehub.population.fst.chart.model.GenomeContext;
import java.awt.Graphics2D;
import java.awt.Point;

/**
 * 数据渲染器接口
 */
public interface DataRenderer {
    /**
     * 渲染数据
     */
    void render(Graphics2D g, PlotContext ctx, GenomeContext genome, PlotItem item, 
                java.util.Map<String, Object> renderState);
    
    /**
     * 命中测试：返回鼠标位置对应的数据点信息
     */
    HitResult hitTest(Point p, PlotContext ctx, GenomeContext genome, PlotItem item);
    
    /**
     * 命中结果
     */
    class HitResult {
        public final int originalIndex;
        public final boolean isTop;
        
        public HitResult(int originalIndex, boolean isTop) {
            this.originalIndex = originalIndex;
            this.isTop = isTop;
        }
        
        public static HitResult none() {
            return new HitResult(-1, false);
        }
    }
}
