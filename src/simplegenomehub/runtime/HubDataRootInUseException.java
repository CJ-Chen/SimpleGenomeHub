package simplegenomehub.runtime;

import java.io.IOException;
import java.nio.file.Path;

/** A live operating-system lock, rather than the mere presence of .sgh.lock. */
public final class HubDataRootInUseException extends IOException {
    private final Path root;

    public HubDataRootInUseException(Path root) {
        super("The data directory is already open in another Simple Genome Hub or TBtools window: "
            + root + ". Close that window, then try again.");
        this.root = root;
    }

    public Path getRoot() { return root; }
}
