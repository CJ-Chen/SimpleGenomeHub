package simplegenomehub.population.fst.chart.view;

import simplegenomehub.population.fst.chart.model.DataSet;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

// 卡牌绘制器：负责卡牌的绘制逻辑
public class CardView2 {
    
    // 绘制卡牌背景
    public static void drawCardBackground(Graphics2D g, int x, int y, int cardWidth, int cardHeight, boolean isDragging) {
        if (g == null) return;
        
        RoundRectangle2D cardRect = new RoundRectangle2D.Float(x, y, cardWidth - 1, cardHeight - 1, 6, 6);
        
        if (isDragging) {
            g.setColor(new Color(255, 255, 255, 200));
        } else {
            g.setColor(new Color(255, 255, 255));
        }
        g.fill(cardRect);
        
        if (isDragging) {
            g.setColor(simplegenomehub.population.fst.FstUiStyle.CARD_BORDER);
        } else {
            g.setColor(simplegenomehub.population.fst.FstUiStyle.CARD_BORDER);
        }
        g.setStroke(new BasicStroke(1.5f));
        g.draw(cardRect);
    }
    
    // 绘制卡牌上的数字标识（带圆形背景）- 使用扩展名+数字
    public static void drawCardNumber(Graphics2D g, DataSet dataSet, int x, int y, int cardWidth, boolean isDragging) {
        if (g == null || dataSet == null) return;
        
        int number = dataSet.getIndex();
        if (number <= 0) return;
        
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        
        try {
            int circleSize = 32;
            int circleX = x + cardWidth - circleSize - 8;
            int circleY = y + 8;
            
            Color bgColor = ColorHelper.getLightRandomColor(number);
            if (isDragging) {
                g2.setColor(new Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), 200));
            } else {
                g2.setColor(bgColor);
            }
            g2.fillOval(circleX, circleY, circleSize, circleSize);
            
            if (isDragging) {
                g2.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_SECONDARY);
            } else {
                g2.setColor(simplegenomehub.population.fst.FstUiStyle.TEXT_SECONDARY);
            }
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval(circleX, circleY, circleSize, circleSize);
            
            // 获取文件扩展名并分别绘制
            String fileName = dataSet.getFileName();
            String extension = getFileExtensionForDisplay(dataSet).toUpperCase();
            String numberStr = String.valueOf(number);
            
            // 设置字体
            Font extensionFont = simplegenomehub.population.fst.FstUiStyle.sans(12, Font.BOLD);
            Font numberFont = simplegenomehub.population.fst.FstUiStyle.sans(15, Font.BOLD);
            FontMetrics extensionFm = g2.getFontMetrics(extensionFont);
            FontMetrics numberFm = g2.getFontMetrics(numberFont);
            
            // 计算总宽度
            int extensionWidth = extensionFm.stringWidth(extension);
            int numberWidth = numberFm.stringWidth(numberStr);
            int totalWidth = extensionWidth + numberWidth;
            
            // 计算起始位置（居中）
            int startX = circleX + (circleSize - totalWidth) / 2;
            int baselineY = circleY + (circleSize + numberFm.getAscent()) / 2 - 2;
            
            // 设置颜色
            Color textColor;
            if (isDragging) {
                textColor = new Color(0, 0, 0, 200);
            } else {
                textColor = Color.BLACK;
            }
            
            // 绘制扩展名
            g2.setFont(extensionFont);
            g2.setColor(textColor);
            g2.drawString(extension, startX, baselineY);
            
            // 绘制数字
            g2.setFont(numberFont);
            g2.drawString(numberStr, startX + extensionWidth, baselineY);
        } finally {
            g2.dispose();
        }
    }
    
    // 绘制卡牌名称（在圆形左侧多行显示，优先自定义的Compare/类群名称）
    public static void drawCardName(Graphics2D g, DataSet dataSet, int x, int y, int cardWidth) {
        if (g == null || dataSet == null) return;
        
        String cardName = CardLoad.getCardDisplayName(dataSet);
        if (cardName == null || cardName.isEmpty()) return;
        
        int circleSize = 32;
        int circleX = x + cardWidth - circleSize - 8;
        int circleY = y + 8;
        
        // 只显示卡牌名称（多行显示）
        g.setColor(Color.BLACK);
        g.setFont(simplegenomehub.population.fst.FstUiStyle.sans(11, Font.PLAIN));
        FontMetrics nameFm = g.getFontMetrics();
        int lineHeight = nameFm.getHeight();
        int nameX = x + 5; // 圆形左侧
        int nameStartY = circleY + 5; // 从圆形顶部开始
        
        // 多行显示卡牌名称
        drawMultilineCardName(g, cardName, nameX, nameStartY, circleX - nameX - 5, lineHeight);
    }
    
    // 获取文件扩展名（最后一个.后面的内容）
    private static String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) return "";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0 || lastDot >= fileName.length() - 1) return "";
        return fileName.substring(lastDot + 1);
    }
    
    // 根据数据类型获取用于显示的扩展名
    private static String getFileExtensionForDisplay(DataSet dataSet) {
        if (dataSet == null) return "";
        
        // 对于TajimaD类型，返回"TD"
        if (dataSet.getType() == DataSet.DataType.TAJIMAD) {
            return "TD";
        }
        
        // 对于Logistic类型，返回"GWAS"
        if (dataSet.getType() == DataSet.DataType.LOGISTIC) {
            return "GWAS";
        }
        
        // 对于其他类型，使用文件扩展名
        String fileName = dataSet.getFileName();
        return getFileExtension(fileName);
    }
    
    // 多行绘制卡牌名称
    private static void drawMultilineCardName(Graphics2D g2, String text, int x, int y, int maxWidth, int lineHeight) {
        if (text == null || text.isEmpty()) return;
        
        FontMetrics fm = g2.getFontMetrics();
        String[] words = text.split("_"); // 按_分割
        
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
