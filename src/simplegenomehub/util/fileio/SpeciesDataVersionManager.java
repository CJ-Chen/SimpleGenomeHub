package simplegenomehub.util.fileio;

import java.io.File;

/** Historical API retained for compatibility. Both layouts are resolved in place. */
public final class SpeciesDataVersionManager {
    private SpeciesDataVersionManager() { }
    /** Opening data must never move results or stamp statistics. */
    @Deprecated
    public static void migrateLegacyDataIfNeeded(File dataRootDir) {
        // Deliberately read-only; AnalysisDirectoryResolver handles legacy directories.
    }
}
