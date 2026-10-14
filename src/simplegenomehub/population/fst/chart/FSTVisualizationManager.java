package simplegenomehub.population.fst.chart;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.population.fst.view.FSTJieMian;
import simplegenomehub.visualization.regionset.RegionSetContext;
import simplegenomehub.runtime.HubSession;
import simplegenomehub.population.fst.FstRunContext;

/**
 * FST可视化管理器
 * 统一管理从用户文件和从计算结果目录打开可视化窗口的逻辑
 */
public class FSTVisualizationManager {
    
    /**
     * 从用户文件打开可视化窗口（用于FSTReadFile）
     * 
     * @param fstFile 单个FST文件（可为null）
     * @param fstFiles 多个FST文件列表（可为null）
     * @param piFile 单个PI文件（可为null）
     * @param piFiles 多个PI文件列表（可为null）
     * @param tajimaDFile 单个TajimaD文件（可为null）
     * @param tajimaDFiles 多个TajimaD文件列表（可为null）
     * @param logisticFile 单个Logistic文件（可为null）
     * @param logisticFiles 多个Logistic文件列表（可为null）
     * @param genomeFile 基因组文件（可为null）
     * @param gff3File GFF3文件（可为null）
     * @param parentWindow 父窗口（关闭时使用，可为null）
     */
    public static void openVisualizationFromFiles(File fstFile, List<File> fstFiles,
                                                  File piFile, List<File> piFiles,
                                                  File tajimaDFile, List<File> tajimaDFiles,
                                                  File logisticFile, List<File> logisticFiles,
                                                  File genomeFile, File gff3File,
                                                  Window parentWindow) {
        // 收集文件路径
        List<String> fstFilePaths = collectFilePaths(fstFile, fstFiles);
        List<String> piFilePaths = collectFilePaths(piFile, piFiles);
        List<String> tajimaDFilePaths = collectFilePaths(tajimaDFile, tajimaDFiles);
        List<String> logisticFilePaths = collectFilePaths(logisticFile, logisticFiles);
        
        // 打开可视化窗口（直接打开用户预计算结果：保持文件内的出场顺序）
        if (!fstFilePaths.isEmpty() || !piFilePaths.isEmpty() || !tajimaDFilePaths.isEmpty() || !logisticFilePaths.isEmpty()) {
            String gff3Path = (gff3File != null && gff3File.exists()) ? gff3File.getAbsolutePath() : null;
            String genomePath = (genomeFile != null && genomeFile.exists()) ? genomeFile.getAbsolutePath() : null;
            openVisualizationWindow(fstFilePaths, piFilePaths, tajimaDFilePaths, logisticFilePaths, gff3Path, genomePath, false, false, null, null, parentWindow);
            
            if (parentWindow != null) {
                parentWindow.dispose();
            }
        }
    }
    
    /**
     * 从计算结果目录打开可视化窗口（用于VCF计算后的结果）
     * 
     * @param workDirectory 工作目录路径（包含所有结果文件的目录）
     * @param gff3FilePath GFF3文件路径（可选）
     * @param genomeFilePath 基因组文件路径（可选，用于生成染色体长度文件）
     * @param parentComponent 父组件（用于显示错误对话框）
     */
    public static void openVisualizationFromDirectory(String workDirectory, 
                                                       String gff3FilePath,
                                                       String genomeFilePath,
                                                       Component parentComponent) {
        openVisualizationFromDirectory(workDirectory, gff3FilePath, genomeFilePath, parentComponent, false);
    }

    /**
     * 从计算结果目录打开可视化窗口（用于VCF计算后的结果）
     *
     * @param workDirectory 工作目录路径（包含所有结果文件的目录）
     * @param gff3FilePath GFF3文件路径（可选）
     * @param genomeFilePath 基因组文件路径（可选，用于生成染色体长度文件）
     * @param parentComponent 父组件（用于显示错误对话框）
     * @param sortChromosomesByNumber 展示结果时是否按染色体名称末尾数字排序（如 Chr1, Chr2, ..., Chr10）
     */
    public static void openVisualizationFromDirectory(String workDirectory, 
                                                       String gff3FilePath,
                                                       String genomeFilePath,
                                                       Component parentComponent,
                                                       boolean sortChromosomesByNumber) {
        openVisualizationFromDirectory(workDirectory, gff3FilePath, genomeFilePath, parentComponent,
                                       sortChromosomesByNumber, true, null);
    }

