# Correctness regressions

Run `ant test-regression` with Java 11. These two plain Java `main` programs do not require JUnit, external sequence tools, a graphical desktop, or existing user data. The Ant target places all generated files, `user.home`, and temporary data beneath `build/regression/`. It does not read or write a configured SGH data root.

- `simplegenomehub.regression.SequenceExtractionRegression` verifies exact expected plus/minus CDS and protein residues, representative transcript selection, inclusive coordinates, and promoter clipping on a nonuniform chromosome. The `minusNested` promoter case uses distinct transcript parents sharing one gene ID; different isoforms may have nested genomic CDS intervals. Its promoter anchor remains the gene group's maximum CDS end.
- `simplegenomehub.regression.AnnotationPersistenceRegression` verifies strict header recognition, a first `geneA` data row, ordinary comments, repeated/conflicting and late headers, unknown and empty columns, PFAM/InterPro/CUSTOM model edits and reopen, transcript mapping, transactional import/publication failures, mixed-type save failure reporting, clear-and-reopen persistence, lazy-data preservation, and metadata/table full, filtered, and paged views.

Each program accepts an optional output directory. The sequence test reads `test/fixtures/science` by default; override it with `-Dsgh.fixtures=<fixture-directory>`. Annotation fixtures are generated inside the selected isolated output directory.

## Reproduce the published baseline defects

The optional `--baseline` mode is a diagnostic that **expects old defects**. It is not the acceptance test for current code. Compile the tests against the patched sources first, then place the separately preserved classes compiled from `cf8fd620` first on the runtime classpath:

```powershell
java '-Djava.awt.headless=true' -cp 'build/baseline-classes;build/test/classes;lib/TBtools_JRE1.6.jar' simplegenomehub.regression.SequenceExtractionRegression '<isolated-baseline-science-output>' --baseline
java '-Djava.awt.headless=true' -cp 'build/baseline-classes;build/test/classes;lib/TBtools_JRE1.6.jar' simplegenomehub.regression.AnnotationPersistenceRegression '<isolated-baseline-annotation-output>' --baseline
```

These branches call only APIs present in the published version. They fail if a stated defect is not reproduced and print `REPRODUCED baseline` only after observing incorrect sequence residues/coordinates or real annotation data loss/failure reporting. They do not treat a missing newer API as evidence. Preserve baseline classes and output outside `build/` before running `ant clean`, which removes that directory.
