package simplegenomehub.blast;

/** The identifier convention shared by BLAST result mapping and sequence extraction. */
public final class BlastSequenceIds {
    private BlastSequenceIds() { }

    public static String fromFastaHeader(String header) {
        if (header == null) return "";
        String value = header.trim();
        if (value.startsWith(">")) value = value.substring(1).trim();
        String primary = value.split("\\s+", 2)[0];
        if (primary.startsWith("gi|")) {
            String[] fields = primary.split("\\|");
            if (fields.length >= 4) return fields[3];
        }
        return primary;
    }
}
