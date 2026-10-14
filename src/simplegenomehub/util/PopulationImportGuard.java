package simplegenomehub.util;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process registry of running population-data imports.
 * <p>
 * A VCF import stages a large file inside its genome directory and publishes it when the
 * statistics are ready. Moving or deleting that directory mid-import would strand the staged
 * file, so the operations that can do that ask this guard first. Imports of different genomes
 * are independent; two imports of the same genome are refused because they would collide on
 * the same file names.
 */
public final class PopulationImportGuard {

    private static final Map<String, String> ACTIVE_IMPORTS = new ConcurrentHashMap<>();

    private PopulationImportGuard() {
    }

    /**
     * Registers an import for the given genome directory.
     *
     * @return false when that directory (or an equivalent path) already has a running import
     */
    public static boolean tryBegin(File speciesDirectory, String description) {
        String key = keyOf(speciesDirectory);
        if (key.isEmpty()) {
            return false;
        }
        return ACTIVE_IMPORTS.putIfAbsent(key, description == null ? "import" : description) == null;
    }

    /** Releases a registration; safe to call more than once. */
    public static void end(File speciesDirectory) {
        String key = keyOf(speciesDirectory);
        if (!key.isEmpty()) {
            ACTIVE_IMPORTS.remove(key);
        }
    }

    public static boolean isBusy(File speciesDirectory) {
        String key = keyOf(speciesDirectory);
        return !key.isEmpty() && ACTIVE_IMPORTS.containsKey(key);
    }

    public static boolean isAnyBusy() {
        return !ACTIVE_IMPORTS.isEmpty();
    }

    /** Message explaining why an operation touching this genome directory was refused. */
    public static String describeBusyFor(File speciesDirectory) {
        String key = keyOf(speciesDirectory);
        String description = key.isEmpty() ? null : ACTIVE_IMPORTS.get(key);
        if (description == null) {
            return "No import is running for this genome.";
        }
        return "A " + description + " is currently running for this genome.";
    }

    /** Message explaining why a global operation was refused. */
    public static String describeAnyBusy() {
        if (ACTIVE_IMPORTS.isEmpty()) {
            return "No import is running.";
        }
        String description = ACTIVE_IMPORTS.values().iterator().next();
        return "A " + description + " is currently running.";
    }

    private static String keyOf(File directory) {
        if (directory == null) {
            return "";
        }
        try {
            return directory.getCanonicalPath();
        } catch (IOException e) {
            return directory.getAbsolutePath();
        }
    }
}
