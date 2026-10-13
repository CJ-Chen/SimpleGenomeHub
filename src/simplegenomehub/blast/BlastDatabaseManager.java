/*
 * SimpleGenomeHub BLAST Database Manager
 * Manages on-demand creation and caching of BLAST databases
 */
package simplegenomehub.blast;

import simplegenomehub.blast.BlastConfig.SequenceType;
import simplegenomehub.blast.model.BlastDatabase;
import simplegenomehub.model.SpeciesInfo;
import biocjava.bioDoer.BLAST.BlastZone.BlastZone.MakeBlastDBonTheFly;
import java.io.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages BLAST databases with on-demand creation and intelligent caching
 * 
 * @author SimpleGenomeHub Team
 */
public class BlastDatabaseManager {
    
    private static final Logger logger = Logger.getLogger(BlastDatabaseManager.class.getName());
    private static final ConcurrentMap<String, Object> BUILD_LOCKS = new ConcurrentHashMap<>();
    
    private final BlastConfig config;
    private final simplegenomehub.runtime.HubSession session;
    private final ConcurrentMap<String, BlastDatabase> databaseCache;
    private final ConcurrentMap<String, CompletableFuture<BlastDatabase>> creationTasks;
    private final ExecutorService executorService;
    private final ScheduledExecutorService cleanupExecutor;
    
    /**
     * Initialize the database manager
     */
    public BlastDatabaseManager(BlastConfig config) {
        this(config, simplegenomehub.runtime.HubSession.current());
    }

    public BlastDatabaseManager(BlastConfig config, simplegenomehub.runtime.HubSession session) {
        this.config = config;
        this.session = session;
        this.databaseCache = new ConcurrentHashMap<>();
        this.creationTasks = new ConcurrentHashMap<>();
        this.executorService = Executors.newFixedThreadPool(4);
        this.cleanupExecutor = Executors.newScheduledThreadPool(1);
        
        // Schedule periodic cleanup
        if (session != null) session.addCleanup(this::shutdown);
        cleanupExecutor.scheduleAtFixedRate(() -> {
            if (session != null && !session.isActive()) return;
            try (simplegenomehub.runtime.HubSession.Task task = session == null ? null : session.beginTask("BLAST cache cleanup", null)) {
                performCleanup();
            } catch (java.util.concurrent.RejectedExecutionException ignored) { }
        }, 1, 1, TimeUnit.HOURS);
        
        logger.info("BLAST database manager initialized");
    }
    
    /**
     * Ensure database exists for the given species and sequence type
     * This is the main entry point for on-demand database creation
     */
    public synchronized BlastDatabase ensureDatabaseExists(SpeciesInfo species, SequenceType seqType)
            throws Exception {
        
        String dbKey = getDatabaseKey(species, seqType);
        
        // 1. Check memory cache first
        BlastDatabase cachedDb = databaseCache.get(dbKey);
        if (cachedDb != null && isValidAndUpToDate(cachedDb, species, seqType)) {
            logger.fine("Returning database from cache: " + dbKey);
            return cachedDb;
        }
        
        // 2. Check if creation is already in progress
        CompletableFuture<BlastDatabase> existingTask = creationTasks.get(dbKey);
        if (existingTask != null) {
            logger.info("Waiting for database creation to complete: " + dbKey);
            return existingTask.get();
        }
        
        // 3. Check disk database
        File dbDir = config.getSequenceTypeDbDir(species, seqType);
        File sourceFile = seqType.getSourceFile(species);
        
        // Try to find the actual source file if the default doesn't exist
        if (!sourceFile.exists()) {
            sourceFile = findActualSourceFile(species, seqType);
            if (sourceFile == null || !sourceFile.exists()) {
                throw new FileNotFoundException("Source sequence file not found for " + 
                    species.getSpeciesName() + " " + seqType + ". Expected: " + seqType.getSourceFile(species).getAbsolutePath());
            }
        }
        
        BlastDatabase diskDb = loadDatabaseFromDisk(dbDir, seqType);
        if (diskDb != null && diskDb.isUpToDate(sourceFile)) {
            databaseCache.put(dbKey, diskDb);
            logger.info("Loaded database from disk: " + dbKey);
            return diskDb;
        }
        
        // 4. Create new database
        logger.info("Starting creation of new database: " + dbKey);
        CompletableFuture<BlastDatabase> createTask = createDatabaseAsync(species, seqType, sourceFile, dbDir);
        creationTasks.put(dbKey, createTask);
        
        try {
            BlastDatabase newDb = createTask.get();
            databaseCache.put(dbKey, newDb);
            return newDb;
        } finally {
            creationTasks.remove(dbKey);
        }
    }
    
