/*
 * SimpleGenomeHub Configuration Management
 * Based on TBtools configuration pattern
 */
package simplegenomehub.config;

import java.io.*;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import java.awt.GraphicsEnvironment;
import java.util.HashMap;
import java.util.Map;
import simplegenomehub.util.fileio.AtomicFiles;

/**
 * Configuration management for SimpleGenomeHub
 * Handles data directory settings and persistent configuration storage
 * 
 * @author SimpleGenomeHub
 */
public class SimpleGenomeHubConfig {
    
    private static final Logger logger = Logger.getLogger(SimpleGenomeHubConfig.class.getName());
    
    // Configuration keys
    public static final String DATA_ROOT_DIR = "data.root.directory";
    public static final String LAST_SPECIES_COUNT = "data.species.count";
    public static final String CONFIG_VERSION = "config.version";
    public static final String GO_OBO_GLOBAL_PATH = "go.obo.global.path";
    public static final String GO_OBO_LAST_SOURCE = "go.obo.last.source";
    public static final String GO_OBO_LAST_VERIFIED_EPOCH = "go.obo.last.verified.epoch";
    public static final String KEGG_BACKEND_MODE = "kegg.backend.mode";
    public static final String KEGG_BACKEND_TYPE = "kegg.backend.type";
    public static final String KEGG_CUSTOM_BACKEND_PATH = "kegg.custom.backend.path";
    public static final String EGGNOG_TAX_SCOPE = "eggnog.tax.scope";
    public static final String EGGNOG_CPU = "eggnog.cpu";
    public static final String EGGNOG_EVALUE = "eggnog.evalue";
    public static final String EGGNOG_OUTPUT_PREFIX = "eggnog.output.prefix";
    public static final String EGGNOG_KEGG_BACKEND_MODE = "eggnog.kegg.backend.mode";
    public static final String EGGNOG_KEGG_BACKEND_TYPE = "eggnog.kegg.backend.type";
    public static final String UI_FONT_SCALE_PERCENT = "ui.font.scale.percent";
    
    // Default values
    private static final String DEFAULT_CONFIG_VERSION = "1.0";
    private static final String CONFIG_FILE_NAME = "SimpleGenomeHub.config";
    
    // Instance variables
    private File homeDir;
    private File configFile;
    private File dataRootDir;
    private Properties configProperties;
    private Properties savedProperties = new Properties();
    
    private static SimpleGenomeHubConfig instance;
    
