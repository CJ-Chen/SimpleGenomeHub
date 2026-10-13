package simplegenomehub.population.fst.view;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import javax.imageio.ImageIO;

import de.erichseifert.vectorgraphics2d.Document;
import de.erichseifert.vectorgraphics2d.Processor;
import de.erichseifert.vectorgraphics2d.Processors;
import de.erichseifert.vectorgraphics2d.VectorGraphics2D;
import de.erichseifert.vectorgraphics2d.VectorHints;
import de.erichseifert.vectorgraphics2d.util.PageSize;

/**
 * 可视化区域导出：PNG / SVG / PDF。
 * <p>
 * SVG/PDF 依赖 lib/VectorGraphics2D-0.13.jar（de.erichseifert.vectorgraphics2d）。
 */
public class ImageExporter {

    /** 支持的导出格式 */
    public enum Format {
        PNG("png", ".png"),
        SVG("svg", ".svg"),
        PDF("pdf", ".pdf");

        private final String processorName;
        private final String extension;

        Format(String processorName, String extension) {
            this.processorName = processorName;
            this.extension = extension;
        }

        public String getExtension() {
            return extension;
        }
    }

    /** 纯图渲染回调：把"干净图"画到给定 Graphics2D 上 */
    public interface FigureRenderer {
        void render(Graphics2D g, int width, int height);
    }

    /**
     * 导出"干净图"（不带交互元件），尺寸由调用方给定。
     */
    public static void exportFigure(FigureRenderer renderer, int width, int height,
                                    Format format, File outFile) throws IOException {
        if (renderer == null) throw new IOException("Nothing to export.");
        int w = Math.max(1, width);
        int h = Math.max(1, height);

        if (format == Format.PNG) {
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            try {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, w, h);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                renderer.render(g, w, h);
            } finally {
                g.dispose();
            }
            ImageIO.write(image, "png", outFile);
        } else {
            VectorGraphics2D g = new VectorGraphics2D();
            g.setRenderingHint(VectorHints.KEY_EXPORT, VectorHints.VALUE_EXPORT_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            renderer.render(g, w, h);

            Processor processor = Processors.get(format.processorName);
            Document document = processor.getDocument(g.getCommands(), new PageSize(w, h));
            try (OutputStream out = new FileOutputStream(outFile)) {
                document.writeTo(out);
            }
        }
    }

    /**
     * 把组件渲染并导出到文件。
     *
     * @param component 要导出的组件（使用其当前尺寸）
     * @param format    导出格式
     * @param outFile   目标文件
     */
    public static void export(Component component, Format format, File outFile) throws IOException {
        if (component == null) {
            throw new IOException("No component to export.");
        }
        int width = Math.max(1, component.getWidth());
        int height = Math.max(1, component.getHeight());

        if (format == Format.PNG) {
            exportPng(component, outFile, width, height);
        } else {
            exportVector(component, format, outFile, width, height);
        }
    }

    private static void exportPng(Component component, File outFile, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            component.paint(g);
        } finally {
            g.dispose();
        }
        ImageIO.write(image, "png", outFile);
    }

    private static void exportVector(Component component, Format format, File outFile,
                                     int width, int height) throws IOException {
        VectorGraphics2D g = new VectorGraphics2D();
        g.setRenderingHint(VectorHints.KEY_EXPORT, VectorHints.VALUE_EXPORT_QUALITY);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        component.paint(g);

        Processor processor = Processors.get(format.processorName);
        Document document = processor.getDocument(g.getCommands(), new PageSize(width, height));
        try (OutputStream out = new FileOutputStream(outFile)) {
            document.writeTo(out);
        }
    }
}