    /**
     * Create database asynchronously
     */
    private CompletableFuture<BlastDatabase> createDatabaseAsync(SpeciesInfo species, 
                                                               SequenceType seqType, 
                                                               File sourceFile, 
                                                               File dbDir) {
        CompletableFuture<BlastDatabase> completed = new CompletableFuture<>();
        try { completed.complete(createDatabase(species, seqType, sourceFile, dbDir)); }
        catch (Exception failure) { completed.completeExceptionally(failure); }
        return completed;
    }
    
    /**
     * Actually create the database using TBtools
     */
    private BlastDatabase createDatabase(SpeciesInfo species, SequenceType seqType, 
                                       File sourceFile, File dbDir) throws Exception {
        // Multiple BLAST dialogs can request the same index. Publish only one complete directory.
        synchronized (BUILD_LOCKS.computeIfAbsent(dbDir.getCanonicalPath(), ignored -> new Object())) {
            BlastDatabase existing = loadDatabaseFromDisk(dbDir, seqType);
            if (existing != null && existing.isUpToDate(sourceFile)) return existing;
            java.nio.file.Path parent = dbDir.toPath().toAbsolutePath().getParent();
            java.nio.file.Files.createDirectories(parent);
            java.nio.file.Path staging = java.nio.file.Files.createTempDirectory(parent, ".sgh-blast-build-");
            java.nio.file.Path backup = null;
            boolean published = false;
            try {
                long startTime = System.currentTimeMillis();
                simplegenomehub.runtime.HubToolResolver.ResolvedTool tool =
                    simplegenomehub.runtime.HubToolResolver.resolveTool("makeblastdb", config.getBlastExecutablePath());
                logger.info("Building BLAST database with " + tool);
                createIndex(tool.getExecutable(), session, sourceFile, staging.toFile(),
                    seqType.toString().toLowerCase(), seqType.getMolecularType());
                checkCancelled();
                java.nio.file.Path oldMetadata = dbDir.toPath().resolve("database.info");
                if (java.nio.file.Files.isRegularFile(oldMetadata))
                    java.nio.file.Files.copy(oldMetadata, staging.resolve("database.info"));
                BlastDatabase database = new BlastDatabase(staging.toFile(), seqType, sourceFile);
                if (!database.isValid()) throw new IOException("makeblastdb did not produce a complete database");
                database.addMetadataChecked("creation_duration_ms", String.valueOf(System.currentTimeMillis() - startTime));
                database.addMetadataChecked("species_name", species.getSpeciesName());
                database.addMetadataChecked("species_version", species.getVersion());
                database.addMetadataChecked("tool.path", tool.getExecutable());
                database.addMetadataChecked("tool.source", tool.getSource());
                database.addMetadataChecked("tool.version", tool.getVersion());
                checkCancelled();
                if (java.nio.file.Files.exists(dbDir.toPath())) {
                    backup = parent.resolve(".sgh-blast-old-" + java.util.UUID.randomUUID());
                    moveDirectory(dbDir.toPath(), backup);
                }
                try { moveDirectory(staging, dbDir.toPath()); }
                catch (IOException failedPublish) {
                    if (backup != null) { moveDirectory(backup, dbDir.toPath()); backup = null; }
                    throw failedPublish;
                }
                published = true;
                return BlastDatabase.loadFromDisk(dbDir, seqType);
            } finally {
                deleteScratch(staging);
                // If restoring a failed publication also fails, preserve the old directory for recovery.
                if (backup != null && published) deleteScratch(backup);
            }
        }
    }

