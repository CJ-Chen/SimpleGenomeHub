package simplegenomehub.population.fst.chart.view;

import java.awt.Color;

/**
 * 根据索引生成清淡的随机颜色
 */
public class ColorHelper {
    public static Color getLightRandomColor(int index) {
        // 使用索引作为种子，确保相同索引总是生成相同颜色
        java.util.Random random = new java.util.Random(index * 12345);
        
        // 生成清淡的颜色（RGB值在180-255之间）
        int r = 180 + random.nextInt(76); // 180-255
        int g = 180 + random.nextInt(76); // 180-255
        int b = 180 + random.nextInt(76); // 180-255
        
        return new Color(r, g, b);
    }
}
