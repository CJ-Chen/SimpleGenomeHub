/*
 * SimpleGenomeHub BLAST Database Model
 * Represents a BLAST database with metadata and validation capabilities
 */
package simplegenomehub.blast.model;

import simplegenomehub.blast.BlastConfig;
import simplegenomehub.blast.BlastConfig.SequenceType;
import simplegenomehub.blast.BlastConfig.MolecularType;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Model class representing a BLAST database
 * Handles database metadata, validation, and integrity checking
 * 
 * @author SimpleGenomeHub Team
 */
public class BlastDatabase {
    
    private static final Logger logger = Logger.getLogger(BlastDatabase.class.getName());
    private static final String METADATA_FILE_NAME = "database.info";
    
    private File databaseDir;
    private String databaseName;
    private SequenceType sequenceType;
    private MolecularType molecularType;
    private long creationTime;
    private long sourceFileModificationTime;
    private long sourceFileSize;
    private int sequenceCount;
    private String sourceFilePath;
    private String sourceContentDigest;
    private File boundSourceFile;
    private Map<String, String> customMetadata;
    
    /**
     * Create a new BlastDatabase instance
     */
    public BlastDatabase(File databaseDir, SequenceType sequenceType, 
                        File sourceFile) throws IOException {
        this(databaseDir, sequenceType, sourceFile, null);
    }

    /** The digest, when supplied, describes the immutable input used by makeblastdb. */
    public BlastDatabase(File databaseDir, SequenceType sequenceType,
                         File sourceFile, String indexedSourceDigest) throws IOException {
        this.databaseDir = databaseDir;
        this.databaseName = sequenceType.toString().toLowerCase();
        this.sequenceType = sequenceType;
        this.molecularType = sequenceType.getMolecularType();
        this.creationTime = System.currentTimeMillis();
        this.sourceFileModificationTime = sourceFile.lastModified();
        this.sourceFileSize = sourceFile.length();
        this.sourceFilePath = sourceFile.getAbsolutePath();
        this.customMetadata = new HashMap<>();
        this.sourceContentDigest = digestSourceFile(sourceFile);
        if (indexedSourceDigest != null && !indexedSourceDigest.equals(sourceContentDigest))
            throw new IOException("Source FASTA changed while its BLAST database was being built: " + sourceFile);
        this.boundSourceFile = sourceFile.getCanonicalFile();
        
        // Count sequences in source file
        this.sequenceCount = countSequencesInFasta(sourceFile);
        
        // Save metadata
        saveMetadata();
    }
    
