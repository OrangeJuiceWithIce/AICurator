package model;

public class FileRecord {
    public String fullpath;
    public long size;
    public String creation;
    public String lastAccess;
    public String lastWrite;

    public int aiRisk;

    public FileRecord(String fullpath, long size,
                      String creation, String lastAccess, String lastWrite) {
        this(fullpath, size, creation, lastAccess, lastWrite, -1);
    }

    public FileRecord(String fullpath, long size,
                      String creation, String lastAccess, String lastWrite,
                      int aiRisk) {
        this.fullpath = fullpath;
        this.size = size;
        this.creation = creation;
        this.lastAccess = lastAccess;
        this.lastWrite = lastWrite;
        this.aiRisk = aiRisk;
    }
}
