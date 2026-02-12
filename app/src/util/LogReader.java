package util;

import model.LogEntry;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class LogReader {

    // ✅ 与 OpLogger 保持一致：logs/ops.log
    private static final Path LOG_DIR = Paths.get("logs");
    private static final Path LOG_FILE = LOG_DIR.resolve("ops.log");

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private LogReader() {}

    /** ✅ 只读 DELETE/RENAME */
    public static List<LogEntry> readAllDeleteRename() {
        List<LogEntry> res = new ArrayList<>();
        if (!Files.exists(LOG_FILE)) return res;

        try (BufferedReader br = Files.newBufferedReader(LOG_FILE, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                LogEntry e = parseLine(line);
                if (e == null) continue;
                if (!"DELETE".equals(e.action) && !"RENAME".equals(e.action)) continue;
                res.add(e);
            }
        } catch (Exception ignored) {}
        return res;
    }

    // 你的日志格式：time\tACTION\tPATH\tDETAIL
    private static LogEntry parseLine(String raw) {
        if (raw == null) return null;
        String line = raw.trim();
        if (line.isEmpty()) return null;

        String[] parts = line.split("\\t", 4);
        if (parts.length < 4) return null;

        try {
            LocalDateTime t = LocalDateTime.parse(parts[0].trim(), TS_FMT);
            String action = parts[1].trim();
            String path = parts[2].trim();
            String detail = parts[3].trim();
            return new LogEntry(t, action, path, detail, raw);
        } catch (Exception e) {
            return null;
        }
    }

    public static void clear() throws IOException {
        if (!Files.exists(LOG_DIR)) Files.createDirectories(LOG_DIR);
        Files.write(LOG_FILE, new byte[0], StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /** 删掉第一条与 rawLine 完全一致的日志行（用于撤回后删除 DELETE 日志） */
    public static void removeExactLine(String rawLine) throws IOException {
        if (rawLine == null || rawLine.isEmpty()) return;
        if (!Files.exists(LOG_FILE)) return;

        List<String> lines = Files.readAllLines(LOG_FILE, StandardCharsets.UTF_8);
        boolean removed = false;

        List<String> out = new ArrayList<>(lines.size());
        for (String l : lines) {
            if (!removed && l.equals(rawLine)) {
                removed = true;
                continue;
            }
            out.add(l);
        }

        if (removed) {
            if (!Files.exists(LOG_DIR)) Files.createDirectories(LOG_DIR);
            Files.write(LOG_FILE, out, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    /** 方便排查：打印 LogReader 实际在读哪个文件 */
    public static Path getLogFilePath() {
        return LOG_FILE.toAbsolutePath().normalize();
    }
}
