package simplegenomehub.util.fileio;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import simplegenomehub.model.SpeciesInfo;

/** Presents old and current analysis layouts without moving or renaming any result. */
public final class AnalysisDirectoryResolver {
    private AnalysisDirectoryResolver() { }
    public static boolean isAnalysisType(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.equals("advancecircos") || lower.equals("genomecompare") || lower.equals("multiplecompare");
    }
    public static List<File> existingRoots(SpeciesInfo species) {
        List<File> result = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        if (species == null) return result;
        addChildren(species.getGenomeAnalysisDir(), false, result, identities);
        addChildren(species.getFunctionalAnnotationDir(), true, result, identities);
        return result;
    }
    private static void addChildren(File parent, boolean legacy, List<File> result, Set<String> identities) {
        if (parent == null) return;
        File[] dirs = parent.listFiles(file -> file.isDirectory() && (!legacy || isAnalysisType(file.getName())));
        if (dirs == null) return;
        Arrays.sort(dirs, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File dir : dirs) {
            String identity;
            try { identity = dir.getCanonicalPath(); } catch (IOException e) { identity = dir.getAbsolutePath(); }
            if (identities.add(identity)) result.add(dir);
        }
    }
}
