package model;

public class FileRecord {
    public String fullpath;
    public long size;
    public String creation;
    public String lastAccess;
    public String lastWrite;

    public FileRecord(String fullpath, long size,
                      String creation, String lastAccess, String lastWrite) {
        this.fullpath = fullpath;
        this.size = size;
        this.creation = creation;
        this.lastAccess = lastAccess;
        this.lastWrite = lastWrite;
    }
}