    private void checkCancelled() throws InterruptedException {
        if (Thread.currentThread().isInterrupted() || (session != null && !session.isActive()))
            throw new InterruptedException("BLAST database creation cancelled");
    }

    private static void moveDirectory(java.nio.file.Path source, java.nio.file.Path target) throws IOException {
        try { java.nio.file.Files.move(source, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException unsupported) { java.nio.file.Files.move(source, target); }
    }

    private static void deleteScratch(java.nio.file.Path directory) throws IOException {
        if (!java.nio.file.Files.exists(directory)) return;
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(directory)) {
            for (java.nio.file.Path path : (Iterable<java.nio.file.Path>) files.sorted(java.util.Comparator.reverseOrder())::iterator)
                java.nio.file.Files.deleteIfExists(path);
        }
    }
    
    /** NCBI BLAST parses -in/-out itself; use local ASCII names even when the data root contains spaces. */
    static void createIndex(BlastConfig config, simplegenomehub.runtime.HubSession session, File source,
                            File directory, String name, BlastConfig.MolecularType molecularType) throws Exception {
        createIndex(config.resolveBlastCommand("makeblastdb"), session, source, directory, name, molecularType);
    }

    private static void createIndex(String executable, simplegenomehub.runtime.HubSession session, File source,
                            File directory, String name, BlastConfig.MolecularType molecularType) throws Exception {
        java.nio.file.Path input = java.nio.file.Files.createTempFile(directory.toPath(), "sgh-input-", ".fa");
        try {
            java.nio.file.Files.delete(input);
            try { java.nio.file.Files.createLink(input, source.toPath()); }
            catch (IOException | UnsupportedOperationException failure) { java.nio.file.Files.copy(source.toPath(), input); }
            biocjava.bioDoer.BLAST.ToolProcessRunner.Result completed = simplegenomehub.runtime.HubProcessRunner.run(session,
                new ProcessBuilder(executable, "-in", input.getFileName().toString(),
                    "-dbtype", molecularType == BlastConfig.MolecularType.PROTEIN ? "prot" : "nucl",
                    "-out", name, "-blastdb_version", "4").directory(directory), 5, TimeUnit.MINUTES);
            if (completed.exitCode != 0) throw new IOException("makeblastdb failed: " + completed.output);
        } finally { java.nio.file.Files.deleteIfExists(input); }
    }

