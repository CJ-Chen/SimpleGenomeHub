package simplegenomehub.genomebrowser;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Random-access view over an imported FASTA plus its GFF3/GTF annotation.
 * The sequence is never loaded as a whole: bases are pulled through a small
 * per-chromosome window cache backed by the byte offsets of the FASTA index.
 */
public final class GenomeBrowserData implements Closeable {

    private static final int WINDOW_BASES = 1 << 20;

    public static final class Entry {
        public final String name;
        public final long length;
        public final long offset;
        public final int lineBases;
        public final int lineBytes;

        Entry(String name, long length, long offset, int lineBases, int lineBytes) {
            this.name = name;
            this.length = length;
            this.offset = offset;
            this.lineBases = Math.max(1, lineBases);
            this.lineBytes = Math.max(this.lineBases, lineBytes);
        }
    }

    private final File fastaFile;
    private final RandomAccessFile raf;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final List<String> chromosomes = new ArrayList<>();
    private final AnnotationIndex annotation;
    private final Map<String, Window> windows = new LinkedHashMap<>();

    public GenomeBrowserData(File fastaFile, File gffFile) throws IOException {
        this.fastaFile = fastaFile;
        this.raf = new RandomAccessFile(fastaFile, "r");
        try {
            loadIndex();
            Map<String, Integer> lengths = new LinkedHashMap<>();
            for (Entry entry : entries.values()) {
                lengths.put(entry.name, entry.length > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) entry.length);
            }
            this.annotation = AnnotationIndex.load(gffFile, lengths);
        } catch (IOException | RuntimeException | Error failure) {
            try { raf.close(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }

    public List<String> chromosomes() {
        return new ArrayList<>(chromosomes);
    }

    public AnnotationIndex annotation() {
        return annotation;
    }

    public int length(String chromosome) {
        Entry entry = entries.get(chromosome);
        return entry == null ? 0 : (int) Math.min(Integer.MAX_VALUE, entry.length);
    }

    public char baseAt(String chromosome, long position) {
        Entry entry = entries.get(chromosome);
        if (entry == null || position < 0 || position >= entry.length) return 'N';
        Window window = windows.computeIfAbsent(chromosome, Window::new);
        return window.baseAt(position);
    }

    public String read(String chromosome, long from, long to) {
        Entry entry = entries.get(chromosome);
        if (entry == null) return "";
        long start = Math.max(0, Math.min(from, to));
        long end = Math.min(entry.length, Math.max(from, to));
        StringBuilder builder = new StringBuilder((int) Math.min(Integer.MAX_VALUE, end - start));
        for (long p = start; p < end; p++) builder.append(baseAt(chromosome, p));
        return builder.toString();
    }

    @Override
    public void close() {
        try { raf.close(); } catch (IOException ignored) { }
    }

    private void loadIndex() throws IOException {
        File sidecar = new File(fastaFile.getAbsolutePath() + ".sghidx");
        if (sidecar.isFile() && sidecar.lastModified() >= fastaFile.lastModified() && readSidecar(sidecar)) {
            chromosomes.addAll(entries.keySet());
            return;
        }
        Map<String, Entry> existing = readSaneFai(new File(fastaFile.getAbsolutePath() + ".fai"));
        if (existing != null) {
            entries.putAll(existing);
        } else {
            scanFasta();
            writeSidecar(sidecar);
        }
        chromosomes.addAll(entries.keySet());
    }

    private boolean readSidecar(File sidecar) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(sidecar), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                String[] parts = line.split("\t");
                if (parts.length < 5) { entries.clear(); return false; }
                entries.put(parts[0], new Entry(parts[0], Long.parseLong(parts[1]), Long.parseLong(parts[2]),
                    Integer.parseInt(parts[3]), Integer.parseInt(parts[4])));
            }
            return !entries.isEmpty();
        } catch (IOException | NumberFormatException ex) {
            entries.clear();
            return false;
        }
    }

    private void writeSidecar(File sidecar) {
        try {
            simplegenomehub.util.fileio.AtomicFiles.write(sidecar.toPath(), output -> {
                BufferedWriter writer = new BufferedWriter(output);
                for (Entry entry : entries.values()) {
                    writer.write(entry.name + "\t" + entry.length + "\t" + entry.offset + "\t"
                        + entry.lineBases + "\t" + entry.lineBytes);
                    writer.newLine();
                }
                writer.flush();
            });
        } catch (IOException ignored) {
            // A read-only data directory still works, just without the optional cache.
        }
    }

    /** Reads the samtools-style .fai when its offsets are valid for the raw file. */
    private Map<String, Entry> readSaneFai(File fai) {
        if (fai == null || !fai.isFile()) return null;
        Map<String, Entry> parsed = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(fai), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split("\t");
                if (parts.length < 5) return null;
                parsed.put(parts[0], new Entry(parts[0], Long.parseLong(parts[1]), Long.parseLong(parts[2]),
                    Integer.parseInt(parts[3]), Integer.parseInt(parts[4])));
            }
        } catch (IOException | NumberFormatException ex) {
            return null;
        }
        if (parsed.isEmpty()) return null;
        for (Entry entry : parsed.values()) {
            int newline = detectNewline(entry);
            if (newline <= 0) return null;
            parsed.put(entry.name, new Entry(entry.name, entry.length, entry.offset, entry.lineBases,
                entry.lineBases + newline));
        }
        return parsed;
    }

    private int detectNewline(Entry entry) {
        try {
            if (entry.offset >= raf.length()) return 0;
            raf.seek(entry.offset);
            if (!isBase((char) raf.read())) return 0;
            long newlinePosition = entry.offset + entry.lineBases;
            if (newlinePosition >= raf.length()) return 1;
            raf.seek(newlinePosition);
            int first = raf.read();
            if (first == '\n') return 1;
            if (first == '\r') return 2;
            return 0;
        } catch (IOException ex) {
            return 0;
        }
    }

    private void scanFasta() throws IOException {
        Entry current = null;
        long sequenceLength = 0;
        long firstBaseOffset = 0;
        int lineBases = 0;
        int lineBytes = 0;
        boolean firstSequenceLine = false;
        StringBuilder name = new StringBuilder();

        try (InputStream in = new BufferedInputStream(new FileInputStream(fastaFile), 1 << 16)) {
            StringBuilder text = new StringBuilder();
            long position = 0;
            long lineStart = 0;
            int value;
            while ((value = in.read()) != -1) {
                if (value == '\n') {
                    int consumed = (int) (position + 1 - lineStart);
                    String content = text.toString();
                    if (content.startsWith(">")) {
                        if (current != null) {
                            entries.put(current.name, new Entry(current.name, sequenceLength, firstBaseOffset,
                                lineBases, lineBytes));
                        }
                        name.setLength(0);
                        for (int i = 1; i < content.length(); i++) {
                            char c = content.charAt(i);
                            if (Character.isWhitespace(c)) break;
                            name.append(c);
                        }
                        current = new Entry(name.length() == 0 ? "sequence" : name.toString(), 0, 0, 1, 1);
                        sequenceLength = 0;
                        firstBaseOffset = position + 1;
                        firstSequenceLine = true;
                    } else if (current != null && !content.isEmpty()) {
                        String bases = content.replace("\r", "");
                        if (firstSequenceLine) {
                            lineBases = Math.max(1, bases.length());
                            lineBytes = consumed;
                            firstBaseOffset = lineStart;
                            firstSequenceLine = false;
                        }
                        sequenceLength += bases.length();
                    }
                    text.setLength(0);
                    lineStart = position + 1;
                } else if (value != '\r') {
                    text.append((char) value);
                }
                position++;
            }
            if (text.length() > 0 && current != null && !text.toString().startsWith(">")) {
                sequenceLength += text.length();
            }
            if (current != null) {
                entries.put(current.name, new Entry(current.name, sequenceLength,
                    firstSequenceLine ? current.offset : firstBaseOffset, Math.max(1, lineBases), Math.max(1, lineBytes)));
            }
        }
    }

    private static boolean isBase(char c) {
        char upper = Character.toUpperCase(c);
        return upper >= 'A' && upper <= 'Z';
    }

    private final class Window {
        private final String chromosome;
        private long start;
        private char[] bases = new char[0];

        Window(String chromosome) {
            this.chromosome = chromosome;
        }

        char baseAt(long position) {
            if (position < start || position >= start + bases.length) {
                load(position);
            }
            if (position < start || position >= start + bases.length) return 'N';
            return bases[(int) (position - start)];
        }

        private void load(long position) {
            Entry entry = entries.get(chromosome);
            if (entry == null) return;
            long from = Math.max(0, position - WINDOW_BASES / 2);
            long to = Math.min(entry.length, from + WINDOW_BASES);
            from = Math.max(0, Math.min(from, to - WINDOW_BASES));
            int wanted = (int) Math.max(0, to - from);
            if (wanted <= 0) {
                start = 0;
                bases = new char[0];
                return;
            }
            char[] loaded = new char[wanted];
            int count = 0;
            try {
                raf.seek(fileOffset(entry, from));
                long lines = (long) Math.ceil(wanted / (double) entry.lineBases) + 2;
                int bytesToRead = (int) Math.min(Integer.MAX_VALUE - 8,
                    wanted + lines * Math.max(0, entry.lineBytes - entry.lineBases) + 64);
                byte[] buffer = new byte[bytesToRead];
                int read = raf.read(buffer, 0, bytesToRead);
                for (int i = 0; i < read && count < wanted; i++) {
                    char c = (char) (buffer[i] & 0xFF);
                    if (c == '\n' || c == '\r' || c == ' ' || c == '\t') continue;
                    loaded[count++] = Character.toUpperCase(c);
                }
            } catch (IOException ex) {
                count = 0;
            }
            start = from;
            bases = new char[count];
            System.arraycopy(loaded, 0, bases, 0, count);
        }

        private long fileOffset(Entry entry, long position) {
            long line = position / entry.lineBases;
            int column = (int) (position % entry.lineBases);
            return entry.offset + line * entry.lineBytes + column;
        }
    }
}
