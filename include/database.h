#pragma once
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
    bool addRecord(const std::string& path);
    bool deleteRecord(const std::string& path);
    bool deleteRecordById(int id);
    bool updateRecord(int id, const std::string& newPath);
    bool recordExists(const std::string& path);

    // 查询操作
    bool getAllRecords(std::vector<std::pair<int, std::string>>& records);
    bool getRecordById(int id, std::string& path);
    int getRecordCount();

    // 批量操作
    bool addRecordsBatch(const std::vector<std::string>& paths);
    bool deleteRecordsBatch(const std::vector<std::string>& paths);
    bool updatePathsOnDirectoryRename(const std::string& oldDir,const std::string& newDir);

    // 获取数据库句柄（用于高级操作）
    sqlite3* getHandle() { return db; }
};

