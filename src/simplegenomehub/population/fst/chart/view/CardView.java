package simplegenomehub.population.fst.chart.view;

import simplegenomehub.population.fst.chart.model.DataSet;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;

/**
 * 卡牌视图：显示一个数据集
 */
public class CardView extends JComponent {
    private static final int CARD_WIDTH = 120;  // 宽度
    private static final int CARD_HEIGHT = 150; // 高度大于宽度
    private static final int CORNER_RADIUS = 8;
    
    private static final int DEAL_SLIDE_DISTANCE = 40; // 发牌时从左侧滑入的距离(px)
    
    private final DataSet dataSet;
    private boolean hovered = false;
    /** 发牌动画进度：0=刚出现(左侧、半透明)，1=完全就位 */
    private float appearProgress = 1f;
    
    public CardView(DataSet dataSet) {
        this.dataSet = dataSet;
        setPreferredSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
        setMinimumSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
        setMaximumSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
        // 悬停提示：显示自定义名称 + 具体哪些类群参与比较的说明
        setToolTipText(CardLoad.getCardToolTip(dataSet));
    }
    
    public DataSet getDataSet() {
        return dataSet;
    }
    
    public void setHovered(boolean hovered) {
        this.hovered = hovered;
        repaint();
    }
    
    /** 设置发牌动画进度（0~1） */
    public void setAppearProgress(float progress) {
        if (progress < 0f) progress = 0f;
        if (progress > 1f) progress = 1f;
        if (Math.abs(progress - appearProgress) < 0.001f) return;
        this.appearProgress = progress;
        repaint();
    }
    
    public float getAppearProgress() {
        return appearProgress;
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 发牌动画：未就位时从左侧滑入并淡入
        if (appearProgress < 1f) {
            float p = Math.max(0f, appearProgress);
            g2.translate(-(1f - p) * DEAL_SLIDE_DISTANCE, 0f);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0.12f, p)));
        }
        
        int w = getWidth();
        int h = getHeight();
        
        // 绘制圆角矩形背景
        RoundRectangle2D rect = new RoundRectangle2D.Float(0, 0, w - 1, h - 1, CORNER_RADIUS, CORNER_RADIUS);
        
        // 背景色（根据hover状态）
        if (hovered) {
            g2.setColor(simplegenomehub.population.fst.FstUiStyle.SELECTION); // 浅蓝色
        } else {
            g2.setColor(new Color(255, 255, 255)); // 白色
        }
        g2.fill(rect);
        
        // 边框
        g2.setColor(simplegenomehub.population.fst.FstUiStyle.CARD_BORDER);
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(rect);
        
        // 绘制数字标识（带圆形背景）- 在上方居中
        int number = dataSet.getIndex();
        // 圆形背景大小（在if块外定义，确保后续代码可以访问）
        int circleSize = 40;
        int circleX = (w - circleSize) / 2; // 居中
        int circleY = 15; // 上方
        
        if (number > 0) {
            // 生成清淡的随机颜色（基于索引）
            Color bgColor = ColorHelper.getLightRandomColor(number);
            g2.setColor(bgColor);
            g2.fillOval(circleX, circleY, circleSize, circleSize);
            
            // 绘制圆形边框
            g2.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_SECONDARY);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval(circleX, circleY, circleSize, circleSize);
            
            // 绘制数字（扩展名+数字，分别绘制）
            String fileName = dataSet.getFileName();
            String extension = getFileExtensionForDisplay(dataSet).toUpperCase();
            String numberStr = String.valueOf(number);
            
            // 设置字体
            Font extensionFont = simplegenomehub.population.fst.FstUiStyle.sans(12, Font.BOLD);
            Font numberFont = simplegenomehub.population.fst.FstUiStyle.sans(18, Font.BOLD);
            FontMetrics extensionFm = g2.getFontMetrics(extensionFont);
            FontMetrics numberFm = g2.getFontMetrics(numberFont);
            
            // 计算总宽度
            int extensionWidth = extensionFm.stringWidth(extension);
            int numberWidth = numberFm.stringWidth(numberStr);
            int totalWidth = extensionWidth + numberWidth;
            
            // 计算起始位置（居中）
            int startX = circleX + (circleSize - totalWidth) / 2;
            int baselineY = circleY + (circleSize + numberFm.getAscent()) / 2 - 2;
            
            // 绘制扩展名（字号12）
            g2.setFont(extensionFont);
            g2.setColor(Color.BLACK);
            g2.drawString(extension, startX, baselineY);
            
            // 绘制数字（字号18）
            g2.setFont(numberFont);
            g2.drawString(numberStr, startX + extensionWidth, baselineY);
        }
        
        // 绘制卡牌名称（优先自定义的Compare/类群名称，无清单时回退文件名解析，多行显示）
        String cardName = CardLoad.getCardDisplayName(dataSet);
        if (cardName != null && !cardName.isEmpty()) {
            g2.setColor(Color.BLACK);
            g2.setFont(simplegenomehub.population.fst.FstUiStyle.sans(11, Font.PLAIN));
            FontMetrics fm = g2.getFontMetrics();
            int lineHeight = fm.getHeight();
            int textX = 8;
            int textY = circleY + circleSize + 20; // 在圆形下方
            
            // 多行显示文本
            drawMultilineText(g2, cardName, textX, textY, w - 16, lineHeight);
        }
        
        g2.dispose();
    }
    
    // 获取文件扩展名（最后一个.后面的内容）
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) return "";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0 || lastDot >= fileName.length() - 1) return "";
        return fileName.substring(lastDot + 1);
    }
    
    // 根据数据类型获取用于显示的扩展名
    private String getFileExtensionForDisplay(DataSet dataSet) {
        if (dataSet == null) return "";
        
        // 对于TajimaD类型，返回"TD"
        if (dataSet.getType() == DataSet.DataType.TAJIMAD) {
            return "TD";
        }
        
        // 对于其他类型，使用文件扩展名
        String fileName = dataSet.getFileName();
        return getFileExtension(fileName);
    }
    
    // 多行绘制文本
    private void drawMultilineText(Graphics2D g2, String text, int x, int y, int maxWidth, int lineHeight) {
        if (text == null || text.isEmpty()) return;
        
        FontMetrics fm = g2.getFontMetrics();
        String[] words = text.split("_"); // 按_分割，方便换行
        
        int currentX = x;
        int currentY = y;
        
        for (String word : words) {
            int wordWidth = fm.stringWidth(word);
            
            // 如果当前行放不下这个词，换行
            if (currentX > x && currentX + wordWidth > x + maxWidth) {
                currentX = x;
                currentY += lineHeight;
            }
            
            // 如果单个词就超过最大宽度，强制显示
            if (wordWidth > maxWidth) {
                // 截断单词
                String displayWord = word;
                while (fm.stringWidth(displayWord + "...") > maxWidth && displayWord.length() > 0) {
                    displayWord = displayWord.substring(0, displayWord.length() - 1);
                }
                displayWord = displayWord + "...";
                g2.drawString(displayWord, currentX, currentY);
                currentX = x;
                currentY += lineHeight;
            } else {
                g2.drawString(word, currentX, currentY);
                currentX += wordWidth;
                
                // 如果不是最后一个词，添加分隔符
                if (!word.equals(words[words.length - 1])) {
                    int separatorWidth = fm.stringWidth("_");
                    if (currentX + separatorWidth <= x + maxWidth) {
                        g2.drawString("_", currentX, currentY);
                        currentX += separatorWidth;
                    } else {
                        currentX = x;
                        currentY += lineHeight;
                    }
                }
            }
        }
    }
}
