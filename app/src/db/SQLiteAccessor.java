package db;

import model.AiAnalysis;
import model.FileRecord;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SQLiteAccessor {

    public static final String TAG_AI_ANALYSIS = "ai_analysis";
    private final String dbPath;

    public SQLiteAccessor(String dbPath) {
        this.dbPath = dbPath;
        // 自动迁移（不依赖 DLL 升级）
        ensureSchema();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    /**
     * 确保 file_tags 表具备新字段（origin/risk/advice/raw）。
     * 兼容 DLL 已创建旧表的情况：旧表只有 (fullpath, tag, reason, updatedTime)
     */
    private void ensureSchema() {
        try (Connection conn = connect();
             Statement st = conn.createStatement()) {

            // 1) 先保证表存在（如果 DLL 还没建也没关系）
            st.execute("""
                CREATE TABLE IF NOT EXISTS file_tags (
                  fullpath    TEXT NOT NULL,
                  tag         TEXT NOT NULL,
                  reason      TEXT,
                  updatedTime INTEGER NOT NULL,
                  PRIMARY KEY(fullpath, tag)
                )
            """);

            // 2) 检查列并补齐
            boolean hasOrigin = false, hasRisk = false, hasAdvice = false, hasRaw = false;
            try (ResultSet rs = st.executeQuery("PRAGMA table_info(file_tags)")) {
                while (rs.next()) {
                    String col = rs.getString("name");
                    if ("origin".equalsIgnoreCase(col)) hasOrigin = true;
                    if ("risk".equalsIgnoreCase(col)) hasRisk = true;
                    if ("advice".equalsIgnoreCase(col)) hasAdvice = true;
                    if ("raw".equalsIgnoreCase(col)) hasRaw = true;
                }
            }

            if (!hasOrigin) st.execute("ALTER TABLE file_tags ADD COLUMN origin TEXT");
            if (!hasRisk)   st.execute("ALTER TABLE file_tags ADD COLUMN risk INTEGER");
            if (!hasAdvice) st.execute("ALTER TABLE file_tags ADD COLUMN advice TEXT");
            if (!hasRaw)    st.execute("ALTER TABLE file_tags ADD COLUMN raw TEXT");

            // 3) 索引（可选但建议）
            st.execute("CREATE INDEX IF NOT EXISTS idx_file_tags_path ON file_tags(fullpath)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_file_tags_tag  ON file_tags(tag)");

        } catch (Exception e) {
            // 不抛出，避免 UI 启动失败；但会影响 tag 功能
            e.printStackTrace();
        }
    }

    // -----------------------------
    // 搜索：带上 AI risk（表格展示字段）
    // -----------------------------
    public List<FileRecord> search(String keyword) {
        List<FileRecord> list = new ArrayList<>();

        // 只把 ai_analysis 的 risk join 出来（避免 GROUP_CONCAT）
        String sql = """
            SELECT
              f.fullpath,
              f.fileSize,
              f.creationTime,
              f.lastAccessTime,
              f.lastWriteTime,
              COALESCE(t.risk, -1) AS ai_risk
            FROM files f
            LEFT JOIN file_tags t
              ON t.fullpath = f.fullpath AND t.tag = ?
            WHERE f.fullpath LIKE ?
            ORDER BY f.id
            LIMIT 1000
            """;

        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, TAG_AI_ANALYSIS);
            ps.setString(2, "%" + keyword + "%");

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new FileRecord(
                        rs.getString(1),
                        rs.getLong(2),
                        fileTime(rs.getLong(3)),
                        fileTime(rs.getLong(4)),
                        fileTime(rs.getLong(5)),
                        rs.getInt(6)
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // 用于 DeepSeek 需要的文件信息
    public FileRecord getByPath(String fullpath) {
        String sql = """
                SELECT fullpath, fileSize, creationTime, lastAccessTime, lastWriteTime
                FROM files
                WHERE fullpath = ?
                LIMIT 1
                """;

        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, fullpath);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new FileRecord(
                        rs.getString("fullpath"),
                        rs.getLong("fileSize"),
                        fileTime(rs.getLong("creationTime")),
                        fileTime(rs.getLong("lastAccessTime")),
                        fileTime(rs.getLong("lastWriteTime")),
                        -1
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // -----------------------------
    // AI 缓存：读取全部字段
    // -----------------------------
    public AiAnalysis getAiAnalysis(String fullpath) {
        String sql = """
            SELECT origin, risk, advice, raw, updatedTime
            FROM file_tags
            WHERE fullpath = ? AND tag = ?
            LIMIT 1
            """;

        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, fullpath);
            ps.setString(2, TAG_AI_ANALYSIS);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                AiAnalysis a = new AiAnalysis();
                a.origin = safe(rs.getString(1));
                a.risk = rs.getObject(2) == null ? 0 : rs.getInt(2);
                a.advice = safe(rs.getString(3));
                a.raw = safe(rs.getString(4));
                return a;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String safe(String s) { return s == null ? "" : s; }

    // -----------------------------
    // AI 缓存：写入多字段（upsert）
    // -----------------------------
    public boolean upsertAiAnalysis(String fullpath, AiAnalysis a) {
        String sql = """
            INSERT INTO file_tags(fullpath, tag, reason, updatedTime, origin, risk, advice, raw)
            VALUES(?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(fullpath, tag) DO UPDATE SET
              reason=excluded.reason,
              updatedTime=excluded.updatedTime,
              origin=excluded.origin,
              risk=excluded.risk,
              advice=excluded.advice,
              raw=excluded.raw
            """;

        String reasonCompat = buildReasonCompat(a); // 兼容旧字段 reason（可用于调试）
        long now = System.currentTimeMillis() / 1000;

        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, fullpath);
            ps.setString(2, TAG_AI_ANALYSIS);
            ps.setString(3, reasonCompat);
            ps.setLong(4, now);

            ps.setString(5, a.origin);
            ps.setInt(6, a.risk);
            ps.setString(7, a.advice);
            ps.setString(8, a.raw);

            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String buildReasonCompat(AiAnalysis a) {
        return "origin=" + safe(a.origin) + "\n" +
               "risk=" + a.risk + "\n" +
               "advice=" + safe(a.advice);
    }

    // -----------------------------
    // UI 删除：同步删 file_tags + files
    // -----------------------------
    public boolean delete(String fullpath) {
        String delTags = "DELETE FROM file_tags WHERE fullpath = ?";
        String delFile = "DELETE FROM files WHERE fullpath = ?";

        try (Connection conn = connect()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps1 = conn.prepareStatement(delTags);
                 PreparedStatement ps2 = conn.prepareStatement(delFile)) {

                ps1.setString(1, fullpath);
                ps1.executeUpdate();

                ps2.setString(1, fullpath);
                int ok = ps2.executeUpdate();

                conn.commit();
                return ok > 0;
            } catch (Exception e) {
                conn.rollback();
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            return false;
        }
    }

    // UI 重命名：同步更新 file_tags + files（单文件重命名）
    public boolean rename(String oldPath, String newPath) {
        String updFile = "UPDATE files SET fullpath = ? WHERE fullpath = ?";
        String updTags = "UPDATE file_tags SET fullpath = ? WHERE fullpath = ?";

        try (Connection conn = connect()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps1 = conn.prepareStatement(updTags);
                 PreparedStatement ps2 = conn.prepareStatement(updFile)) {

                ps1.setString(1, newPath);
                ps1.setString(2, oldPath);
                ps1.executeUpdate();

                ps2.setString(1, newPath);
                ps2.setString(2, oldPath);
                int ok = ps2.executeUpdate();

                conn.commit();
                return ok > 0;
            } catch (Exception e) {
                conn.rollback();
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            return false;
        }
    }

    // FILETIME -> readable time
    private String fileTime(long filetime) {
        if (filetime == 0) return "";

        long msSince1601 = filetime / 10000;
        long epochDiff = 11644473600000L;
        long ms = msSince1601 - epochDiff;

        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new java.util.Date(ms));
    }
}