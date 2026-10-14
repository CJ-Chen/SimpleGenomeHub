package simplegenomehub.population.fst;

import java.io.File;

/**
 * Scratch files shared between the FST calculation, the strategy window and the chart viewer.
 * <p>
 * FSTdraw wrote its chromosome-length scratch file into the process working directory. Inside
 * SimpleGenomeHub that directory belongs to the application, so the file is kept in the system
 * temporary directory instead.
 */
public final class FstWorkFiles {

    private static final String CHROMOSOME_LENGTH_FILE = "SimpleGenomeHub.Chr.Length.temp.txt";

    private FstWorkFiles() {
    }

    /** Scratch file holding {@code chromosome<TAB>length} lines for the genome browser. */
    public static File chromosomeLengthFile() {
        String tempDirectory = System.getProperty("java.io.tmpdir");
        File directory = (tempDirectory == null || tempDirectory.trim().isEmpty())
            ? new File(".") : new File(tempDirectory);
        return new File(directory, CHROMOSOME_LENGTH_FILE);
    }
}
