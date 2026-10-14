package simplegenomehub.gui;

import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.population.fst.FSTRunInfo;
import simplegenomehub.population.fst.FSTRunManifest;
import simplegenomehub.population.fst.ResultDirectoryScanner;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Formats a finished FST run for the Other Data card: the populations with all of their members,
 * the comparison strategy that was run, and the result files it produced.
 * <p>
 * The content comes from {@code run.info.txt} (written next to the results) and, when present,
 * {@code run.names.tsv} (the display names of the produced files).
 */
final class PopulationFstSummarySupport {

    /** Wrapping width for the population member listing, in characters of the monospaced font. */
    private static final int MEMBER_LINE_WIDTH = 56;
    private static final String MEMBER_INDENT = "    ";
    private static final int MAX_LISTED_FILES = 24;

    private static final SimpleDateFormat CREATED_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private PopulationFstSummarySupport() {
    }

    /**
     * True when the folder is an FST result folder, or an FST run / section folder that holds
     * result folders directly below it. Used to decide whether the tree selection is browsable.
     */
    static boolean isFstResultTree(File directory) {
        return !collectResultDirectories(directory).isEmpty();
    }

    /** Summary text for the Other Data card. */
    static String buildSummaryText(SpeciesInfo species, File directory) {
        if (directory == null || !directory.isDirectory()) {
            return "No FST result selected.";
        }

        List<File> resultDirectories = collectResultDirectories(directory);
        if (resultDirectories.isEmpty()) {
            return "No FST result files were found in this folder.\n";
        }

        boolean singleRun = resultDirectories.size() == 1
            && sameFile(resultDirectories.get(0), directory);

        StringBuilder text = new StringBuilder();
        text.append("FST RESULT\n");
        text.append("==========\n");
        text.append("Folder: ").append(directory.getName()).append('\n');
        if (species != null) {
            text.append("Genome: ").append(species.getSpeciesName())
                .append(" (").append(species.getVersion()).append(")\n");
        }
        text.append("Result folder(s): ").append(resultDirectories.size()).append('\n');
        text.append("Location: ").append(directory.getAbsolutePath()).append('\n');

        for (int i = 0; i < resultDirectories.size(); i++) {
            File runDirectory = resultDirectories.get(i);
            if (!singleRun) {
                text.append('\n');
                text.append("--------------------------------------------------------------\n");
                text.append("RUN ").append(i + 1).append(": ")
                    .append(relativeName(directory, runDirectory)).append('\n');
                text.append("--------------------------------------------------------------\n");
            } else {
                text.append('\n');
            }
            appendRunDetail(text, runDirectory);
        }

        if (!singleRun) {
            appendTotals(text, resultDirectories);
        }
        return text.toString();
    }

    // ---- one result folder ----

    private static void appendRunDetail(StringBuilder text, File runDirectory) {
        FSTRunInfo.Info info = FSTRunInfo.read(runDirectory);

        text.append("RUN SETTINGS\n");
        text.append("============\n");
        text.append("Folder: ").append(runDirectory.getName()).append('\n');
        if (info != null && !info.created.isEmpty()) {
            text.append("Created: ").append(formatCreated(info.created)).append('\n');
        }
        text.append("Window: ").append(formatSize(info != null ? info.windowSize : -1))
            .append("    Step: ").append(formatSize(info != null ? info.stepSize : -1)).append('\n');
        if (info != null) {
            text.append("Chromosome order: ")
                .append(info.sortChromosomes ? "sorted by chromosome number" : "as in the result file")
                .append('\n');
        }

        if (info == null) {
            text.append('\n');
            text.append("This run has no run.info.txt (older result), so the populations and the\n");
            text.append("comparison strategy are not stored with it. The produced files are listed below.\n");
        } else {
            appendInputFiles(text, info);
            appendPopulations(text, info);
            appendComparisons(text, info, runDirectory);
        }
        appendResultFiles(text, runDirectory);
    }

    private static void appendInputFiles(StringBuilder text, FSTRunInfo.Info info) {
        text.append('\n').append("INPUT FILES\n");
        text.append("===========\n");
        text.append("VCF: ").append(describePath(info.vcf)).append('\n');
        text.append("Genome sequence: ").append(describePath(info.genome)).append('\n');
        text.append("Annotation: ").append(describePath(info.gff3)).append('\n');
    }

