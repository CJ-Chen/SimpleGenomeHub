/*
 * Lazy Species Loader for Performance Optimization
 */
package simplegenomehub.util.performance;

import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.runtime.HubSession;
import simplegenomehub.model.GenomeData;
import simplegenomehub.model.SpeciesMetadata;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Lazy loading utility for species data to improve performance
 * with large datasets. Loads basic info immediately but defers
 * heavy operations like genome statistics until actually needed.
 * 
 * @author SimpleGenomeHub
 */
public class LazySpeciesLoader {
    
    private static final Logger logger = Logger.getLogger(LazySpeciesLoader.class.getName());
    private static final ConcurrentHashMap<String, ExecutorService> executors = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong> rootGenerations = new ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong generation = new java.util.concurrent.atomic.AtomicLong();
    private static String rootKey(File root) {
        try { return root.getCanonicalPath(); }
        catch (java.io.IOException e) { return root.getAbsolutePath(); }
    }
    private static java.util.concurrent.atomic.AtomicLong rootGeneration(SpeciesInfo species) {
        return rootGenerations.computeIfAbsent(rootKey(species.getSpeciesDir().getParentFile()), ignored -> new java.util.concurrent.atomic.AtomicLong());
    }
    private static synchronized ExecutorService executor(SpeciesInfo species) {
        return executors.computeIfAbsent(rootKey(species.getSpeciesDir().getParentFile()), ignored ->
            Executors.newFixedThreadPool(4, runnable -> {
                Thread thread = new Thread(runnable, "SGH-species-loader");
                thread.setDaemon(true);
                return thread;
            }));
    }

    // Cache for loaded genome data
    private static final ConcurrentHashMap<String, GenomeData> genomeDataCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CompletableFuture<GenomeData>> loadingTasks = new ConcurrentHashMap<>();
    
    /**
     * Load species with lazy loading strategy
     */
    public static SpeciesInfo loadSpeciesLazy(File speciesDir) {
        String dirName = speciesDir.getName();
        
        // Parse directory name (Species.version format)
        String[] parts = dirName.split("\\.", 2);
        if (parts.length != 2) {
            logger.warning("Invalid species directory name format: " + dirName);
            return null;
        }
        
        String speciesName = parts[0];
        String version = parts[1];
        
        // Create species info with basic data only
        SpeciesInfo species = new SpeciesInfo(speciesName, version);
        species.initializeFileStructure(speciesDir.getParentFile());
        
        // Try to load metadata first
        SpeciesMetadata metadata = SpeciesMetadata.loadFromFile(speciesDir);
        if (metadata != null) {
            metadata.applyToSpeciesInfo(species);
            logger.info("Loaded metadata for " + speciesName + "." + version + " with notes: " + 
                       (metadata.getNotes().isEmpty() ? "none" : "'" + metadata.getNotes() + "'"));
        } else {
            // Fallback: Set import time from directory modification time
            try {
                long lastModified = speciesDir.lastModified();
                species.setImportTime(java.time.LocalDateTime.ofEpochSecond(
                    lastModified / 1000, 0, java.time.ZoneOffset.systemDefault().getRules()
                    .getOffset(java.time.Instant.ofEpochMilli(lastModified))));
            } catch (Exception e) {
                logger.warning("Failed to determine import time for: " + dirName);
            }
        }
        
        // Check if genome data is immediately available (cached or quick load)
        String cacheKey = getCacheKey(species);
        GenomeData cachedData = genomeDataCache.get(cacheKey);
        if (cachedData != null) {
            species.setGenomeData(cachedData);
        } else if (species.getStatsFile().exists() && species.getStatsFile().length() < 1024 * 1024) {
            // Quick load for small stats files
            try {
                GenomeData genomeData = GenomeData.loadFromFile(species.getStatsFile());
                if (genomeData != null) {
                    species.setGenomeData(genomeData);
                    genomeDataCache.put(cacheKey, genomeData);
                }
            } catch (Exception e) {
                logger.warning("Failed to quick-load stats for: " + dirName);
            }
        }
        
        // Note: Functional annotations are now loaded on-demand to improve startup performance
        
        return species;
    }
    
    /**
     * Load genome data asynchronously
     */
    public static CompletableFuture<GenomeData> loadGenomeDataAsync(SpeciesInfo species) {
        return loadGenomeDataAsync(species, HubSession.current());
    }

