#pragma once
#include <windows.h>

#ifdef MONITOR_API_EXPORTS
    #define MONITOR_API __declspec(dllexport)
#else
    #define MONITOR_API __declspec(dllimport)
#endif

extern "C" {

// 初始化并启动目录监控（内部会起线程，非阻塞）
// monitorPath: 要监控的目录路径（UTF-8/本地多字节字符串，例如 "D:\\test"）
// dbPath: SQLite 数据库文件路径（例如 "file_index.db"）
// 返回值：0表示成功，1表示已经有monitor在监视，2表示打开数据库失败，-1表示有其它错误
MONITOR_API int __stdcall StartFileMonitor(const char* monitorPath,
                                           const char* dbPath);

// 请求停止监控并回收资源
MONITOR_API void __stdcall StopFileMonitor();

} // extern "C"