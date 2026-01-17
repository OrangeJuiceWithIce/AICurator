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

    const char* createFilesSQL =
        "CREATE TABLE IF NOT EXISTS files ("
        "id INTEGER PRIMARY KEY AUTOINCREMENT, "
        "fullpath TEXT UNIQUE NOT NULL, "
        "fileSize INTEGER NOT NULL DEFAULT 0, "
        "creationTime INTEGER NOT NULL DEFAULT 0, "
        "lastAccessTime INTEGER NOT NULL DEFAULT 0, "
        "lastWriteTime INTEGER NOT NULL DEFAULT 0"
        ");";

    const char* createAiSQL =
        "CREATE TABLE IF NOT EXISTS AiAnalysis ("
        "fullpath TEXT PRIMARY KEY, "
        "origin TEXT, "
        "risk INTEGER NOT NULL DEFAULT 0, "
        "advice TEXT, "
        "raw TEXT, "
        "updatedTime INTEGER NOT NULL"
        ");"
        "CREATE INDEX IF NOT EXISTS idx_AiAnalysis_risk ON AiAnalysis(risk);"
        "CREATE INDEX IF NOT EXISTS idx_AiAnalysis_time ON AiAnalysis(updatedTime);";

    char* errMsg = nullptr;
    if (sqlite3_exec(db, createFilesSQL, nullptr, nullptr, &errMsg) != SQLITE_OK) {
        std::cerr << "[ERROR] SQL 错误(create files): " << errMsg << std::endl;
        sqlite3_free(errMsg);
        return false;
    }
    if (sqlite3_exec(db, createAiSQL, nullptr, nullptr, &errMsg) != SQLITE_OK) {
        std::cerr << "[ERROR] SQL 错误(create AiAnalysis): " << errMsg << std::endl;
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

    if (sqlite3_exec(db, "BEGIN TRANSACTION;", nullptr, nullptr, nullptr) != SQLITE_OK) {
        std::cerr << "[ERROR] 开始事务失败: " << sqlite3_errmsg(db) << std::endl;
        return false;
    }

    bool ok = true;

    // 1) 先删 AiAnalysis
    {
        sqlite3_stmt* stmt = nullptr;
        const char* sql = "DELETE FROM AiAnalysis WHERE fullpath = ?;";
        if (sqlite3_prepare_v2(db, sql, -1, &stmt, nullptr) != SQLITE_OK) {
            std::cerr << "[ERROR] 准备语句失败(delete AiAnalysis): " << sqlite3_errmsg(db) << std::endl;
            ok = false;
        } else {
            sqlite3_bind_text(stmt, 1, path.c_str(), -1, SQLITE_TRANSIENT);
            if (sqlite3_step(stmt) != SQLITE_DONE) ok = false;
            sqlite3_finalize(stmt);
        }
    }

    // 2) 再删 files
    if (ok) {
        sqlite3_stmt* stmt = nullptr;
        const char* sql = "DELETE FROM files WHERE fullpath = ?;";
        if (sqlite3_prepare_v2(db, sql, -1, &stmt, nullptr) != SQLITE_OK) {
            std::cerr << "[ERROR] 准备语句失败(delete files): " << sqlite3_errmsg(db) << std::endl;
            ok = false;
        } else {
            sqlite3_bind_text(stmt, 1, path.c_str(), -1, SQLITE_TRANSIENT);
            if (sqlite3_step(stmt) != SQLITE_DONE) ok = false;
            sqlite3_finalize(stmt);
        }
    }

    if (ok) {
        if (sqlite3_exec(db, "COMMIT;", nullptr, nullptr, nullptr) != SQLITE_OK) {
            sqlite3_exec(db, "ROLLBACK;", nullptr, nullptr, nullptr);
            return false;
        }
        return true;
    } else {
        sqlite3_exec(db, "ROLLBACK;", nullptr, nullptr, nullptr);
        return false;
    }
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
    if (paths.empty()) return true;

    if (sqlite3_exec(db, "BEGIN TRANSACTION;", nullptr, nullptr, nullptr) != SQLITE_OK) {
        std::cerr << "[ERROR] 开始事务失败: " << sqlite3_errmsg(db) << std::endl;
        return false;
    }

    bool ok = true;

    // 1) 批量删 AiAnalysis
    {
        sqlite3_stmt* stmt = nullptr;
        const char* sql = "DELETE FROM AiAnalysis WHERE fullpath = ?;";
        if (sqlite3_prepare_v2(db, sql, -1, &stmt, nullptr) != SQLITE_OK) {
            std::cerr << "[ERROR] 准备语句失败(delete AiAnalysis batch): " << sqlite3_errmsg(db) << std::endl;
            ok = false;
        } else {
            for (const auto& p : paths) {
                sqlite3_bind_text(stmt, 1, p.c_str(), -1, SQLITE_TRANSIENT);
                if (sqlite3_step(stmt) != SQLITE_DONE) { ok = false; break; }
                sqlite3_reset(stmt);
                sqlite3_clear_bindings(stmt);
            }
            sqlite3_finalize(stmt);
        }
    }

    // 2) 批量删 files
    if (ok) {
        sqlite3_stmt* stmt = nullptr;
        const char* sql = "DELETE FROM files WHERE fullpath = ?;";
        if (sqlite3_prepare_v2(db, sql, -1, &stmt, nullptr) != SQLITE_OK) {
            std::cerr << "[ERROR] 准备语句失败(delete files batch): " << sqlite3_errmsg(db) << std::endl;
            ok = false;
        } else {
            for (const auto& p : paths) {
                sqlite3_bind_text(stmt, 1, p.c_str(), -1, SQLITE_TRANSIENT);
                if (sqlite3_step(stmt) != SQLITE_DONE) { ok = false; break; }
                sqlite3_reset(stmt);
                sqlite3_clear_bindings(stmt);
            }
            sqlite3_finalize(stmt);
        }
    }

    if (ok) {
        if (sqlite3_exec(db, "COMMIT;", nullptr, nullptr, nullptr) != SQLITE_OK) {
            sqlite3_exec(db, "ROLLBACK;", nullptr, nullptr, nullptr);
            return false;
        }
        return true;
    } else {
        sqlite3_exec(db, "ROLLBACK;", nullptr, nullptr, nullptr);
        return false;
    }
}