    /** Populations with every member listed in full. */
    private static void appendPopulations(StringBuilder text, FSTRunInfo.Info info) {
        text.append('\n').append("POPULATIONS (").append(info.groups.size()).append(")\n");
        text.append("===========\n");
        if (info.groups.isEmpty()) {
            text.append("No population was recorded for this run.\n");
            return;
        }

        int totalMembers = 0;
        for (FSTRunInfo.Group group : info.groups) {
            totalMembers += group.samples.size();
        }
        text.append("Total members: ").append(totalMembers).append('\n');

        for (FSTRunInfo.Group group : info.groups) {
            String name = group.name == null || group.name.isEmpty() ? "(unnamed)" : group.name;
            text.append('\n').append(name).append(" - ").append(group.samples.size())
                .append(group.samples.size() == 1 ? " sample\n" : " samples\n");
            text.append(PopulationVcfSummarySupport.wrapNameList(
                group.samples, MEMBER_INDENT, MEMBER_LINE_WIDTH));
        }
    }

    /** Comparison strategy: which populations were compared, and the files each produced. */
    private static void appendComparisons(StringBuilder text, FSTRunInfo.Info info, File runDirectory) {
        text.append('\n').append("COMPARISON STRATEGY (").append(info.comparisons.size()).append(")\n");
        text.append("===================\n");
        if (info.comparisons.isEmpty()) {
            text.append("No comparison was recorded for this run.\n");
            return;
        }

        List<File> resultFiles = listResultFiles(runDirectory);
        Map<String, FSTRunManifest.RunEntry> names = FSTRunManifest.read(runDirectory);
        for (int i = 0; i < info.comparisons.size(); i++) {
            FSTRunInfo.Comparison comparison = info.comparisons.get(i);
            String name = comparison.name == null || comparison.name.isEmpty()
                ? describeComparison(comparison) : comparison.name;
            text.append('\n').append(i + 1).append(") ").append(name).append('\n');
            text.append("   Left:  ").append(describeSide(info, comparison.leftGroups)).append('\n');
            text.append("   Right: ").append(describeSide(info, comparison.rightGroups)).append('\n');

            String fstFragment = joinGroups(comparison.leftGroups) + "_vs_"
                + joinGroups(comparison.rightGroups);
            File fstFile = findFstFile(resultFiles, names, fstFragment, name);
            text.append("   FST file: ").append(fstFile != null
                ? fstFile.getName() : "not found (" + fstFragment + ".windowed.weir.fst)").append('\n');

            text.append("   Per-population files:\n");
            appendSideFiles(text, resultFiles, comparison.leftGroups, "left");
            appendSideFiles(text, resultFiles, comparison.rightGroups, "right");
        }
    }

