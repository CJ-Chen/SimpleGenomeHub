package simplegenomehub.blast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import simplegenomehub.blast.model.BlastDatabase;
import simplegenomehub.runtime.HubProcessRunner;
import simplegenomehub.runtime.HubSession;
import simplegenomehub.runtime.HubToolResolver;

/** Reads the database actually searched, without relying on mutable source FASTA files or indexed sequence IDs. */
public final class BlastDatabaseSequenceExtractor {
    private static final Logger LOGGER = Logger.getLogger(BlastDatabaseSequenceExtractor.class.getName());
    private BlastDatabaseSequenceExtractor() { }

    public static final class SequenceRecord {
        private final String id, header, sequence;
        private SequenceRecord(String id, String header, String sequence) {
            this.id = id; this.header = header; this.sequence = sequence;
        }
        /** The requested hit identifier, retained for correspondence with the BLAST result. */
        public String getId() { return id; }
        /** The original FASTA header returned by the database, without its leading greater-than sign. */
        public String getHeader() { return header; }
        public String getSequence() { return sequence; }
    }

    /** Extract unique hit IDs in first-request order, keeping only selected sequences in memory. */
    public static List<SequenceRecord> extract(File databasePrefix, BlastConfig.SequenceType type,
            List<String> orderedHitIds, HubSession session) throws IOException, InterruptedException {
        java.util.Objects.requireNonNull(type, "sequence type");
        java.util.Objects.requireNonNull(orderedHitIds, "hit identifiers");
        LinkedHashSet<String> requested = new LinkedHashSet<>();
        for (String id : orderedHitIds) {
            if (id == null || id.trim().isEmpty()) throw new IOException("A BLAST hit has no sequence identifier");
            requested.add(id.trim());
        }
        checkCancelled(session);
        if (requested.isEmpty()) return new ArrayList<>();
        if (databasePrefix == null || !BlastDatabase.hasCompleteIndex(databasePrefix, type.getMolecularType()))
            throw new IOException("Target BLAST database is missing or incomplete: " + databasePrefix);

        File database = databasePrefix.getAbsoluteFile();
        final String executable;
        try { executable = HubToolResolver.resolve("blastdbcmd", null); }
        catch (IllegalStateException missingTool) { throw new IOException(missingTool.getMessage(), missingTool); }
        LOGGER.info("Extracting BLAST hit sequences with " + executable + " from " + database);
        SelectedFasta parser = new SelectedFasta(requested);
        HubProcessRunner.StreamingResult result = HubProcessRunner.runStreaming(session,
            new ProcessBuilder(executable, "-db", database.getName(), "-dbtype", type.getMolecularType().getBlastDbType(),
                "-entry", "all", "-outfmt", "%f").directory(database.getParentFile()),
            parser::accept, 30, TimeUnit.MINUTES);
        checkCancelled(session);
        if (result.exitCode != 0) throw new IOException("BLAST sequence extraction failed (exit " + result.exitCode + "): " + result.stderr);
        parser.finishRecord();
        List<SequenceRecord> extracted = new ArrayList<>();
        for (String id : requested) {
            if (parser.ambiguous.contains(id)) throw new IOException("Ambiguous sequence identifier in BLAST database: " + id);
            SequenceRecord record = parser.found.get(id);
            if (record == null) throw new IOException("Sequence not found in the searched BLAST database: " + id);
            extracted.add(record);
        }
        return extracted;
    }

    private static void checkCancelled(HubSession session) throws InterruptedException {
        if (Thread.currentThread().isInterrupted() || (session != null && !session.isActive()))
            throw new InterruptedException("BLAST sequence extraction cancelled");
    }

    private static Set<String> aliases(String header) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        String primary = header.trim().split("\\s+", 2)[0];
        values.add(primary);
        values.add(BlastSequenceIds.fromFastaHeader(header));
        if (primary.startsWith("lcl|")) values.add(primary.substring(4));
        return values;
    }

    private static final class SelectedFasta {
        final Map<String, SequenceRecord> found = new LinkedHashMap<>();
        final Map<String, Integer> matchRanks = new LinkedHashMap<>();
        final Set<String> ambiguous = new LinkedHashSet<>();
        final Map<String, Set<String>> wantedByAlias = new LinkedHashMap<>();
        Set<String> currentIds = new LinkedHashSet<>();
        String header;
        StringBuilder sequence;

        SelectedFasta(Set<String> requested) {
            for (String id : requested) for (String alias : aliases(id))
                wantedByAlias.computeIfAbsent(alias, ignored -> new LinkedHashSet<>()).add(id);
        }

        void accept(String line) throws IOException {
            if (line.startsWith(">")) {
                finishRecord();
                header = line.substring(1).trim();
                currentIds = new LinkedHashSet<>();
                for (String alias : aliases(header)) {
                    Set<String> matching = wantedByAlias.get(alias);
                    if (matching != null) currentIds.addAll(matching);
                }
                sequence = currentIds.isEmpty() ? null : new StringBuilder();
            } else if (sequence != null) {
                for (int index = 0; index < line.length(); index++)
                    if (!Character.isWhitespace(line.charAt(index))) sequence.append(line.charAt(index));
            } else if (header == null && !line.trim().isEmpty()) {
                throw new IOException("blastdbcmd returned non-FASTA sequence output");
            }
        }

        void finishRecord() throws IOException {
            if (sequence == null) return;
            if (sequence.length() == 0) throw new IOException("Empty sequence in BLAST database: " + header);
            String residues = sequence.toString();
            String primary = header.split("\\s+", 2)[0];
            String canonical = BlastSequenceIds.fromFastaHeader(header);
            for (String id : currentIds) {
                // A literal A and a literal lcl|A may both exist in an unparsed
                // database. Compatibility aliases must never override an exact ID.
                int rank = id.equals(primary) || id.equals(canonical) ? 2 : 1;
                Integer previous = matchRanks.get(id);
                if (previous == null || rank > previous) {
                    found.put(id, new SequenceRecord(id, header, residues));
                    matchRanks.put(id, rank);
                    ambiguous.remove(id);
                } else if (rank == previous) {
                    // Delay the error until the scan ends: a later exact match
                    // can still resolve otherwise ambiguous fallback aliases.
                    ambiguous.add(id);
                }
            }
            sequence = null;
        }
    }
}
