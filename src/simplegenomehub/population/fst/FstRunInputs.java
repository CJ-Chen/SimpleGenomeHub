package simplegenomehub.population.fst;

import java.io.File;

/** Resolves copied run manifests without silently falling back to another data root. */
public final class FstRunInputs {
    private FstRunInputs() { }

    public static File resolveVcf(String recordedVcf, String recordedGenome, String recordedAnnotation,
                                  File currentSpeciesDirectory, File currentVcfDirectory) {
        if (recordedVcf == null || recordedVcf.trim().isEmpty()) return null;
        String normalized = recordedVcf.replace('\\', '/');
        int marker = normalized.lastIndexOf("/Population/VCF/");
        if (marker >= 0 && currentVcfDirectory != null) {
            String unit = normalized.substring(0, marker);
            String name = normalized.substring(marker + "/Population/VCF/".length());
            boolean belongsToRecordedUnit = under(recordedGenome, unit) || under(recordedAnnotation, unit);
            if (blank(recordedGenome) && blank(recordedAnnotation) && currentSpeciesDirectory != null)
                belongsToRecordedUnit = unit.endsWith("/" + currentSpeciesDirectory.getName());
            if (belongsToRecordedUnit && !name.isEmpty() && name.indexOf('/') < 0
                    && !name.equals(".") && !name.equals("..")) {
                // Return the new path even when missing. The caller must report it;
                // an existing file at the old root is never a substitute.
                return new File(currentVcfDirectory, name);
            }
        }
        return new File(recordedVcf); // An explicitly external/temporary VCF keeps its meaning.
    }

    private static boolean under(String path, String unit) {
        return !blank(path) && path.replace('\\', '/').startsWith(unit + "/");
    }
    private static boolean blank(String text) { return text == null || text.trim().isEmpty(); }
}