    /**
     * The FST file of a comparison: the run names it after the joined population names
     * ({@code Group1_vs_Group2Group3}); when that does not match, the display name stored in
     * {@code run.names.tsv} still identifies it.
     */
    private static File findFstFile(List<File> resultFiles,
                                    Map<String, FSTRunManifest.RunEntry> names,
                                    String fragment, String comparisonName) {
        File byFragment = findFile(resultFiles, fragment, ".windowed.weir.fst");
        if (byFragment != null) {
            return byFragment;
        }
        for (File file : resultFiles) {
            if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".windowed.weir.fst")) {
                continue;
            }
            FSTRunManifest.RunEntry entry = names.get(file.getName());
            if (entry != null && entry.displayName != null
                    && entry.displayName.equalsIgnoreCase(comparisonName)) {
                return file;
            }
        }
        return null;
    }

    private static void appendSideFiles(StringBuilder text, List<File> resultFiles,
                                        List<String> sideGroups, String sideLabel) {
        if (sideGroups.isEmpty()) {
            return;
        }
        String joined = joinGroups(sideGroups);
        StringBuilder line = new StringBuilder();
        line.append("     ").append(sideLabel).append(" (").append(joined).append("): ");
        List<String> names = new ArrayList<>();
        File pi = findFile(resultFiles, joined, ".windowed.pi");
        File tajimaD = findFile(resultFiles, joined, ".Tajima.D");
        names.add("PI " + (pi != null ? pi.getName() : "-"));
        names.add("Tajima's D " + (tajimaD != null ? tajimaD.getName() : "-"));
        line.append(String.join(", ", names));
        text.append(line).append('\n');
    }

    private static void appendResultFiles(StringBuilder text, File runDirectory) {
        ResultDirectoryScanner.ResultFiles scanned =
            ResultDirectoryScanner.scanDirectory(runDirectory.getAbsolutePath());
        Map<String, FSTRunManifest.RunEntry> names = FSTRunManifest.read(runDirectory);

        text.append('\n').append("RESULT FILES\n");
        text.append("============\n");
        appendFileGroup(text, "FST (windowed)", scanned.fstFiles, names);
        appendFileGroup(text, "PI (windowed)", scanned.piFiles, names);
        appendFileGroup(text, "Tajima's D", scanned.tajimaDFiles, names);
        appendFileGroup(text, "Logistic / GWAS", scanned.logisticFiles, names);
        text.append("Total size: ")
            .append(PopulationVcfSummarySupport.formatFileSize(directorySize(runDirectory))).append('\n');
    }

    private static void appendFileGroup(StringBuilder text, String label, List<String> paths,
                                        Map<String, FSTRunManifest.RunEntry> names) {
        text.append(label).append(": ").append(paths.size()).append('\n');
        if (paths.isEmpty()) {
            return;
        }
        List<String> sorted = new ArrayList<>(paths);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < sorted.size(); i++) {
            if (i >= MAX_LISTED_FILES) {
                text.append(MEMBER_INDENT).append("... ").append(sorted.size() - MAX_LISTED_FILES)
                    .append(" more file(s)\n");
                break;
            }
            String fileName = new File(sorted.get(i)).getName();
            text.append(MEMBER_INDENT).append(fileName);
            FSTRunManifest.RunEntry entry = names.get(fileName);
            if (entry != null && entry.displayName != null && !entry.displayName.isEmpty()
                    && !entry.displayName.equals(fileName)) {
                text.append("   ->  ").append(entry.displayName);
            }
            text.append('\n');
        }
    }

    private static void appendTotals(StringBuilder text, List<File> resultDirectories) {
        int fstFiles = 0;
        int piFiles = 0;
        int tajimaDFiles = 0;
        long bytes = 0;
        for (File directory : resultDirectories) {
            ResultDirectoryScanner.ResultFiles scanned =
                ResultDirectoryScanner.scanDirectory(directory.getAbsolutePath());
            fstFiles += scanned.fstFiles.size();
            piFiles += scanned.piFiles.size();
            tajimaDFiles += scanned.tajimaDFiles.size();
            bytes += directorySize(directory);
        }

        text.append('\n').append("TOTAL\n");
        text.append("=====\n");
        text.append("FST files: ").append(fstFiles).append('\n');
        text.append("PI files: ").append(piFiles).append('\n');
        text.append("Tajima's D files: ").append(tajimaDFiles).append('\n');
        text.append("Total size: ").append(PopulationVcfSummarySupport.formatFileSize(bytes)).append('\n');
    }

    // ---- helpers ----

    /**
     * The result folders to describe. A {@code w<window>_s<step>} folder is one section; a run folder
     * ({@code <time>/w..}) and the whole Population/FST section are expanded into their result
     * folders, at most two levels below the selected folder.
     */
    private static List<File> collectResultDirectories(File directory) {
        List<File> found = new ArrayList<>();
        if (directory == null || !directory.isDirectory()) {
            return found;
        }
        if (holdsResultFiles(directory)) {
            found.add(directory);
            return found;
        }
        collectResultDirectories(directory, 0, found);
        found.sort(Comparator.comparing(File::getAbsolutePath, String.CASE_INSENSITIVE_ORDER));
        return found;
    }

    private static void collectResultDirectories(File directory, int depth, List<File> found) {
        if (depth >= 2) {
            return;
        }
        File[] children = directory.listFiles(File::isDirectory);
        if (children == null) {
            return;
        }
        Arrays.sort(children, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File child : children) {
            if (holdsResultFiles(child)) {
                found.add(child);
            } else {
                collectResultDirectories(child, depth + 1, found);
            }
        }
    }

    private static boolean holdsResultFiles(File directory) {
        return directory != null && directory.isDirectory()
            && !ResultDirectoryScanner.scanDirectory(directory.getAbsolutePath()).isEmpty();
    }

    /** Folder name relative to the selected one, e.g. {@code 2026-01-02-03-04-05/w1000_s500}. */
    private static String relativeName(File base, File directory) {
        String basePath = base.getAbsolutePath();
        String path = directory.getAbsolutePath();
        if (path.startsWith(basePath)) {
            String relative = path.substring(basePath.length());
            relative = relative.replaceAll("^[\\\\/]+", "");
            if (!relative.isEmpty()) {
                return relative;
            }
        }
        return directory.getName();
    }

    private static List<File> listResultFiles(File directory) {
        List<File> files = new ArrayList<>();
        File[] children = directory.listFiles(File::isFile);
        if (children != null) {
            files.addAll(Arrays.asList(children));
        }
        return files;
    }

    /**
     * Finds the result file of one comparison side: the run writes {@code <vcf base>.<joined
     * groups><suffix>}. The match is anchored at the {@code .} before the fragment, so
     * {@code Group2} never resolves to the combined {@code Group1_Group2} file.
     */
    private static File findFile(List<File> files, String fragment, String suffix) {
        String exact = fragment + suffix;
        String anchored = "." + exact;
        File anchoredMatch = null;
        for (File file : files) {
            String name = file.getName();
            if (name.equals(exact)) {
                return file;
            }
            if (anchoredMatch == null && name.endsWith(anchored)) {
                anchoredMatch = file;
            }
        }
        return anchoredMatch;
    }

    /** "Group1" or "Group1, Group2" plus the member counts of every group. */
    private static String describeSide(FSTRunInfo.Info info, List<String> groups) {
        if (groups == null || groups.isEmpty()) {
            return "-";
        }
        List<String> parts = new ArrayList<>();
        for (String name : groups) {
            FSTRunInfo.Group group = findGroup(info, name);
            int members = group == null ? -1 : group.samples.size();
            parts.add(members >= 0
                ? name + " (" + members + (members == 1 ? " sample)" : " samples)")
                : name + " (not in this run)");
        }
        return String.join(", ", parts);
    }

    private static FSTRunInfo.Group findGroup(FSTRunInfo.Info info, String name) {
        for (FSTRunInfo.Group group : info.groups) {
            if (group.name != null && group.name.equals(name)) {
                return group;
            }
        }
        return null;
    }

    private static String describeComparison(FSTRunInfo.Comparison comparison) {
        return joinGroups(comparison.leftGroups) + "_vs_" + joinGroups(comparison.rightGroups);
    }

    /** File-name fragment used by the run for a set of groups (concatenated, like the results). */
    private static String joinGroups(List<String> groups) {
        if (groups == null || groups.isEmpty()) {
            return "";
        }
        StringBuilder joined = new StringBuilder();
        for (String group : groups) {
            joined.append(group == null ? "" : group.trim());
        }
        return joined.toString();
    }

    private static String describePath(String path) {
        if (path == null || path.isEmpty()) {
            return "-";
        }
        return path;
    }

    private static String formatCreated(String created) {
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(created);
            return CREATED_FORMAT.format(date);
        } catch (Exception ex) {
            return created;
        }
    }

    private static String formatSize(int value) {
        return value > 0 ? String.format(Locale.ROOT, "%,d", value) : "-";
    }

    private static long directorySize(File directory) {
        long bytes = 0L;
        File[] children = directory.listFiles(File::isFile);
        if (children != null) {
            for (File child : children) {
                bytes += child.length();
            }
        }
        return bytes;
    }

    private static boolean sameFile(File left, File right) {
        if (left == null || right == null) {
            return false;
        }
        return left.getAbsolutePath().equals(right.getAbsolutePath());
    }
}
