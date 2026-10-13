package simplegenomehub.runtime;

import biocjava.bioDoer.BLAST.ToolProcessRunner;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Registers every Hub-owned process with the session before waiting for completion. */
public final class HubProcessRunner {
    private HubProcessRunner() { }

    public static ToolProcessRunner.Result run(ProcessBuilder builder, long timeout, TimeUnit unit)
            throws IOException, InterruptedException {
        return run(HubSession.current(), builder, timeout, unit);
    }

    public static ToolProcessRunner.Result run(HubSession session, ProcessBuilder builder,
                                              long timeout, TimeUnit unit)
            throws IOException, InterruptedException {
        ToolProcessRunner.Result result = ToolProcessRunner.run(builder, timeout, unit,
            session == null ? null : process -> session.registerProcess(process));
        if (session != null && !session.isActive()) throw new InterruptedException("Hub tool execution cancelled");
        return result;
    }
}