    /**
     * Load existing BlastDatabase from disk
     */
    public static BlastDatabase loadFromDisk(File databaseDir, SequenceType sequenceType) 
            throws IOException {
        File metadataFile = new File(databaseDir, METADATA_FILE_NAME);
        if (!metadataFile.exists()) {
            return null;
        }
        
        Properties props = new Properties();
        try (InputStream is = new FileInputStream(metadataFile)) {
            props.load(is);
        }
        
        BlastDatabase db = new BlastDatabase();
        db.databaseDir = databaseDir;
        db.databaseName = sequenceType.toString().toLowerCase();
        db.sequenceType = sequenceType;
        db.molecularType = MolecularType.valueOf(props.getProperty("molecular.type"));
        db.creationTime = Long.parseLong(props.getProperty("creation.time"));
        db.sourceFileModificationTime = Long.parseLong(props.getProperty("source.modification.time"));
        db.sourceFileSize = Long.parseLong(props.getProperty("source.file.size", "0"));
        db.sequenceCount = Integer.parseInt(props.getProperty("sequence.count", "0"));
        db.sourceFilePath = props.getProperty("source.file.path", "");
        db.sourceContentDigest = props.getProperty("source.sha256", "");
        db.customMetadata = new HashMap<>();
        
        // Load custom metadata
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("custom.")) {
                db.customMetadata.put(key.substring(7), props.getProperty(key));
            }
        }
        
        return db;
    }
    
    /**
     * Private constructor for loading from disk
     */
    private BlastDatabase() {
        // Used for loading from disk
    }
    
    /**
     * Check if database is valid (all required files exist)
     */
    public boolean isValid() {
        return databaseDir.exists() && 
               hasRequiredFiles() && 
               !isCorrupted();
    }
    
    /**
     * Check if database is up to date compared to source file
     */
    public boolean isUpToDate(File sourceFile) {
        try { return bindSourceFile(sourceFile); }
        catch (IOException failure) {
            logger.warning("Cannot verify BLAST source identity: " + failure.getMessage());
            return false;
        }
    }

    /** Bind only the current species' source; historical metadata paths are never a fallback. */
    public synchronized boolean bindSourceFile(File sourceFile) throws IOException {
        boundSourceFile = null;
        // Legacy metadata has no proof of sequence content or ordinal order. Rebuild it once.
        if (sourceFile == null || !sourceFile.isFile() || !sourceContentDigest.matches("[0-9a-f]{64}")
            || sourceFile.length() != sourceFileSize) return false;
        if (!sourceContentDigest.equals(digestSourceFile(sourceFile))) return false;
        boundSourceFile = sourceFile.getCanonicalFile();
        return true;
    }

    /** Recheck before mapping BLAST ordinals, since the source may change during a long search. */
    public synchronized File requireVerifiedSourceFile() throws IOException {
        File source = boundSourceFile;
        if (source == null || !bindSourceFile(source))
            throw new IOException("BLAST source is unbound or its content has changed; rebuild the database: " + databaseDir);
        return source;
    }

    /** A streaming, byte-exact identity includes FASTA headers and record order. */
    public static String digestSourceFile(File source) throws IOException {
        BasicFileAttributes before = Files.readAttributes(source.toPath(), BasicFileAttributes.class);
        if (!before.isRegularFile()) throw new IOException("Source FASTA is not a regular file: " + source);
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        try (InputStream input = Files.newInputStream(source.toPath())) {
            byte[] buffer = new byte[65536];
            int length;
            while ((length = input.read(buffer)) >= 0) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("BLAST source verification cancelled");
                if (length > 0) digest.update(buffer, 0, length);
            }
        }
        BasicFileAttributes after = Files.readAttributes(source.toPath(), BasicFileAttributes.class);
        if (before.size() != after.size() || !before.lastModifiedTime().equals(after.lastModifiedTime())
            || !java.util.Objects.equals(before.fileKey(), after.fileKey()))
            throw new IOException("Source FASTA changed while verifying its content: " + source);
        StringBuilder encoded = new StringBuilder(64);
        for (byte value : digest.digest()) encoded.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
        return encoded.toString();
    }
    
    /**
     * Check if all required database files exist
     */
    private boolean hasRequiredFiles() {
        java.util.List<File> required = requiredFiles();
        if (required.isEmpty()) return false;
        for (File dbFile : required) {
            if (!dbFile.exists() || dbFile.length() == 0) {
                logger.warning("Missing database file: " + dbFile.getAbsolutePath());
                return false;
            }
        }
        return true;
    }

    /** v4 databases larger than makeblastdb's volume limit use .00/.01 indexes plus an alias. */
    private java.util.List<File> requiredFiles() {
        return requiredFiles(databaseDir, databaseName, molecularType);
    }

    /** Shared by extraction of old databases that have no SGH metadata. */
    public static boolean hasCompleteIndex(File databasePrefix, MolecularType molecularType) {
        if (databasePrefix == null || molecularType == null) return false;
        File absolute = databasePrefix.getAbsoluteFile();
        java.util.List<File> files = requiredFiles(absolute.getParentFile(), absolute.getName(), molecularType);
        if (files.isEmpty()) return false;
        for (File file : files) if (!file.isFile() || file.length() < 10) return false;
        return true;
    }

    private static java.util.List<File> requiredFiles(File databaseDir, String databaseName, MolecularType molecularType) {
        java.util.List<File> files = new java.util.ArrayList<>();
        String[] extensions = molecularType.getRequiredExtensions();
        File alias = new File(databaseDir, databaseName + (molecularType == MolecularType.PROTEIN ? ".pal" : ".nal"));
        if (!alias.exists() && new File(databaseDir, databaseName + extensions[0]).isFile()) {
            for (String extension : extensions) files.add(new File(databaseDir, databaseName + extension));
            return files;
        }
        if (!alias.isFile() || alias.length() == 0) return files;
        // The alias is authoritative: enumerating existing volumes would miss a
        // completely deleted final volume and incorrectly accept a partial DB.
        try {
            int expected = 0;
            for (String line : java.nio.file.Files.readAllLines(alias.toPath())) {
                String trimmed = line.trim();
                if (!trimmed.matches("DBLIST\\s+.*")) continue;
                java.util.regex.Matcher tokens = java.util.regex.Pattern.compile("\"([^\"]+)\"|(\\S+)")
                    .matcher(trimmed.substring(6));
                while (tokens.find()) {
                    String prefix = tokens.group(1) == null ? tokens.group(2) : tokens.group(1);
                    if (!prefix.matches(java.util.regex.Pattern.quote(databaseName) + "\\.[0-9]+")
                            || Integer.parseInt(prefix.substring(databaseName.length() + 1)) != expected++)
                        return java.util.Collections.emptyList();
                    for (String extension : extensions) files.add(new File(databaseDir, prefix + extension));
                }
            }
            if (expected == 0) return java.util.Collections.emptyList();
        } catch (IOException | NumberFormatException failure) {
            logger.warning("Cannot read BLAST database volumes: " + alias + ": " + failure.getMessage());
            return java.util.Collections.emptyList();
        }
        return files;
    }
    
    /**
     * Check if database files are corrupted
     */
    private boolean isCorrupted() {
        // Basic corruption check - ensure files have reasonable sizes
        for (File dbFile : requiredFiles()) {
            if (dbFile.exists() && dbFile.length() < 10) {
                logger.warning("Database file may be corrupted (file too small): " + dbFile.getAbsolutePath());
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get the database path for BLAST commands
     */
    public String getDatabasePath() {
        return new File(databaseDir, databaseName).getAbsolutePath();
    }
    
    /**
     * Get database size in bytes
     */
    public long getDatabaseSize() {
        long totalSize = 0;
        for (File dbFile : requiredFiles()) {
            if (dbFile.exists()) {
                totalSize += dbFile.length();
            }
        }
        
        return totalSize;
    }
    
    /**
     * Save database metadata to file
     */
    private void saveMetadata() throws IOException {
        File metadataFile = new File(databaseDir, METADATA_FILE_NAME);
        Properties props = new Properties();
        
        props.setProperty("database.name", databaseName);
        props.setProperty("sequence.type", sequenceType.name());
        props.setProperty("molecular.type", molecularType.name());
        props.setProperty("creation.time", String.valueOf(creationTime));
        props.setProperty("source.modification.time", String.valueOf(sourceFileModificationTime));
        props.setProperty("source.file.size", String.valueOf(sourceFileSize));
        props.setProperty("sequence.count", String.valueOf(sequenceCount));
        props.setProperty("source.file.path", sourceFilePath);
        if (sourceContentDigest != null && !sourceContentDigest.isEmpty()) props.setProperty("source.sha256", sourceContentDigest);
        
        // Save custom metadata
        for (Map.Entry<String, String> entry : customMetadata.entrySet()) {
            props.setProperty("custom." + entry.getKey(), entry.getValue());
        }
        
        java.util.Map<String, String> changes = new java.util.LinkedHashMap<>();
        for (String key : props.stringPropertyNames()) changes.put(key, props.getProperty(key));
        simplegenomehub.util.fileio.AtomicFiles.updateProperties(metadataFile.toPath(), changes,
            "SimpleGenomeHub BLAST Database Metadata");
    }
    
    /**
     * Count sequences in FASTA file
     */
    private int countSequencesInFasta(File fastaFile) {
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(fastaFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(">")) {
                    count++;
                }
            }
        } catch (IOException e) {
            logger.warning("Unable to count sequences in FASTA file: " + e.getMessage());
        }
        return count;
    }
    
    /**
     * Delete database files and metadata
     */
    public boolean delete() {
        boolean success = true;
        
        // Delete database files
        String[] extensions = molecularType.getRequiredExtensions();
        for (String ext : extensions) {
            File dbFile = new File(databaseDir, databaseName + ext);
            if (dbFile.exists() && !dbFile.delete()) {
                logger.warning("Unable to delete database file: " + dbFile.getAbsolutePath());
                success = false;
            }
        }
        
        // Delete metadata file
        File metadataFile = new File(databaseDir, METADATA_FILE_NAME);
        if (metadataFile.exists() && !metadataFile.delete()) {
            logger.warning("Unable to delete metadata file: " + metadataFile.getAbsolutePath());
            success = false;
        }
        
        // Try to delete directory if empty
        if (databaseDir.exists() && databaseDir.list().length == 0) {
            databaseDir.delete();
        }
        
        return success;
    }
    
    /**
     * Add custom metadata
     */
    public void addMetadata(String key, String value) {
        customMetadata.put(key, value);
        try {
            saveMetadata();
        } catch (IOException e) {
            logger.warning("Unable to save custom metadata: " + e.getMessage());
        }
    }

    public void addMetadataChecked(String key, String value) throws IOException {
        customMetadata.put(key, value);
        saveMetadata();
    }
    
    /**
     * Get custom metadata
     */
    public String getMetadata(String key) {
        return customMetadata.get(key);
    }
    
    // Getters
    public File getDatabaseDir() {
        return databaseDir;
    }
    
    public String getDatabaseName() {
        return databaseName;
    }
    
    public SequenceType getSequenceType() {
        return sequenceType;
    }
    
    public MolecularType getMolecularType() {
        return molecularType;
    }
    
    public long getCreationTime() {
        return creationTime;
    }
    
    public long getSourceFileModificationTime() {
        return sourceFileModificationTime;
    }
    
    public long getSourceFileSize() {
        return sourceFileSize;
    }
    
    public int getSequenceCount() {
        return sequenceCount;
    }
    
    public String getSourceFilePath() {
        File source = boundSourceFile;
        return source == null ? sourceFilePath : source.getAbsolutePath();
    }

    public String getSourceContentDigest() { return sourceContentDigest; }
    
    public Map<String, String> getCustomMetadata() {
        return new HashMap<>(customMetadata);
    }
    
    @Override
    public String toString() {
        return String.format("BlastDatabase{name='%s', type=%s, sequences=%d, size=%d bytes}", 
                           databaseName, sequenceType, sequenceCount, getDatabaseSize());
    }
}
