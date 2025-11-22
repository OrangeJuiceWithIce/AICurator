#include <iostream>
#include <vector>
#include <thread>

#include "../include/volume.h"
#include "../include/util.h"
#include "../include/database.h"
#include "../include/monitor.h"

int main() {
    SetConsoleOutputCP(CP_UTF8);
    SetConsoleCP(CP_UTF8);

    // 初始化数据库
    Database db("file_index.db");
    if (!db.open()) {
        std::cerr << "[ERROR] 无法打开数据库" << std::endl;
        return 1;
    }

    if (!db.createTable()) {
        std::cerr << "[ERROR] 创建数据库表失败" << std::endl;
        return 1;
    }

    std::cout << "[INFO] 数据库表已准备好。" << std::endl;

    Volume vol('D');  // 要扫描的盘符

    if (!vol.getHandle()) return 1;

    vol.createUSN();  // 即使失败也继续尝试（可能日志已存在）
    if (!vol.getUSNInfo()) return 1;
    if (!vol.getUSNJournal()) return 1;

    std::cout << "\n[INFO] 开始处理文件路径并保存到数据库:\n" << std::endl;

    // 收集所有路径用于批量插入
    std::vector<std::string> paths;
    int count = 0;

    for (const auto& [frn, info] : vol.frnMap) {
        std::wstring fullPath;
        vol.getPath(frn, fullPath);
        std::string utf8Path = wide_to_utf8(fullPath);
        paths.push_back(utf8Path);
        count++;
    }

    // 批量插入到数据库
    std::cout << "\n[INFO] 正在批量插入 " << count << " 条记录到数据库..." << std::endl;
    if (db.addRecordsBatch(paths)) {
        std::cout << "[INFO] 成功插入 " << count << " 条记录。" << std::endl;
        std::cout << "[INFO] 数据库中共有 " << db.getRecordCount() << " 条记录。" << std::endl;
    } else {
        std::cerr << "[ERROR] 批量插入失败" << std::endl;
    }

    vol.deleteUSN();
    vol.closeHandle();
    db.close();

    std::cout << "\n[INFO] 数据库扫描完毕，退出。" << std::endl;
    return 0;
}