#pragma once
#include <windows.h>
#include <string>
#include <atomic>

// 前向声明
class Database;

// 文件监控类
class DirectoryMonitor {
private:
    HANDLE dirHandle;
    std::string monitorPath;
    std::atomic<bool> isRunning{false};
    Database* db;

public:
    DirectoryMonitor(const std::string& path, Database* database);
    ~DirectoryMonitor();

    // 开始监控（阻塞调用）
    void start();
    
    // 停止监控
    void stop() { isRunning = false; }
    
    // 检查是否正在运行
    bool running() const { return isRunning; }
};

