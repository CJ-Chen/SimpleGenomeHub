package simplegenomehub.util.fileio;

import biocjava.bioDoer.JIGplotToolkit.Circos.SuperCircos.AmazingSuperCircos2;
import jigplot.engine.JIGBasePanel;

import java.io.File;
import java.io.IOException;

/**
 * Exports a full-canvas preview image for an Advanced Circos result folder.
 */
public final class AdvancedCircosPreviewExporter {

    public static final String PREVIEW_FILE_NAME = "preview.png";

    private AdvancedCircosPreviewExporter() {
    }

    public static File exportPreview(AmazingSuperCircos2 circos, File projectDir) throws IOException {
        if (circos == null) {
            throw new IllegalArgumentException("Circos instance is required.");
        }
        if (projectDir == null || !projectDir.isDirectory()) {
            throw new IllegalArgumentException("Project directory is invalid.");
        }

        File previewFile = getPreviewFile(projectDir);
        JIGBasePanel basePanel = circos.getResultPanel();
        if (basePanel == null) {
            throw new IOException("Failed to resolve Advanced Circos canvas for preview export.");
        }

        basePanel.save2PNG(previewFile);
        return previewFile;
    }

    public static File getPreviewFile(File projectDir) {
        return new File(projectDir, PREVIEW_FILE_NAME);
    }

}
