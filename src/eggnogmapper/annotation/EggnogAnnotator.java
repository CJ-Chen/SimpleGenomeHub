package eggnogmapper.annotation;

import eggnogmapper.annotation.db.EggnogSqliteRepository;
import eggnogmapper.annotation.orthology.CoOrthologInferer;
import eggnogmapper.annotation.tax.NcbiTaxonomyRepository;
import eggnogmapper.annotation.tax.TaxScopeData;
import eggnogmapper.annotation.tax.TaxScopeLoader;
import eggnogmapper.annotation.tax.TaxScopeSelector;
import eggnogmapper.search.Hit;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EggnogAnnotator {
    private static final Set<String> GO_EXPERIMENTAL = new HashSet<String>(Arrays.asList("EXP", "IDA", "IPI", "IMP", "IGI", "IEP"));
    private static final Set<String> GO_EXCLUDED_NON_ELECTRONIC = new HashSet<String>(Arrays.asList("ND", "IEA"));

    private final EggnogSqliteRepository db;
    private final NcbiTaxonomyRepository ncbi;
    private final TaxScopeData taxData;
    private final String taxScopeMode;
    private final Set<String> taxScopeIds;
    private final Set<String> taxScopeModeIds;
    private final String targetOrthologs;
    private final Set<Integer> targetTaxa;
    private final Set<Integer> excludedTaxa;
    private final String goEvidenceMode;
    private final Map<String, CachedSeedData> seedCache = new HashMap<String, CachedSeedData>();

    public EggnogAnnotator(EggnogSqliteRepository db,
                           NcbiTaxonomyRepository ncbi,
                           String taxScopeVarsFile,
                           String taxScopeMode,
                           String taxScope,
                           String targetOrthologs,
                           String targetTaxa,
                           String excludedTaxa,
                           String goEvidenceMode) throws IOException, SQLException {
        this.db = db;
        this.ncbi = ncbi;
        this.taxData = TaxScopeLoader.load(taxScopeVarsFile);
        File taxScopeDir = new File(taxScopeVarsFile).getParentFile();
        this.taxScopeMode = taxScopeMode == null ? TaxScopeSelector.INNER_NARROWEST : taxScopeMode;
        this.taxScopeIds = TaxScopeSelector.parseTaxScopeIds(taxScope, this.taxData, taxScopeDir);
        this.taxScopeModeIds = TaxScopeSelector.isNamedMode(this.taxScopeMode)
                ? null
                : TaxScopeSelector.parseTaxScopeIds(this.taxScopeMode, this.taxData, taxScopeDir);
        this.targetOrthologs = targetOrthologs == null ? "all" : targetOrthologs;
        this.targetTaxa = parseAndExpandTaxa(targetTaxa);
        this.excludedTaxa = parseAndExpandTaxa(excludedTaxa);
        this.goEvidenceMode = goEvidenceMode == null ? "non-electronic" : goEvidenceMode.toLowerCase();
    }

    public List<AnnotationRecord> annotate(List<Hit> hits, Double seedEvalue, Double seedScore) throws SQLException {
        List<AnnotationRecord> out = new ArrayList<AnnotationRecord>();
        CoOrthologInferer inferer = new CoOrthologInferer();
        for (Hit h : hits) {
            if (filterOut(h, seedEvalue, seedScore)) {
                continue;
            }

            CachedSeedData cachedSeed = seedCache.get(h.target);
            if (cachedSeed == null) {
                cachedSeed = buildSeedData(h.target, inferer);
                seedCache.put(h.target, cachedSeed);
            }
            if (!cachedSeed.valid) {
                continue;
            }

            AnnotationRecord r = new AnnotationRecord();
            r.query = h.query;
            r.seedOrtholog = h.target;
            r.evalue = h.evalue;
            r.score = h.score;
            applySeedData(cachedSeed, r);
            out.add(r);
        }
        return out;
    }

    private CachedSeedData buildSeedData(String seedOrtholog, CoOrthologInferer inferer) throws SQLException {
        String memberOgs = db.getMemberOgs(seedOrtholog);
        if (memberOgs == null || memberOgs.trim().isEmpty()) {
            return CachedSeedData.invalid();
        }

        List<String> matchNogs = new ArrayList<String>();
        for (String og : memberOgs.split(",")) {
            String trimmed = og.trim();
            if (!trimmed.isEmpty()) {
                matchNogs.add(trimmed);
            }
        }
        if (matchNogs.isEmpty()) {
            return CachedSeedData.invalid();
        }

        TaxScopeSelector.NogSelection sel = TaxScopeSelector.parseNogs(matchNogs, taxScopeMode, taxScopeIds, taxScopeModeIds, taxData);
        if (sel.bestOgs == null || sel.bestOgs.isEmpty()) {
            return CachedSeedData.invalid();
        }

        CachedSeedData cached = new CachedSeedData();
        cached.valid = true;
        cached.eggnogOgs = join(sel.matchNogNames.toArray(new String[sel.matchNogNames.size()]));

        CoOrthologInferer.OrthologyResult orthoRes = inferer.getMemberOrthologs(seedOrtholog, sel.bestOgs, sel.allNogs, db);
        cached.annotOrthologs = Collections.unmodifiableList(filterOrthologs(orthoRes.allOrthologs, targetOrthologs, targetTaxa, excludedTaxa));
        cached.allOrthologies = wrapOrthologies(orthoRes.allOrthologs);

        TaxScopeSelector.NogEntry bestForAnnot = orthoRes.bestOgOverride != null ? orthoRes.bestOgOverride : sel.bestOgs.get(0);
        cached.maxAnnotLvl = bestForAnnot.taxId;

        EggnogSqliteRepository.OgDescription desc = getOgsDescription(sel.allNogs);
        if (desc != null) {
            cached.cogCategory = safe(desc.cogCategory);
            cached.description = safe(desc.description);
        } else {
            cached.cogCategory = "-";
            cached.description = "-";
        }

        Map<String, Map<String, Integer>> cnt = initCounters();
        if (!cached.annotOrthologs.isEmpty()) {
            List<EggnogSqliteRepository.ProtAnnotations> rows = db.getAnnotationsForNames(cached.annotOrthologs);
            for (EggnogSqliteRepository.ProtAnnotations p : rows) {
                addCounter(cnt.get("Preferred_name"), safeName(p.preferredName));
                addGoCounter(cnt.get("GOs"), p.gos);
                addCsvCounter(cnt.get("EC"), p.ec);
                addCsvCounter(cnt.get("KEGG_ko"), p.keggKo);
                addCsvCounter(cnt.get("KEGG_Pathway"), p.keggPathway);
                addCsvCounter(cnt.get("KEGG_Module"), p.keggModule);
                addCsvCounter(cnt.get("KEGG_Reaction"), p.keggReaction);
                addCsvCounter(cnt.get("KEGG_rclass"), p.keggRclass);
                addCsvCounter(cnt.get("BRITE"), p.brite);
                addCsvCounter(cnt.get("KEGG_TC"), p.keggTc);
                addCsvCounter(cnt.get("CAZy"), p.cazy);
                addCsvCounter(cnt.get("BiGG_Reaction"), p.biggReaction);
                addCsvCounter(cnt.get("PFAMs"), p.pfams);
            }
        }

        cached.fields.put("Preferred_name", pickPreferredName(cnt.get("Preferred_name")));
        cached.fields.put("GOs", joinSorted(cnt.get("GOs")));
        cached.fields.put("EC", joinSorted(cnt.get("EC")));
        cached.fields.put("KEGG_ko", joinSorted(cnt.get("KEGG_ko")));
        cached.fields.put("KEGG_Pathway", joinSorted(cnt.get("KEGG_Pathway")));
        cached.fields.put("KEGG_Module", joinSorted(cnt.get("KEGG_Module")));
        cached.fields.put("KEGG_Reaction", joinSorted(cnt.get("KEGG_Reaction")));
        cached.fields.put("KEGG_rclass", joinSorted(cnt.get("KEGG_rclass")));
        cached.fields.put("BRITE", joinSorted(cnt.get("BRITE")));
        cached.fields.put("KEGG_TC", joinSorted(cnt.get("KEGG_TC")));
        cached.fields.put("CAZy", joinSorted(cnt.get("CAZy")));
        cached.fields.put("BiGG_Reaction", joinSorted(cnt.get("BiGG_Reaction")));
        cached.fields.put("PFAMs", joinPfams(cnt.get("PFAMs"), cached.annotOrthologs.size()));
        return cached;
    }

    private void applySeedData(CachedSeedData cachedSeed, AnnotationRecord target) {
        target.eggnogOgs = cachedSeed.eggnogOgs;
        target.maxAnnotLvl = cachedSeed.maxAnnotLvl;
        target.cogCategory = cachedSeed.cogCategory;
        target.description = cachedSeed.description;
        target.annotOrthologs = cachedSeed.annotOrthologs;
        target.allOrthologies.putAll(cachedSeed.allOrthologies);
        target.fields.putAll(cachedSeed.fields);
    }

    private static Map<String, Set<String>> wrapOrthologies(Map<String, Set<String>> source) {
        Map<String, Set<String>> copy = new LinkedHashMap<String, Set<String>>();
        for (Map.Entry<String, Set<String>> entry : source.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    private EggnogSqliteRepository.OgDescription getOgsDescription(List<TaxScopeSelector.NogEntry> nogs) throws SQLException {
        for (int i = nogs.size() - 1; i >= 0; i--) {
            TaxScopeSelector.NogEntry e = nogs.get(i);
            EggnogSqliteRepository.OgDescription desc = db.getOgDescription(e.ogId, e.taxId);
            if (desc != null && desc.description != null && !desc.description.trim().isEmpty() && !"N/A".equalsIgnoreCase(desc.description) && !"NA".equalsIgnoreCase(desc.description)) {
                return desc;
            }
        }
        return null;
    }

    private Set<Integer> parseAndExpandTaxa(String csv) throws SQLException {
        if (csv == null || csv.trim().isEmpty()) {
            return null;
        }
        Set<Integer> out = new HashSet<Integer>();
        for (String raw : csv.split(",")) {
            String token = raw.trim();
            if (token.isEmpty()) {
                continue;
            }
            int taxid = ncbi.resolveTaxonToken(token);
            out.addAll(ncbi.getDescendantTaxa(taxid, true));
        }
        return out;
    }

    private static boolean filterOut(Hit hit, Double thresholdEvalue, Double thresholdScore) {
        if (hit == null || hit.target == null || hit.target.trim().isEmpty() || "-".equals(hit.target) || "ERROR".equals(hit.target)) {
            return true;
        }
        if (thresholdEvalue != null && hit.evalue > thresholdEvalue.doubleValue()) {
            return true;
        }
        return thresholdScore != null && hit.score < thresholdScore.doubleValue();
    }

    private static List<String> filterOrthologs(Map<String, Set<String>> allOrthologs,
                                                String targetOrthologs,
                                                Set<Integer> targetTaxa,
                                                Set<Integer> excludedTaxa) {
        Set<String> selected = allOrthologs.get(targetOrthologs);
        if (selected == null) {
            selected = allOrthologs.get("all");
        }
        List<String> out = new ArrayList<String>();
        if (selected == null) {
            return out;
        }
        for (String orth : selected) {
            String[] p = orth.split("\\.", 2);
            if (p.length != 2) {
                continue;
            }
            int tax;
            try {
                tax = Integer.parseInt(p[0]);
            } catch (NumberFormatException ex) {
                continue;
            }
            if (excludedTaxa != null && excludedTaxa.contains(Integer.valueOf(tax))) {
                continue;
            }
            if (targetTaxa != null && !targetTaxa.contains(Integer.valueOf(tax))) {
                continue;
            }
            out.add(orth);
        }
        Collections.sort(out);
        return out;
    }

    private Map<String, Map<String, Integer>> initCounters() {
        Map<String, Map<String, Integer>> c = new LinkedHashMap<String, Map<String, Integer>>();
        String[] keys = {"Preferred_name", "GOs", "EC", "KEGG_ko", "KEGG_Pathway", "KEGG_Module",
                "KEGG_Reaction", "KEGG_rclass", "BRITE", "KEGG_TC", "CAZy", "BiGG_Reaction", "PFAMs"};
        for (String k : keys) {
            c.put(k, new HashMap<String, Integer>());
        }
        return c;
    }

    private void addGoCounter(Map<String, Integer> map, String gos) {
        if (gos == null || gos.trim().isEmpty()) {
            return;
        }
        for (String g : gos.split(",")) {
            String[] p = g.trim().split("\\|");
            if (p.length < 3) {
                continue;
            }
            String go = p[1].trim();
            String ev = p[2].trim();
            if (go.isEmpty()) {
                continue;
            }
            if ("experimental".equals(goEvidenceMode)) {
                if (!GO_EXPERIMENTAL.contains(ev) || GO_EXCLUDED_NON_ELECTRONIC.contains(ev)) {
                    continue;
                }
            } else if ("non-electronic".equals(goEvidenceMode)) {
                if (GO_EXCLUDED_NON_ELECTRONIC.contains(ev)) {
                    continue;
                }
            }
            addCounter(map, go);
        }
    }

    private static void addCsvCounter(Map<String, Integer> map, String csv) {
        if (csv == null || csv.trim().isEmpty()) {
            return;
        }
        for (String value : csv.split(",")) {
            addCounter(map, value.trim());
        }
    }

    private static void addCounter(Map<String, Integer> map, String value) {
        if (value == null || value.trim().isEmpty() || "-".equals(value)) {
            return;
        }
        Integer count = map.get(value);
        map.put(value, count == null ? Integer.valueOf(1) : Integer.valueOf(count.intValue() + 1));
    }

    private static String pickPreferredName(Map<String, Integer> map) {
        String best = "-";
        int max = 0;
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            if (e.getValue().intValue() > max) {
                max = e.getValue().intValue();
                best = e.getKey();
            }
        }
        return max >= 2 ? best : "-";
    }

    private static String joinSorted(Map<String, Integer> map) {
        if (map == null || map.isEmpty()) {
            return "-";
        }
        List<String> keys = new ArrayList<String>(map.keySet());
        Collections.sort(keys);
        return join(keys.toArray(new String[keys.size()]));
    }

    private static String joinPfams(Map<String, Integer> map, int totalOrthologs) {
        if (map == null || map.isEmpty() || totalOrthologs <= 0) {
            return "-";
        }
        List<String> keep = new ArrayList<String>();
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            int count = e.getValue().intValue();
            double rate = (double) count / (double) totalOrthologs;
            if (count > 1 && rate > 0.05d) {
                keep.add(e.getKey());
            }
        }
        if (keep.isEmpty()) {
            return "-";
        }
        Collections.sort(keep);
        return join(keep.toArray(new String[keep.size()]));
    }

    private static String safeName(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "-";
        }
        return value.trim();
    }

    private static String safe(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "-";
        }
        return value.trim();
    }

    private static String join(String[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    private static final class CachedSeedData {
        private boolean valid;
        private String eggnogOgs = "-";
        private String maxAnnotLvl = "-";
        private String cogCategory = "-";
        private String description = "-";
        private List<String> annotOrthologs = Collections.emptyList();
        private Map<String, String> fields = new LinkedHashMap<String, String>();
        private Map<String, Set<String>> allOrthologies = new LinkedHashMap<String, Set<String>>();

        private static CachedSeedData invalid() {
            CachedSeedData data = new CachedSeedData();
            data.valid = false;
            return data;
        }
    }
}
