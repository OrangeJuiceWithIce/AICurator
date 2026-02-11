package model;

import java.time.LocalDateTime;

public class LogEntry {
    public final LocalDateTime time;
    public final String action;
    public final String path;
    public final String detail;

    public LogEntry(LocalDateTime time, String action, String path, String detail) {
        this.time = time;
        this.action = action;
        this.path = path;
        this.detail = detail;
    }
}
