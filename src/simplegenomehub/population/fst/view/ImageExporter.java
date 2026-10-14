package simplegenomehub.population.fst.view;

import com.orsonpdf.PDFDocument;
import com.orsonpdf.PDFGraphics2D;
import com.orsonpdf.PDFHints;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import org.jfree.graphics2d.svg.*;
import simplegenomehub.util.fileio.AtomicFiles;

/** Captures the actual figure on the EDT, then publishes PNG/SVG/PDF atomically. */
public final class ImageExporter {
    private static final double POINTS_PER_MM = 72.0 / 25.4;
    private ImageExporter() { }
    public enum Format {
        PNG(".png"), SVG(".svg"), PDF(".pdf");
        private final String extension;
        Format(String extension) { this.extension = extension; }
        public String getExtension() { return extension; }
    }
    @FunctionalInterface public interface FigureRenderer {
        void render(Graphics2D graphics, int width, int height);
    }
    /** Drawing snapshot: encoders and all file I/O can run off the EDT. */
    public static final class RenderedFigure {
        private final BufferedImage image;
        private final String svg;
        private final PDFDocument pdf;
        private RenderedFigure(BufferedImage image, String svg, PDFDocument pdf) {
            this.image = image; this.svg = svg; this.pdf = pdf;
        }
        public void writeTo(File target) throws IOException {
            if (target == null) throw new IOException("No export destination was selected.");
            Path destination = target.toPath().toAbsolutePath().normalize();
            Path directory = destination.getParent();
            Files.createDirectories(directory);
            AtomicFiles.withLock(destination, () -> {
                Path staged = Files.createTempFile(directory, ".sgh-figure-", ".tmp");
                try {
                    if (image != null) {
                        if (!ImageIO.write(image, "png", staged.toFile())) throw new IOException("A PNG image encoder is unavailable.");
                    } else if (svg != null) Files.writeString(staged, svg, StandardCharsets.UTF_8);
                    else Files.write(staged, pdf.getPDFBytes());
                    AtomicFiles.replace(staged, destination);
                } finally { Files.deleteIfExists(staged); }
                return null;
            });
        }
    }
    /** Capture drawing state on the EDT; the caller owns the subsequent file-writing task. */
    public static RenderedFigure renderFigure(FigureRenderer renderer, int width, int height, Format format) throws IOException {
        if (renderer == null || format == null) throw new IOException("Nothing to export.");
        return onEdt(() -> render(renderer, Math.max(1, width), Math.max(1, height), format));
    }
    /** Compatibility API; Hub UI callers submit this through their background task lease. */
    public static void exportFigure(FigureRenderer renderer, int width, int height, Format format, File target) throws IOException {
        renderFigure(renderer, width, height, format).writeTo(target);
    }
    public static void export(Component component, Format format, File target) throws IOException {
        if (component == null || format == null) throw new IOException("No component or format to export.");
        RenderedFigure captured = onEdt(() -> render((graphics, width, height) -> component.paint(graphics),
            Math.max(1, component.getWidth()), Math.max(1, component.getHeight()), format));
        captured.writeTo(target);
    }
    @FunctionalInterface private interface Rendering<T> { T run(); }
    private static <T> T onEdt(Rendering<T> renderer) throws IOException {
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Runnable capture = () -> { try { value.set(renderer.run()); } catch (Throwable failure) { error.set(failure); } };
        if (SwingUtilities.isEventDispatchThread()) capture.run();
        else {
            try { SwingUtilities.invokeAndWait(capture); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while rendering the figure.", interrupted);
            } catch (InvocationTargetException failure) { throw new IOException("Failed to render the figure.", failure.getCause()); }
        }
        if (error.get() != null) throw new IOException("Failed to render the figure: " + error.get().getMessage(), error.get());
        return value.get();
    }
    private static RenderedFigure render(FigureRenderer renderer, int width, int height, Format format) {
        if (format == Format.PNG) {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            try { prepare(graphics, width, height); renderer.render(graphics, width, height); }
            finally { graphics.dispose(); }
            return new RenderedFigure(image, null, null);
        }
        if (format == Format.SVG) {
            SVGGraphics2D graphics = new SVGGraphics2D(width, height);
            try {
                prepare(graphics, width, height);
                graphics.setRenderingHint(SVGHints.KEY_DRAW_STRING_TYPE, SVGHints.VALUE_DRAW_STRING_TYPE_STANDARD);
                graphics.setRenderingHint(SVGHints.KEY_IMAGE_HANDLING, SVGHints.VALUE_IMAGE_HANDLING_EMBED);
                renderer.render(graphics, width, height);
                String element = graphics.getSVGElement(null, false, new ViewBox(0, 0, width, height), null, null);
                String dimensions = "width=\"" + width + "px\" height=\"" + height + "px\" ";
                return new RenderedFigure(null, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + element.replaceFirst("<svg ", "<svg " + dimensions), null);
            } finally { graphics.dispose(); }
        }
        PDFDocument pdf = new PDFDocument();
        pdf.setTitle("Simple Genome Hub FST figure");
        PDFGraphics2D graphics = pdf.createPage(new Rectangle2D.Double(0, 0, width * POINTS_PER_MM, height * POINTS_PER_MM)).getGraphics2D();
        try {
            graphics.scale(POINTS_PER_MM, POINTS_PER_MM);
            prepare(graphics, width, height);
            graphics.setRenderingHint(PDFHints.KEY_DRAW_STRING_TYPE, PDFHints.VALUE_DRAW_STRING_TYPE_VECTOR);
            renderer.render(graphics, width, height);
        } finally { graphics.dispose(); }
        return new RenderedFigure(null, null, pdf);
    }
    private static void prepare(Graphics2D graphics, int width, int height) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
    }
}
