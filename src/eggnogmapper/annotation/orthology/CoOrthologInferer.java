package eggnogmapper.annotation.orthology;

import eggnogmapper.annotation.db.EggnogSqliteRepository;
import eggnogmapper.annotation.tax.TaxScopeSelector;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CoOrthologInferer {
    public static class OrthologyResult {
        public final Map<String, Set<String>> allOrthologs;
        public final TaxScopeSelector.NogEntry bestOgOverride;

        public OrthologyResult(Map<String, Set<String>> allOrthologs, TaxScopeSelector.NogEntry bestOgOverride) {
            this.allOrthologs = allOrthologs;
            this.bestOgOverride = bestOgOverride;
        }
    }

    public OrthologyResult getMemberOrthologs(String member, List<TaxScopeSelector.NogEntry> bestOgs,
                                              List<TaxScopeSelector.NogEntry> allNogs,
                                              EggnogSqliteRepository db) throws SQLException {
        int bestPos = Integer.MAX_VALUE;
        for (TaxScopeSelector.NogEntry b : bestOgs) {
            int p = indexOf(allNogs, b);
            if (p >= 0 && p < bestPos) bestPos = p;
        }
        if (bestPos == Integer.MAX_VALUE) {
            bestPos = 0;
        }
        List<TaxScopeSelector.NogEntry> annotOgs = allNogs.subList(bestPos, allNogs.size());
        Map<SpeciesGroup, Set<SpeciesGroup>> orthology = setupOrthology(member, annotOgs, db);
        if (orthology != null && !orthology.isEmpty()) {
            return new OrthologyResult(loadOrthology(orthology), null);
        }

        TaxScopeSelector.NogEntry bestOg = null;
        if (bestPos > 0) {
            List<TaxScopeSelector.NogEntry> bt = new ArrayList<TaxScopeSelector.NogEntry>(allNogs.subList(0, bestPos));
            Collections.reverse(bt);
            for (TaxScopeSelector.NogEntry nog : bt) {
                orthology = setupOrthology(member, Collections.singletonList(nog), db);
                if (orthology != null && !orthology.isEmpty()) {
                    bestOg = nog;
                    return new OrthologyResult(loadOrthology(orthology), bestOg);
                }
            }
        }

        Map<String, Set<String>> fallback = initOrthologMap();
        fallback.get("one2one").add(member);
        fallback.get("all").add(member);
        TaxScopeSelector.NogEntry seedFallback = new TaxScopeSelector.NogEntry("seed_ortholog", "-", "seed_ortholog@" + member + "|-", -1);
        return new OrthologyResult(fallback, seedFallback);
    }

    private Map<SpeciesGroup, Set<SpeciesGroup>> setupOrthology(String member, List<TaxScopeSelector.NogEntry> ogs,
                                                                EggnogSqliteRepository db) throws SQLException {
        Map<SpeciesGroup, Set<SpeciesGroup>> orthology = new LinkedHashMap<SpeciesGroup, Set<SpeciesGroup>>();
        Set<String> memberSet = new HashSet<String>();
        memberSet.add(member);
        Set<String> levels = new HashSet<String>();
        for (TaxScopeSelector.NogEntry e : ogs) {
            levels.add(e.taxId);
        }
        List<EggnogSqliteRepository.MemberEvent> events = db.getMemberEvents(member, levels);
        for (EggnogSqliteRepository.MemberEvent ev : events) {
            Map<String, Set<String>> bySp1 = bySpecies(ev.side1);
            Map<String, Set<String>> bySp2 = bySpecies(ev.side2);
            setCoorthologs(bySp1, bySp2, memberSet, orthology);
            setCoorthologs(bySp2, bySp1, memberSet, orthology);
        }
        return orthology;
    }

    private Map<String, Set<String>> bySpecies(String side) {
        Map<String, Set<String>> out = new LinkedHashMap<String, Set<String>>();
        if (side == null || side.trim().isEmpty()) return out;
        String[] members = side.split(",");
        for (String m : members) {
            String[] p = m.trim().split("\\.", 2);
            if (p.length != 2) continue;
            String tax = p[0];
            String mid = p[0] + "." + p[1];
            if (!out.containsKey(tax)) out.put(tax, new HashSet<String>());
            out.get(tax).add(mid);
        }
        return out;
    }

    private void setCoorthologs(Map<String, Set<String>> bySp1, Map<String, Set<String>> bySp2,
                                Set<String> targetMembers, Map<SpeciesGroup, Set<SpeciesGroup>> orthology) {
        for (Map.Entry<String, Set<String>> e1 : bySp1.entrySet()) {
            Set<String> co1 = e1.getValue();
            if (!intersects(co1, targetMembers)) continue;
            SpeciesGroup key1 = new SpeciesGroup(e1.getKey(), co1);
            if (!orthology.containsKey(key1)) orthology.put(key1, new HashSet<SpeciesGroup>());
            for (Map.Entry<String, Set<String>> e2 : bySp2.entrySet()) {
                SpeciesGroup key2 = new SpeciesGroup(e2.getKey(), e2.getValue());
                orthology.get(key1).add(key2);
            }
        }
    }

    private boolean intersects(Set<String> a, Set<String> b) {
        for (String x : a) if (b.contains(x)) return true;
        return false;
    }

    private Map<String, Set<String>> loadOrthology(Map<SpeciesGroup, Set<SpeciesGroup>> orthology) {
        Map<String, Set<String>> all = initOrthologMap();
        for (Map.Entry<SpeciesGroup, Set<SpeciesGroup>> kv : orthology.entrySet()) {
            SpeciesGroup k = kv.getKey();
            all.get("all").addAll(k.members);
            String prefix = k.members.size() == 1 ? "one2" : "many2";
            for (SpeciesGroup v : kv.getValue()) {
                all.get("all").addAll(v.members);
                String otype = prefix + (v.members.size() == 1 ? "one" : "many");
                all.get(otype).addAll(k.members);
                all.get(otype).addAll(v.members);
            }
        }
        return all;
    }

    private Map<String, Set<String>> initOrthologMap() {
        Map<String, Set<String>> m = new LinkedHashMap<String, Set<String>>();
        m.put("one2one", new HashSet<String>());
        m.put("one2many", new HashSet<String>());
        m.put("many2many", new HashSet<String>());
        m.put("many2one", new HashSet<String>());
        m.put("all", new HashSet<String>());
        return m;
    }

    private int indexOf(List<TaxScopeSelector.NogEntry> all, TaxScopeSelector.NogEntry e) {
        for (int i = 0; i < all.size(); i++) {
            TaxScopeSelector.NogEntry x = all.get(i);
            if (x.ogId.equals(e.ogId) && x.taxId.equals(e.taxId)) return i;
        }
        return -1;
    }

    private static class SpeciesGroup {
        public final String species;
        public final Set<String> members;
        private final String signature;

        SpeciesGroup(String species, Set<String> members) {
            this.species = species;
            this.members = new HashSet<String>(members);
            List<String> sorted = new ArrayList<String>(this.members);
            Collections.sort(sorted);
            this.signature = species + "|" + String.join(",", sorted);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SpeciesGroup)) return false;
            SpeciesGroup that = (SpeciesGroup) o;
            return signature.equals(that.signature);
        }

        @Override
        public int hashCode() {
            return signature.hashCode();
        }
    }
}