    /**
     * Load database from disk if it exists
     */
    private BlastDatabase loadDatabaseFromDisk(File dbDir, SequenceType seqType) {
        try {
            if (!dbDir.exists()) {
                return null;
            }
            
            BlastDatabase db = BlastDatabase.loadFromDisk(dbDir, seqType);
            if (db != null && db.isValid()) {
                logger.fine("Loaded database from disk: " + dbDir.getAbsolutePath());
                return db;
            }
        } catch (Exception e) {
            logger.warning("Unable to load database from disk: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Check if database is valid and up to date
     */
    private boolean isValidAndUpToDate(BlastDatabase db, SpeciesInfo species, SequenceType seqType) {
        if (!db.isValid()) {
            return false;
        }
        
        File sourceFile = seqType.getSourceFile(species);
        if (!sourceFile.exists()) {
            sourceFile = findActualSourceFile(species, seqType);
            if (sourceFile == null) return false;
        }
        return db.isUpToDate(sourceFile);
    }
    
    /**
     * Generate unique key for database
     */
    private String getDatabaseKey(SpeciesInfo species, SequenceType seqType) {
        try { return species.getSpeciesDir().getCanonicalPath() + "|" + seqType.name(); }
        catch (IOException ex) { return species.getSpeciesDir().getAbsolutePath() + "|" + seqType.name(); }
    }
    
    /**
     * Find actual source file by searching for matching files in species directory
     */
    private File findActualSourceFile(SpeciesInfo species, BlastConfig.SequenceType seqType) {
        if (species.getSequenceDir() == null || !species.getSequenceDir().exists()) {
            return null;
        }
        
        String targetName = seqType.toString().toLowerCase();
        
        // Search for files matching the sequence type
        File[] candidateFiles = species.getSequenceDir().listFiles((dir, name) -> {
            String lowerName = name.toLowerCase();
            // Support common FASTA file extensions: .fasta, .fa, .fna, .fas
            boolean isFastaFile = lowerName.endsWith(".fasta") ||
                                  lowerName.endsWith(".fa") ||
                                  lowerName.endsWith(".fna") ||
                                  lowerName.endsWith(".fas");
            return lowerName.contains(targetName) && isFastaFile;
        });
        
        if (candidateFiles != null && candidateFiles.length > 0) {
            // Return the first matching file
            logger.info("Found alternative source file: " + candidateFiles[0].getAbsolutePath() + 
                       " for sequence type: " + seqType);
            return candidateFiles[0];
        }
        
        // Special case: for protein sequences, also look for files containing "peptide" or "aa"
        if (seqType == BlastConfig.SequenceType.PROTEIN) {
            candidateFiles = species.getSequenceDir().listFiles((dir, name) -> {
                String lowerName = name.toLowerCase();
                // Support common FASTA file extensions: .fasta, .fa, .fna, .fas
                boolean isFastaFile = lowerName.endsWith(".fasta") ||
                                      lowerName.endsWith(".fa") ||
                                      lowerName.endsWith(".fna") ||
                                      lowerName.endsWith(".fas");
                return (lowerName.contains("peptide") || lowerName.contains("aa") ||
                        lowerName.contains("pep")) && isFastaFile;
            });
            
            if (candidateFiles != null && candidateFiles.length > 0) {
                logger.info("Found protein alternative: " + candidateFiles[0].getAbsolutePath());
                return candidateFiles[0];
            }
        }
        
        return null;
    }
    
    /**
     * Check if database exists for given species and sequence type
     */
    public boolean databaseExists(SpeciesInfo species, SequenceType seqType) {
        String dbKey = getDatabaseKey(species, seqType);
        
        // Check cache
        BlastDatabase cachedDb = databaseCache.get(dbKey);
        if (cachedDb != null && isValidAndUpToDate(cachedDb, species, seqType)) {
            return true;
        }
        
        // Check disk
        File dbDir = config.getSequenceTypeDbDir(species, seqType);
        BlastDatabase diskDb = loadDatabaseFromDisk(dbDir, seqType);
        if (diskDb != null) {
            File sourceFile = seqType.getSourceFile(species);
            return diskDb.isUpToDate(sourceFile);
        }
        
        return false;
    }
    
    /**
     * Get database status for given species and sequence type
     */
    public DatabaseStatus getDatabaseStatus(SpeciesInfo species, SequenceType seqType) {
        File sourceFile = seqType.getSourceFile(species);
        
        if (!sourceFile.exists()) {
            return DatabaseStatus.SOURCE_MISSING;
        }
        
        String dbKey = getDatabaseKey(species, seqType);
        
        // Check if creation is in progress
        if (creationTasks.containsKey(dbKey)) {
            return DatabaseStatus.CREATING;
        }
        
        // Check cache
        BlastDatabase cachedDb = databaseCache.get(dbKey);
        if (cachedDb != null && isValidAndUpToDate(cachedDb, species, seqType)) {
            return DatabaseStatus.UP_TO_DATE;
        }
        
        // Check disk
        File dbDir = config.getSequenceTypeDbDir(species, seqType);
        BlastDatabase diskDb = loadDatabaseFromDisk(dbDir, seqType);
        if (diskDb != null) {
            if (diskDb.isUpToDate(sourceFile)) {
                return DatabaseStatus.UP_TO_DATE;
            } else {
                return DatabaseStatus.OUTDATED;
            }
        }
        
        return DatabaseStatus.NOT_CREATED;
    }
    
    /**
     * Delete database for given species and sequence type
     */
    public boolean deleteDatabase(SpeciesInfo species, SequenceType seqType) {
        String dbKey = getDatabaseKey(species, seqType);
        
        // Remove from cache
        BlastDatabase db = databaseCache.remove(dbKey);
        
        // Delete from disk
        File dbDir = config.getSequenceTypeDbDir(species, seqType);
        if (dbDir.exists()) {
            try {
                if (db == null) {
                    db = loadDatabaseFromDisk(dbDir, seqType);
                }
                
                if (db != null) {
                    return db.delete();
                } else {
                    // Manual cleanup if metadata is corrupted
                    return deleteDirectoryContents(dbDir);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to delete database: " + dbKey, e);
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Delete directory contents
     */
    private boolean deleteDirectoryContents(File dir) {
        boolean success = true;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.delete()) {
                    logger.warning("Unable to delete file: " + file.getAbsolutePath());
                    success = false;
                }
            }
        }
        
        if (success && dir.list().length == 0) {
            dir.delete();
        }
        
        return success;
    }
    
    /**
     * Perform periodic cleanup of old databases and cache
     */
    private void performCleanup() {
        logger.fine("Running database cleanup...");
        
        long currentTime = System.currentTimeMillis();
        long maxAge = config.getMaxCacheAgeMillis();
        
        // Clean memory cache
        databaseCache.entrySet().removeIf(entry -> {
            BlastDatabase db = entry.getValue();
            return (currentTime - db.getCreationTime()) > maxAge;
        });
        
        // TODO: Implement disk space-based cleanup if needed
        
        logger.fine("Database cleanup completed");
    }
    
    /**
     * Get cache statistics
     */
    public CacheStatistics getCacheStatistics() {
        CacheStatistics stats = new CacheStatistics();
        stats.memoryCacheSize = databaseCache.size();
        stats.activeTasks = creationTasks.size();
        
        long totalDiskSize = 0;
        int validDatabases = 0;
        
        for (BlastDatabase db : databaseCache.values()) {
            if (db.isValid()) {
                validDatabases++;
                totalDiskSize += db.getDatabaseSize();
            }
        }
        
        stats.validDatabases = validDatabases;
        stats.totalDiskSizeBytes = totalDiskSize;
        
        return stats;
    }
    
    /**
     * Shutdown the database manager
     */
    public void shutdown() {
        logger.info("Shutting down BLAST database manager...");
        
        executorService.shutdown();
        cleanupExecutor.shutdown();
        
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            if (!cleanupExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("BLAST database manager closed");
    }
    
    /**
     * Database status enumeration
     */
    public enum DatabaseStatus {
        UP_TO_DATE("Up to date"),
        OUTDATED("Outdated"),
        NOT_CREATED("Not created"),
        CREATING("Creating"),
        SOURCE_MISSING("Source missing"),
        ERROR("Error");
        
        private final String description;
        
        DatabaseStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Cache statistics
     */
    public static class CacheStatistics {
        public int memoryCacheSize;
        public int activeTasks;
        public int validDatabases;
        public long totalDiskSizeBytes;
        
        @Override
        public String toString() {
            return String.format("Cache statistics: memory=%d, active tasks=%d, valid databases=%d, disk size=%d bytes",
                               memoryCacheSize, activeTasks, validDatabases, totalDiskSizeBytes);
        }
    }
}
