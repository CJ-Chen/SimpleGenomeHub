package simplegenomehub.runtime;

import biocjava.bioDoer.DependencyCheck.ExecutablePathProbe;
import biocjava.bioDoer.Diamond.DiamondLocator;
import simplegenomehub.config.ApplicationLayout;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Resolves a tool once and passes the same absolute executable to each pipeline stage. */
public final class HubToolResolver {
    private HubToolResolver() { }

    public static String resolve(String name, String explicitPath) {
        if (explicitPath != null && !explicitPath.trim().isEmpty()) {
            File configured = new File(explicitPath.trim());
            File hit = configured.isFile() ? configured : ApplicationLayout.resolveExecutable(configured, name);
            if (hit == null) hit = ApplicationLayout.resolveExecutable(new File(configured, "bin"), name);
            if (hit != null && hit.isFile()) return hit.getAbsolutePath();
            // An explicit invalid path must produce a useful execution error, not silently use another version.
            return configured.isDirectory() ? new File(configured, executableName(name)).getAbsolutePath()
                                            : configured.getAbsolutePath();
        }
        List<File> roots = new ArrayList<>();
        String property = System.getProperty("tbtools.tools." + name, "");
        if (!property.isEmpty()) return resolve(name, property);
        if ("diamond".equals(name)) {
            try {
                String home = DiamondLocator.getDiamondHome();
                if (home != null) roots.add(new File(home));
            } catch (IOException ignored) { }
        }
        File home = ApplicationLayout.getAppHomeDirectory();
        roots.add(new File(home, "bin"));
        roots.addAll(ApplicationLayout.getBundledToolSearchDirectories());
        File plugins = new File(new File(System.getProperty("user.home"), ".TBtools"), ".Plugin");
        for (String plugin : new String[]{"BLAST", "Blast", "BlastPlus", "Diamond", "MCScanX"}) {
            roots.add(new File(new File(plugins, plugin), "bin"));
            roots.add(new File(plugins, plugin));
        }
        for (File root : roots) {
            File hit = ApplicationLayout.resolveExecutable(root, name);
            if (hit != null) return hit.getAbsolutePath();
        }
        File path = ExecutablePathProbe.findFirstOnPathForFileName(executableName(name));
        return path == null ? executableName(name) : path.getAbsolutePath();
    }

    private static String executableName(String name) {
        return ApplicationLayout.isWindows() && !name.endsWith(".exe") ? name + ".exe" : name;
    }
}
