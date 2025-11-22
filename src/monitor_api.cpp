#include "../include/monitor_api.h"
#include "../include/monitor.h"
#include "../include/database.h"
#include "../include/util.h"
#include <thread>
#include <atomic>
#include <memory>
#include <io.h>
#include <fcntl.h>

static std::unique_ptr<Database> g_db;
static std::unique_ptr<DirectoryMonitor> g_monitor;
static std::thread g_monitorThread;
static std::atomic<bool> g_running{false};

int __stdcall StartFileMonitor(const char* monitorPath, const char* dbPath) {
    if (g_running.load()) {
        return 1;
    }

    try {
        g_db = std::make_unique<Database>(dbPath);
        if (!g_db->open() || !g_db->createTable()) {
            return 2;
        }

        std::string path(monitorPath);
        g_monitor = std::make_unique<DirectoryMonitor>(path, g_db.get());

        g_running = true;
        g_monitorThread = std::thread([]() {
            g_monitor->start();   // 阻塞在内部循环
            g_running = false;
        });
        return 0;
    } catch (...) {
        return -1;
    }
}

void __stdcall StopFileMonitor() {
    if (!g_running.load())
        return;

    if (g_monitor) {
        g_monitor->stop();
    }
    if (g_monitorThread.joinable()) {
        g_monitorThread.join();
    }
    if (g_db) {
        g_db->close();
    }

    g_monitor.reset();
    g_db.reset();
    g_running = false;
}