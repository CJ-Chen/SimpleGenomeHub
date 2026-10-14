package simplegenomehub.population.fst;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.runtime.HubSession;
import simplegenomehub.visualization.regionset.RegionSetContext;

/** Immutable ownership and output identity for one computation or result window. */
public final class FstRunContext implements AutoCloseable {
    private final HubSession session;
    private final long generation;
    private final File dataRoot;
    private final SpeciesInfo species;
    private final File resultDirectory;
    private final boolean sortChromosomes;
    private Path temporaryDirectory;
    private final java.util.Map<String, File> indexFiles = new java.util.HashMap<>();
    private java.nio.channels.FileChannel outputLockChannel;
    private java.nio.channels.FileLock outputLock;

    public FstRunContext(HubSession session, SpeciesInfo species, File resultDirectory,
                         boolean sortChromosomes) {
        this.session = session;
        this.generation = session == null ? 0 : session.getGeneration();
        this.dataRoot = session == null ? null : session.getDataRoot();
        this.species = species;
        try {
            this.resultDirectory = resultDirectory == null ? null : resultDirectory.getCanonicalFile();
        } catch (IOException failure) {
            throw new IllegalArgumentException("Cannot resolve FST result directory", failure);
        }
        if (dataRoot != null && this.resultDirectory != null
                && !this.resultDirectory.toPath().startsWith(dataRoot.toPath())) {
            throw new IllegalArgumentException("FST result directory is outside its Hub data root");
        }
        this.sortChromosomes = sortChromosomes;
    }

    public HubSession getSession() { return session; }
    public SpeciesInfo getSpecies() { return species; }
    public File getDataRoot() { return dataRoot; }
    public File getResultDirectory() { return resultDirectory; }
    public boolean isSortChromosomesByNumber() { return sortChromosomes; }
    public RegionSetContext getRegionSetContext() { return RegionSetContext.fromSpecies(species); }
    public boolean isCurrent() { return session == null || session.accepts(generation); }

    public synchronized File createTemporaryFile(String prefix, String suffix) throws IOException {
        if (temporaryDirectory == null) temporaryDirectory = Files.createTempDirectory("sgh-fst-run-");
        return Files.createTempFile(temporaryDirectory, prefix, suffix).toFile();
    }

    public synchronized File createTemporaryDirectory(String prefix) throws IOException {
        if (temporaryDirectory == null) temporaryDirectory = Files.createTempDirectory("sgh-fst-run-");
        return Files.createTempDirectory(temporaryDirectory, prefix).toFile();
    }

    /** A run-local cache, shared by comparisons but never beside an external VCF. */
    public synchronized File getVcfIndexFile(File input) throws IOException {
        String key = input.getCanonicalPath();
        File index = indexFiles.get(key);
        if (index == null) {
            index = createTemporaryFile("vcf-index-", ".idx");
            Files.delete(index.toPath());
            indexFiles.put(key, index);
        }
        return index;
    }

    public synchronized void acquireOutputLease() throws IOException {
        if (outputLock != null) return;
        if (resultDirectory == null) throw new IOException("FST result directory is missing");
        Files.createDirectories(resultDirectory.toPath());
        outputLockChannel = java.nio.channels.FileChannel.open(
            resultDirectory.toPath().resolve(".sgh-fst-run.lock"),
            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE);
        try {
            outputLock = outputLockChannel.tryLock();
            if (outputLock == null) throw new IOException("Another FST task is writing this result directory");
        } catch (java.nio.channels.OverlappingFileLockException failure) {
            outputLockChannel.close(); outputLockChannel = null;
            throw new IOException("Another FST task is writing this result directory", failure);
        } catch (IOException | RuntimeException failure) {
            outputLockChannel.close(); outputLockChannel = null;
            throw failure;
        }
    }

    /** Called only after all child computation threads have joined. */
    @Override public synchronized void close() throws IOException {
        try {
            if (temporaryDirectory != null) {
                try (java.util.stream.Stream<Path> paths = Files.walk(temporaryDirectory)) {
                    for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator)
                        Files.deleteIfExists(path);
                }
                temporaryDirectory = null;
                indexFiles.clear();
            }
        } finally {
            if (outputLockChannel != null) {
                outputLockChannel.close(); outputLockChannel = null; outputLock = null;
            }
        }
    }
}
