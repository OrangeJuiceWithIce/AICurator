package util;

import model.LogEntry;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class LogReader {

    private static final Path LOG_FILE = Paths.get("logs").resolve("ops.log");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private LogReader() {}

    /** 读取全部（保持原能力，若你其他地方还用得到） */
    public static List<LogEntry> readAll() {
        return readInternal(false);
    }

    /** 只读取 DELETE / RENAME（与你现在的日志策略一致） */
    public static List<LogEntry> readAllDeleteRename() {
        return readInternal(true);
    }

    /** 清空日志文件（截断为 0 字节） */
    public static void clear() throws IOException {
        Path dir = LOG_FILE.getParent();
        if (dir != null && !Files.exists(dir)) Files.createDirectories(dir);
        Files.writeString(LOG_FILE, "", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static List<LogEntry> readInternal(boolean onlyDeleteRename) {
        if (!Files.exists(LOG_FILE)) return Collections.emptyList();

        List<LogEntry> res = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(LOG_FILE, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                // expected: ts \t action \t path \t detail
                String[] parts = line.split("\t", -1);
                if (parts.length < 3) continue;

                LocalDateTime t;
                try {
                    t = LocalDateTime.parse(parts[0], TS);
                } catch (Exception e) {
                    continue;
                }

                String action = parts[1];
                if (onlyDeleteRename && !( "DELETE".equals(action) || "RENAME".equals(action) )) {
                    continue;
                }

                String path = parts[2];
                String detail = (parts.length >= 4) ? parts[3] : "";

                res.add(new LogEntry(t, action, path, detail));
            }
        } catch (IOException ignored) {}

        // 时间倒序
        res.sort((a, b) -> b.time.compareTo(a.time));
        return res;
    }
}
