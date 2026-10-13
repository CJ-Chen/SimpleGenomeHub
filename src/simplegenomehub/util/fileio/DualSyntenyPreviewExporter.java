package simplegenomehub.util.fileio;

import JJpolt2.Example.DualSyntenyPlotter;
import jjplot2.JJplot2GUI;
import java.awt.EventQueue;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

/** Exports only the result belonging to the requested plotting instance. */
public final class DualSyntenyPreviewExporter {
    public static final String PREVIEW_FILE_NAME = "preview.png";
    private DualSyntenyPreviewExporter() { }

    public static File exportPreview(File outputDir, DualSyntenyPlotter plotter) throws IOException {
        if (outputDir == null || !outputDir.isDirectory()) throw new IOException("Invalid output directory");
        JJplot2GUI gui = plotter == null ? null : plotter.getResultGui();
        if (gui == null) throw new IOException("Dual Synteny plotting did not return a canvas");
        File preview = getPreviewFile(outputDir);
        savePreview(gui, preview);
        return preview;
    }

    public static File exportPreviewFromOutputDirectory(File outputDir, boolean reorderChromosomes) throws Exception {
        DualSyntenyPlotter plotter = DualSyntenyPlotLauncher.createPlotterFromOutputDirectory(outputDir, reorderChromosomes);
        try {
            plotter.plot();
            return exportPreview(outputDir, plotter);
        } finally {
            JJplot2GUI gui = plotter.getResultGui();
            if (gui != null) EventQueue.invokeLater(() -> gui.getF().dispose());
        }
    }

    public static File getPreviewFile(File outputDir) { return new File(outputDir, PREVIEW_FILE_NAME); }

    private static void savePreview(JJplot2GUI plotGui, File previewFile) throws IOException {
        if (plotGui == null) {
            throw new IllegalArgumentException("Dual Synteny plot GUI is required.");
        }
        if (previewFile == null) {
            throw new IllegalArgumentException("Preview file is required.");
        }

        if (EventQueue.isDispatchThread()) {
            plotGui.saveImageAsPNG(previewFile.getAbsolutePath());
            return;
        }

        try {
            EventQueue.invokeAndWait(() -> {
                try {
                    plotGui.saveImageAsPNG(previewFile.getAbsolutePath());
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while exporting Dual Synteny preview image.", ex);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException && cause.getCause() instanceof IOException) {
                throw (IOException) cause.getCause();
            }
            throw new IOException("Failed to export Dual Synteny preview image.", cause != null ? cause : ex);
        }
    }

}
