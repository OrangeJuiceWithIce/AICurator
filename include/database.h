#pragma once
#include "../include/volume.h"

#include "sqlite3.h"
#include <string>
#include <vector>

// 数据库操作类
class Database {
private:
    sqlite3* db;
    std::string dbPath;
    bool isOpen;

public:
    Database(const std::string& path);
    ~Database();

    // 数据库连接管理
    bool open();
    bool close();
    bool isConnected() const { return isOpen && db != nullptr; }

    // 表管理
    bool createTable();
    bool dropTable();

    // 记录操作
    bool addRecord(const FileRecord& record);
    bool deleteRecord(const std::string& path);
    bool recordExists(const std::string& path);

    // 查询操作
    int getRecordCount();

    // 批量操作
    bool addRecordsBatch(const std::vector<FileRecord>& records);
    bool deleteRecordsBatch(const std::vector<std::string>& paths);
    bool updatePathsOnDirectoryRename(const std::string& oldDir,const std::string& newDir);

    // 获取数据库句柄（用于高级操作）
    sqlite3* getHandle() { return db; }
};

