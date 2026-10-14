package simplegenomehub.population.fst;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * FST输出目录管理类
 * 用于创建和管理基于时间戳的输出文件夹
 */
public class FSTOutputDirectory {
    private static File outputDirectory = null;  // 当前结果目录（= 批次目录/w<W>_s<S>）
    private static File batchDirectory = null;    // 当前批次目录（FSTool-时间戳）
    private static File baseDirectory = null;     // 最近一次创建输出目录所用的父目录

    /**
     * 创建并返回输出目录
     * 目录命名格式：FSTool-YYYY-MM-DD-HH-mm
     * 注意：如果进程内已存在有效输出目录，会直接复用（不会新建）。
     * 
     * @param baseDir 基础目录（如果为null，使用当前工作目录）
     * @return 创建的输出目录
     */
    public static File createOutputDirectory(File baseDir) {
        if (outputDirectory != null && outputDirectory.exists()) {
            return outputDirectory;
        }
        outputDirectory = createUniqueTimestampDirectory(baseDir);
        return outputDirectory;
    }

    /**
     * 无条件创建并返回一个全新的输出目录（每个运行批次一个目录）。
     * 与 createOutputDirectory 不同：即使已有有效目录，也会在 baseDir 下
     * 新建一个带时间戳的新目录，并把当前输出目录切换到新目录。
     * 目录命名格式：FSTool-YYYY-MM-DD-HH-mm（已存在时追加 -1、-2 序号）。
     *
     * @param baseDir 父目录（如果为null，使用当前工作目录）
     * @return 新建的输出目录
     */
    public static File createNewRunDirectory(File baseDir) {
        outputDirectory = createUniqueTimestampDirectory(baseDir);
        batchDirectory = outputDirectory;
        return outputDirectory;
    }

    /**
     * 在"批次目录"（FSTool-时间戳）下创建/复用按窗口与步长命名的结果文件夹：
     * <pre>FSTool-时间戳/w&lt;windowSize&gt;_s&lt;stepSize&gt;/</pre>
     * 并把当前结果目录切换到该文件夹。已存在则直接复用。
     *
     * @param windowSize 窗口大小
     * @param stepSize   步长
     * @return 结果文件夹（所有结果文件都写到这里）
     */
    public static File getOrCreateParamDirectory(int windowSize, int stepSize) {
        File base = batchDirectory;
        if (base == null) {
            base = (outputDirectory != null && outputDirectory.exists()) ? outputDirectory : baseDirectory;
        }
        if (base == null || !base.exists()) {
            base = new File(System.getProperty("user.dir"));
        }
        File param = new File(base, "w" + windowSize + "_s" + stepSize);
        if (!param.exists() && !param.mkdirs()) {
            throw new RuntimeException("Cannot create result folder: " + param.getAbsolutePath());
        }
        outputDirectory = param;
        System.out.println("Result folder: " + param.getAbsolutePath());
        return param;
    }

    /**
     * 无条件创建并返回一个全新的输出目录（无参版本）。
     * 父目录使用最近一次创建输出目录所用的父目录；如果没有记录，使用当前工作目录。
     *
     * @return 新建的输出目录
     */
    public static File createNewRunDirectory() {
        return createNewRunDirectory(baseDirectory);
    }

    /**
     * 在指定父目录下生成一个不重复的时间戳目录，并记录该父目录。
     * 目录名就是运行时间（例如 2026-09-22-14-30-05）；同一秒内重复运行时追加序号。
     */
    private static File createUniqueTimestampDirectory(File baseDir) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        String folderName = sdf.format(new Date());

        // 确定基础目录：SimpleGenomeHub 会显式传入 <物种单元>/Population/FST
        File base = (baseDir != null && baseDir.exists()) ? baseDir : new File(System.getProperty("user.dir"));
        baseDirectory = base;

        // 如果目录已存在，添加序号后缀
        File finalDir = new File(base, folderName);
        int counter = 1;
        while (finalDir.exists()) {
            finalDir = new File(base, folderName + "-" + counter);
            counter++;
        }

        // 创建目录
        if (!finalDir.mkdirs()) {
            throw new RuntimeException("Cannot create output folder: " + finalDir.getAbsolutePath());
        }

        System.out.println("Output folder created: " + finalDir.getAbsolutePath());
        return finalDir;
    }
    
    /**
     * 获取当前输出目录
     * 如果目录尚未创建，则创建它
     * 
     * @return 输出目录
     */
    public static File getOutputDirectory() {
        if (outputDirectory == null || !outputDirectory.exists()) {
            // 如果没有指定基础目录，使用当前工作目录
            return createOutputDirectory(null);
        }
        return outputDirectory;
    }
    
    /**
     * 只登记输出目录的父目录，不创建任何目录。
     * 供"先确认再运行"的流程使用：点击确认开始运行后，再调用
     * createNewRunDirectory() 在该父目录下创建批次目录。
     *
     * @param baseDir 父目录（可为null，表示使用当前工作目录）
     */
    public static void setBaseDirectory(File baseDir) {
        baseDirectory = baseDir;
    }

    /**
     * 获取最近一次登记/创建输出目录所用的父目录
     */
    public static File getBaseDirectory() {
        return baseDirectory;
    }

    /**
     * 设置当前批次目录（FSTool-时间戳）。加载已有结果目录后，用它的父目录作为批次目录，
     * 以便按新 window/step 在该批次下创建新的 w&lt;W&gt;_s&lt;S&gt; 文件夹。
     */
    public static void setBatchDirectory(File dir) {
        batchDirectory = dir;
    }

    public static File getBatchDirectory() {
        return batchDirectory;
    }

    /**
     * 设置输出目录（用于测试或特殊场景）
     * 
     * @param dir 输出目录
     */
    public static void setOutputDirectory(File dir) {
        outputDirectory = dir;
    }
    
    /**
     * 重置输出目录（用于测试）
     */
    public static void reset() {
        outputDirectory = null;
        batchDirectory = null;
    }
}