    public static CompletableFuture<GenomeData> loadGenomeDataAsync(SpeciesInfo species, HubSession session) {
        String cacheKey = getCacheKey(species);
        
        // Return cached data if available
        GenomeData cached = genomeDataCache.get(cacheKey);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        
        // Return existing loading task if already in progress
        CompletableFuture<GenomeData> existingTask = loadingTasks.get(cacheKey);
        if (existingTask != null) {
            return existingTask;
        }
        
        // A retired session must not repopulate caches after close or root switch.
        final long expectedGeneration = generation.get();
        final java.util.concurrent.atomic.AtomicLong rootEpoch = rootGeneration(species);
        final long expectedRootEpoch = rootEpoch.get();
        final long sessionGeneration = session == null ? 0 : session.getGeneration();
        final HubSession.Task actualTask = session == null ? null : session.beginTask("Read genome statistics", null);
        CompletableFuture<GenomeData> loadingTask = CompletableFuture.supplyAsync(() -> {
            try {
                GenomeData genomeData = null;
                
                // Try to load from stats file first
                if (species.getStatsFile().exists()) {
                    genomeData = GenomeData.loadFromFile(species.getStatsFile());
                }
                
                // If no stats file or loading failed, calculate fresh
                if (genomeData == null) {
                    File genomeFile = findGenomeFile(species);
                    File annotationFile = findAnnotationFile(species);
                    
                    if (genomeFile != null && annotationFile != null) {
                        genomeData = simplegenomehub.util.fileio.GenomeStatsCalculator
                            .calculateGenomeStats(genomeFile, annotationFile);
                    }
                }
                
                if (genomeData != null && generation.get() == expectedGeneration && rootEpoch.get() == expectedRootEpoch && (session == null || session.accepts(sessionGeneration))) {
                    genomeDataCache.put(cacheKey, genomeData);
                }
                
                return genomeData;
                
            } catch (Exception e) {
                logger.warning("Failed to load genome data for " + species.getDisplayName() + ": " + e.getMessage());
                return null;
            } finally {
                if (actualTask != null) actualTask.close();
            }
        }, executor(species));

        loadingTasks.put(cacheKey, loadingTask);
        loadingTask.whenComplete((value, failure) -> loadingTasks.remove(cacheKey, loadingTask));
        return loadingTask;
    }
    
    /**
     * Preload genome data for multiple species in background
     */
    public static void preloadGenomeData(java.util.List<SpeciesInfo> speciesList) {
        preloadGenomeData(speciesList, HubSession.current());
    }

    public static void preloadGenomeData(java.util.List<SpeciesInfo> speciesList, HubSession session) {
        final long sessionGeneration = session == null ? 0 : session.getGeneration();
        final long expectedGeneration = generation.get();
        for (SpeciesInfo species : speciesList) {
            final java.util.concurrent.atomic.AtomicLong rootEpoch = rootGeneration(species);
            final long expectedRootEpoch = rootEpoch.get();
            if (species.getGenomeData() == null) {
                loadGenomeDataAsync(species, session).thenAccept(genomeData -> {
                    if (genomeData != null && expectedGeneration == generation.get() && rootEpoch.get() == expectedRootEpoch && (session == null || session.accepts(sessionGeneration))) {
                        species.setGenomeData(genomeData);
                    }
                });
            }
        }
    }
    
    /**
     * Clear cache to free memory
     */
    public static void clearCache() {
        generation.incrementAndGet();
        genomeDataCache.clear();
        loadingTasks.clear();
        logger.info("Cleared genome data cache");
    }
    
    /**
     * Get cache statistics
     */
    public static String getCacheStats() {
        return String.format("Cache: %d entries, Loading: %d tasks", 
            genomeDataCache.size(), loadingTasks.size());
    }
    
    /**
     * Generate cache key for species
     */
    private static String getCacheKey(SpeciesInfo species) {
        File directory = species.getSpeciesDir();
        try { return directory.getCanonicalPath(); }
        catch (java.io.IOException e) { return directory.getAbsolutePath(); }
    }
    
    /**
     * Find genome file in species
     */
    private static File findGenomeFile(SpeciesInfo species) {
        if (species.getSequenceDir() == null) return null;
        
        File[] genomeFiles = species.getSequenceDir().listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".fa") || 
            name.toLowerCase().endsWith(".fasta") ||
            name.toLowerCase().endsWith(".fas"));
        
        return (genomeFiles != null && genomeFiles.length > 0) ? genomeFiles[0] : null;
    }
    
    /**
     * Find annotation file in species
     */
    private static File findAnnotationFile(SpeciesInfo species) {
        if (species.getAnnotationDir() == null) return null;
        
        File[] annotationFiles = species.getAnnotationDir().listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".gff") || 
            name.toLowerCase().endsWith(".gff3") ||
            name.toLowerCase().endsWith(".gtf"));
        
        return (annotationFiles != null && annotationFiles.length > 0) ? annotationFiles[0] : null;
    }
    
    /**
     * Shutdown executor on application exit
     */
    public static void clearRoot(File root) {
        if (root == null) return;
        String identity = rootKey(root);
        rootGenerations.computeIfAbsent(identity, ignored -> new java.util.concurrent.atomic.AtomicLong()).incrementAndGet();
        String prefix = identity + File.separator;
        genomeDataCache.keySet().removeIf(key -> key.startsWith(prefix));
        loadingTasks.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public static void shutdownRoot(File root) {
        if (root == null) return;
        clearRoot(root);
        ExecutorService old;
        synchronized (LazySpeciesLoader.class) { old = executors.remove(rootKey(root)); }
        awaitShutdown(old);
    }

    public static void shutdown() {
        clearCache();
        java.util.List<ExecutorService> old;
        synchronized (LazySpeciesLoader.class) { old = new java.util.ArrayList<>(executors.values()); executors.clear(); }
        for (ExecutorService executor : old) awaitShutdown(executor);
    }

    private static void awaitShutdown(ExecutorService old) {
        if (old == null) return;
        // Queued read tasks also own session leases; let their finally blocks run.
        old.shutdown();
        boolean interrupted = false;
        while (!old.isTerminated()) {
            try { old.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException e) { interrupted = true; }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }
}