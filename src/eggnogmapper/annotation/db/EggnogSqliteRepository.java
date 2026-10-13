package eggnogmapper.annotation.db;

import eggnogmapper.common.SqliteDriverSupport;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class EggnogSqliteRepository implements AutoCloseable {
    private static final int IN_CLAUSE_BATCH_SIZE = 256;

    private final Connection conn;
    private final PreparedStatement memberOgsStatement;
    private final PreparedStatement ogDescriptionStatement;
    private final PreparedStatement protAnnotationsStatement;
    private final PreparedStatement memberOrthoindexStatement;

    private final Map<String, String> memberOgsCache = new HashMap<String, String>();
    private final Map<String, OgDescription> ogDescriptionCache = new HashMap<String, OgDescription>();
    private final Map<String, ProtAnnotations> protAnnotationsCache = new HashMap<String, ProtAnnotations>();
    private final Map<String, String> memberOrthoindexCache = new HashMap<String, String>();
    private final Map<String, List<MemberEvent>> memberEventsCache = new HashMap<String, List<MemberEvent>>();

    public EggnogSqliteRepository(String sqlitePath) throws SQLException {
        ensureSqliteDriver();
        this.conn = SqliteDriverSupport.open(sqlitePath);
        applyPragma("PRAGMA synchronous=OFF;");
        applyPragma("PRAGMA journal_mode=OFF;");
        applyPragma("PRAGMA cache_size=2000;");
        this.memberOgsStatement = conn.prepareStatement("SELECT ogs FROM prots WHERE name == ?;");
        this.ogDescriptionStatement = conn.prepareStatement(
                "SELECT nm, description, COG_categories FROM og WHERE og.og == ? AND og.level == ?;"
        );
        this.protAnnotationsStatement = conn.prepareStatement(
                "SELECT pname, gos, kegg_ec, kegg_ko, kegg_pathway, kegg_module, kegg_reaction, " +
                        "kegg_rclass, kegg_brite, kegg_tc, kegg_cazy, bigg_reaction, pfam " +
                        "FROM prots WHERE name == ?;"
        );
        this.memberOrthoindexStatement = conn.prepareStatement("SELECT orthoindex FROM prots WHERE name == ?;");
    }

    private void applyPragma(String sql) throws SQLException {
        Statement st = conn.createStatement();
        try {
            st.execute(sql);
        } finally {
            st.close();
        }
    }

    private static void ensureSqliteDriver() throws SQLException {
        SqliteDriverSupport.ensureLoaded();
    }

    public String getMemberOgs(String name) throws SQLException {
        if (memberOgsCache.containsKey(name)) {
            return memberOgsCache.get(name);
        }
        String out = null;
        memberOgsStatement.setString(1, name);
        ResultSet rs = memberOgsStatement.executeQuery();
        try {
            if (rs.next()) {
                out = rs.getString(1);
            }
        } finally {
            rs.close();
        }
        memberOgsCache.put(name, out);
        return out;
    }

    public OgDescription getOgDescription(String og, String level) throws SQLException {
        String cacheKey = og + "\t" + level;
        if (ogDescriptionCache.containsKey(cacheKey)) {
            return ogDescriptionCache.get(cacheKey);
        }
        OgDescription best = null;
        ogDescriptionStatement.setString(1, og);
        ogDescriptionStatement.setString(2, level);
        ResultSet rs = ogDescriptionStatement.executeQuery();
        try {
            while (rs.next()) {
                String nm = rs.getString(1);
                String desc = rs.getString(2);
                String cat = rs.getString(3);
                if (desc != null) {
                    String d = desc.trim();
                    if (!d.isEmpty() && !"N/A".equalsIgnoreCase(d) && !"NA".equalsIgnoreCase(d)) {
                        best = new OgDescription(nm, d, cat == null ? "-" : cat);
                        break;
                    }
                }
            }
        } finally {
            rs.close();
        }
        ogDescriptionCache.put(cacheKey, best);
        return best;
    }

    public ProtAnnotations getProtAnnotations(String name) throws SQLException {
        if (protAnnotationsCache.containsKey(name)) {
            return protAnnotationsCache.get(name);
        }
        ProtAnnotations out = null;
        protAnnotationsStatement.setString(1, name);
        ResultSet rs = protAnnotationsStatement.executeQuery();
        try {
            if (rs.next()) {
                out = new ProtAnnotations(
                        rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                        rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12),
                        rs.getString(13)
                );
            }
        } finally {
            rs.close();
        }
        protAnnotationsCache.put(name, out);
        return out;
    }

    public List<ProtAnnotations> getAnnotationsForNames(List<String> names) throws SQLException {
        if (names == null || names.isEmpty()) {
            return Collections.emptyList();
        }

        List<ProtAnnotations> out = new ArrayList<ProtAnnotations>(names.size());
        List<String> misses = new ArrayList<String>();
        Set<String> missSet = new TreeSet<String>();

        for (String name : names) {
            if (protAnnotationsCache.containsKey(name)) {
                ProtAnnotations cached = protAnnotationsCache.get(name);
                if (cached != null) {
                    out.add(cached);
                }
            } else if (missSet.add(name)) {
                misses.add(name);
            }
        }

        if (!misses.isEmpty()) {
            loadProtAnnotationsBatch(misses);
            for (String name : names) {
                ProtAnnotations cached = protAnnotationsCache.get(name);
                if (cached != null) {
                    out.add(cached);
                }
            }
        }

        return out;
    }

    public List<MemberEvent> getMemberEvents(String member, Set<String> targetLevels) throws SQLException {
        if (member == null || targetLevels == null || targetLevels.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> sortedLevels = new ArrayList<String>(new TreeSet<String>(targetLevels));
        String cacheKey = member.trim() + "\t" + String.join(",", sortedLevels);
        if (memberEventsCache.containsKey(cacheKey)) {
            return new ArrayList<MemberEvent>(memberEventsCache.get(cacheKey));
        }

        List<MemberEvent> out = new ArrayList<MemberEvent>();
        String idxs = getMemberOrthoindex(member.trim());
        if (idxs == null || idxs.trim().isEmpty()) {
            memberEventsCache.put(cacheKey, out);
            return out;
        }

        List<Integer> eventIds = new ArrayList<Integer>();
        for (String raw : idxs.split(",")) {
            String value = raw.trim();
            if (value.isEmpty()) {
                continue;
            }
            try {
                eventIds.add(Integer.valueOf(Integer.parseInt(value)));
            } catch (NumberFormatException ex) {
                // Ignore malformed event ids.
            }
        }
        if (eventIds.isEmpty()) {
            memberEventsCache.put(cacheKey, out);
            return out;
        }

        for (int idOffset = 0; idOffset < eventIds.size(); idOffset += IN_CLAUSE_BATCH_SIZE) {
            List<Integer> idBatch = eventIds.subList(idOffset, Math.min(eventIds.size(), idOffset + IN_CLAUSE_BATCH_SIZE));
            for (int levelOffset = 0; levelOffset < sortedLevels.size(); levelOffset += IN_CLAUSE_BATCH_SIZE) {
                List<String> levelBatch = sortedLevels.subList(levelOffset, Math.min(sortedLevels.size(), levelOffset + IN_CLAUSE_BATCH_SIZE));
                String sql = "SELECT level, side1, side2 FROM event WHERE i IN (" + placeholders(idBatch.size())
                        + ") AND level IN (" + placeholders(levelBatch.size()) + ")";
                PreparedStatement ps = conn.prepareStatement(sql);
                try {
                    int bindIndex = 1;
                    bindInts(ps, idBatch, bindIndex);
                    bindIndex += idBatch.size();
                    bindStrings(ps, levelBatch, bindIndex);
                    ResultSet rs = ps.executeQuery();
                    try {
                        while (rs.next()) {
                            out.add(new MemberEvent(rs.getString(1), rs.getString(2), rs.getString(3)));
                        }
                    } finally {
                        rs.close();
                    }
                } finally {
                    ps.close();
                }
            }
        }

        memberEventsCache.put(cacheKey, new ArrayList<MemberEvent>(out));
        return out;
    }

    private void loadProtAnnotationsBatch(List<String> names) throws SQLException {
        for (int offset = 0; offset < names.size(); offset += IN_CLAUSE_BATCH_SIZE) {
            List<String> batch = names.subList(offset, Math.min(names.size(), offset + IN_CLAUSE_BATCH_SIZE));
            String sql =
                    "SELECT name, pname, gos, kegg_ec, kegg_ko, kegg_pathway, kegg_module, kegg_reaction, " +
                            "kegg_rclass, kegg_brite, kegg_tc, kegg_cazy, bigg_reaction, pfam " +
                            "FROM prots WHERE name IN (" + placeholders(batch.size()) + ")";
            PreparedStatement ps = conn.prepareStatement(sql);
            try {
                bindStrings(ps, batch, 1);
                ResultSet rs = ps.executeQuery();
                try {
                    while (rs.next()) {
                        protAnnotationsCache.put(
                                rs.getString(1),
                                new ProtAnnotations(
                                        rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                                        rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9),
                                        rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13),
                                        rs.getString(14)
                                )
                        );
                    }
                } finally {
                    rs.close();
                }
            } finally {
                ps.close();
            }
            for (String name : batch) {
                if (!protAnnotationsCache.containsKey(name)) {
                    protAnnotationsCache.put(name, null);
                }
            }
        }
    }

    private String getMemberOrthoindex(String member) throws SQLException {
        if (memberOrthoindexCache.containsKey(member)) {
            return memberOrthoindexCache.get(member);
        }
        String out = null;
        memberOrthoindexStatement.setString(1, member);
        ResultSet rs = memberOrthoindexStatement.executeQuery();
        try {
            if (rs.next()) {
                out = rs.getString(1);
            }
        } finally {
            rs.close();
        }
        memberOrthoindexCache.put(member, out);
        return out;
    }

    @Override
    public void close() throws SQLException {
        closeQuietly(memberOgsStatement);
        closeQuietly(ogDescriptionStatement);
        closeQuietly(protAnnotationsStatement);
        closeQuietly(memberOrthoindexStatement);
        if (conn != null) {
            conn.close();
        }
    }

    private static void closeQuietly(PreparedStatement statement) throws SQLException {
        if (statement != null) {
            statement.close();
        }
    }

    private static void bindInts(PreparedStatement ps, List<Integer> values, int startIndex) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            ps.setInt(startIndex + i, values.get(i).intValue());
        }
    }

    private static void bindStrings(PreparedStatement ps, List<String> values, int startIndex) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            ps.setString(startIndex + i, values.get(i));
        }
    }

    private static String placeholders(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("?");
        }
        return sb.toString();
    }

    public static class OgDescription {
        public final String name;
        public final String description;
        public final String cogCategory;

        public OgDescription(String name, String description, String cogCategory) {
            this.name = name;
            this.description = description;
            this.cogCategory = cogCategory;
        }
    }

    public static class ProtAnnotations {
        public final String preferredName;
        public final String gos;
        public final String ec;
        public final String keggKo;
        public final String keggPathway;
        public final String keggModule;
        public final String keggReaction;
        public final String keggRclass;
        public final String brite;
        public final String keggTc;
        public final String cazy;
        public final String biggReaction;
        public final String pfams;

        public ProtAnnotations(String preferredName, String gos, String ec, String keggKo,
                               String keggPathway, String keggModule, String keggReaction,
                               String keggRclass, String brite, String keggTc, String cazy,
                               String biggReaction, String pfams) {
            this.preferredName = preferredName;
            this.gos = gos;
            this.ec = ec;
            this.keggKo = keggKo;
            this.keggPathway = keggPathway;
            this.keggModule = keggModule;
            this.keggReaction = keggReaction;
            this.keggRclass = keggRclass;
            this.brite = brite;
            this.keggTc = keggTc;
            this.cazy = cazy;
            this.biggReaction = biggReaction;
            this.pfams = pfams;
        }
    }

    public static class MemberEvent {
        public final String level;
        public final String side1;
        public final String side2;

        public MemberEvent(String level, String side1, String side2) {
            this.level = level;
            this.side1 = side1;
            this.side2 = side2;
        }
    }
}
