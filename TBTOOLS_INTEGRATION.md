# TBtools embedded integration

This branch adapts publication baseline `cf8fd620a92e9902ff62608bd12d46c4b944e12f`
for the existing TBtools menu entry `simplegenomehub.gui.SimpleGenomeHubMainPanel`.
The no-argument constructor opens the embedded page. The application's existing
main method retains a standalone launch mode; independent distribution scripts
and installers are unchanged.

The parent TBtools Ant build compiles its own sources first, compiles this `src`
against those fresh host classes and the parent's resolved dependencies, and
merges the two outputs only while assembling the complete TBtools JAR. It does
not run this repository's default build and does not use `lib/TBtools_JRE1.6.jar`.

`HubSession` owns the data-root lease, running work, subprocesses and Hub windows.
Writers must finish before the lease is released. UI workers dispatch through
their explicit component/session owner. Model dialogs resolve their session from
the canonical species path; a stale or ambiguous owner is rejected.

Opening shared data binds existing paths without moving legacy analysis folders
or rewriting business files. New and legacy result locations are resolved by
their actual paths. Configuration/statistics updates retain unknown properties
and publish complete files. Old published applications do not honor the new
`.sgh.lock`; use them and the embedded Hub in turn.

The parent repository contains the isolated regression harness, small scientific
fixtures, the companion JJplot2 source patch and the final validation report.

BLAST databases are built as v4 without requiring indexed sequence IDs, preserving
long FASTA identifiers. Source SHA-256 binds ordinal mapping to the current data
root; legacy caches without this evidence may be rebuilt once. Copying a root
never falls back to a historical absolute source path. Tree building streams
FASTA from the actual searched database with `blastdbcmd -entry all`, supporting
unindexed, split-volume and older indexed databases. It retains selected hits in
request order and rejects missing or ambiguous identifiers. Streaming stdout is
separate from bounded stderr diagnostics; cancellation and parser errors wait
for the process and reader threads to exit. The parent native regression runs
the search, extraction, alignment, trimming and tree inference chain.
