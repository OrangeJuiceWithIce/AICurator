package model;

import java.time.LocalDateTime;

public class LogEntry {
    public LocalDateTime time;
    public String action;
    public String path;
    public String detail;

    public String rawLine;

    public LogEntry(LocalDateTime time, String action, String path, String detail, String rawLine) {
        this.time = time;
        this.action = action;
        this.path = path;
        this.detail = detail;
        this.rawLine = rawLine;
    }
}
