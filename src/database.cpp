#include "../include/database.h"
#include "../include/volume.h"

#include <iostream>
#include <vector>

Database::Database(const std::string& path) : db(nullptr), dbPath(path), isOpen(false) {
}

Database::~Database() {
    close();
}

bool Database::open() {
    if (isOpen) {
        return true;
    }

    if (sqlite3_open(dbPath.c_str(), &db) != SQLITE_OK) {
        std::cerr << "[ERROR] 无法打开数据库: " << sqlite3_errmsg(db) << std::endl;
        return false;
    }

    isOpen = true;
    return true;
}

bool Database::close() {
    if (!isOpen || !db) {
        return true;
    }

    if (sqlite3_close(db) != SQLITE_OK) {
        std::cerr << "[ERROR] 关闭数据库失败: " << sqlite3_errmsg(db) << std::endl;
        return false;
    }

    db = nullptr;
    isOpen = false;
    return true;
}

bool Database::createTable() {
    if (!isOpen) {
        std::cerr << "[ERROR] 数据库未打开" << std::endl;
        return false;
    }

    const char* createTableSQL =
        "CREATE TABLE IF NOT EXISTS files ("
        "id INTEGER PRIMARY KEY AUTOINCREMENT, "
        "fullpath TEXT UNIQUE NOT NULL, "
        "fileSize INTEGER NOT NULL DEFAULT 0, "
        "creationTime INTEGER NOT NULL DEFAULT 0, "
        "lastAccessTime INTEGER NOT NULL DEFAULT 0, "
        "lastWriteTime INTEGER NOT NULL DEFAULT 0"
        ");";

    char* errMsg = nullptr;
    if (sqlite3_exec(db, createTableSQL, nullptr, nullptr, &errMsg) != SQLITE_OK) {
        std::cerr << "[ERROR] SQL 错误: " << errMsg << std::endl;
        sqlite3_free(errMsg);
        return false;
    }

    return true;
}

bool Database::dropTable() {
    if (!isOpen) {
        std::cerr << "[ERROR] 数据库未打开" << std::endl;
        return false;
    }

    const char* dropTableSQL = "DROP TABLE IF EXISTS files;";
    char* errMsg = nullptr;
    if (sqlite3_exec(db, dropTableSQL, nullptr, nullptr, &errMsg) != SQLITE_OK) {
        std::cerr << "[ERROR] SQL 错误: " << errMsg << std::endl;
        sqlite3_free(errMsg);
        return false;
    }

    return true;
}

bool Database::addRecord(const FileRecord& record) {
    if (!isOpen) return false;

    sqlite3_stmt* stmt = nullptr;
    const char* sql = 
        "INSERT OR IGNORE INTO files(fullpath, fileSize, creationTime, lastAccessTime, lastWriteTime) "
        "VALUES (?, ?, ?, ?, ?);";

    if (sqlite3_prepare_v2(db, sql, -1, &stmt, nullptr) != SQLITE_OK) {
        std::cerr << "[ERROR] 准备语句失败: " << sqlite3_errmsg(db) << std::endl;
        return false;
    }

    sqlite3_bind_text(stmt, 1, record.fullpath.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int64(stmt, 2, record.fileSize);
    sqlite3_bind_int64(stmt, 3, *reinterpret_cast<const sqlite3_int64*>(&record.creationTime));
    sqlite3_bind_int64(stmt, 4, *reinterpret_cast<const sqlite3_int64*>(&record.lastAccessTime));
    sqlite3_bind_int64(stmt, 5, *reinterpret_cast<const sqlite3_int64*>(&record.lastWriteTime));

    int result = sqlite3_step(stmt);
    sqlite3_finalize(stmt);

    return result == SQLITE_DONE;
}

bool Database::deleteRecord(const std::string& path) {
    if (!isOpen) {
        std::cerr << "[ERROR] 数据库未打开" << std::endl;
        return false;
    }

    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(db, "DELETE FROM files WHERE fullpath = ?;", -1, &stmt, nullptr) != SQLITE_OK) {
        std::cerr << "[ERROR] 准备语句失败: " << sqlite3_errmsg(db) << std::endl;
        return false;
    }

    sqlite3_bind_text(stmt, 1, path.c_str(), -1, SQLITE_TRANSIENT);
    int result = sqlite3_step(stmt);
    sqlite3_finalize(stmt);

    if (result != SQLITE_DONE) {
        std::cerr << "[ERROR] 删除记录失败: " << sqlite3_errmsg(db) << std::endl;
        return false;
    }

    return true;
}

bool Database::recordExists(const std::string& path) {
    if (!isOpen) {
        std::cerr << "[ERROR] 数据库未打开" << std::endl;
        return false;
    }

    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(db, "SELECT COUNT(*) FROM files WHERE fullpath = ?;", -1, &stmt, nullptr) != SQLITE_OK) {
        std::cerr << "[ERROR] 准备语句失败: " << sqlite3_errmsg(db) << std::endl;
        return false;
    }

    sqlite3_bind_text(stmt, 1, path.c_str(), -1, SQLITE_TRANSIENT);
    bool exists = false;
    if (sqlite3_step(stmt) == SQLITE_ROW) {
        exists = sqlite3_column_int(stmt, 0) > 0;
    }
    sqlite3_finalize(stmt);

    return exists;
}

int Database::getRecordCount() {
    if (!isOpen) {
        std::cerr << "[ERROR] 数据库未打开" << std::endl;
        return -1;
    }

    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(db, "SELECT COUNT(*) FROM files;", -1, &stmt, nullptr) != SQLITE_OK) {
        std::cerr << "[ERROR] 准备语句失败: " << sqlite3_errmsg(db) << std::endl;
        return -1;
    }

    int count = 0;
    if (sqlite3_step(stmt) == SQLITE_ROW) {
        count = sqlite3_column_int(stmt, 0);
    }
    sqlite3_finalize(stmt);

    return count;
}

