package simplegenomehub.runtime;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicBoolean;

/** Exclusive session lease. Old published SGH versions do not recognize this advisory lock. */
public final class HubDataRootLock implements AutoCloseable {
    private final Path root;
    private final FileChannel channel;
    private final FileLock lock;
    private final AtomicBoolean closed = new AtomicBoolean();
    private HubDataRootLock(Path root, FileChannel channel, FileLock lock) {
        this.root = root; this.channel = channel; this.lock = lock;
    }
    public static HubDataRootLock acquire(File root) throws IOException { return acquire(root.toPath()); }
    public static HubDataRootLock acquire(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) throw new IOException("Data directory does not exist: " + directory);
        Path root = directory.toRealPath();
        FileChannel channel = FileChannel.open(root.resolve(".sgh.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock;
            try { lock = channel.tryLock(); } catch (OverlappingFileLockException e) { lock = null; }
            if (lock == null) throw new IOException("This data directory is already open in another Hub session: " + root);
            return new HubDataRootLock(root, channel, lock);
        } catch (IOException | RuntimeException failure) { channel.close(); throw failure; }
    }
    public Path getRoot() { return root; }
    public boolean isOpen() { return !closed.get(); }
    @Override public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            try { lock.release(); } finally { channel.close(); }
        }
    }
}
