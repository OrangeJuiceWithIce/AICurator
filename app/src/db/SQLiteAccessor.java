package db;

import model.FileRecord;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SQLiteAccessor {

    private final String dbPath;

    public SQLiteAccessor(String dbPath) {
        this.dbPath = dbPath;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    public List<FileRecord> search(String keyword) {
        List<FileRecord> list = new ArrayList<>();

        String sql = "SELECT fullpath, fileSize, creationTime, lastAccessTime, lastWriteTime " +
                "FROM files WHERE fullpath LIKE ? ORDER BY id LIMIT 1000";

        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, "%" + keyword + "%");

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new FileRecord(
                        rs.getString(1),
                        rs.getLong(2),
                        fileTime(rs.getLong(3)),
                        fileTime(rs.getLong(4)),
                        fileTime(rs.getLong(5))
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean delete(String fullpath) {
        String sql = "DELETE FROM files WHERE fullpath = ?";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fullpath);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean rename(String oldPath, String newPath) {
        String sql = "UPDATE files SET fullpath = ? WHERE fullpath = ?";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPath);
            ps.setString(2, oldPath);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String fileTime(long filetime) {
        if (filetime == 0) return "";

        long msSince1601 = filetime / 10000;
        long epochDiff = 11644473600000L;
        long ms = msSince1601 - epochDiff;

        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new java.util.Date(ms));
    }

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
                        fileTime(rs.getLong("lastWriteTime"))
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}