bool Database::addRecordsBatch(const std::vector<FileRecord>& records) {
    if (!isOpen) return false;

    if (sqlite3_exec(db, "BEGIN TRANSACTION;", nullptr, nullptr, nullptr) != SQLITE_OK) {
        std::cerr << "[ERROR] 开始事务失败: " << sqlite3_errmsg(db) << std::endl;
        return false;
    }

    sqlite3_stmt* stmt = nullptr;
    const char* sql = 
        "INSERT OR IGNORE INTO files(fullpath, fileSize, creationTime, lastAccessTime, lastWriteTime) "
        "VALUES (?, ?, ?, ?, ?);";

    if (sqlite3_prepare_v2(db, sql, -1, &stmt, nullptr) != SQLITE_OK) {
        std::cerr << "[ERROR] 准备语句失败: " << sqlite3_errmsg(db) << std::endl;
        sqlite3_exec(db, "ROLLBACK;", nullptr, nullptr, nullptr);
        return false;
    }

    for (const auto& record : records) {
        sqlite3_bind_text(stmt, 1, record.fullpath.c_str(), -1, SQLITE_TRANSIENT);
        sqlite3_bind_int64(stmt, 2, record.fileSize);
        sqlite3_bind_int64(stmt, 3, *reinterpret_cast<const sqlite3_int64*>(&record.creationTime));
        sqlite3_bind_int64(stmt, 4, *reinterpret_cast<const sqlite3_int64*>(&record.lastAccessTime));
        sqlite3_bind_int64(stmt, 5, *reinterpret_cast<const sqlite3_int64*>(&record.lastWriteTime));

        if (sqlite3_step(stmt) != SQLITE_DONE) {
            std::cerr << "[ERROR] 批量插入失败: " << sqlite3_errmsg(db) << std::endl;
            sqlite3_finalize(stmt);
            sqlite3_exec(db, "ROLLBACK;", nullptr, nullptr, nullptr);
            return false;
        }
        sqlite3_reset(stmt);
    }

    sqlite3_finalize(stmt);

    if (sqlite3_exec(db, "COMMIT;", nullptr, nullptr, nullptr) != SQLITE_OK) {
        std::cerr << "[ERROR] 提交事务失败: " << sqlite3_errmsg(db) << std::endl;
        sqlite3_exec(db, "ROLLBACK;", nullptr, nullptr, nullptr);
        return false;
    }

    return true;
}

bool Database::deleteRecordsBatch(const std::vector<std::string>& paths) {
    if (!isOpen) {
        std::cerr << "[ERROR] 数据库未打开" << std::endl;
        return false;
    }

    // 开始事务
    if (sqlite3_exec(db, "BEGIN TRANSACTION;", nullptr, nullptr, nullptr) != SQLITE_OK) {
        std::cerr << "[ERROR] 开始事务失败: " << sqlite3_errmsg(db) << std::endl;
        return false;
    }

    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(db, "DELETE FROM files WHERE fullpath = ?;", -1, &stmt, nullptr) != SQLITE_OK) {
        std::cerr << "[ERROR] 准备语句失败: " << sqlite3_errmsg(db) << std::endl;
        sqlite3_exec(db, "ROLLBACK;", nullptr, nullptr, nullptr);
        return false;
    }

    for (const auto& path : paths) {
        sqlite3_bind_text(stmt, 1, path.c_str(), -1, SQLITE_TRANSIENT);
        if (sqlite3_step(stmt) != SQLITE_DONE) {
            std::cerr << "[ERROR] 批量删除失败: " << sqlite3_errmsg(db) << std::endl;
            sqlite3_finalize(stmt);
            sqlite3_exec(db, "ROLLBACK;", nullptr, nullptr, nullptr);
            return false;
        }
        sqlite3_reset(stmt);
    }

    sqlite3_finalize(stmt);

    // 提交事务
    if (sqlite3_exec(db, "COMMIT;", nullptr, nullptr, nullptr) != SQLITE_OK) {
        std::cerr << "[ERROR] 提交事务失败: " << sqlite3_errmsg(db) << std::endl;
        sqlite3_exec(db, "ROLLBACK;", nullptr, nullptr, nullptr);
        return false;
    }

    return true;
}

bool Database::updatePathsOnDirectoryRename(
    const std::string& oldDir,
    const std::string& newDir)
{
    std::string oldPrefix = oldDir;
    if (oldPrefix.back() != '\\') oldPrefix += "\\";

    std::string newPrefix = newDir;
    if (newPrefix.back() != '\\') newPrefix += "\\";

    sqlite3_stmt* stmt = nullptr;

    std::string sql =
        "UPDATE files SET fullpath = REPLACE(fullpath, ?, ?) "
        "WHERE fullpath LIKE ?;";

    if (sqlite3_prepare_v2(db, sql.c_str(), -1, &stmt, nullptr) != SQLITE_OK){
        std::cout << "[ERROR] SQLite error: " << sqlite3_errmsg(db) << std::endl;
        return false;
    }

    std::string likePattern = oldPrefix + "%";

    sqlite3_bind_text(stmt, 1, oldPrefix.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, newPrefix.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 3, likePattern.c_str(), -1, SQLITE_TRANSIENT);

    bool success = (sqlite3_step(stmt) == SQLITE_DONE);
    sqlite3_finalize(stmt);

    return success;
}