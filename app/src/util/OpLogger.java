package util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class OpLogger {

    private static final Object LOCK = new Object();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    // 默认输出到项目目录下 logs/ops.log
    private static final Path LOG_DIR = Paths.get("logs");
    private static final Path LOG_FILE = LOG_DIR.resolve("ops.log");

    private OpLogger() {}

    public static void log(String action, String path) {
        log(action, path, "");
    }

    public static void log(String action, String path, String detail) {
        String ts = LocalDateTime.now().format(TS);
        String a = safe(action);
        String p = safe(path);
        String d = safe(detail);

        // 一行日志：时间\t操作\t文件\t详情
        String line = ts + "\t" + a + "\t" + p + "\t" + d;

        synchronized (LOCK) {
            try {
                if (!Files.exists(LOG_DIR)) Files.createDirectories(LOG_DIR);

                try (BufferedWriter bw = Files.newBufferedWriter(
                        LOG_FILE,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND
                )) {
                    bw.write(line);
                    bw.newLine();
                }
            } catch (IOException ignored) {
                // 日志失败不影响主流程（避免 UI 卡死/崩）
            }
        }
    }

    private static String safe(String s) {
        if (s == null) return "";
        // 避免把换行写进日志导致“多行污染”
        return s.replace("\r", "\\r").replace("\n", "\\n");
    }
}