bool Database::updatePathsOnDirectoryRename(const std::string& oldPath,
                                            const std::string& newPath)
{
    if (!isOpen) {
        std::cerr << "[ERROR] 数据库未打开" << std::endl;
        return false;
    }

    // oldPrefix = oldPath + "\"，用于更新子路径
    std::string oldPrefix = oldPath;
    if (!oldPrefix.empty() && oldPrefix.back() != '\\') oldPrefix += "\\";

    std::string newPrefix = newPath;
    if (!newPrefix.empty() && newPrefix.back() != '\\') newPrefix += "\\";

    if (sqlite3_exec(db, "BEGIN TRANSACTION;", nullptr, nullptr, nullptr) != SQLITE_OK) {
        std::cerr << "[ERROR] 开始事务失败: " << sqlite3_errmsg(db) << std::endl;
        return false;
    }

    bool ok = true;

    auto updateExact = [&](const char* table) -> bool {
        // 精确改名：oldPath -> newPath
        std::string sql =
            std::string("UPDATE ") + table + " SET fullpath = ? WHERE fullpath = ?;";

        sqlite3_stmt* stmt = nullptr;
        if (sqlite3_prepare_v2(db, sql.c_str(), -1, &stmt, nullptr) != SQLITE_OK) {
            std::cerr << "[ERROR] exact rename prepare(" << table << "): " << sqlite3_errmsg(db) << std::endl;
            return false;
        }

        sqlite3_bind_text(stmt, 1, newPath.c_str(), -1, SQLITE_TRANSIENT);
        sqlite3_bind_text(stmt, 2, oldPath.c_str(), -1, SQLITE_TRANSIENT);

        bool success = (sqlite3_step(stmt) == SQLITE_DONE);
        sqlite3_finalize(stmt);
        return success;
    };

    auto updatePrefix = [&](const char* table) -> bool {
        // 前缀改名：oldPath\xxx -> newPath\xxx （只替换一次前缀，避免 REPLACE 的潜在误替换）
        std::string sql =
            std::string("UPDATE ") + table +
            " SET fullpath = ? || SUBSTR(fullpath, LENGTH(?) + 1) "
            " WHERE fullpath LIKE ?;";

        sqlite3_stmt* stmt = nullptr;
        if (sqlite3_prepare_v2(db, sql.c_str(), -1, &stmt, nullptr) != SQLITE_OK) {
            std::cerr << "[ERROR] prefix rename prepare(" << table << "): " << sqlite3_errmsg(db) << std::endl;
            return false;
        }

        std::string likePattern = oldPrefix + "%";
        sqlite3_bind_text(stmt, 1, newPrefix.c_str(), -1, SQLITE_TRANSIENT);
        sqlite3_bind_text(stmt, 2, oldPrefix.c_str(), -1, SQLITE_TRANSIENT);
        sqlite3_bind_text(stmt, 3, likePattern.c_str(), -1, SQLITE_TRANSIENT);

        bool success = (sqlite3_step(stmt) == SQLITE_DONE);
        sqlite3_finalize(stmt);
        return success;
    };

    // 1) 先做精确改名（文件改名 / 目录自身改名）
    if (ok) ok = updateExact("files");
    if (ok) ok = updateExact("AiAnalysis");   // 如果你还没改成 AiAnalysis，这里换成 file_tags

    // 2) 再做前缀批量改名（目录改名才会生效）
    if (ok) ok = updatePrefix("files");
    if (ok) ok = updatePrefix("AiAnalysis");  // 同上

    if (ok) {
        if (sqlite3_exec(db, "COMMIT;", nullptr, nullptr, nullptr) != SQLITE_OK) {
            std::cerr << "[ERROR] 提交事务失败: " << sqlite3_errmsg(db) << std::endl;
            sqlite3_exec(db, "ROLLBACK;", nullptr, nullptr, nullptr);
            return false;
        }
        return true;
    } else {
        sqlite3_exec(db, "ROLLBACK;", nullptr, nullptr, nullptr);
        return false;
    }
}