    /**
     * 从计算结果目录打开可视化窗口（完整参数）
     *
     * @param workDirectory 工作目录路径（包含所有结果文件的目录）
     * @param gff3FilePath GFF3文件路径（可选）
     * @param genomeFilePath 基因组文件路径（可选，用于生成染色体长度文件）
     * @param parentComponent 父组件（用于显示错误对话框）
     * @param sortChromosomesByNumber 展示结果时是否按染色体名称末尾数字排序
     * @param fromVCF true=由VCF计算流程打开（关闭结果窗口不退出程序）；
     *                false=由起始窗口加载结果文件夹打开（关闭结果窗口时退出程序）
     */
    public static void openVisualizationFromDirectory(String workDirectory, 
                                                       String gff3FilePath,
                                                       String genomeFilePath,
                                                       Component parentComponent,
                                                       boolean sortChromosomesByNumber,
                                                       boolean fromVCF,
                                                       SpeciesInfo species) {
        try {
            // 扫描工作目录，自动识别FST、PI和TajimaD文件
            simplegenomehub.population.fst.ResultDirectoryScanner.ResultFiles resultFiles = 
                simplegenomehub.population.fst.ResultDirectoryScanner.scanDirectory(workDirectory);
            
            if (resultFiles.isEmpty()) {
                JOptionPane.showMessageDialog(parentComponent,
                    "No result files (FST, PI, Tajima's D or Logistic) were found in this folder.\nFolder: " + workDirectory,
                    "No result files found",
                    JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            // 获取文件路径
            String singleFst = resultFiles.getSingleFSTFile();
            List<String> multipleFst = resultFiles.getMultipleFSTFiles();
            String singlePi = resultFiles.getSinglePIFile();
            List<String> multiplePi = resultFiles.getMultiplePIFiles();
            String singleTajimaD = resultFiles.getSingleTajimaDFile();
            List<String> multipleTajimaD = resultFiles.getMultipleTajimaDFiles();
            
            // 转换为统一格式
            List<String> fstFilePaths = new ArrayList<>();
            if (singleFst != null) {
                fstFilePaths.add(singleFst);
            } else if (multipleFst != null) {
                fstFilePaths.addAll(multipleFst);
            }
            
            List<String> piFilePaths = new ArrayList<>();
            if (singlePi != null) {
                piFilePaths.add(singlePi);
            } else if (multiplePi != null) {
                piFilePaths.addAll(multiplePi);
            }
            
            List<String> tajimaDFilePaths = new ArrayList<>();
            if (singleTajimaD != null) {
                tajimaDFilePaths.add(singleTajimaD);
            } else if (multipleTajimaD != null) {
                tajimaDFilePaths.addAll(multipleTajimaD);
            }
            
            // Logistic文件路径
            String singleLogistic = resultFiles.getSingleLogisticFile();
            List<String> multipleLogistic = resultFiles.getMultipleLogisticFiles();
            List<String> logisticFilePaths = new ArrayList<>();
            if (singleLogistic != null) {
                logisticFilePaths.add(singleLogistic);
            } else if (multipleLogistic != null) {
                logisticFilePaths.addAll(multipleLogistic);
            }
            
            // 打开可视化窗口
            openVisualizationWindow(fstFilePaths, piFilePaths, tajimaDFilePaths, logisticFilePaths, gff3FilePath, genomeFilePath, fromVCF, sortChromosomesByNumber, workDirectory, species, parentComponent);
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parentComponent,
                "Error opening the visualization window: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }
    
    /**
     * 收集文件路径（从单个文件或文件列表）
     */
    private static List<String> collectFilePaths(File singleFile, List<File> multipleFiles) {
        List<String> paths = new ArrayList<>();
        if (multipleFiles != null && !multipleFiles.isEmpty()) {
            // 多个文件
            for (File file : multipleFiles) {
                if (file != null && file.exists()) {
                    paths.add(file.getAbsolutePath());
                }
            }
        } else if (singleFile != null && singleFile.exists()) {
            // 单个文件
            paths.add(singleFile.getAbsolutePath());
        }
        return paths;
    }
    
    /**
     * 打开可视化窗口（统一入口）
     */
    private static void openVisualizationWindow(List<String> fstFilePaths, 
                                                List<String> piFilePaths,
                                                List<String> tajimaDFilePaths,
                                                List<String> logisticFilePaths,
                                                String gff3FilePath,
                                                String genomeFilePath,
                                                boolean isFromVCF,
                                                boolean sortChromosomesByNumber,
                                                String workDirectory,
                                                SpeciesInfo species, Component owner) {
        // 转换为单个/多个格式
        String singleFst = fstFilePaths.size() == 1 ? fstFilePaths.get(0) : null;
        List<String> multipleFst = fstFilePaths.size() > 1 ? fstFilePaths : null;
        String singlePi = piFilePaths.size() == 1 ? piFilePaths.get(0) : null;
        List<String> multiplePi = piFilePaths.size() > 1 ? piFilePaths : null;
        String singleTajimaD = tajimaDFilePaths.size() == 1 ? tajimaDFilePaths.get(0) : null;
        List<String> multipleTajimaD = tajimaDFilePaths.size() > 1 ? tajimaDFilePaths : null;
        String singleLogistic = logisticFilePaths.size() == 1 ? logisticFilePaths.get(0) : null;
        List<String> multipleLogistic = logisticFilePaths.size() > 1 ? logisticFilePaths : null;
        
        final HubSession session = species == null ? HubSession.forComponent(owner)
            : HubSession.forDataPath(species.getSpeciesDir());
        final FstRunContext context = new FstRunContext(session, species,
            workDirectory == null ? null : new File(workDirectory), sortChromosomesByNumber);
        Runnable show = () -> {
            if (!context.isCurrent()) return;
            FSTJieMian frame = new FSTJieMian(singleFst, multipleFst, singlePi, multiplePi,
                singleTajimaD, multipleTajimaD, singleLogistic, multipleLogistic, gff3FilePath, context);
            HubSession.attach(frame, session);
            frame.setFromVCF(isFromVCF);
            frame.setGenomeFilePath(genomeFilePath);
            frame.setWorkDirectory(workDirectory);
            frame.setVisible(true);
        };
        if (SwingUtilities.isEventDispatchThread()) show.run();
        else SwingUtilities.invokeLater(show);
    }
}
