package simplegenomehub.runtime;

import biocjava.bioDoer.DependencyCheck.ExecutablePathProbe;
import biocjava.bioDoer.Diamond.DiamondLocator;
import biocjava.bioDoer.BLAST.ToolProcessRunner;
import simplegenomehub.config.ApplicationLayout;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Select from the current JVM PATH first, then explicit configuration and fixed installation layouts. */
public final class HubToolResolver {
    private static final ConcurrentHashMap<String, String> VERSIONS = new ConcurrentHashMap<>();
    private HubToolResolver() { }

    public static final class ResolvedTool {
        private final String executable, source, version;
        private ResolvedTool(String executable, String source, String version) {
            this.executable = executable; this.source = source; this.version = version;
        }
        public String getExecutable() { return executable; }
        public String getSource() { return source; }
        public String getVersion() { return version; }
        @Override public String toString() { return executable + " [" + source + "; " + version + "]"; }
    }

    /** Path-only resolution is safe during UI construction; no subprocess is started here. */
    public static String resolve(String name, String explicitPath) { return select(name, explicitPath).getExecutable(); }

    /** Call on a worker: version inspection is bounded to five seconds and never selects another executable. */
    public static ResolvedTool resolveTool(String name, String explicitPath) {
        ResolvedTool selected = select(name, explicitPath);
        File file = new File(selected.executable);
        String key = selected.executable + "|" + file.lastModified() + "|" + file.length();
        String version = VERSIONS.get(key);
        if (version == null) {
            try {
                ToolProcessRunner.Result result = HubProcessRunner.run(new ProcessBuilder(selected.executable,
                    "diamond".equalsIgnoreCase(name) ? "version" : "-version"), 5, TimeUnit.SECONDS);
                String output = result.output.trim().replaceAll("[\\r\\n]+", " | ");
                version = result.exitCode == 0 && !output.isEmpty() ? output : "version unavailable (exit " + result.exitCode + ")";
                if (version.length() > 500) version = version.substring(0, 500);
            } catch (InterruptedException cancelled) {
                Thread.currentThread().interrupt();
                throw new java.util.concurrent.CancellationException("Tool inspection cancelled: " + name);
            } catch (IOException failure) { version = "version unavailable: " + failure.getMessage(); }
            VERSIONS.put(key, version);
        }
        return new ResolvedTool(selected.executable, selected.source, version);
    }

    private static ResolvedTool select(String name, String explicitPath) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Tool name is required");
        File path = ExecutablePathProbe.findFirstOnPathForFileName(executableName(name));
        if (usable(path)) return selected(path, "PATH");
        if (explicitPath != null && !explicitPath.trim().isEmpty()) {
            ResolvedTool explicit = configured(name, explicitPath, "explicit configuration");
            if (explicit != null) return explicit;
        }
        String property = System.getProperty("tbtools.tools." + name, "").trim();
        if (!property.isEmpty()) {
            ResolvedTool configured = configured(name, property, "system property tbtools.tools." + name);
            if (configured != null) return configured;
        }
        List<File> roots = new ArrayList<>();
        if ("diamond".equals(name)) {
            try { String home = DiamondLocator.getDiamondHome(); if (home != null) roots.add(new File(home)); }
            catch (IOException ignored) { }
        }
        File home = ApplicationLayout.getAppHomeDirectory();
        roots.add(new File(home, "bin"));
        roots.addAll(ApplicationLayout.getBundledToolSearchDirectories());
        File plugins = new File(new File(System.getProperty("user.home"), ".TBtools"), ".Plugin");
        for (String plugin : new String[]{"BLAST", "Blast", "BlastPlus", "Diamond", "MCScanX"}) {
            roots.add(new File(new File(plugins, plugin), "bin")); roots.add(new File(plugins, plugin));
        }
        for (File root : roots) {
            File hit = ApplicationLayout.resolveExecutable(root, name);
            if (usable(hit)) return selected(hit, "installed/bundled tools");
        }
        throw new IllegalStateException("Cannot find " + name + ". Add it to PATH or configure its executable directory."
            + (explicitPath == null || explicitPath.trim().isEmpty() ? "" : " Unavailable configuration: " + explicitPath)
            + (property.isEmpty() ? "" : " Unavailable system property: " + property));
    }

    private static ResolvedTool configured(String name, String value, String source) {
        File configured = new File(value.trim());
        File hit = configured.isFile() ? configured : ApplicationLayout.resolveExecutable(configured, name);
        if (hit == null) hit = ApplicationLayout.resolveExecutable(new File(configured, "bin"), name);
        if (usable(hit)) return selected(hit, source);
        return null;
    }

    private static boolean usable(File file) {
        if (file == null || !file.isFile()) return false;
        if (ApplicationLayout.isWindows() || file.canExecute()) return true;
        try { file.setExecutable(true); } catch (SecurityException ignored) { }
        return file.canExecute();
    }

    private static ResolvedTool selected(File file, String source) {
        return new ResolvedTool(file.getAbsolutePath(), source, "not inspected");
    }

    private static String executableName(String name) {
        return ApplicationLayout.isWindows() && !name.toLowerCase(java.util.Locale.ROOT).endsWith(".exe") ? name + ".exe" : name;
    }
}
