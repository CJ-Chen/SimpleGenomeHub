package simplegenomehub.runtime;

/** Isolates the existing Java eggNOG pipeline so its native descendants can be cancelled safely. */
public final class HubEggnogMain {
    private HubEggnogMain() { }
    public static void main(String[] args) throws Exception {
        new eggnogmapper.emapper.EmapperPipeline(eggnogmapper.cli.CliParser.parse(args)).run();
    }
}
