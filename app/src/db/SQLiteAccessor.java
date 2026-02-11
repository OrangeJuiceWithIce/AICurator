package db;

import model.AiAnalysis;
import model.FileRecord;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class SQLiteAccessor {

    public static final String TAG_AI_ANALYSIS = "ai_analysis";
    private final String dbPath;

    public SQLiteAccessor(String dbPath) {
        this.dbPath = dbPath;
        ensureSchema();
        checkAndRebuildIndex();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    private void ensureSchema() {
        try (Connection conn = connect();
                Statement st = conn.createStatement()) {

            // 1. 基础表
            st.execute("""
                        CREATE TABLE IF NOT EXISTS file_tags (
                          fullpath    TEXT NOT NULL,
                          tag         TEXT NOT NULL,
                          reason      TEXT,
                          updatedTime INTEGER NOT NULL,
                          PRIMARY KEY(fullpath, tag)
                        )
                    """);

            boolean hasOrigin = false, hasRisk = false, hasAdvice = false, hasRaw = false;
            try (ResultSet rs = st.executeQuery("PRAGMA table_info(file_tags)")) {
                while (rs.next()) {
                    String col = rs.getString("name");
                    if ("origin".equalsIgnoreCase(col))
                        hasOrigin = true;
                    if ("risk".equalsIgnoreCase(col))
                        hasRisk = true;
                    if ("advice".equalsIgnoreCase(col))
                        hasAdvice = true;
                    if ("raw".equalsIgnoreCase(col))
                        hasRaw = true;
                }
            }
            if (!hasOrigin)
                st.execute("ALTER TABLE file_tags ADD COLUMN origin TEXT");
            if (!hasRisk)
                st.execute("ALTER TABLE file_tags ADD COLUMN risk INTEGER");
            if (!hasAdvice)
                st.execute("ALTER TABLE file_tags ADD COLUMN advice TEXT");
            if (!hasRaw)
                st.execute("ALTER TABLE file_tags ADD COLUMN raw TEXT");

            st.execute("CREATE INDEX IF NOT EXISTS idx_file_tags_path ON file_tags(fullpath)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_file_tags_tag  ON file_tags(tag)");

            // 2. FTS 索引表
            // 这里我们去掉 tokenize='trigram' 的强制要求，改用 unicode61 (标准分词)
            // 因为我们将通过 SQL 语句层面的 "混合搜索" 来解决模糊匹配问题
            // 这样兼容性最好，也不会出现搜 "poe" 搜不到的问题
            st.execute("""
                        CREATE VIRTUAL TABLE IF NOT EXISTS files_fts
                        USING fts5(fullpath, content='files', content_rowid='id', tokenize='unicode61');
                    """);

            // 3. 触发器
            st.execute(
                    "CREATE TRIGGER IF NOT EXISTS files_ai_insert AFTER INSERT ON files BEGIN INSERT INTO files_fts(rowid, fullpath) VALUES (new.id, new.fullpath); END;");
            st.execute(
                    "CREATE TRIGGER IF NOT EXISTS files_ai_delete AFTER DELETE ON files BEGIN INSERT INTO files_fts(files_fts, rowid, fullpath) VALUES('delete', old.id, old.fullpath); END;");
            st.execute(
                    "CREATE TRIGGER IF NOT EXISTS files_ai_update AFTER UPDATE ON files BEGIN INSERT INTO files_fts(files_fts, rowid, fullpath) VALUES('delete', old.id, old.fullpath); INSERT INTO files_fts(rowid, fullpath) VALUES (new.id, new.fullpath); END;");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void checkAndRebuildIndex() {
        try (Connection conn = connect(); Statement st = conn.createStatement()) {
            boolean filesHasData = false;
            try (ResultSet rs = st.executeQuery("SELECT 1 FROM files LIMIT 1")) {
                if (rs.next())
                    filesHasData = true;
            }

            boolean ftsHasData = false;
            try (ResultSet rs = st.executeQuery("SELECT 1 FROM files_fts LIMIT 1")) {
                if (rs.next())
                    ftsHasData = true;
            }

            if (filesHasData && !ftsHasData) {
                st.execute("INSERT INTO files_fts(files_fts) VALUES('rebuild')");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // -----------------------------
    // 终极搜索方案：混合 FTS + LIKE
    // -----------------------------
    public List<FileRecord> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return searchAll();
        }

        String cleanKey = keyword.replace("\"", "").trim();
        List<FileRecord> list = new ArrayList<>();

        // 使用 Set 防止重复（因为可能 FTS 和 LIKE 搜到了同一个文件）
        Set<String> addedPaths = new HashSet<>();

        // SQL 逻辑解释：
        // 1. 第一部分是 FTS：使用 keyword* (前缀搜索)。
        // 这能极速解决 "poe" 搜到 "poetry" 的问题。
        // 2. UNION
        // 3. 第二部分是 LIKE：使用 %keyword% (全模糊)。
        // 这能解决 "type" 搜到 "poetrytype" 的问题，虽然慢一点，但保证能搜到。
        // 通过 UNION 结合，既满足了项目要求(使用了FTS)，又满足了用户体验(什么都能搜到)。

        String sql = """
                SELECT f.id, f.fullpath, f.fileSize, f.creationTime, f.lastAccessTime, f.lastWriteTime, COALESCE(t.risk, -1) as risk
                FROM files_fts idx
                JOIN files f ON f.id = idx.rowid
                LEFT JOIN file_tags t ON t.fullpath = f.fullpath AND t.tag = ?
                WHERE files_fts MATCH ?

                UNION

                SELECT f.id, f.fullpath, f.fileSize, f.creationTime, f.lastAccessTime, f.lastWriteTime, COALESCE(t.risk, -1) as risk
                FROM files f
                LEFT JOIN file_tags t ON t.fullpath = f.fullpath AND t.tag = ?
                WHERE f.fullpath LIKE ?

                LIMIT 500
                """;

        try (Connection conn = connect();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            // 参数 1: Tag (给 FTS 部分)
            ps.setString(1, TAG_AI_ANALYSIS);

            // 参数 2: FTS 关键词。加上 * 号表示前缀匹配。
            // "poe" -> "poe*" (能匹配 poetry)
            ps.setString(2, "\"" + cleanKey + "*\"");

            // 参数 3: Tag (给 LIKE 部分)
            ps.setString(3, TAG_AI_ANALYSIS);

            // 参数 4: LIKE 关键词。加上 % 号表示包含匹配。
            // "type" -> "%type%" (能匹配 poetrytype)
            ps.setString(4, "%" + cleanKey + "%");

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String path = rs.getString(2); // fullpath
                // 双重保险去重（虽然 UNION 已经去重了，但逻辑上保留 Set 以后扩展方便）
                if (!addedPaths.contains(path)) {
                    list.add(mapRow(rs));
                    addedPaths.add(path);
                }
            }

        } catch (SQLException e) {
            // 如果出错，只用旧方法兜底
            return searchLegacy(cleanKey);
        }
        return list;
    }

    private List<FileRecord> searchAll() {
        List<FileRecord> list = new ArrayList<>();
        String sql = """
                SELECT f.id, f.fullpath, f.fileSize, f.creationTime, f.lastAccessTime, f.lastWriteTime, COALESCE(t.risk, -1)
                FROM files f
                LEFT JOIN file_tags t ON t.fullpath = f.fullpath AND t.tag = ?
                ORDER BY f.id DESC LIMIT 100
                """;
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, TAG_AI_ANALYSIS);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                list.add(mapRow(rs));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    private List<FileRecord> searchLegacy(String keyword) {
        List<FileRecord> list = new ArrayList<>();
        String sql = """
                SELECT f.id, f.fullpath, f.fileSize, f.creationTime, f.lastAccessTime, f.lastWriteTime, COALESCE(t.risk, -1)
                FROM files f
                LEFT JOIN file_tags t ON t.fullpath = f.fullpath AND t.tag = ?
                WHERE f.fullpath LIKE ?
                ORDER BY f.id LIMIT 500
                """;
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, TAG_AI_ANALYSIS);
            ps.setString(2, "%" + keyword + "%");
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                list.add(mapRow(rs));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // 注意：这里 ResultSet 的索引变了，因为 SELECT 里多了 id 在第一位
    private FileRecord mapRow(ResultSet rs) throws SQLException {
        return new FileRecord(
                rs.getString(2), // fullpath
                rs.getLong(3), // fileSize
                fileTime(rs.getLong(4)),
                fileTime(rs.getLong(5)),
                fileTime(rs.getLong(6)),
                rs.getInt(7) // risk
        );
    }

    public FileRecord getByPath(String fullpath) {
        String sql = "SELECT * FROM files WHERE fullpath = ? LIMIT 1";
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fullpath);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new FileRecord(
                        rs.getString("fullpath"), rs.getLong("fileSize"),
                        fileTime(rs.getLong("creationTime")), fileTime(rs.getLong("lastAccessTime")),
                        fileTime(rs.getLong("lastWriteTime")), -1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public AiAnalysis getAiAnalysis(String fullpath) {
        String sql = "SELECT origin, risk, advice, raw FROM file_tags WHERE fullpath = ? AND tag = ? LIMIT 1";
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fullpath);
            ps.setString(2, TAG_AI_ANALYSIS);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                AiAnalysis a = new AiAnalysis();
                a.origin = safe(rs.getString(1));
                a.risk = rs.getInt(2);
                a.advice = safe(rs.getString(3));
                a.raw = safe(rs.getString(4));
                return a;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public boolean upsertAiAnalysis(String fullpath, AiAnalysis a) {
        String sql = """
                INSERT INTO file_tags(fullpath, tag, reason, updatedTime, origin, risk, advice, raw)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(fullpath, tag) DO UPDATE SET
                  reason=excluded.reason, updatedTime=excluded.updatedTime,
                  origin=excluded.origin, risk=excluded.risk,
                  advice=excluded.advice, raw=excluded.raw
                """;
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fullpath);
            ps.setString(2, TAG_AI_ANALYSIS);
            ps.setString(3, "compat");
            ps.setLong(4, System.currentTimeMillis() / 1000);
            ps.setString(5, a.origin);
            ps.setInt(6, a.risk);
            ps.setString(7, a.advice);
            ps.setString(8, a.raw);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean delete(String fullpath) {
        try (Connection conn = connect()) {
            conn.setAutoCommit(false);
            try (PreparedStatement p1 = conn.prepareStatement("DELETE FROM file_tags WHERE fullpath=?");
                    PreparedStatement p2 = conn.prepareStatement("DELETE FROM files WHERE fullpath=?")) {
                p1.setString(1, fullpath);
                p1.executeUpdate();
                p2.setString(1, fullpath);
                int r = p2.executeUpdate();
                conn.commit();
                return r > 0;
            } catch (Exception e) {
                conn.rollback();
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public boolean rename(String oldPath, String newPath) {
        try (Connection conn = connect()) {
            conn.setAutoCommit(false);
            try (PreparedStatement p1 = conn.prepareStatement("UPDATE file_tags SET fullpath=? WHERE fullpath=?");
                    PreparedStatement p2 = conn.prepareStatement("UPDATE files SET fullpath=? WHERE fullpath=?")) {
                p1.setString(1, newPath);
                p1.setString(2, oldPath);
                p1.executeUpdate();
                p2.setString(1, newPath);
                p2.setString(2, oldPath);
                int r = p2.executeUpdate();
                conn.commit();
                return r > 0;
            } catch (Exception e) {
                conn.rollback();
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private String fileTime(long t) {
        if (t == 0)
            return "";
        long ms = t / 10000 - 11644473600000L;
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(ms));
    }
}