    /**
     * Private constructor for singleton pattern
     */
    private SimpleGenomeHubConfig() {
        initializeConfig();
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized SimpleGenomeHubConfig getInstance() {
        if (instance == null) {
            instance = new SimpleGenomeHubConfig();
        }
        return instance;
    }
    
    /**
     * Initialize configuration system
     */
    private void initializeConfig() {
        try {
            // Set up home directory (TBtools userHome pattern)
            String userHome = System.getProperty("user.home");
            homeDir = new File(userHome, ".SimpleGenomeHub");
            
            configFile = new File(homeDir, CONFIG_FILE_NAME);
            configProperties = new Properties();
            
            loadConfig();
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize configuration", e);
            throw new IllegalStateException("Failed to initialize configuration", e);
        }
    }
    
    /**
     * Load configuration from file
     */
    private void loadConfig() {
        if (configFile.exists()) {
            try {
                configProperties.putAll(AtomicFiles.readProperties(configFile.toPath()));
                savedProperties.putAll(configProperties);
                logger.info("Configuration loaded from: " + configFile.getAbsolutePath());
                
                // Validate data root directory
                String dataRootPath = configProperties.getProperty(DATA_ROOT_DIR);
                if (dataRootPath != null) {
                    dataRootDir = new File(dataRootPath);
                    if (!dataRootDir.exists() || !dataRootDir.isDirectory()) {
                        logger.warning("Data root directory not found: " + dataRootPath);
                        dataRootDir = null;
                    }
                }
                
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to load configuration", e);
                setDefaultConfig();
            }
        } else {
            setDefaultConfig();
        }
    }
    
    /**
     * Set default configuration values
     */
    private void setDefaultConfig() {
        configProperties.setProperty(CONFIG_VERSION, DEFAULT_CONFIG_VERSION);
        configProperties.setProperty(LAST_SPECIES_COUNT, "0");
        configProperties.setProperty(EGGNOG_TAX_SCOPE, "Viridiplantae");
        configProperties.setProperty(EGGNOG_CPU, "8");
        configProperties.setProperty(EGGNOG_EVALUE, "0.001");
        configProperties.setProperty(EGGNOG_KEGG_BACKEND_MODE, "PRESET");
        configProperties.setProperty(EGGNOG_KEGG_BACKEND_TYPE, "Plants");
        configProperties.setProperty(UI_FONT_SCALE_PERCENT, "100");
    }
    
    /**
     * Save configuration to file
     */
    public synchronized void saveConfig() {
        try { saveConfigChecked(); }
        catch (IOException e) { reportWriteFailure(e); }
    }

    public synchronized void saveConfigChecked() throws IOException {
        Map<String, String> changes = new HashMap<>();
        for (String key : configProperties.stringPropertyNames()) {
            if (!java.util.Objects.equals(configProperties.getProperty(key), savedProperties.getProperty(key))) {
                changes.put(key, configProperties.getProperty(key));
            }
        }
        for (String key : savedProperties.stringPropertyNames()) {
            if (!configProperties.containsKey(key)) changes.put(key, null);
        }
        if (changes.isEmpty()) return;
        Properties merged = AtomicFiles.updateProperties(configFile.toPath(), changes, "SimpleGenomeHub Configuration File");
        configProperties = merged;
        savedProperties = new Properties();
        savedProperties.putAll(merged);
    }

    private void reportWriteFailure(IOException failure) {
        logger.log(Level.SEVERE, "Failed to save configuration", failure);
        if (!GraphicsEnvironment.isHeadless()) {
            javax.swing.SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                "Failed to save Hub configuration:\n" + failure.getMessage(), "Configuration Error", JOptionPane.ERROR_MESSAGE));
        }
    }

    /**
     * Get data root directory
     */
    public synchronized File getDataRootDir() {
        return dataRootDir;
    }
    
    /**
     * Set data root directory with validation
     */
    public synchronized boolean setDataRootDir(File newDataDir) {
        try { setDataRootDirChecked(newDataDir); return true; }
        catch (IOException e) { reportWriteFailure(e); return false; }
    }

    /** Transaction commit only: callers acquire the candidate session lease before this method. */
    public synchronized void setDataRootDirChecked(File newDataDir) throws IOException {
        if (newDataDir == null || !newDataDir.isDirectory() || !newDataDir.canWrite()) {
            throw new IOException("Directory is not accessible or writable: " + newDataDir);
        }
        File candidate = newDataDir.getCanonicalFile();
        String previous = configProperties.getProperty(DATA_ROOT_DIR);
        configProperties.setProperty(DATA_ROOT_DIR, candidate.getPath());
        try { saveConfigChecked(); }
        catch (IOException failure) {
            if (previous == null) configProperties.remove(DATA_ROOT_DIR);
            else configProperties.setProperty(DATA_ROOT_DIR, previous);
            throw failure;
        }
        dataRootDir = candidate;
    }

    /**
     * Check if data root directory is configured
     */
    public synchronized boolean isDataRootConfigured() {
        return dataRootDir != null && dataRootDir.exists() && dataRootDir.isDirectory();
    }
    
    /**
     * Get configuration property
     */
    public synchronized String getProperty(String key) {
        return configProperties.getProperty(key);
    }
    
    /**
     * Set configuration property
     */
    public synchronized void setProperty(String key, String value) {
        try { setPropertyChecked(key, value); }
        catch (IOException e) { reportWriteFailure(e); }
    }

    public synchronized void setPropertyChecked(String key, String value) throws IOException {
        String previous = configProperties.getProperty(key);
        if (java.util.Objects.equals(previous, value)) return;
        if (value == null) configProperties.remove(key); else configProperties.setProperty(key, value);
        try { saveConfigChecked(); }
        catch (IOException e) {
            if (previous == null) configProperties.remove(key); else configProperties.setProperty(key, previous);
            throw e;
        }
    }

    /**
     * Get configuration property as integer
     */
    public synchronized int getIntProperty(String key, int defaultValue) {
        String value = configProperties.getProperty(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                logger.warning("Invalid integer property: " + key + " = " + value);
            }
        }
        return defaultValue;
    }
    
    /**
     * Set configuration property as integer
     */
    public void setIntProperty(String key, int value) {
        setProperty(key, String.valueOf(value));
    }

    public int getUiFontScalePercent() {
        return getIntProperty(UI_FONT_SCALE_PERCENT, 100);
    }

    public void setUiFontScalePercent(int scalePercent) {
        setIntProperty(UI_FONT_SCALE_PERCENT, scalePercent);
    }
    
    /**
     * Get home directory
     */
    public File getHomeDir() {
        return homeDir;
    }
    
    /**
     * Get config file
     */
    public File getConfigFile() {
        return configFile;
    }
}
