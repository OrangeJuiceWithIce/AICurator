package service;

import db.SQLiteAccessor;
import model.AiAnalysis;
import model.FileRecord;
import util.DeepSeekClient;

public class AiAnalysisService {
    private final SQLiteAccessor db;

    public AiAnalysisService(SQLiteAccessor db) {
        this.db = db;
    }

    public AiAnalysis getCached(String path) {
        return db.getAiAnalysis(path);
    }

    public AiAnalysis analyseAndCache(String path) {
        FileRecord record = db.getByPath(path);
        if (record == null) return null;

        AiAnalysis a = DeepSeekClient.analyseFileStructured(record);
        db.upsertAiAnalysis(path, a);
        return a;
    }
}
