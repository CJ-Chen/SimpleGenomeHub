package simplegenomehub.population.fst.chart.controller;

import simplegenomehub.population.fst.chart.model.DataSet;
import simplegenomehub.population.fst.chart.view.CardLoad;
import simplegenomehub.population.fst.chart.view.CardView;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;

/**
 * 幽灵卡牌覆盖层：拖拽时显示的半透明卡牌
 */
public class GhostCardOverlay extends JComponent {
    private static final int CARD_WIDTH = 150;
    private static final int CARD_HEIGHT = 60;
    private static final int CORNER_RADIUS = 8;
    
    private DataSet dataSet;
    private Point location;
    private boolean visible = false;
    
    public GhostCardOverlay() {
        this.dataSet = null;
        this.location = new Point(0, 0);
        this.visible = false;
        setOpaque(false);
    }
    
    public void setDataSet(DataSet dataSet) {
        this.dataSet = dataSet;
    }
    
    public DataSet getDataSet() {
        return dataSet;
    }
    
    public void setLocation(Point location) {
        this.location = location;
    }
    
    public Point getLocation() {
        return location != null ? new Point(location) : new Point(0, 0);
    }
    
    public void setVisible(boolean visible) {
        this.visible = visible;
    }
    
    public boolean isVisible() {
        return visible;
    }
    
    @Override
    public void paintComponent(Graphics g) {
        if (dataSet == null) return;
        
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 设置半透明
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
        
        int x = location != null ? location.x : 0;
        int y = location != null ? location.y : 0;
        
        // 绘制圆角矩形背景
        RoundRectangle2D rect = new RoundRectangle2D.Float(x, y, CARD_WIDTH - 1, CARD_HEIGHT - 1, 
                                                           CORNER_RADIUS, CORNER_RADIUS);
        g2.setColor(new Color(255, 255, 255, 200));
        g2.fill(rect);
        
        // 边框
        g2.setColor(new Color(100, 100, 100, 200));
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(rect);
        
        // 绘制文件名（优先自定义显示名，便于拖拽时识别）
        g2.setColor(new Color(0, 0, 0, 200));
        g2.setFont(simplegenomehub.population.fst.FstUiStyle.sans(11, Font.PLAIN));
        FontMetrics fm = g2.getFontMetrics();
        String text = CardLoad.getCardDisplayName(dataSet);
        int textWidth = fm.stringWidth(text);
        int maxWidth = CARD_WIDTH - 16;
        if (textWidth > maxWidth) {
            while (fm.stringWidth(text + "...") > maxWidth && text.length() > 0) {
                text = text.substring(0, text.length() - 1);
            }
            text = text + "...";
        }
        int textX = x + 8;
        int textY = y + CARD_HEIGHT / 2 + fm.getAscent() / 2 - 2;
        g2.drawString(text, textX, textY);
        
        g2.dispose();
    }
}
